package zapret.domain

interface ZapretControl {
    fun start(): CommandResult
    fun stop(): CommandResult
    fun restart(): CommandResult
    fun status(): DaemonStatus
}

interface TgWsProxyControl {
    fun start(config: TgWsProxyConfig): CommandResult
    fun stop(): CommandResult
    fun restart(config: TgWsProxyConfig): CommandResult
    fun isRunning(): Boolean
}
