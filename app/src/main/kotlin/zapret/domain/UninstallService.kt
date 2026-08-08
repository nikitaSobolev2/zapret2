package zapret.domain

import java.io.File
import kotlin.time.Duration.Companion.minutes

enum class UninstallScope(val label: String) {
    APP_ONLY("Только приложение"),
    APP_AND_ZAPRET("Приложение и zapret2"),
}

/** Removes the app and, when asked, zapret2 itself. */
class UninstallService(
    private val privileges: PrivilegeRunner,
    private val tgProxy: TgWsProxyService = TgWsProxyService(),
) {

    fun uninstall(scope: UninstallScope): CommandResult {
        tgProxy.stop()
        val zapret = when (scope) {
            UninstallScope.APP_ONLY -> CommandResult(0, "")
            UninstallScope.APP_AND_ZAPRET -> removeZapret()
        }
        if (!zapret.ok) return zapret

        val bundle = ZapretPaths.appBundle() ?: return zapret
        return trash(bundle)
    }

    private fun removeZapret(): CommandResult = privileges.runScriptFile(
        BundledScript.extract("uninstall.sh"),
        args = listOf(ZapretPaths.base.absolutePath),
        timeout = 3.minutes,
    )

    /** Finder puts the bundle in the trash, so an accidental uninstall is recoverable. */
    private fun trash(bundle: File): CommandResult = Shell.run(
        "/usr/bin/osascript",
        "-e", "on run argv",
        "-e", "tell application \"Finder\" to delete POSIX file (item 1 of argv)",
        "-e", "end run",
        bundle.absolutePath,
    )
}
