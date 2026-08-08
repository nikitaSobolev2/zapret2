package zapret.domain

/** Persists strategy/ipset mode (+ optional list drafts) and restarts the engine if running. */
class ConfigWriter(
    private val privileges: PrivilegeRunner,
    private val lists: EngineListsStore = EngineListsStore(),
    private val service: ZapretControl = ZapretService(privileges, lists),
) {

    fun apply(config: ZapretConfig, listDrafts: Map<String, String> = emptyMap()): CommandResult {
        if (!StrategyCatalog.isValidId(config.strategyId)) {
            throw InstallFailed("Некорректная стратегия: ${config.strategyId}")
        }
        lists.ensureSeeded()
        lists.writeConfig(config)
        for ((name, text) in listDrafts) {
            if (name in EngineListsStore.LIST_FILES) {
                lists.writeList(name, text)
            }
        }
        if (!ZapretPaths.isInstalled) {
            return CommandResult(0, "config saved")
        }
        val restart = ZapretPaths.restartScript
        if (!restart.canExecute()) {
            return service.start()
        }
        val sudo = Shell.run("/usr/bin/sudo", "-n", restart.absolutePath)
        if (sudo.ok || !sudo.output.contains("a password is required")) return sudo
        return EnginePrivileged.runScriptText(privileges, restart)
    }
}
