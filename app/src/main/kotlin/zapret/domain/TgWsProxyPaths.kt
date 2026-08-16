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
        val dir = File(resources, "tg-ws-proxy")
        val candidate = File(dir, "tg-ws-proxy")
        if (!candidate.isFile || !File(dir, "_internal").isDirectory) return null
        restoreExecuteBits(dir)
        if (!candidate.canExecute()) {
            candidate.setExecutable(true, false)
        }
        return candidate.takeIf { it.canExecute() }
    }

    /** Dev fallback: `third_party/tg-ws-proxy` under the repo root. */
    fun sourcePackageRoot(): File? {
        val cwd = File("").absoluteFile
        return generateSequence(cwd) { it.parentFile }
            .map { File(it, "third_party/tg-ws-proxy") }
            .firstOrNull { File(it, "proxy/tg_ws_proxy.py").isFile }
    }

    private fun restoreExecuteBits(dir: File) {
        dir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name
            if (name == "tg-ws-proxy" || name == "Python" ||
                name.endsWith(".so") || name.endsWith(".dylib")
            ) {
                file.setExecutable(true, false)
            }
        }
    }
}
