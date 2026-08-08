package zapret.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceOrchestratorTest {

    @Test
    fun applyTgPersistsAndSkipsStartWhenZapretStopped() {
        val store = storeInTemp()
        val tg = RecordingTg()
        val orchestrator = ServiceOrchestrator(FakeZapret(running = false), tg, store)

        val config = TgWsProxyConfig(
            enabled = true,
            secret = "0123456789abcdef0123456789abcdef",
        )
        assertTrue(orchestrator.applyTg(config).ok)
        assertEquals(config, store.read())
        assertEquals(0, tg.startCount)
        assertEquals(0, tg.restartCount)
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

private class RecordingTg : TgWsProxyControl {
    var startCount = 0
    var stopCount = 0
    var restartCount = 0
    override fun start(config: TgWsProxyConfig): CommandResult {
        startCount++
        return CommandResult(0, "start")
    }
    override fun stop(): CommandResult {
        stopCount++
        return CommandResult(0, "stop")
    }
    override fun restart(config: TgWsProxyConfig): CommandResult {
        restartCount++
        return CommandResult(0, "restart")
    }
    override fun isRunning(): Boolean = false
}
