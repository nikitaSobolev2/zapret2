package zapret.domain

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
        return privileges.runScript(
            INSTALL,
            args = listOf(
                user,
                ZapretPaths.stopScript.absolutePath,
                ZapretPaths.restartScript.absolutePath,
            ),
        )
    }

    fun disable(): CommandResult = privileges.runScript(REMOVE)

    private companion object {
        const val SUDOERS = "/etc/sudoers.d/zapret"

        val INSTALL = """
            #!/bin/sh
            set -e
            PATH="/usr/sbin:/sbin:/usr/bin:/bin"
            export PATH
            user="${'$'}1"
            stop="${'$'}2"
            restart="${'$'}3"
            case "${'$'}user" in
                ""|*[!A-Za-z0-9._-]*)
                    echo "invalid sudoers username" >&2
                    exit 1
                    ;;
            esac
            expected_stop="/Library/Application Support/Zapret/stop.sh"
            expected_restart="/Library/Application Support/Zapret/restart.sh"
            if [ "${'$'}stop" != "${'$'}expected_stop" ] || [ "${'$'}restart" != "${'$'}expected_restart" ]; then
                echo "invalid script path" >&2
                exit 1
            fi
            tmp="${'$'}(mktemp)"
            umask 337
            # sudoers: escape spaces in paths (Application Support).
            stop_esc="${'$'}(printf '%s' "${'$'}stop" | sed 's/ /\\ /g')"
            restart_esc="${'$'}(printf '%s' "${'$'}restart" | sed 's/ /\\ /g')"
            printf '%s ALL=(root) NOPASSWD: %s, %s\n' "${'$'}user" "${'$'}stop_esc" "${'$'}restart_esc" >"${'$'}tmp"
            if visudo -cf "${'$'}tmp" >/dev/null 2>&1; then
                install -m 440 -o root -g wheel "${'$'}tmp" "$SUDOERS"
                rm -f "${'$'}tmp"
                # drop legacy rule
                rm -f /etc/sudoers.d/zapret2
                echo OK
            else
                rm -f "${'$'}tmp"
                echo "generated sudoers rule failed validation" >&2
                cat "${'$'}tmp" >&2 || true
                exit 1
            fi
        """.trimIndent()

        val REMOVE = """
            #!/bin/sh
            rm -f "$SUDOERS" /etc/sudoers.d/zapret2
            echo OK
        """.trimIndent()
    }
}
