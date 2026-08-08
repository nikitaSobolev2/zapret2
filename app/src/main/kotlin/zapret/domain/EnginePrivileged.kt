package zapret.domain

import java.io.File
import java.nio.file.Files
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
        timeout: Duration = 5.minutes,
    ): CommandResult {
        val stage = Files.createTempDirectory("zapret-engine-").toFile()
        return try {
            payload.copyRecursively(stage, overwrite = true)
            markExecutables(stage)
            val install = File(stage, "install.sh")
            if (!install.isFile) return CommandResult(1, "install.sh missing in staged payload")
            privileges.runScript(
                install.readText(),
                args = listOf(stage.absolutePath, dataRoot.absolutePath),
                timeout = timeout,
            )
        } finally {
            stage.deleteRecursively()
        }
    }

    fun runScriptText(
        privileges: PrivilegeRunner,
        scriptFile: File,
        args: List<String> = emptyList(),
        timeout: Duration = 3.minutes,
    ): CommandResult {
        if (!scriptFile.isFile) return CommandResult(1, "script not found: ${scriptFile.name}")
        // System install path is already root-owned; /tmp copy still works and is safer.
        return privileges.runScript(scriptFile.readText(), args = args, timeout = timeout)
    }

    private fun markExecutables(root: File) {
        // Avoid rsync -a copying a 0700 temp dir into /Library (breaks droproot reads).
        root.setExecutable(true, false)
        root.setReadable(true, false)
        root.walkTopDown().forEach { file ->
            if (file.isDirectory) {
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
