package zapret.domain

import java.io.File
import kotlin.time.Duration.Companion.minutes

enum class UninstallScope(val label: String) {
    APP_ONLY("Только приложение"),
    APP_AND_ZAPRET("Приложение и движок zapret"),
}

/** Removes the app and, when asked, the utunws system install (+ legacy /opt/zapret2). */
class UninstallService(
    private val privileges: PrivilegeRunner,
    private val tgProxy: TgWsProxyService = TgWsProxyService(),
) {

    fun uninstall(scope: UninstallScope): CommandResult {
        tgProxy.stop()
        val zapret = when (scope) {
            UninstallScope.APP_ONLY -> CommandResult(0, "")
            UninstallScope.APP_AND_ZAPRET -> removeEngine()
        }
        if (!zapret.ok) return zapret

        val bundle = ZapretPaths.appBundle() ?: return zapret
        return trash(bundle)
    }

    private fun removeEngine(): CommandResult {
        val script = sequenceOf(
            ZapretPaths.uninstallScript,
            ZapretPaths.enginePayload()?.resolve("uninstall.sh"),
        ).firstOrNull { it != null && it.isFile } ?: return CommandResult(1, "uninstall.sh not found")

        return EnginePrivileged.runScriptText(privileges, script, timeout = 3.minutes)
    }

    private fun trash(bundle: File): CommandResult = Shell.run(
        "/usr/bin/osascript",
        "-e", "on run argv",
        "-e", "tell application \"Finder\" to delete POSIX file (item 1 of argv)",
        "-e", "end run",
        bundle.absolutePath,
    )
}
