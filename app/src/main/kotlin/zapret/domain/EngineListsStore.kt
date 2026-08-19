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
        prepareListsDir()
        val defaults = defaultsDir()
        for (name in LIST_FILES) {
            val target = regularFile(name)
            if (target.isFile) continue
            val source = defaults?.let { File(it, name) }
            if (source != null && source.isFile && !SafeFiles.isSymlink(source)) {
                source.copyTo(target, overwrite = false)
            } else {
                target.writeText("")
            }
        }
        ensureStrategyDefaults()
    }

    fun ensureStrategyDefaults() {
        SafeFiles.privateDirectory(ZapretPaths.userDataRoot)
        if (!ZapretPaths.selectedStrategyFile.isFile) {
            ZapretPaths.selectedStrategyFile.writeText(ZapretConfig.DEFAULT_STRATEGY + "\n")
        }
        if (!ZapretPaths.ipsetModeFile.isFile) {
            ZapretPaths.ipsetModeFile.writeText(IpsetMode.NONE.value + "\n")
        }
        if (!discordUdpFile.isFile) {
            discordUdpFile.writeText("1\n")
        }
        if (!blockQuicFile.isFile) {
            blockQuicFile.writeText("1\n")
        }
    }

    fun readConfig(): ZapretConfig {
        ensureStrategyDefaults()
        val strategy = ZapretPaths.selectedStrategyFile.readText().trim()
            .ifEmpty { ZapretConfig.DEFAULT_STRATEGY }
        val mode = IpsetMode.of(ZapretPaths.ipsetModeFile.readText().trim())
        val discordUdp = discordUdpFile.readText().trim() != "0"
        val blockQuic = blockQuicFile.readText().trim() != "0"
        return ZapretConfig(
            strategyId = if (StrategyCatalog.isValidId(strategy)) strategy else ZapretConfig.DEFAULT_STRATEGY,
            ipsetMode = mode,
            discordUdp = discordUdp,
            blockQuic = blockQuic,
        )
    }

    fun writeConfig(config: ZapretConfig) {
        require(StrategyCatalog.isValidId(config.strategyId)) { "invalid strategy" }
        SafeFiles.privateDirectory(ZapretPaths.userDataRoot)
        ZapretPaths.selectedStrategyFile.writeText(config.strategyId.trim() + "\n")
        ZapretPaths.ipsetModeFile.writeText(config.ipsetMode.value + "\n")
        discordUdpFile.writeText(if (config.discordUdp) "1\n" else "0\n")
        blockQuicFile.writeText(if (config.blockQuic) "1\n" else "0\n")
    }

    private val discordUdpFile: File
        get() = File(ZapretPaths.userDataRoot, "discord-udp")

    private val blockQuicFile: File
        get() = File(ZapretPaths.userDataRoot, "block-quic")

    fun fileFor(name: String): File = regularFile(name)

    fun tooLargeForEditor(name: String): Boolean {
        val file = regularFile(name)
        return file.isFile && file.length() > EDITOR_MAX_BYTES
    }

    fun readList(name: String): String {
        ensureSeeded()
        return regularFile(name).takeIf { it.isFile }?.readText().orEmpty()
    }

    fun writeList(name: String, text: String) {
        prepareListsDir()
        val normalized = if (name.startsWith("ipset-")) {
            HostListText.normalizeIpset(text)
        } else {
            HostListText.normalizeHosts(text)
        }
        regularFile(name).writeText(normalized)
    }

    fun resetList(name: String) {
        val defaults = defaultsDir()
        val source = defaults?.let { File(it, name) }
        prepareListsDir()
        val target = regularFile(name)
        if (source != null && source.isFile && !SafeFiles.isSymlink(source)) {
            source.copyTo(target, overwrite = true)
        } else {
            target.writeText("")
        }
    }

    fun resetAll() {
        for (name in LIST_FILES) resetList(name)
    }

    private fun prepareListsDir() {
        SafeFiles.deleteIfSymlink(listsDir)
        listsDir.mkdirs()
    }

    private fun regularFile(name: String): File {
        require(name in LIST_FILES) { "unknown list: $name" }
        val file = File(listsDir, name)
        SafeFiles.deleteIfSymlink(file)
        return file
    }

    companion object {
        const val USER_HOST_LIST = "list-general-user.txt"

        /** Compose TextField cannot layout tens of thousands of lines (ipset-all). */
        const val EDITOR_MAX_BYTES = 48L * 1024

        fun applyListDrafts(
            drafts: Map<String, String>,
            oversized: Set<String>,
            replaceOversized: Set<String> = emptySet(),
        ): Map<String, String> =
            drafts.filter { (name, text) ->
                name in LIST_FILES && when {
                    name !in oversized -> true
                    name !in replaceOversized -> false
                    text.isBlank() -> false
                    else -> true
                }
            }

        val LIST_FILES = listOf(
            "list-general.txt",
            USER_HOST_LIST,
            "list-google.txt",
            "list-exclude.txt",
            "list-exclude-user.txt",
            "ipset-all.txt",
            "ipset-exclude.txt",
            "ipset-exclude-user.txt",
        )

        val LIST_LABELS = mapOf(
            "list-general.txt" to "Домены (general)",
            USER_HOST_LIST to "Домены пользователя",
            "list-google.txt" to "Домены Google",
            "list-exclude.txt" to "Исключения доменов",
            "list-exclude-user.txt" to "Исключения доменов (user)",
            "ipset-all.txt" to "IP-список (ipset-all)",
            "ipset-exclude.txt" to "Исключения IP",
            "ipset-exclude-user.txt" to "Исключения IP (user)",
        )
    }
}
