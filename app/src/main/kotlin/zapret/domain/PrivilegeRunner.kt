package zapret.domain

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class PrivilegeEscalationCancelled : Exception("Запрос прав администратора отменён")

/**
 * Runs shell code as root through the macOS authorization dialog.
 *
 * Every privileged step is a whole script, not a chain of commands, so the user sees one
 * password prompt per operation instead of one per command.
 */
class PrivilegeRunner {

    fun runScript(
        script: String,
        args: List<String> = emptyList(),
        timeout: Duration = DEFAULT_TIMEOUT,
    ): CommandResult {
        val file = SecureTemp.file("zapret-priv-", ".sh")
        return try {
            file.writeText(script)
            SecureTemp.lockDown(file, executable = true)
            runScriptFile(file, args, timeout)
        } finally {
            file.delete()
        }
    }

    fun runScriptFile(
        file: File,
        args: List<String> = emptyList(),
        timeout: Duration = DEFAULT_TIMEOUT,
    ): CommandResult {
        val argv = buildList {
            add("/usr/bin/osascript")
            APPLESCRIPT.forEach { line -> add("-e"); add(line) }
            add(file.absolutePath)
            addAll(args)
        }
        val result = Shell.run(*argv.toTypedArray(), timeout = timeout)
        if (!result.ok && result.output.contains(USER_CANCELLED_ERROR)) throw PrivilegeEscalationCancelled()
        return result
    }

    private companion object {
        val DEFAULT_TIMEOUT = 10.minutes

        /** argv is quoted one by one so that paths with spaces survive the AppleScript hop. */
        val APPLESCRIPT = listOf(
            "on run argv",
            "set cmd to \"/bin/sh\"",
            "repeat with a in argv",
            "set cmd to cmd & \" \" & quoted form of (a as text)",
            "end repeat",
            "do shell script cmd & \" 2>&1\" with administrator privileges",
            "end run",
        )

        const val USER_CANCELLED_ERROR = "-128"
    }
}
