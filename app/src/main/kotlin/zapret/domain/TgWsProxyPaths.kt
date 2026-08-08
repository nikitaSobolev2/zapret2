package zapret.domain

import java.io.File

/** User-level layout for the bundled TG WS Proxy sidecar (no root / LaunchDaemon). */
object TgWsProxyPaths {

    private val supportRoot: File
        get() = File(
            System.getProperty("user.home"),
            "Library/Application Support/Zapret/tg-ws-proxy",
        ).also { it.mkdirs() }

    val configFile: File get() = File(supportRoot, "config.json")
    val pidFile: File get() = File(supportRoot, "proxy.pid")
    val logFile: File get() = File(supportRoot, "proxy.log")

    /** Packaged onedir binary, or null when running from Gradle without a built sidecar. */
    fun bundledBinary(): File? {
        val resources = System.getProperty("compose.application.resources.dir")?.let(::File) ?: return null
        val candidate = File(resources, "tg-ws-proxy/tg-ws-proxy")
        if (!candidate.isFile) return null
        // DMG / Homebrew installs often drop the execute bit from nested Mach-O binaries.
        if (!candidate.canExecute()) {
            candidate.setExecutable(true, false)
        }
        return candidate.takeIf { it.canExecute() }
    }

    /** Dev fallback: `third_party/tg-ws-proxy` next to the zapret2 tree. */
    fun sourcePackageRoot(): File? {
        val zapret = ZapretPaths.sourceTree() ?: return null
        val root = File(zapret, "third_party/tg-ws-proxy")
        return root.takeIf { File(root, "proxy/tg_ws_proxy.py").isFile }
    }
}
