package zapret.domain

import java.io.File

/**
 * Narrow sudoers rule: NOPASSWD for stop.sh and restart.sh under the system engine root.
 * Install still prompts (it accepts a payload path argument).
 */
class PasswordlessControl(private val privileges: PrivilegeRunner) {

    val user: String = System.getProperty("user.name")

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

    private fun sudoersInstallScript(): File? = sequenceOf(
        File(ZapretPaths.systemRoot, "install-sudoers.sh"),
        ZapretPaths.enginePayload()?.let { File(it, "install-sudoers.sh") },
    ).filterNotNull().firstOrNull { it.isFile }

    private companion object {
        const val SUDOERS = "/etc/sudoers.d/zapret"

        val REMOVE = """
            #!/bin/sh
            rm -f "$SUDOERS" /etc/sudoers.d/zapret2
            echo OK
        """.trimIndent()
    }
}
