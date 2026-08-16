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
    val googlevideo: HostProbeMetrics,
    val control: HostProbeMetrics,
) {
    val discordOk: Boolean get() = discord.ok
    /** Site shell is not enough — playback must also fetch googlevideo bytes. */
    val youtubeOk: Boolean get() = youtube.ok && googlevideo.ok
    /** Control must be mostly stable, not a single lucky hit. */
    val controlOk: Boolean
        get() = control.successes * 2 >= control.attempts.coerceAtLeast(1) && control.ok

    val usable: Boolean get() = controlOk && (discordOk || youtubeOk)

    /** Mean latency of Discord/YouTube/CDN successes (lower is better). */
    val avgTargetLatencyMs: Int?
        get() {
            val samples = listOfNotNull(
                discord.avgLatencyMs,
                youtube.avgLatencyMs,
                googlevideo.avgLatencyMs,
            )
            if (samples.isEmpty()) return null
            return samples.average().toInt()
        }

    /** Combined Discord+YouTube+CDN success rate, 0–1000. */
    val targetStabilityPermille: Int
        get() {
            val attempts = discord.attempts + youtube.attempts + googlevideo.attempts
            if (attempts <= 0) return 0
            val successes = discord.successes + youtube.successes + googlevideo.successes
            return (successes * 1000) / attempts
        }

    /**
     * Higher is better. YouTube+CDN dominates (page shell alone still “no internet”),
     * then Discord, then stability, then lower latency; mildness via shortlist index.
     */
    val score: Long
        get() {
            if (!controlOk) return -1L
            val reach = (if (youtubeOk) 3 else 0) + (if (discordOk) 1 else 0)
            val latencyCap = 9_999
            val latencyScore = latencyCap - (avgTargetLatencyMs ?: latencyCap).coerceIn(0, latencyCap)
            return reach * 10_000_000L +
                targetStabilityPermille * 10_000L +
                latencyScore
        }

    /** True if this row should replace current best (score, then earlier shortlist index). */
    fun beats(bestScore: Long, bestIndex: Int, index: Int): Boolean =
        score > bestScore || (score == bestScore && index < bestIndex)

    companion object {
        fun failed(strategyId: String) = StrategyProbeRow(
            strategyId = strategyId,
            discord = HostProbeMetrics.FAILED,
            youtube = HostProbeMetrics.FAILED,
            googlevideo = HostProbeMetrics.FAILED,
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

    @Volatile var lastWinnerId: String? = null
        private set

    @Volatile var lastRestored: Boolean = false
        private set

    @Volatile var lastResults: List<StrategyProbeRow> = emptyList()
        private set

    fun cancel() {
        cancelRequested.set(true)
    }

    fun lastReportOrNull(): StrategyProbeReport? {
        val results = lastResults
        val winnerId = lastWinnerId
        if (results.isEmpty() && winnerId == null) return null
        return runCatching { StrategyProbeReport(results, winnerId, lastRestored) }.getOrNull()
    }

    fun run(
        candidates: List<String> = SHORTLIST,
        onPhase: (String) -> Unit = {},
    ): StrategyProbeReport {
        cancelRequested.set(false)
        rememberCompletion(emptyList(), winnerId = null, restored = false)
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
        var applied = false

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
                val googlevideo = probeGooglevideo()
                val control = probeHost("www.apple.com")
                val row = StrategyProbeRow(id, discord, youtube, googlevideo, control)
                rows += row
                appendLog(
                    "$id score=${row.score} discord=${discord.successes}/${discord.attempts}" +
                        "@${discord.avgLatencyMs ?: "-"}ms yt=${youtube.successes}/${youtube.attempts}" +
                        "@${youtube.avgLatencyMs ?: "-"}ms video=${googlevideo.successes}/${googlevideo.attempts}" +
                        "@${googlevideo.avgLatencyMs ?: "-"}ms ctrl=${control.successes}/${control.attempts}" +
                        "@${control.avgLatencyMs ?: "-"}ms",
                )
                if (row.usable && row.beats(bestScore, bestIndex, index)) {
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
            applied = true
            rememberCompletion(rows, winner, restored)
            appendLog("winner=${winner ?: "none"} restored=$restored")
            return StrategyProbeReport(results = lastResults, winnerId = winner, restoredPrevious = restored)
        } catch (e: Throwable) {
            if (!applied) {
                runCatching {
                    lists.writeConfig(previous)
                    service.restart()
                }
            }
            appendLog("probe failed: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }

    private fun rememberCompletion(
        rows: List<StrategyProbeRow>,
        winnerId: String?,
        restored: Boolean,
    ) {
        lastResults = rows.toList()
        lastWinnerId = winnerId
        lastRestored = restored
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

    private fun probeHost(host: String): HostProbeMetrics = probeUrl("https://$host/")

    /**
     * Load a real YouTube media chunk (videoplayback). `/generate_204` on a POP
     * is only a fallback when innertube does not yield a URL.
     */
    private fun probeGooglevideo(): HostProbeMetrics {
        val playback = fetchYoutubePlaybackUrl()
        if (playback != null) return probeVideoPlayback(playback)
        appendLog("no videoplayback url, fallback generate_204")
        return probeGooglevideoGenerate204()
    }

    private fun probeGooglevideoGenerate204(): HostProbeMetrics {
        val host = GOOGLEVIDEO_CANDIDATES.firstOrNull(::hostResolves)
            ?: return HostProbeMetrics.fromLatencies(emptyList(), attempts = SAMPLES)
        return probeUrl("https://$host/generate_204")
    }

    private fun fetchYoutubePlaybackUrl(): String? {
        for (client in YoutubePlaybackUrls.clients) {
            if (cancelRequested.get()) return null
            val json = postYoutubePlayer(client) ?: continue
            YoutubePlaybackUrls.firstFromPlayerJson(json)?.let { return it }
        }
        return null
    }

    private fun postYoutubePlayer(client: YoutubePlaybackUrls.Client): String? {
        val request = File.createTempFile("zapret-yt-req", ".json")
        val response = File.createTempFile("zapret-yt-resp", ".json")
        try {
            request.writeText(client.body)
            val result = Shell.run(
                "/usr/bin/curl",
                "-sS",
                "-o", response.absolutePath,
                "-w", "%{http_code}",
                "--connect-timeout", "5",
                "--max-time", "12",
                "-H", "Content-Type: application/json",
                "-H", "User-Agent: ${client.userAgent}",
                "-X", "POST",
                "--data-binary", "@${request.absolutePath}",
                YoutubePlaybackUrls.PLAYER_URL,
                timeout = 15.seconds,
            )
            val http = result.output.trim().toIntOrNull() ?: return null
            if (!(result.ok && http in 200..299 && response.length() > 0)) return null
            return response.readText()
        } finally {
            request.delete()
            response.delete()
        }
    }

    private fun probeVideoPlayback(url: String): HostProbeMetrics {
        val latencies = mutableListOf<Int>()
        repeat(SAMPLES) {
            if (cancelRequested.get()) {
                return HostProbeMetrics.fromLatencies(latencies, attempts = SAMPLES)
            }
            sampleVideoPlayback(url)?.let { latencies += it }
            Thread.sleep(SAMPLE_GAP_MS)
        }
        return HostProbeMetrics.fromLatencies(latencies, attempts = SAMPLES)
    }

    /** HTTP 206/200 plus at least 2 KiB of media — not an empty/error page. */
    private fun sampleVideoPlayback(url: String): Int? {
        val result = Shell.run(
            "/usr/bin/curl",
            "-sS",
            "-o", "/dev/null",
            "-w", "%{http_code} %{time_total} %{size_download}",
            "--connect-timeout", "3",
            "--max-time", "10",
            "-L",
            "-r", "0-65535",
            "-H", "Origin: https://www.youtube.com",
            "-H", "Referer: https://www.youtube.com/",
            "-A", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
            url,
            timeout = 14.seconds,
        )
        val parts = result.output.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null
        val http = parts[0].toIntOrNull() ?: return null
        val seconds = parts[1].toDoubleOrNull() ?: return null
        val bytes = parts[2].toLongOrNull() ?: return null
        if (!(result.ok && http in 200..299 && bytes >= 2048)) return null
        return (seconds * 1000.0).toInt().coerceAtLeast(1)
    }

    private fun hostResolves(host: String): Boolean {
        val result = Shell.run(
            "/usr/bin/dig",
            "+short",
            "+time=2",
            "+tries=1",
            host,
            timeout = 4.seconds,
        )
        if (!result.ok) return false
        return result.output.lineSequence().any { line ->
            val t = line.trim()
            t.isNotEmpty() && (IPV4.matches(t) || t.endsWith('.'))
        }
    }

    private fun probeUrl(url: String): HostProbeMetrics {
        val latencies = mutableListOf<Int>()
        repeat(SAMPLES) {
            if (cancelRequested.get()) {
                return HostProbeMetrics.fromLatencies(latencies, attempts = SAMPLES)
            }
            val sample = sampleHttpsUrl(url) ?: return@repeat
            latencies += sample
            Thread.sleep(SAMPLE_GAP_MS)
        }
        return HostProbeMetrics.fromLatencies(latencies, attempts = SAMPLES)
    }

    /** Returns latency ms on HTTP 200–499, else null. */
    private fun sampleHttpsUrl(url: String): Int? {
        val result = Shell.run(
            "/usr/bin/curl",
            "-sS",
            "-o", "/dev/null",
            "-w", "%{http_code} %{time_total}",
            "--connect-timeout", "3",
            "--max-time", "8",
            "-L",
            url,
            timeout = 12.seconds,
        )
        val parts = result.output.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val http = parts[0].toIntOrNull() ?: return null
        // generate_204 returns 204; treat that as success too.
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
        private val IPV4 = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")

        private val GOOGLEVIDEO_CANDIDATES = listOf(
            "rr3---sn-5goeenes.googlevideo.com",
            "rr1---sn-gvnuxaxjvh-o8ge.googlevideo.com",
            "rr2---sn-gvnuxaxjvh-o8ges.googlevideo.com",
            "rr4---sn-gvnuxaxjvh-bvwz.googlevideo.com",
        )

        /**
         * YouTube needs a real googlevideo `videoplayback` chunk, not only the site shell.
         * Skip simple-fake*: fake+ts often kills YT TLS while Discord still works.
         */
        val SHORTLIST = listOf(
            "general-fake-tls-auto",
            "general-fake-tls-auto-alt",
            "general-fake-tls-auto-alt2",
            "general-fake-tls-auto-alt3",
            "general-pq-multisplit",
            "general-pq-multi",
            "general-pq-disorder-midsld",
            "general-pq-split-seqovl",
            "general-exp",
            "general",
            "general-alt",
        )
    }
}

object StrategyProbeMessages {
    const val FAILED = "Подбор не удался"
    const val NONE_FOUND = "Рабочая стратегия не найдена — восстановлена прежняя"

    fun banner(winnerId: String?, error: Throwable?, hasResults: Boolean): String = when {
        !winnerId.isNullOrBlank() -> "Подобрана стратегия $winnerId"
        hasResults || error == null -> NONE_FOUND
        else -> forError(error)
    }

    fun forError(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        if (message.isEmpty() || isJvmInternalName(message)) return FAILED
        return message
    }

    fun isJvmInternalName(message: String): Boolean = JVM_INTERNAL_NAME.matches(message)

    private val JVM_INTERNAL_NAME = Regex("""^[a-zA-Z_][\w$]*(?:/[a-zA-Z_][\w$]*)+$""")
}
