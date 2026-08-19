package zapret.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class DaemonStatus(
    val transparent: Boolean = false,
    val socks: Boolean = false,
    val uptime: Duration? = null,
) {
    val running: Boolean get() = transparent || socks
}

/** Starts/stops the LaunchDaemon-backed utunws engine. */
class ZapretService(
    private val privileges: PrivilegeRunner,
    private val lists: EngineListsStore = EngineListsStore(),
    private val passwordless: PasswordlessControl = PasswordlessControl(privileges),
) : ZapretControl {

    override fun start(): CommandResult {
        // Already installed: kickstart via restart.sh (covered by NOPASSWD).
        // Fresh install still needs admin once through install.sh.
        if (ZapretPaths.isInstalled && ZapretPaths.restartScript.canExecute()) {
            return runInstalled(ZapretPaths.restartScript)
        }
        return installOrKick()
    }

    override fun stop(): CommandResult {
        val script = ZapretPaths.stopScript.takeIf { it.canExecute() }
            ?: return CommandResult(0, "engine not installed")
        return runInstalled(script)
    }

    override fun restart(): CommandResult {
        val script = ZapretPaths.restartScript.takeIf { it.canExecute() }
            ?: return installOrKick()
        return runInstalled(script)
    }

    override fun status(): DaemonStatus {
        val pid = pidOfUtunws()
        return DaemonStatus(
            transparent = pid != null,
            socks = false,
            uptime = pid?.let(::elapsed),
        )
    }

    private fun installOrKick(): CommandResult {
        lists.ensureSeeded()
        val payload = ZapretPaths.enginePayload()
            ?: return CommandResult(1, "engine payload not found; reinstall the app")
        if (!ZapretPaths.hasPrebuiltUtunws(payload)) {
            return CommandResult(1, "utunws binary missing in payload; rebuild the app")
        }
        if (!ZapretPaths.isValidUserDataRoot(ZapretPaths.userDataRoot)) {
            return CommandResult(1, "invalid user data path")
        }
        return EnginePrivileged.install(
            privileges = privileges,
            payload = payload,
            dataRoot = ZapretPaths.userDataRoot,
            passwordless = runCatching { AppPrefsStore().read().passwordless }.getOrDefault(true),
            timeout = 3.minutes,
        )
    }

    private fun runInstalled(script: java.io.File): CommandResult {
        val sudo = Shell.run("/usr/bin/sudo", "-n", script.absolutePath, timeout = INIT_TIMEOUT)
        if (sudo.ok || !sudo.needsPassword()) return sudo
        val wantSudoers = runCatching { AppPrefsStore().read().passwordless }.getOrDefault(true) &&
            !passwordless.isEnabled()
        return passwordless.runEngineThenMaybeSudoers(
            engineScript = script,
            installSudoers = wantSudoers,
            timeout = INIT_TIMEOUT,
        )
    }

    private fun CommandResult.needsPassword(): Boolean =
        output.contains("a password is required") || output.contains("a terminal is required")

    private fun pidOfUtunws(): String? =
        Shell.run("/usr/bin/pgrep", "-x", "utunws", timeout = PROBE_TIMEOUT)
            .takeIf { it.ok }
            ?.output
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()

    private fun elapsed(pid: String): Duration? =
        Shell.run("/bin/ps", "-o", "etime=", "-p", pid, timeout = PROBE_TIMEOUT)
            .takeIf { it.ok }
            ?.let { parseElapsed(it.output.trim()) }

    companion object {
        private val PROBE_TIMEOUT = 5.seconds
        private val INIT_TIMEOUT = 90.seconds

        fun parseElapsed(text: String): Duration? {
            val dash = text.indexOf('-')
            val days = if (dash < 0) 0L else text.substring(0, dash).toLongOrNull() ?: return null
            val clock = if (dash < 0) text else text.substring(dash + 1)
            val parts = clock.split(':').map { it.toLongOrNull() ?: return null }
            val seconds = when (parts.size) {
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> return null
            }
            return (days * 86_400 + seconds).seconds
        }
    }
}
