package zapret.domain

import java.io.File

/** Filesystem layout for the utunws engine and discovery of the bundled payload. */
object ZapretPaths {

    const val DAEMON_LABEL = "org.zapret.macos.engine"
    const val PF_ANCHOR = "com.apple/zapret"

    val systemRoot = File("/Library/Application Support/Zapret")
    val launchDaemon = File("/Library/LaunchDaemons/$DAEMON_LABEL.plist")
    val utunws = File(systemRoot, "bin/utunws")
    val stopScript = File(systemRoot, "stop.sh")
    val restartScript = File(systemRoot, "restart.sh")
    val installScript = File(systemRoot, "install.sh")
    val uninstallScript = File(systemRoot, "uninstall.sh")

    val userDataRoot: File
        get() = SafeFiles.privateDirectory(
            File(
                System.getProperty("user.home"),
                "Library/Application Support/Zapret",
            ),
        )

    val selectedStrategyFile: File get() = File(userDataRoot, "selected-strategy")
    val ipsetModeFile: File get() = File(userDataRoot, "ipset-mode")
    val listsDir: File get() = File(userDataRoot, "lists")

    val isInstalled: Boolean
        get() = utunws.canExecute() && launchDaemon.isFile

    fun isValidUserDataRoot(path: File): Boolean {
        val home = System.getProperty("user.home") ?: return false
        val expected = File(home, "Library/Application Support/Zapret").canonicalFile
        return path.canonicalFile == expected
    }

    /**
     * Bundled engine payload (strategies, lists, scripts, and prebuilt utunws):
     * packaged app resources, or `engine/payload` next to the repo when running from Gradle.
     */
    fun enginePayload(): File? = sequenceOf(
        bundledResources()?.let { File(it, "engine") },
        repositoryEnginePayload(),
    ).filterNotNull().firstOrNull(::isEnginePayload)

    /**
     * True when bundled `bin/utunws` exists and is runnable.
     * Compose/DMG often copies Mach-O without +x; restore the bit in-place when possible.
     */
    fun hasPrebuiltUtunws(payload: File): Boolean {
        val binary = File(payload, "bin/utunws")
        if (!binary.isFile) return false
        if (binary.canExecute()) return true
        // Packaging strips mode bits; app resources are usually user-writable.
        binary.setExecutable(true, false)
        return binary.canExecute()
    }

    fun isEnginePayload(dir: File): Boolean =
        File(dir, "run.sh").isFile &&
            File(dir, "strategies.tsv").isFile &&
            File(dir, "default-lists").isDirectory

    fun appBundle(): File? {
        val start = bundledResources() ?: return null
        return generateSequence(start) { it.parentFile }.firstOrNull { it.name.endsWith(".app") }
    }

    private fun bundledResources(): File? =
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }

    private fun repositoryEnginePayload(): File? {
        val cwd = File("").absoluteFile
        return generateSequence(cwd) { it.parentFile }.map { File(it, "engine/payload") }
            .firstOrNull(::isEnginePayload)
            ?: generateSequence(cwd) { it.parentFile }.map { File(it, "payload") }
                .firstOrNull(::isEnginePayload)
    }
}
