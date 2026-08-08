package zapret.domain

import java.io.File

/** Seeds and edits host/IP lists under ~/Library/Application Support/Zapret/lists. */
class EngineListsStore(
    private val listsDir: File = ZapretPaths.listsDir,
    private val defaultsDir: () -> File? = {
        ZapretPaths.enginePayload()?.let { File(it, "default-lists") }
    },
) {

    fun ensureSeeded() {
        listsDir.mkdirs()
        val defaults = defaultsDir() ?: return
        for (name in LIST_FILES) {
            val target = File(listsDir, name)
            if (target.isFile) continue
            val source = File(defaults, name)
            if (source.isFile) {
                source.copyTo(target, overwrite = false)
            } else {
                target.writeText("")
            }
        }
        ensureStrategyDefaults()
    }

    fun ensureStrategyDefaults() {
        ZapretPaths.userDataRoot.mkdirs()
        if (!ZapretPaths.selectedStrategyFile.isFile) {
            ZapretPaths.selectedStrategyFile.writeText(ZapretConfig.DEFAULT_STRATEGY + "\n")
        }
        if (!ZapretPaths.ipsetModeFile.isFile) {
            ZapretPaths.ipsetModeFile.writeText(IpsetMode.NONE.value + "\n")
        }
    }

    fun readConfig(): ZapretConfig {
        ensureStrategyDefaults()
        val strategy = ZapretPaths.selectedStrategyFile.readText().trim()
            .ifEmpty { ZapretConfig.DEFAULT_STRATEGY }
        val mode = IpsetMode.of(ZapretPaths.ipsetModeFile.readText().trim())
        return ZapretConfig(
            strategyId = if (StrategyCatalog.isValidId(strategy)) strategy else ZapretConfig.DEFAULT_STRATEGY,
            ipsetMode = mode,
        )
    }

    fun writeConfig(config: ZapretConfig) {
        require(StrategyCatalog.isValidId(config.strategyId)) { "invalid strategy" }
        ZapretPaths.userDataRoot.mkdirs()
        ZapretPaths.selectedStrategyFile.writeText(config.strategyId.trim() + "\n")
        ZapretPaths.ipsetModeFile.writeText(config.ipsetMode.value + "\n")
    }

    fun readList(name: String): String {
        require(name in LIST_FILES) { "unknown list: $name" }
        ensureSeeded()
        return File(listsDir, name).takeIf { it.isFile }?.readText().orEmpty()
    }

    fun writeList(name: String, text: String) {
        require(name in LIST_FILES) { "unknown list: $name" }
        listsDir.mkdirs()
        File(listsDir, name).writeText(text)
    }

    fun resetList(name: String) {
        require(name in LIST_FILES) { "unknown list: $name" }
        val defaults = defaultsDir()
        val source = defaults?.let { File(it, name) }
        listsDir.mkdirs()
        val target = File(listsDir, name)
        if (source != null && source.isFile) {
            source.copyTo(target, overwrite = true)
        } else {
            target.writeText("")
        }
    }

    fun resetAll() {
        for (name in LIST_FILES) resetList(name)
    }

    companion object {
        val LIST_FILES = listOf(
            "list-general.txt",
            "list-general-user.txt",
            "list-google.txt",
            "list-exclude.txt",
            "list-exclude-user.txt",
            "ipset-all.txt",
            "ipset-exclude.txt",
            "ipset-exclude-user.txt",
        )

        val LIST_LABELS = mapOf(
            "list-general.txt" to "Домены (general)",
            "list-general-user.txt" to "Домены пользователя",
            "list-google.txt" to "Домены Google",
            "list-exclude.txt" to "Исключения доменов",
            "list-exclude-user.txt" to "Исключения доменов (user)",
            "ipset-all.txt" to "IP-список (ipset-all)",
            "ipset-exclude.txt" to "Исключения IP",
            "ipset-exclude-user.txt" to "Исключения IP (user)",
        )
    }
}
