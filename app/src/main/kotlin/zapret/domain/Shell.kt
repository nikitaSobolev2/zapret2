package zapret.domain

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class CommandResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0

    /**
     * One-line summary for the UI. Prefers `zapret2:` diagnostics (rollback / VPN conflict)
     * over incidental lines like "Stopping daemon…".
     */
    fun lastLine(): String {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return lines.lastOrNull { it.startsWith("zapret2:") }
            ?: lines.lastOrNull()
            ?: ""
    }
}

/** Thin blocking wrapper over [ProcessBuilder]. Callers are responsible for staying off the UI thread. */
object Shell {

    private val defaultTimeout = 120.seconds

    fun run(
        vararg argv: String,
        workDir: File? = null,
        env: Map<String, String> = emptyMap(),
        timeout: Duration = defaultTimeout,
    ): CommandResult {
        val builder = ProcessBuilder(*argv).redirectErrorStream(true)
        workDir?.let { builder.directory(it) }
        builder.environment().putAll(env)

        val process = builder.start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().use { it.readText() }

        if (!process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return CommandResult(TIMEOUT_EXIT_CODE, output + "\ntimed out after $timeout")
        }
        return CommandResult(process.exitValue(), output.trim())
    }

    const val TIMEOUT_EXIT_CODE = 124
}
