package zapret.ui

import zapret.domain.CombinedStatus
import zapret.domain.DaemonStatus
import zapret.domain.Prerequisites
import zapret.domain.StrategyEntry
import zapret.domain.StrategyProbeReport
import zapret.domain.TgWsProxyConfig
import zapret.domain.UpdateInfo
import zapret.domain.UpdatePhase
import zapret.domain.ZapretConfig
import kotlin.time.Duration
import kotlin.time.TimeSource

enum class Screen { HOME, SETTINGS }

data class Notice(val text: String, val isError: Boolean)

data class UiState(
    val installed: Boolean = false,
    val status: DaemonStatus = DaemonStatus(),
    val tgRunning: Boolean = false,
    val config: ZapretConfig = ZapretConfig(),
    val strategies: List<StrategyEntry> = emptyList(),
    val listContents: Map<String, String> = emptyMap(),
    val defaultListContents: Map<String, String> = emptyMap(),
    val tgConfig: TgWsProxyConfig = TgWsProxyConfig(),
    val screen: Screen = Screen.HOME,
    val busy: String? = null,
    val notice: Notice? = null,
    val passwordless: Boolean = false,
    val autoUpdate: Boolean = true,
    val appVersion: String = "0.0.0",
    val updateAvailable: UpdateInfo? = null,
    val updatePhase: UpdatePhase = UpdatePhase.Idle,
    val updateProgressLabel: String = "",
    val showUpdateModal: Boolean = false,
    val updateUpToDate: Boolean = false,
    val probePhase: String? = null,
    val probeReport: StrategyProbeReport? = null,
    val prerequisites: Prerequisites = Prerequisites(
        hasCompiler = false,
        hasSources = false,
        hasPrebuiltBinary = false,
        passwordlessControl = false,
        wanInterface = null,
        zapretInstalled = false,
    ),
    private val uptimeAt: TimeSource.Monotonic.ValueTimeMark? = null,
) {
    val running: Boolean get() = status.running

    fun withStatus(combined: CombinedStatus): UiState = copy(
        status = combined.zapret,
        tgRunning = combined.tgRunning,
        uptimeAt = combined.zapret.uptime?.let { TimeSource.Monotonic.markNow() },
    )

    fun withStatus(status: DaemonStatus): UiState = copy(
        status = status,
        uptimeAt = status.uptime?.let { TimeSource.Monotonic.markNow() },
    )

    /** Ticks between polls so the timer on Home moves every second. */
    fun uptime(): Duration? {
        val measured = status.uptime ?: return null
        val at = uptimeAt ?: return measured
        return measured + at.elapsedNow()
    }

    fun tgProxyToggleLabel(): String =
        if (tgRunning) "Выключить TG proxy" else "Включить TG proxy"
}

fun Duration?.asTimer(): String {
    if (this == null) return "--:--:--"
    val total = inWholeSeconds
    return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
}
