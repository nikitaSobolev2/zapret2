package zapret.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zapret.domain.AppPrefs
import zapret.domain.AppPrefsStore
import zapret.domain.AppUpdateService
import zapret.domain.AppVersion
import zapret.domain.CombinedStatus
import zapret.domain.CommandResult
import zapret.domain.ConfigWriter
import zapret.domain.DesktopOpen
import zapret.domain.EngineListsStore
import zapret.domain.InstallService
import zapret.domain.PasswordlessControl
import zapret.domain.Prerequisites
import zapret.domain.PrivilegeEscalationCancelled
import zapret.domain.PrivilegeRunner
import zapret.domain.ServiceOrchestrator
import zapret.domain.StatusPoller
import zapret.domain.StrategyCatalog
import zapret.domain.StrategyProbe
import zapret.domain.StrategyProbeMessages
import zapret.domain.StrategyProbeReport
import zapret.domain.TgWsProxyConfig
import zapret.domain.TgWsProxyService
import zapret.domain.TgWsProxyStore
import zapret.domain.TgWsProxyValidation
import zapret.domain.UninstallScope
import zapret.domain.UninstallService
import zapret.domain.UpdateInfo
import zapret.domain.UpdatePhase
import zapret.domain.ZapretConfig
import zapret.domain.ZapretPaths
import zapret.domain.ZapretService
import java.io.File
import kotlin.system.exitProcess

/**
 * Single source of truth for the window and the menu bar. It owns no logic of its own:
 * every action is delegated to a domain service and reflected back into [UiState].
 */
class AppViewModel(private val scope: CoroutineScope) {

    private val privileges = PrivilegeRunner()
    private val listsStore = EngineListsStore()
    private val service = ZapretService(privileges, listsStore)
    private val tgProxy = TgWsProxyService()
    private val tgStore = TgWsProxyStore()
    private val orchestrator = ServiceOrchestrator(service, tgProxy, tgStore)
    private val poller = StatusPoller(service, tgProxy)
    private val installer = InstallService(privileges, listsStore, service)
    private val configWriter = ConfigWriter(privileges, listsStore, service)
    private val uninstaller = UninstallService(privileges, tgProxy)
    private val passwordless = PasswordlessControl(privileges)
    private val prefsStore = AppPrefsStore()
    private val updater by lazy { AppUpdateService() }
    private val strategyProbe by lazy { StrategyProbe(listsStore, service) }

    private var updateJob: Job? = null
    private var probeJob: Job? = null
    private var probeGeneration = 0

    var state by mutableStateOf(UiState(appVersion = AppVersion.current()))
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
        if (state.autoUpdate) {
            scope.launch { runStartupUpdateCheck() }
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
            state = state.copy(notice = Notice("Пакет двигателя не найден. Переустановите приложение из DMG.", isError = true))
            return
        }
        if (!ready.canInstall) {
            state = state.copy(notice = Notice("В пакете нет utunws. Пересоберите приложение (Gradle packageDmg / run).", isError = true))
            return
        }
        operation("Установка") {
            val installed = installer.install { step -> state = state.copy(busy = step) }
            if (!installed.ok) return@operation installed
            installed.withWarningFrom(orchestrator.startOptionalTg())
        }
    }

    fun applyConfig(
        config: ZapretConfig,
        tgConfig: TgWsProxyConfig,
        listDrafts: Map<String, String> = emptyMap(),
    ) = operation("Применение настроек") {
        val merged = tgConfig.withLiveEnabled(state.tgConfig.enabled)
        TgWsProxyValidation.requireValid(merged)
        val zapretResult = configWriter.apply(config, listDrafts)
        if (!zapretResult.ok) return@operation zapretResult
        orchestrator.applyTg(merged)
    }

    fun openListFile(name: String) {
        if (name !in EngineListsStore.LIST_FILES) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                listsStore.ensureSeeded()
                DesktopOpen.textFile(listsStore.fileFor(name))
            }
        }
    }

    fun restartTgProxy() = operation("Перезапуск TG proxy") {
        val config = tgStore.read()
        if (!config.enabled) return@operation CommandResult(1, "TG proxy выключен в настройках")
        tgProxy.restart(config)
    }

    fun toggleTgProxy() {
        if (!canControlServices()) return
        val current = runCatching { tgStore.read() }.getOrDefault(state.tgConfig)
        val enable = !state.tgRunning
        operation(if (enable) "Запуск TG proxy" else "Остановка TG proxy") {
            orchestrator.applyTg(current.copy(enabled = enable))
        }
    }

    fun uninstall(what: UninstallScope) = operation("Удаление") {
        uninstaller.uninstall(what).also { if (it.ok) exitProcess(0) }
    }

    fun setPasswordless(enabled: Boolean) =
        operation(if (enabled) "Настройка входа без пароля" else "Отключение входа без пароля") {
            if (!ZapretPaths.isInstalled) {
                prefsStore.write(prefsStore.read().copy(passwordless = enabled))
                return@operation CommandResult(0, "saved")
            }
            val result = if (enabled) passwordless.enable() else passwordless.disable()
            if (result.ok) {
                prefsStore.write(prefsStore.read().copy(passwordless = enabled))
            }
            result
        }

    fun setAutoUpdate(enabled: Boolean) {
        val prefs = prefsStore.read().copy(autoUpdate = enabled)
        prefsStore.write(prefs)
        state = state.copy(autoUpdate = enabled)
    }

    fun setTgEnabled(enabled: Boolean) {
        if (!canControlServices()) return
        val current = runCatching { tgStore.read() }.getOrDefault(state.tgConfig)
        if (current.enabled == enabled) {
            if (state.tgConfig.enabled != enabled) {
                state = state.copy(tgConfig = current.copy(enabled = enabled))
            }
            return
        }
        operation(if (enabled) "Запуск TG proxy" else "Остановка TG proxy") {
            orchestrator.applyTg(current.copy(enabled = enabled))
        }
    }

    fun probeStrategies() {
        if (state.busy != null || state.probePhase != null) return
        if (!state.installed) {
            state = state.copy(notice = Notice("Сначала установите движок", isError = true))
            return
        }
        probeJob?.cancel()
        strategyProbe.cancel()
        val generation = ++probeGeneration
        probeJob = scope.launch {
            state = state.copy(probePhase = "Подбор стратегии…", probeReport = null, notice = null)
            val outcome = try {
                Result.success(
                    withContext(Dispatchers.IO) {
                        strategyProbe.run { phase ->
                            scope.launch {
                                if (generation != probeGeneration) return@launch
                                state = state.copy(probePhase = phase)
                            }
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            finishProbe(generation, outcome)
        }
    }

    fun checkForUpdates() {
        if (state.updatePhase == UpdatePhase.Checking || state.updatePhase == UpdatePhase.Downloading) return
        updateJob?.cancel()
        updateJob = scope.launch {
            state = state.copy(
                updatePhase = UpdatePhase.Checking,
                updateUpToDate = false,
                notice = null,
                updateProgressLabel = "Проверка…",
            )
            val result = withContext(Dispatchers.IO) { updater.checkLatest(state.appVersion) }
            result.fold(
                onSuccess = { info ->
                    if (info == null) {
                        state = state.copy(
                            updatePhase = UpdatePhase.Idle,
                            updateAvailable = null,
                            updateUpToDate = true,
                            updateProgressLabel = "",
                            notice = Notice("Установлена актуальная версия ${state.appVersion}", isError = false),
                        )
                    } else {
                        state = state.copy(
                            updatePhase = UpdatePhase.Available,
                            updateAvailable = info,
                            updateUpToDate = false,
                            updateProgressLabel = "",
                        )
                    }
                },
                onFailure = { error ->
                    state = state.copy(
                        updatePhase = UpdatePhase.Idle,
                        updateProgressLabel = "",
                        notice = Notice("Проверка обновлений: ${error.message}", isError = true),
                    )
                },
            )
        }
    }

    fun startUpdate(info: UpdateInfo? = state.updateAvailable) {
        val target = info ?: return
        if (ZapretPaths.appBundle() == null) {
            state = state.copy(
                notice = Notice(AppUpdateService.NOT_PACKAGED, isError = true),
                showUpdateModal = false,
                updatePhase = UpdatePhase.Available,
            )
            return
        }
        updateJob?.cancel()
        updater.clearCancel()
        updateJob = scope.launch { performUpdate(target) }
    }

    fun cancelUpdate() {
        updater.cancel()
        updateJob?.cancel()
        updateJob = null
        state = state.copy(
            showUpdateModal = false,
            updatePhase = if (state.updateAvailable != null) UpdatePhase.Available else UpdatePhase.Idle,
            updateProgressLabel = "",
            notice = Notice("Обновление отменено", isError = false),
        )
    }

    fun installCompilerTools() {
        if (state.busy != null) return
        scope.launch(Dispatchers.IO) {
            Prerequisites.requestCompilerInstall()
            refreshPrerequisites()
        }
    }

    private suspend fun runStartupUpdateCheck() {
        state = state.copy(
            showUpdateModal = true,
            updatePhase = UpdatePhase.Checking,
            updateProgressLabel = "Проверка обновлений…",
        )
        val result = withContext(Dispatchers.IO) { updater.checkLatest(state.appVersion) }
        result.fold(
            onSuccess = { info ->
                if (info == null) {
                    state = state.copy(
                        showUpdateModal = false,
                        updatePhase = UpdatePhase.Idle,
                        updateProgressLabel = "",
                    )
                } else {
                    state = state.copy(updateAvailable = info)
                    if (ZapretPaths.appBundle() == null) {
                        state = state.copy(
                            showUpdateModal = false,
                            updatePhase = UpdatePhase.Available,
                            updateProgressLabel = "",
                            notice = Notice(
                                "Доступна версия ${info.version}. ${AppUpdateService.NOT_PACKAGED}",
                                isError = false,
                            ),
                        )
                    } else {
                        performUpdate(info)
                    }
                }
            },
            onFailure = {
                state = state.copy(
                    showUpdateModal = false,
                    updatePhase = UpdatePhase.Idle,
                    updateProgressLabel = "",
                )
            },
        )
    }

    private suspend fun performUpdate(info: UpdateInfo) {
        state = state.copy(
            showUpdateModal = true,
            updatePhase = UpdatePhase.Downloading,
            updateAvailable = info,
            updateProgressLabel = "Скачивание ${info.assetName}…",
            notice = null,
        )
        val download = withContext(Dispatchers.IO) {
            updater.download(info) { read, total ->
                val label = if (total != null && total > 0) {
                    "Скачивание… ${read * 100 / total}%"
                } else {
                    "Скачивание… ${read / 1024} КБ"
                }
                scope.launch {
                    state = state.copy(updateProgressLabel = label)
                }
            }
        }
        val dmg = download.getOrElse { error ->
            val cancelled = error.message == "cancelled"
            state = state.copy(
                showUpdateModal = false,
                updatePhase = UpdatePhase.Available,
                updateProgressLabel = "",
                notice = if (cancelled) {
                    Notice("Обновление отменено", isError = false)
                } else {
                    Notice("Загрузка не удалась: ${error.message}", isError = true)
                },
            )
            return
        }

        state = state.copy(
            updatePhase = UpdatePhase.Applying,
            updateProgressLabel = "Установка и перезапуск…",
        )
        val applied = withContext(Dispatchers.IO) { updater.applyAndRelaunch(dmg) }
        applied.fold(
            onSuccess = { exitProcess(0) },
            onFailure = { error ->
                val cancelled = error.message == "cancelled"
                state = state.copy(
                    showUpdateModal = false,
                    updatePhase = UpdatePhase.Available,
                    updateProgressLabel = "",
                    notice = if (cancelled) {
                        Notice("Обновление отменено", isError = false)
                    } else {
                        Notice("Установка не удалась: ${error.message}", isError = true)
                    },
                )
            },
        )
    }

    private fun syncPollAttention() {
        poller.setAttentive(windowVisible || panelVisible)
    }

    private fun reload() {
        val passwordlessOn = runCatching { passwordless.isEnabled() }.getOrDefault(false)
        val prefs = runCatching { prefsStore.read() }.getOrDefault(AppPrefs())
        runCatching { listsStore.ensureSeeded() }
        val payload = ZapretPaths.enginePayload()
        val strategies = StrategyCatalog.load(payload)
        val oversizedLists = EngineListsStore.LIST_FILES.mapNotNull { name ->
            val file = listsStore.fileFor(name)
            val size = file.takeIf { it.isFile }?.length() ?: 0L
            if (size > EngineListsStore.EDITOR_MAX_BYTES) name to size else null
        }.toMap()
        val listContents = EngineListsStore.LIST_FILES
            .filterNot { it in oversizedLists }
            .associateWith { name ->
                runCatching { listsStore.readList(name) }.getOrDefault("")
            }
        val defaultsDir = payload?.let { File(it, "default-lists") }
        val defaultListContents = EngineListsStore.LIST_FILES.associateWith { name ->
            val file = defaultsDir?.let { File(it, name) }?.takeIf { it.isFile }
            if (file == null || file.length() > EngineListsStore.EDITOR_MAX_BYTES) ""
            else file.readText()
        }
        state = state.copy(
            installed = ZapretPaths.isInstalled,
            config = runCatching { listsStore.readConfig() }.getOrDefault(state.config),
            strategies = strategies,
            listContents = listContents,
            defaultListContents = defaultListContents,
            oversizedLists = oversizedLists,
            tgConfig = runCatching { tgStore.read() }.getOrDefault(state.tgConfig),
            passwordless = passwordlessSetting(ZapretPaths.isInstalled, passwordlessOn, prefs),
            autoUpdate = prefs.autoUpdate,
            appVersion = AppVersion.current(),
            prerequisites = Prerequisites.probe(passwordlessOn),
        )
    }

    private fun refreshPrerequisites() {
        val passwordlessOn = runCatching { passwordless.isEnabled() }.getOrDefault(false)
        val prefs = runCatching { prefsStore.read() }.getOrDefault(AppPrefs())
        state = state.copy(
            passwordless = passwordlessSetting(ZapretPaths.isInstalled, passwordlessOn, prefs),
            prerequisites = Prerequisites.probe(passwordlessOn),
        )
    }

    private fun passwordlessSetting(installed: Boolean, enabledOnSystem: Boolean, prefs: AppPrefs): Boolean =
        if (installed) enabledOnSystem else prefs.passwordless

    private fun finishProbe(generation: Int, outcome: Result<StrategyProbeReport>) {
        if (generation != probeGeneration) return
        probeGeneration++
        reload()
        val report = outcome.getOrNull() ?: strategyProbe.lastReportOrNull()
        val winnerId = report?.winnerId ?: strategyProbe.lastWinnerId
        val hasResults = strategyProbe.lastResults.isNotEmpty()
        state = state.copy(
            probePhase = null,
            probeReport = report,
            notice = Notice(
                text = StrategyProbeMessages.banner(winnerId, outcome.exceptionOrNull(), hasResults),
                isError = winnerId == null,
            ),
        )
    }

    private fun canControlServices(): Boolean =
        state.busy == null && state.probePhase == null

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
        if (result.ok) {
            return result.warning?.let { Notice(it, isError = true) }
        }
        return Notice("$label не удалось: ${result.lastLine()}", isError = true)
    }

    private fun CommandResult.withWarningFrom(other: CommandResult): CommandResult =
        if (other.warning != null) copy(warning = other.warning) else this
}
