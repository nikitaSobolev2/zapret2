package zapret.domain

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Runs engine scripts as root. Copies the payload out of the app/build tree first:
 * elevated `osascript` cannot execute files under Desktop/Documents (TCC → exit 126).
 */
object EnginePrivileged {

    fun install(
        privileges: PrivilegeRunner,
        payload: File,
        dataRoot: File,
        passwordless: Boolean = true,
        timeout: Duration = 5.minutes,
    ): CommandResult {
        val stage = Files.createTempDirectory("zapret-engine-").toFile()
        return try {
            SafeFiles.copyTree(payload, stage)
            markExecutables(stage)
            val install = File(stage, "install.sh")
            if (!install.isFile) return CommandResult(1, "install.sh missing in staged payload")
            privileges.runScript(
                install.readText(),
                args = listOf(
                    stage.absolutePath,
                    dataRoot.absolutePath,
                    if (passwordless) "1" else "0",
                ),
                timeout = timeout,
            )
        } finally {
            SafeFiles.deleteTree(stage)
        }
    }

    fun runScriptText(
        privileges: PrivilegeRunner,
        scriptFile: File,
        args: List<String> = emptyList(),
        timeout: Duration = 3.minutes,
    ): CommandResult {
        if (!scriptFile.isFile) return CommandResult(1, "script not found: ${scriptFile.name}")
        return privileges.runScript(scriptFile.readText(), args = args, timeout = timeout)
    }

    private fun markExecutables(root: File) {
        Files.walk(root.toPath()).use { stream ->
            stream.forEach { entry ->
                if (Files.isSymbolicLink(entry)) return@forEach
                val file = entry.toFile()
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    file.setExecutable(true, false)
                    file.setReadable(true, false)
                    return@forEach
                }
                file.setReadable(true, false)
                val name = file.name
                if (name.endsWith(".sh") || name == "utunws") {
                    file.setExecutable(true, false)
                }
            }
        }
    }
}
