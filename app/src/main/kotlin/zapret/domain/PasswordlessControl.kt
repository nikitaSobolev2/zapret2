package zapret.domain

/**
 * Manages a narrowly-scoped sudoers rule that lets the current user run only
 * `zapret2 start|stop|restart` without a password. Everything else still prompts.
 *
 * The rule targets the fixed, root-owned init script with fixed arguments, so a
 * non-root user cannot use it to run anything else as root.
 */
class PasswordlessControl(private val privileges: PrivilegeRunner) {

    val user: String = System.getProperty("user.name")

    /** True when `sudo -n zapret2 <action>` already works, i.e. the rule is installed. */
    fun isEnabled(): Boolean {
        val probe = Shell.run("/usr/bin/sudo", "-n", "-l", ZapretPaths.initScript.absolutePath, "start")
        return probe.ok
    }

    fun enable(): CommandResult = privileges.runScript(INSTALL, args = listOf(user, ZapretPaths.initScript.absolutePath))

    fun disable(): CommandResult = privileges.runScript(REMOVE)

    private companion object {
        const val SUDOERS = "/etc/sudoers.d/zapret2"

        // $1 = user, $2 = init script path. Validated with visudo before being installed.
        val INSTALL = """
            #!/bin/sh
            set -e
            PATH="/usr/sbin:/sbin:/usr/bin:/bin"
            export PATH
            user="${'$'}1"
            init="${'$'}2"
            tmp="${'$'}(mktemp)"
            umask 337
            printf '%s ALL=(root) NOPASSWD: %s start, %s stop, %s restart\n' "${'$'}user" "${'$'}init" "${'$'}init" "${'$'}init" >"${'$'}tmp"
            if visudo -cf "${'$'}tmp" >/dev/null 2>&1; then
                install -m 440 -o root -g wheel "${'$'}tmp" "$SUDOERS"
                rm -f "${'$'}tmp"
                echo OK
            else
                rm -f "${'$'}tmp"
                echo "generated sudoers rule failed validation" >&2
                exit 1
            fi
        """.trimIndent()

        val REMOVE = """
            #!/bin/sh
            rm -f "$SUDOERS"
            echo OK
        """.trimIndent()
    }
}
