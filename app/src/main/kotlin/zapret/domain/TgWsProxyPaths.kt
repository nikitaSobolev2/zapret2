package zapret.domain

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** User-level layout for the bundled TG WS Proxy sidecar (no root / LaunchDaemon). */
object TgWsProxyPaths {

    private val supportRoot: File
        get() = SafeFiles.privateDirectory(
            File(
                System.getProperty("user.home"),
                "Library/Application Support/Zapret/tg-ws-proxy",
            ),
        )

    val configFile: File get() = File(supportRoot, "config.json")
    val pidFile: File get() = File(supportRoot, "proxy.pid")
    val logFile: File get() = File(supportRoot, "proxy.log")

    /** Packaged onedir binary, or null when running from Gradle without a built sidecar. */
    fun bundledBinary(): File? {
        val resources = System.getProperty("compose.application.resources.dir")?.let(::File) ?: return null
        val dir = File(resources, "tg-ws-proxy")
        val candidate = File(dir, "tg-ws-proxy")
        val internal = File(dir, "_internal")
        if (!candidate.isFile || SafeFiles.isSymlink(candidate)) return null
        if (SafeFiles.isSymlink(internal) || !Files.isDirectory(internal.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
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
        Files.walk(dir.toPath()).use { stream ->
            stream.forEach { path ->
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    return@forEach
                }
                val file = path.toFile()
                val name = file.name
                if (name == "tg-ws-proxy" || name == "Python" ||
                    name.endsWith(".so") || name.endsWith(".dylib")
                ) {
                    file.setExecutable(true, false)
                }
            }
        }
    }
}
