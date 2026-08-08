package zapret.domain

/**
 * One power-control surface: zapret2 (privileged) plus optional TG WS Proxy (user).
 */
class ServiceOrchestrator(
    private val zapret: ZapretControl,
    private val tgProxy: TgWsProxyControl,
    private val tgStore: TgWsProxyStore = TgWsProxyStore(),
) {

    fun startAll(): CommandResult {
        val zapretResult = zapret.start()
        if (!zapretResult.ok) return zapretResult
        val tg = tgStore.read()
        if (!tg.enabled) return zapretResult
        val tgResult = tgProxy.start(tg)
        return if (tgResult.ok) zapretResult else merge(zapretResult, tgResult)
    }

    fun stopAll(): CommandResult {
        val tgResult = tgProxy.stop()
        val zapretResult = zapret.stop()
        return if (zapretResult.ok) {
            if (tgResult.ok) zapretResult else tgResult
        } else {
            zapretResult
        }
    }

    fun restartAll(): CommandResult {
        val zapretResult = zapret.restart()
        if (!zapretResult.ok) return zapretResult
        val tg = tgStore.read()
        val tgResult = if (tg.enabled) tgProxy.restart(tg) else tgProxy.stop()
        return if (tgResult.ok) zapretResult else merge(zapretResult, tgResult)
    }

    fun applyTg(config: TgWsProxyConfig): CommandResult {
        TgWsProxyValidation.requireValid(config)
        tgStore.write(config)
        val zapretRunning = zapret.status().running
        return when {
            !config.enabled -> tgProxy.stop()
            zapretRunning -> tgProxy.restart(config)
            else -> CommandResult(0, "tg-ws-proxy config saved")
        }
    }

    fun tgRunning(): Boolean = tgProxy.isRunning()

    private fun merge(primary: CommandResult, secondary: CommandResult): CommandResult =
        CommandResult(
            exitCode = if (primary.ok && secondary.ok) 0 else 1,
            output = listOf(primary.output, secondary.output).filter { it.isNotBlank() }.joinToString("\n"),
        )
}
