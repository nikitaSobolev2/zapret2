package zapret.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceOrchestratorTest {

    @Test
    fun applyTgStartsProxyWithoutStartingZapret() {
        val store = storeInTemp()
        val tg = RecordingTg()
        val zapret = FakeZapret(running = false)
        val orchestrator = ServiceOrchestrator(zapret, tg, store)

        val config = TgWsProxyConfig(
            enabled = true,
            secret = "0123456789abcdef0123456789abcdef",
        )
        assertTrue(orchestrator.applyTg(config).ok)
        assertEquals(config, store.read())
        assertEquals(0, zapret.startCount)
        assertEquals(1, tg.restartCount)
        assertEquals(0, tg.startCount)
    }

    @Test
    fun applyTgRestartsWhenZapretRunning() {
        val store = storeInTemp()
        val tg = RecordingTg()
        val orchestrator = ServiceOrchestrator(FakeZapret(running = true), tg, store)

        val config = TgWsProxyConfig(
            enabled = true,
            secret = "0123456789abcdef0123456789abcdef",
        )
        assertTrue(orchestrator.applyTg(config).ok)
        assertEquals(1, tg.restartCount)
    }

    @Test
    fun applyTgStopsWhenDisabled() {
        val store = storeInTemp()
        val tg = RecordingTg()
        val orchestrator = ServiceOrchestrator(FakeZapret(running = true), tg, store)

        val config = TgWsProxyConfig(
            enabled = false,
            secret = "0123456789abcdef0123456789abcdef",
        )
        assertTrue(orchestrator.applyTg(config).ok)
        assertEquals(1, tg.stopCount)
        assertFalse(store.read().enabled)
    }

    @Test
    fun applyTgDoesNotPersistEnabledWhenRestartFails() {
        val store = storeInTemp()
        val previous = TgWsProxyConfig(
            enabled = false,
            host = "127.0.0.1",
            secret = "0123456789abcdef0123456789abcdef",
        )
        store.write(previous)
        val tg = RecordingTg(restartResult = CommandResult(139, "tg-ws-proxy exited early (code=139)"))
        val orchestrator = ServiceOrchestrator(FakeZapret(running = false), tg, store)

        val attempted = previous.copy(enabled = true, host = "10.0.0.1")
        val result = orchestrator.applyTg(attempted)
        assertFalse(result.ok)
        assertEquals(1, tg.restartCount)
        val persisted = store.read()
        assertFalse(persisted.enabled)
        assertEquals("10.0.0.1", persisted.host)
    }

    @Test
    fun applyTgPersistsDisableEvenWhenStopFails() {
        val store = storeInTemp()
        val enabled = TgWsProxyConfig(
            enabled = true,
            secret = "0123456789abcdef0123456789abcdef",
        )
        store.write(enabled)
        val tg = RecordingTg(stopResult = CommandResult(1, "kill failed"))
        val orchestrator = ServiceOrchestrator(FakeZapret(running = true), tg, store)

        val result = orchestrator.applyTg(enabled.copy(enabled = false))
        assertFalse(result.ok)
        assertEquals(1, tg.stopCount)
        assertFalse(store.read().enabled)
    }

    @Test
    fun startAllStartsTgWhenEnabled() {
        val store = storeInTemp()
        store.write(TgWsProxyConfig(enabled = true, secret = "0123456789abcdef0123456789abcdef"))
        val tg = RecordingTg()
        val zapret = FakeZapret(running = false)
        val orchestrator = ServiceOrchestrator(zapret, tg, store)

        assertTrue(orchestrator.startAll().ok)
        assertEquals(1, zapret.startCount)
        assertEquals(1, tg.startCount)
    }

    @Test
    fun startAllSkipsTgWhenDisabled() {
        val store = storeInTemp()
        store.write(TgWsProxyConfig(enabled = false, secret = "0123456789abcdef0123456789abcdef"))
        val tg = RecordingTg()
        val orchestrator = ServiceOrchestrator(FakeZapret(running = false), tg, store)

        val result = orchestrator.startAll()
        assertTrue(result.ok)
        assertEquals(null, result.warning)
        assertEquals(0, tg.startCount)
    }

    @Test
    fun startAllKeepsZapretWhenTgCrashes() {
        val store = storeInTemp()
        store.write(TgWsProxyConfig(enabled = true, secret = "0123456789abcdef0123456789abcdef"))
        val tg = RecordingTg(
            startResult = CommandResult(139, "tg-ws-proxy exited early (code=139)"),
        )
        val zapret = FakeZapret(running = false)
        val orchestrator = ServiceOrchestrator(zapret, tg, store)

        val result = orchestrator.startAll()
        assertTrue(result.ok)
        assertEquals(1, zapret.startCount)
        assertEquals(1, tg.startCount)
        assertTrue(result.warning?.contains("139") == true)
    }

    @Test
    fun restartAllStopsTgWhenDisabled() {
        val store = storeInTemp()
        store.write(TgWsProxyConfig(enabled = false, secret = "0123456789abcdef0123456789abcdef"))
        val tg = RecordingTg()
        val orchestrator = ServiceOrchestrator(FakeZapret(running = true), tg, store)

        assertTrue(orchestrator.restartAll().ok)
        assertEquals(1, tg.stopCount)
        assertEquals(0, tg.restartCount)
    }

    private fun storeInTemp(): TgWsProxyStore {
        val dir = File.createTempFile("tg-ws-cfg", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        return TgWsProxyStore(File(dir, "config.json"))
    }
}

private class FakeZapret(private val running: Boolean) : ZapretControl {
    var startCount = 0
    override fun start(): CommandResult {
        startCount++
        return CommandResult(0, "ok")
    }
    override fun stop(): CommandResult = CommandResult(0, "ok")
    override fun restart(): CommandResult = CommandResult(0, "ok")
    override fun status(): DaemonStatus = DaemonStatus(transparent = running)
}

private class RecordingTg(
    private val startResult: CommandResult = CommandResult(0, "start"),
    private val stopResult: CommandResult = CommandResult(0, "stop"),
    private val restartResult: CommandResult = CommandResult(0, "restart"),
) : TgWsProxyControl {
    var startCount = 0
    var stopCount = 0
    var restartCount = 0
    override fun start(config: TgWsProxyConfig): CommandResult {
        startCount++
        return startResult
    }
    override fun stop(): CommandResult {
        stopCount++
        return stopResult
    }
    override fun restart(config: TgWsProxyConfig): CommandResult {
        restartCount++
        return restartResult
    }
    override fun isRunning(): Boolean = false
}
