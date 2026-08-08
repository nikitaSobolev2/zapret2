package zapret.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrategyProbeTest {

    @Test
    fun scorePrefersReachabilityThenStabilityThenLatency() {
        val bothFast = row(
            "both-fast",
            discord = HostProbeMetrics(3, 3, 80),
            youtube = HostProbeMetrics(3, 3, 90),
            control = HostProbeMetrics(3, 3, 50),
        )
        val bothFlaky = row(
            "both-flaky",
            discord = HostProbeMetrics(1, 3, 80),
            youtube = HostProbeMetrics(1, 3, 90),
            control = HostProbeMetrics(3, 3, 50),
        )
        val bothSlow = row(
            "both-slow",
            discord = HostProbeMetrics(3, 3, 800),
            youtube = HostProbeMetrics(3, 3, 900),
            control = HostProbeMetrics(3, 3, 50),
        )
        val discordOnly = row(
            "discord-only",
            discord = HostProbeMetrics(3, 3, 50),
            youtube = HostProbeMetrics(0, 3, null),
            control = HostProbeMetrics(3, 3, 50),
        )
        val brokenCtrl = row(
            "broken",
            discord = HostProbeMetrics(3, 3, 50),
            youtube = HostProbeMetrics(3, 3, 50),
            control = HostProbeMetrics(1, 3, 50),
        )

        assertTrue(bothFast.score > bothFlaky.score)
        assertTrue(bothFast.score > bothSlow.score)
        assertTrue(bothFlaky.score > discordOnly.score)
        assertEquals(-1L, brokenCtrl.score)
        assertTrue(bothFast.usable)
        assertFalse(brokenCtrl.usable)
    }

    @Test
    fun controlNeedsMajoritySuccesses() {
        val ok = row(
            "ok",
            discord = HostProbeMetrics(1, 3, 100),
            youtube = HostProbeMetrics(0, 3, null),
            control = HostProbeMetrics(2, 3, 40),
        )
        val bad = row(
            "bad",
            discord = HostProbeMetrics(3, 3, 100),
            youtube = HostProbeMetrics(3, 3, 100),
            control = HostProbeMetrics(1, 3, 40),
        )
        assertTrue(ok.controlOk)
        assertFalse(bad.controlOk)
    }

    @Test
    fun shortlistStartsWithMildSimpleFake() {
        assertEquals("general-simple-fake", StrategyProbe.SHORTLIST.first())
        assertTrue(StrategyProbe.SHORTLIST.contains("general-fake-tls-auto"))
        assertTrue(StrategyProbe.SHORTLIST.contains("general-exp"))
    }

    @Test
    fun milderWinsOnEqualScore() {
        val metrics = HostProbeMetrics(2, 3, 120)
        val rows = listOf(
            row("mild", metrics, metrics, HostProbeMetrics(3, 3, 40)),
            row("harsh", metrics, metrics, HostProbeMetrics(3, 3, 40)),
        )
        var winner: String? = null
        var bestScore = Long.MIN_VALUE
        var bestIndex = Int.MAX_VALUE
        rows.forEachIndexed { index, probeRow ->
            if (probeRow.usable && (probeRow.score > bestScore || (probeRow.score == bestScore && index < bestIndex))) {
                bestScore = probeRow.score
                bestIndex = index
                winner = probeRow.strategyId
            }
        }
        assertEquals("mild", winner)
    }

    private fun row(
        id: String,
        discord: HostProbeMetrics,
        youtube: HostProbeMetrics,
        control: HostProbeMetrics,
    ) = StrategyProbeRow(id, discord, youtube, control)
}
