package zapret.domain

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/** Per-host probe: several HTTPS samples for reachability, latency, and stability. */
data class HostProbeMetrics(
    val successes: Int,
    val attempts: Int,
    /** Mean RTT of successful samples, milliseconds. */
    val avgLatencyMs: Int? = null,
) {
    val ok: Boolean get() = successes > 0

    /** 0–1000 (permille of successful attempts). */
    val stabilityPermille: Int
        get() = if (attempts <= 0) 0 else (successes * 1000) / attempts

    companion object {
        val FAILED = HostProbeMetrics(successes = 0, attempts = 0, avgLatencyMs = null)

        fun fromLatencies(successLatenciesMs: List<Int>, attempts: Int): HostProbeMetrics {
            val ok = successLatenciesMs.size
            val avg = successLatenciesMs.takeIf { it.isNotEmpty() }?.average()?.toInt()
            return HostProbeMetrics(successes = ok, attempts = attempts, avgLatencyMs = avg)
        }
    }
}

data class StrategyProbeRow(
    val strategyId: String,
    val discord: HostProbeMetrics,
    val youtube: HostProbeMetrics,
    val control: HostProbeMetrics,
) {
    val discordOk: Boolean get() = discord.ok
    val youtubeOk: Boolean get() = youtube.ok
    /** Control must be mostly stable, not a single lucky hit. */
    val controlOk: Boolean
        get() = control.successes * 2 >= control.attempts.coerceAtLeast(1) && control.ok

    val usable: Boolean get() = controlOk && (discordOk || youtubeOk)

    /** Mean latency of Discord/YouTube successes (lower is better). */
    val avgTargetLatencyMs: Int?
        get() {
            val samples = listOfNotNull(discord.avgLatencyMs, youtube.avgLatencyMs)
            if (samples.isEmpty()) return null
            return samples.average().toInt()
        }

    /** Combined Discord+YouTube success rate, 0–1000. */
    val targetStabilityPermille: Int
        get() {
            val attempts = discord.attempts + youtube.attempts
            if (attempts <= 0) return 0
            return ((discord.successes + youtube.successes) * 1000) / attempts
        }

    /**
     * Higher is better. Reachability dominates, then stability, then lower latency;
     * mildness is applied by shortlist index on equal scores.
     */
    val score: Long
        get() {
            if (!controlOk) return -1L
            val reach = (if (discordOk) 2 else 0) + (if (youtubeOk) 2 else 0)
            val latencyCap = 9_999
            val latencyScore = latencyCap - (avgTargetLatencyMs ?: latencyCap).coerceIn(0, latencyCap)
            return reach * 10_000_000L +
                targetStabilityPermille * 10_000L +
                latencyScore
        }

    companion object {
        fun failed(strategyId: String) = StrategyProbeRow(
            strategyId = strategyId,
            discord = HostProbeMetrics.FAILED,
            youtube = HostProbeMetrics.FAILED,
            control = HostProbeMetrics.FAILED,
        )
    }
}

data class StrategyProbeReport(
    val results: List<StrategyProbeRow>,
    val winnerId: String?,
    val restoredPrevious: Boolean,
)

/**
 * Flowseal-style profile roulette: try packaged strategies in mildest-first order,
 * probe Discord/YouTube/Apple over HTTPS (reachability + latency + stability).
 */
class StrategyProbe(
    private val lists: EngineListsStore = EngineListsStore(),
    private val service: ZapretControl,
    private val logFile: File = File(ZapretPaths.userDataRoot, "strategy-probe.log"),
) {
    private val cancelRequested = AtomicBoolean(false)

    fun cancel() {
        cancelRequested.set(true)
    }

    fun run(
        candidates: List<String> = SHORTLIST,
        onPhase: (String) -> Unit = {},
    ): StrategyProbeReport {
        cancelRequested.set(false)
        if (!ZapretPaths.isInstalled) {
            error("Движок не установлен")
        }
        lists.ensureSeeded()
        val previous = lists.readConfig()
        val available = candidates.filter { id ->
            StrategyCatalog.isValidId(id) &&
                File(ZapretPaths.systemRoot, "strategies/$id.conf.in").isFile
        }.ifEmpty {
            candidates.filter { StrategyCatalog.isValidId(it) }
        }

        val rows = mutableListOf<StrategyProbeRow>()
        var winner: String? = null
        var bestScore = Long.MIN_VALUE
        var bestIndex = Int.MAX_VALUE
        var cancelled = false

        try {
            available.forEachIndexed { index, id ->
                if (cancelRequested.get()) {
                    cancelled = true
                    return@forEachIndexed
                }
                onPhase("Проба $id (${index + 1}/${available.size})")
                lists.writeConfig(previous.copy(strategyId = id))
                val restart = service.restart()
                if (!restart.ok) {
                    appendLog("restart failed for $id: ${restart.lastLine()}")
                    rows += StrategyProbeRow.failed(id)
                    return@forEachIndexed
                }
                if (!waitForUtun()) {
                    appendLog("utun50 timeout for $id")
                    rows += StrategyProbeRow.failed(id)
                    return@forEachIndexed
                }
                val discord = probeHost("discord.com")
                val youtube = probeHost("www.youtube.com")
                val control = probeHost("www.apple.com")
                val row = StrategyProbeRow(id, discord, youtube, control)
                rows += row
                appendLog(
                    "$id score=${row.score} discord=${discord.successes}/${discord.attempts}" +
                        "@${discord.avgLatencyMs ?: "-"}ms yt=${youtube.successes}/${youtube.attempts}" +
                        "@${youtube.avgLatencyMs ?: "-"}ms ctrl=${control.successes}/${control.attempts}" +
                        "@${control.avgLatencyMs ?: "-"}ms",
                )
                if (row.usable && (row.score > bestScore || (row.score == bestScore && index < bestIndex))) {
                    bestScore = row.score
                    bestIndex = index
                    winner = id
                }
            }

            val restored: Boolean
            if (winner != null && !cancelled && !cancelRequested.get()) {
                onPhase("Применение $winner")
                lists.writeConfig(previous.copy(strategyId = winner))
                service.restart()
                restored = false
            } else {
                onPhase("Восстановление прежней стратегии")
                lists.writeConfig(previous)
                service.restart()
                restored = true
                winner = null
            }
            appendLog("winner=${winner ?: "none"} restored=$restored")
            return StrategyProbeReport(results = rows, winnerId = winner, restoredPrevious = restored)
        } catch (e: Exception) {
            runCatching {
                lists.writeConfig(previous)
                service.restart()
            }
            appendLog("probe failed: ${e.message}")
            throw e
        }
    }

    private fun waitForUtun(): Boolean {
        repeat(50) {
            if (cancelRequested.get()) return false
            val ok = Shell.run("/sbin/ifconfig", "utun50", timeout = 2.seconds).ok
            if (ok) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun probeHost(host: String): HostProbeMetrics {
        val latencies = mutableListOf<Int>()
        repeat(SAMPLES) {
            if (cancelRequested.get()) {
                return HostProbeMetrics.fromLatencies(latencies, attempts = SAMPLES)
            }
            val sample = sampleHttps(host) ?: return@repeat
            latencies += sample
            // Brief gap so we measure stability, not one burst.
            Thread.sleep(SAMPLE_GAP_MS)
        }
        return HostProbeMetrics.fromLatencies(latencies, attempts = SAMPLES)
    }

    /** Returns latency ms on HTTP 200–499, else null. */
    private fun sampleHttps(host: String): Int? {
        val result = Shell.run(
            "/usr/bin/curl",
            "-sS",
            "-o", "/dev/null",
            "-w", "%{http_code} %{time_total}",
            "--connect-timeout", "3",
            "--max-time", "8",
            "-L",
            "https://$host/",
            timeout = 12.seconds,
        )
        val parts = result.output.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val http = parts[0].toIntOrNull() ?: return null
        if (!(result.ok && http in 200..499)) return null
        val seconds = parts[1].toDoubleOrNull() ?: return null
        return (seconds * 1000.0).toInt().coerceAtLeast(1)
    }

    private fun appendLog(line: String) {
        runCatching {
            ZapretPaths.userDataRoot.mkdirs()
            logFile.appendText("${java.time.Instant.now()} $line\n")
        }
    }

    companion object {
        private const val SAMPLES = 3
        private const val SAMPLE_GAP_MS = 200L

        /** Mildest-first shortlist (Flowseal “Run Tests” idea). */
        val SHORTLIST = listOf(
            "general-simple-fake",
            "general-simple-fake-alt",
            "general-simple-fake-alt2",
            "general-fake-tls-auto",
            "general-fake-tls-auto-alt",
            "general-fake-tls-auto-alt2",
            "general-fake-tls-auto-alt3",
            "general",
            "general-alt",
            "general-exp",
        )
    }
}
