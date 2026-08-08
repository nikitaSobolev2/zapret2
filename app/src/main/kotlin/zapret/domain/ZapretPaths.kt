package zapret.domain

import java.io.File

/** Filesystem layout of an installed zapret2 plus discovery of the sources the app installs from. */
object ZapretPaths {

    val base = File("/opt/zapret2")
    val initScript = File(base, "init.d/macos/zapret2")
    val config = File(base, "config")
    val configDefault = File(base, "config.default")
    val plist = File(base, "init.d/macos/zapret2.plist")
    val launchDaemon = File("/Library/LaunchDaemons/zapret2.plist")
    val tpws = File(base, "tpws/tpws")

    /** Pidfile names come from `pidfile_of_daemon` in init.d/macos/functions. */
    val transparentPidFile = File("/var/run/tpws_1.pid")
    val socksPidFile = File("/var/run/tpws_2.pid")

    /** Writable copy of the sources, because the tree is compiled in place by `make mac`. */
    val buildDir: File = File(System.getProperty("user.home"), "Library/Caches/org.zapret.macos.control/src")

    val isInstalled: Boolean get() = initScript.canExecute() && config.isFile

    /**
     * The zapret2 tree to install from: an explicit override, the copy bundled into the .app,
     * or the repository this app lives in when running from Gradle.
     */
    fun sourceTree(): File? = sequenceOf(
        System.getenv("ZAPRET_SRC")?.let(::File),
        bundledResources()?.let { File(it, "zapret2-src") },
        repositoryRoot(),
    ).filterNotNull().firstOrNull(::isZapretTree)

    fun isZapretTree(dir: File): Boolean =
        File(dir, "init.d/macos/zapret2").isFile && File(dir, "Makefile").isFile

    /** Set by the Compose packaging when the app runs from a bundle. */
    private fun bundledResources(): File? =
        System.getProperty("compose.application.resources.dir")?.let(::File)?.takeIf { it.isDirectory }

    private fun repositoryRoot(): File? =
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull(::isZapretTree)

    /** The .app the process runs from, if any. Used by the uninstaller to remove itself. */
    fun appBundle(): File? {
        val start = bundledResources() ?: return null
        return generateSequence(start) { it.parentFile }.firstOrNull { it.name.endsWith(".app") }
    }
}
