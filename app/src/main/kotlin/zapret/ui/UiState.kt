package zapret.ui

import zapret.domain.DaemonStatus
import zapret.domain.ZapretConfig
import kotlin.time.Duration
import kotlin.time.TimeSource

enum class Screen { HOME, SETTINGS }

data class Notice(val text: String, val isError: Boolean)

data class UiState(
    val installed: Boolean = false,
    val status: DaemonStatus = DaemonStatus(),
    val config: ZapretConfig = ZapretConfig(),
    val screen: Screen = Screen.HOME,
    val busy: String? = null,
    val notice: Notice? = null,
    val passwordless: Boolean = false,
    private val uptimeAt: TimeSource.Monotonic.ValueTimeMark? = null,
) {
    val running: Boolean get() = status.running

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
}

fun Duration?.asTimer(): String {
    if (this == null) return "--:--:--"
    val total = inWholeSeconds
    return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
}
