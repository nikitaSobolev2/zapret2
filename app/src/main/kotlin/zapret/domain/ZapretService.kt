package zapret.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class DaemonStatus(
    val transparent: Boolean = false,
    val socks: Boolean = false,
    val uptime: Duration? = null,
) {
    val running: Boolean get() = transparent || socks
}

/** Drives the zapret2 init script and reads daemon state without asking for a password. */
class ZapretService(private val privileges: PrivilegeRunner) : ZapretControl {

    override fun start(): CommandResult = init("start")
    override fun stop(): CommandResult = init("stop")
    override fun restart(): CommandResult = init("restart")

    /**
     * Runs an init action as root. Tries the passwordless sudo rule first (see [PasswordlessControl]);
     * only if that is not set up does it fall back to the osascript prompt.
     */
    private fun init(action: String): CommandResult {
        val sudo = Shell.run(
            "/usr/bin/sudo", "-n", ZapretPaths.initScript.absolutePath, action,
            timeout = INIT_TIMEOUT,
        )
        if (sudo.ok || !sudo.needsPassword()) return sudo
        return privileges.runScript(INIT_SCRIPT, args = listOf(ZapretPaths.initScript.absolutePath, action))
    }

    /** `sudo -n` prints this to stderr when the action is not covered by a NOPASSWD rule. */
    private fun CommandResult.needsPassword(): Boolean =
        output.contains("a password is required") || output.contains("a terminal is required")

    override fun status(): DaemonStatus {
        val transparent = pidOf(ZapretPaths.transparentPidFile.absolutePath)
        val socks = pidOf(ZapretPaths.socksPidFile.absolutePath)
        return DaemonStatus(
            transparent = transparent != null,
            socks = socks != null,
            uptime = (transparent ?: socks)?.let(::elapsed),
        )
    }

    /** macOS has no /proc, so the daemon is matched by the pidfile it was told to write. */
    private fun pidOf(pidFile: String): String? =
        Shell.run("/usr/bin/pgrep", "-f", "pidfile=$pidFile", timeout = PROBE_TIMEOUT)
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

        /** Fallback path when passwordless sudo is not configured: run the init script via osascript. */
        private val INIT_SCRIPT = """
            #!/bin/sh
            exec "${'$'}@"
        """.trimIndent()

        /** `ps -o etime=` prints [[dd-]hh:]mm:ss */
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
