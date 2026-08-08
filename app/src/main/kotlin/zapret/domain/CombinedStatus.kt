package zapret.domain

data class CombinedStatus(
    val zapret: DaemonStatus = DaemonStatus(),
    val tgRunning: Boolean = false,
)
