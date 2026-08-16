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
        return zapretResult.withOptionalTg(startOptionalTg())
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
        if (!tg.enabled) {
            tgProxy.stop()
            return zapretResult
        }
        return zapretResult.withOptionalTg(tgProxy.restart(tg))
    }

    /**
     * Starts TG WS Proxy when enabled. A sidecar crash must not fail Zapret itself.
     */
    fun startOptionalTg(): CommandResult {
        val tg = tgStore.read()
        if (!tg.enabled) return CommandResult(0, "tg-ws-proxy disabled")
        val started = tgProxy.start(tg)
        if (started.ok) return started
        val detail = started.lastLine().ifBlank { "tg-ws-proxy failed" }
        return CommandResult(0, started.output, warning = "TG proxy не запущен: $detail")
    }

    fun applyTg(config: TgWsProxyConfig): CommandResult {
        TgWsProxyValidation.requireValid(config)
        return if (config.enabled) startAndPersist(config) else stopAndPersist(config)
    }

    private fun startAndPersist(config: TgWsProxyConfig): CommandResult {
        val result = tgProxy.restart(config)
        tgStore.write(if (result.ok) config else config.copy(enabled = false))
        return result
    }

    private fun stopAndPersist(config: TgWsProxyConfig): CommandResult {
        tgStore.write(config)
        return tgProxy.stop()
    }

    fun tgRunning(): Boolean = tgProxy.isRunning()

    private fun CommandResult.withOptionalTg(tgResult: CommandResult): CommandResult {
        if (tgResult.ok && tgResult.warning == null) return this
        val warning = tgResult.warning
            ?: "TG proxy не запущен: ${tgResult.lastLine().ifBlank { tgResult.output }}"
        return copy(warning = warning)
    }
}
