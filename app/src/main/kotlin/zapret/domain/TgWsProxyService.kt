package zapret.domain

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/** User-level lifecycle for the headless TG WS Proxy process (no sudo). */
class TgWsProxyService : TgWsProxyControl {

    override fun start(config: TgWsProxyConfig): CommandResult {
        TgWsProxyValidation.requireValid(config)
        if (!config.enabled) return CommandResult(0, "tg-ws-proxy disabled")
        if (isRunning()) return CommandResult(0, "tg-ws-proxy already running")

        val launch = resolveLaunch()
            ?: return CommandResult(1, "tg-ws-proxy binary not found; rebuild the app or run packaging/build_sidecar.sh")

        TgWsProxyPaths.logFile.parentFile?.mkdirs()
        val argv = launch.command + config.cliArgs()
        val builder = ProcessBuilder(argv)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(TgWsProxyPaths.logFile))
        launch.workDir?.let { builder.directory(it) }
        builder.environment().putAll(launch.env)

        return try {
            val process = builder.start()
            // Give the listener a moment; fail fast if it exits immediately.
            if (process.waitFor(400, TimeUnit.MILLISECONDS)) {
                val code = process.exitValue()
                return CommandResult(code, "tg-ws-proxy exited early (code=$code); see ${TgWsProxyPaths.logFile}")
            }
            TgWsProxyPaths.pidFile.writeText(process.pid().toString())
            CommandResult(0, "tg-ws-proxy started pid=${process.pid()}")
        } catch (error: Exception) {
            CommandResult(1, error.message ?: error.toString())
        }
    }

    override fun stop(): CommandResult {
        val pid = readPid() ?: return CommandResult(0, "tg-ws-proxy not running")
        val kill = Shell.run("/bin/kill", pid, timeout = 5.seconds)
        if (!kill.ok) {
            Shell.run("/bin/kill", "-9", pid, timeout = 5.seconds)
        }
        waitUntilStopped(pid)
        TgWsProxyPaths.pidFile.delete()
        return CommandResult(0, "tg-ws-proxy stopped")
    }

    override fun restart(config: TgWsProxyConfig): CommandResult {
        val stopped = stop()
        if (!stopped.ok) return stopped
        if (!config.enabled) return CommandResult(0, "tg-ws-proxy disabled")
        Thread.sleep(400)
        return start(config)
    }

    override fun isRunning(): Boolean {
        val pid = readPid() ?: return false
        val alive = Shell.run("/bin/kill", "-0", pid, timeout = 3.seconds).ok
        if (!alive) {
            TgWsProxyPaths.pidFile.delete()
            return false
        }
        return true
    }

    private fun readPid(): String? =
        TgWsProxyPaths.pidFile.takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.toLongOrNull() != null }

    private fun waitUntilStopped(pid: String) {
        repeat(20) {
            if (!Shell.run("/bin/kill", "-0", pid, timeout = 2.seconds).ok) return
            Thread.sleep(100)
        }
        Shell.run("/bin/kill", "-9", pid, timeout = 3.seconds)
    }

    private data class Launch(
        val command: List<String>,
        val workDir: File? = null,
        val env: Map<String, String> = emptyMap(),
    )

    private fun resolveLaunch(): Launch? {
        TgWsProxyPaths.bundledBinary()?.let {
            return Launch(listOf(it.absolutePath), workDir = it.parentFile)
        }

        val source = TgWsProxyPaths.sourcePackageRoot() ?: return null
        val python = sequenceOf(
            File(source, ".venv/bin/python"),
            File("/usr/bin/python3"),
        ).firstOrNull { it.canExecute() } ?: return null

        return Launch(
            command = listOf(python.absolutePath, "-m", "proxy"),
            workDir = source,
            env = mapOf("PYTHONPATH" to source.absolutePath),
        )
    }
}
