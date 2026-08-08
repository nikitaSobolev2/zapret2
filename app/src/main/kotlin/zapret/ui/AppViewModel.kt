package zapret.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zapret.domain.CommandResult
import zapret.domain.ConfigStore
import zapret.domain.ConfigWriter
import zapret.domain.DaemonStatus
import zapret.domain.InstallService
import zapret.domain.PasswordlessControl
import zapret.domain.PrivilegeEscalationCancelled
import zapret.domain.PrivilegeRunner
import zapret.domain.StatusPoller
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
    private val poller = StatusPoller(service)
    private val configStore = ConfigStore()
    private val installer = InstallService(privileges)
    private val configWriter = ConfigWriter(privileges)
    private val uninstaller = UninstallService(privileges)
    private val passwordless = PasswordlessControl(privileges)

    var state by mutableStateOf(UiState())
        private set

    var windowVisible by mutableStateOf(true)
        private set

    init {
        reload()
        scope.launch { poller.statuses().collect { state = state.withStatus(it) } }
    }

    fun show(screen: Screen) {
        state = state.copy(screen = screen)
    }

    fun openWindow(screen: Screen = state.screen) {
        state = state.copy(screen = screen)
        windowVisible = true
    }

    fun hideWindow() {
        windowVisible = false
    }

    fun dismissNotice() {
        state = state.copy(notice = null)
    }

    fun toggle() {
        if (!state.installed) return install()
        if (state.running) operation("Остановка") { service.stop() } else operation("Запуск") { service.start() }
    }

    fun install() = operation("Установка") { installer.install { step -> state = state.copy(busy = step) } }

    fun applyConfig(config: ZapretConfig) = operation("Применение настроек") { configWriter.apply(config) }

    fun uninstall(what: UninstallScope) = operation("Удаление") {
        uninstaller.uninstall(what).also { if (it.ok) exitProcess(0) }
    }

    fun setPasswordless(enabled: Boolean) =
        operation(if (enabled) "Настройка входа без пароля" else "Отключение входа без пароля") {
            if (enabled) passwordless.enable() else passwordless.disable()
        }

    private fun reload() {
        state = state.copy(
            installed = ZapretPaths.isInstalled,
            config = configStore.read() ?: state.config,
            passwordless = runCatching { passwordless.isEnabled() }.getOrDefault(false),
        )
    }

    private fun operation(label: String, block: () -> CommandResult) {
        if (state.busy != null) return
        state = state.copy(busy = label, notice = null)
        scope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { block() } }
            val status = withContext(Dispatchers.IO) { runCatching { service.status() }.getOrDefault(DaemonStatus()) }
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
