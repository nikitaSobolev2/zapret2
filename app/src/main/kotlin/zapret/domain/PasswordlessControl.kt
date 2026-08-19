package zapret.domain

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Narrow sudoers rule: NOPASSWD for stop.sh and restart.sh under the system engine root.
 * Install still prompts (it accepts a payload path argument).
 */
class PasswordlessControl(private val privileges: PrivilegeRunner) {

    val user: String = ConfigValidation.accountName()

    fun isEnabled(): Boolean {
        val probe = Shell.run("/usr/bin/sudo", "-n", "-l", ZapretPaths.stopScript.absolutePath)
        return probe.ok
    }

    fun enable(): CommandResult {
        if (!ConfigValidation.isAllowedUsername(user)) {
            throw InstallFailed("Некорректное имя пользователя для sudoers: $user")
        }
        if (!ZapretPaths.isInstalled) {
            throw InstallFailed("Сначала установите движок zapret")
        }
        val script = sudoersInstallScript()
            ?: throw InstallFailed("install-sudoers.sh не найден")
        return EnginePrivileged.runScriptText(privileges, script, args = listOf(user))
    }

    fun disable(): CommandResult = privileges.runScript(REMOVE)

    fun runEngineThenMaybeSudoers(
        engineScript: File,
        installSudoers: Boolean,
        timeout: Duration = 3.minutes,
    ): CommandResult {
        if (!installSudoers) {
            return EnginePrivileged.runScriptText(privileges, engineScript, timeout = timeout)
        }
        val installer = sudoersInstallScript()
            ?: return EnginePrivileged.runScriptText(privileges, engineScript, timeout = timeout)
        if (!ConfigValidation.isAllowedUsername(user)) {
            return EnginePrivileged.runScriptText(privileges, engineScript, timeout = timeout)
        }
        val installerBody = installer.readText().substringAfter("#!/bin/sh").trimStart()
        val wrapper = """
            #!/bin/sh
            set -e
            engine="${'$'}1"
            account="${'$'}2"
            if [ "${'$'}engine" != "$STOP" ] && [ "${'$'}engine" != "$RESTART" ]; then
                echo "invalid engine script" >&2
                exit 1
            fi
            /bin/sh "${'$'}engine"
            set -- "${'$'}account"
            $installerBody
        """.trimIndent()
        return privileges.runScript(
            wrapper,
            args = listOf(engineScript.absolutePath, user),
            timeout = timeout,
        )
    }

    private fun sudoersInstallScript(): File? = sequenceOf(
        File(ZapretPaths.systemRoot, "install-sudoers.sh"),
        ZapretPaths.enginePayload()?.let { File(it, "install-sudoers.sh") },
    ).filterNotNull().firstOrNull { it.isFile }

    private companion object {
        const val SUDOERS = "/etc/sudoers.d/zapret"
        const val STOP = "/Library/Application Support/Zapret/stop.sh"
        const val RESTART = "/Library/Application Support/Zapret/restart.sh"

        val REMOVE = """
            #!/bin/sh
            rm -f "$SUDOERS" /etc/sudoers.d/zapret2
            echo OK
        """.trimIndent()
    }
}
