package zapret.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zapret.domain.CombinedStatus
import zapret.domain.CommandResult
import zapret.domain.ConfigStore
import zapret.domain.ConfigWriter
import zapret.domain.InstallService
import zapret.domain.PasswordlessControl
import zapret.domain.Prerequisites
import zapret.domain.PrivilegeEscalationCancelled
import zapret.domain.PrivilegeRunner
import zapret.domain.ServiceOrchestrator
import zapret.domain.StatusPoller
import zapret.domain.TgWsProxyConfig
import zapret.domain.TgWsProxyService
import zapret.domain.TgWsProxyStore
import zapret.domain.TgWsProxyValidation
import zapret.domain.UninstallScope
import zapret.domain.UninstallService
import zapret.domain.ZapretConfig
import zapret.domain.ZapretPaths
import zapret.domain.ZapretService
import kotlin.system.exitProcess

/**
 * Single source of truth for the window and the menu bar. It owns no logic of its own:
 * every action is delegated to a domain service and reflected back into [UiState].
 */
class AppViewModel(private val scope: CoroutineScope) {

    private val privileges = PrivilegeRunner()
    private val service = ZapretService(privileges)
    private val tgProxy = TgWsProxyService()
    private val tgStore = TgWsProxyStore()
    private val orchestrator = ServiceOrchestrator(service, tgProxy, tgStore)
    private val poller = StatusPoller(service, tgProxy)
    private val configStore = ConfigStore()
    private val installer = InstallService(privileges)
    private val configWriter = ConfigWriter(privileges)
    private val uninstaller = UninstallService(privileges, tgProxy)
    private val passwordless = PasswordlessControl(privileges)

    var state by mutableStateOf(UiState())
        private set

    var windowVisible by mutableStateOf(true)
        private set

    var panelVisible by mutableStateOf(false)
        private set

    init {
        reload()
        syncPollAttention()
        scope.launch {
            withContext(Dispatchers.IO) { refreshPrerequisites() }
            poller.statuses().collect { state = state.withStatus(it) }
        }
    }

    fun show(screen: Screen) {
        state = state.copy(screen = screen)
    }

    fun openWindow(screen: Screen = state.screen) {
        state = state.copy(screen = screen)
        windowVisible = true
        panelVisible = false
        syncPollAttention()
    }

    fun hideWindow() {
        windowVisible = false
        syncPollAttention()
    }

    fun togglePanel() {
        panelVisible = !panelVisible
        syncPollAttention()
    }

    fun showPanel() {
        panelVisible = true
        syncPollAttention()
    }

    fun hidePanel() {
        panelVisible = false
        syncPollAttention()
    }

    fun dismissNotice() {
        state = state.copy(notice = null)
    }

    fun toggle() {
        if (!state.installed) return install()
        if (state.running) {
            operation("Остановка") { orchestrator.stopAll() }
        } else {
            operation("Запуск") { orchestrator.startAll() }
        }
    }

    fun install() {
        val ready = state.prerequisites
        if (!ready.hasSources) {
            state = state.copy(notice = Notice("Исходники zapret2 не найдены. Переустановите приложение из DMG.", isError = true))
            return
        }
        if (!ready.canInstall) {
            state = state.copy(notice = Notice("Сначала установите инструменты разработчика (Xcode CLT).", isError = true))
            return
        }
        operation("Установка") {
            val installed = installer.install { step -> state = state.copy(busy = step) }
            if (!installed.ok) return@operation installed
            val tg = tgStore.read()
            if (!tg.enabled) return@operation installed
            val tgStart = tgProxy.start(tg)
            if (tgStart.ok) installed else tgStart
        }
    }

    fun applyConfig(config: ZapretConfig, tgConfig: TgWsProxyConfig) = operation("Применение настроек") {
        TgWsProxyValidation.requireValid(tgConfig)
        val zapretResult = configWriter.apply(config)
        if (!zapretResult.ok) return@operation zapretResult
        orchestrator.applyTg(tgConfig)
    }

    fun restartTgProxy() = operation("Перезапуск TG proxy") {
        val config = tgStore.read()
        if (!config.enabled) return@operation CommandResult(1, "TG proxy выключен в настройках")
        tgProxy.restart(config)
    }

    fun uninstall(what: UninstallScope) = operation("Удаление") {
        uninstaller.uninstall(what).also { if (it.ok) exitProcess(0) }
    }

    fun setPasswordless(enabled: Boolean) =
        operation(if (enabled) "Настройка входа без пароля" else "Отключение входа без пароля") {
            if (enabled) passwordless.enable() else passwordless.disable()
        }

    fun installCompilerTools() {
        if (state.busy != null) return
        scope.launch(Dispatchers.IO) {
            Prerequisites.requestCompilerInstall()
            refreshPrerequisites()
        }
    }

    private fun syncPollAttention() {
        poller.setAttentive(windowVisible || panelVisible)
    }

    private fun reload() {
        val passwordlessOn = runCatching { passwordless.isEnabled() }.getOrDefault(false)
        state = state.copy(
            installed = ZapretPaths.isInstalled,
            config = configStore.read() ?: state.config,
            tgConfig = runCatching { tgStore.read() }.getOrDefault(state.tgConfig),
            passwordless = passwordlessOn,
            prerequisites = Prerequisites.probe(passwordlessOn),
        )
    }

    private fun refreshPrerequisites() {
        val passwordlessOn = runCatching { passwordless.isEnabled() }.getOrDefault(false)
        state = state.copy(
            passwordless = passwordlessOn,
            prerequisites = Prerequisites.probe(passwordlessOn),
        )
    }

    private fun operation(label: String, block: () -> CommandResult) {
        if (state.busy != null) return
        state = state.copy(busy = label, notice = null)
        scope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { block() } }
            val status = withContext(Dispatchers.IO) {
                runCatching {
                    CombinedStatus(service.status(), tgProxy.isRunning())
                }.getOrDefault(CombinedStatus())
            }
            state = state.withStatus(status).copy(busy = null, notice = outcome.toNotice(label))
            reload()
        }
    }

    private fun Result<CommandResult>.toNotice(label: String): Notice? {
        val error = exceptionOrNull()
        if (error is PrivilegeEscalationCancelled) return null
        if (error != null) return Notice(error.message ?: error.toString(), isError = true)

        val result = getOrThrow()
        return if (result.ok) null else Notice("$label не удалось: ${result.lastLine()}", isError = true)
    }
}
