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
            googlevideo = HostProbeMetrics(3, 3, 70),
            control = HostProbeMetrics(3, 3, 50),
        )
        val bothFlaky = row(
            "both-flaky",
            discord = HostProbeMetrics(1, 3, 80),
            youtube = HostProbeMetrics(1, 3, 90),
            googlevideo = HostProbeMetrics(1, 3, 70),
            control = HostProbeMetrics(3, 3, 50),
        )
        val bothSlow = row(
            "both-slow",
            discord = HostProbeMetrics(3, 3, 800),
            youtube = HostProbeMetrics(3, 3, 900),
            googlevideo = HostProbeMetrics(3, 3, 850),
            control = HostProbeMetrics(3, 3, 50),
        )
        val discordOnly = row(
            "discord-only",
            discord = HostProbeMetrics(3, 3, 50),
            youtube = HostProbeMetrics(0, 3, null),
            googlevideo = HostProbeMetrics(0, 3, null),
            control = HostProbeMetrics(3, 3, 50),
        )
        val brokenCtrl = row(
            "broken",
            discord = HostProbeMetrics(3, 3, 50),
            youtube = HostProbeMetrics(3, 3, 50),
            googlevideo = HostProbeMetrics(3, 3, 50),
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
    fun youtubeOkRequiresSiteAndVideo() {
        val siteOnly = row(
            "site-only",
            discord = HostProbeMetrics(3, 3, 50),
            youtube = HostProbeMetrics(3, 3, 100),
            googlevideo = HostProbeMetrics(0, 3, null),
            control = HostProbeMetrics(3, 3, 40),
        )
        val both = row(
            "both",
            discord = HostProbeMetrics(3, 3, 50),
            youtube = HostProbeMetrics(3, 3, 100),
            googlevideo = HostProbeMetrics(2, 3, 80),
            control = HostProbeMetrics(3, 3, 40),
        )
        assertFalse(siteOnly.youtubeOk)
        assertTrue(both.youtubeOk)
        assertTrue(both.score > siteOnly.score)
    }

    @Test
    fun controlNeedsMajoritySuccesses() {
        val ok = row(
            "ok",
            discord = HostProbeMetrics(1, 3, 100),
            youtube = HostProbeMetrics(0, 3, null),
            googlevideo = HostProbeMetrics(0, 3, null),
            control = HostProbeMetrics(2, 3, 40),
        )
        val bad = row(
            "bad",
            discord = HostProbeMetrics(3, 3, 100),
            youtube = HostProbeMetrics(3, 3, 100),
            googlevideo = HostProbeMetrics(3, 3, 100),
            control = HostProbeMetrics(1, 3, 40),
        )
        assertTrue(ok.controlOk)
        assertFalse(bad.controlOk)
    }

    @Test
    fun shortlistPrefersFakeTlsAutoFamily() {
        assertEquals("general-fake-tls-auto", StrategyProbe.SHORTLIST.first())
        assertTrue(StrategyProbe.SHORTLIST.contains("general-fake-tls-auto-alt2"))
        assertTrue(StrategyProbe.SHORTLIST.contains("general-exp"))
        assertTrue(StrategyProbe.SHORTLIST.contains("general-pq-multisplit"))
        assertFalse(StrategyProbe.SHORTLIST.contains("general-simple-fake"))
        assertFalse(StrategyProbe.SHORTLIST.contains("general-simple-fake-alt"))
    }

    @Test
    fun scorePrefersYoutubeOverDiscordOnly() {
        val youtubeAndDiscord = row(
            "yt",
            discord = HostProbeMetrics(3, 3, 200),
            youtube = HostProbeMetrics(3, 3, 500),
            googlevideo = HostProbeMetrics(3, 3, 160),
            control = HostProbeMetrics(3, 3, 350),
        )
        val discordOnly = row(
            "discord",
            discord = HostProbeMetrics(3, 3, 50),
            youtube = HostProbeMetrics(0, 3, null),
            googlevideo = HostProbeMetrics(0, 3, null),
            control = HostProbeMetrics(3, 3, 350),
        )
        assertTrue(youtubeAndDiscord.score > discordOnly.score)
        assertTrue(youtubeAndDiscord.beats(discordOnly.score, 0, 1))
    }

    @Test
    fun bannerHidesJvmClassNameAfterSuccessfulWinner() {
        val error = NoClassDefFoundError("zapret/domain/StrategyProbeReport")
        assertEquals(
            "Подобрана стратегия general-fake-tls-auto",
            StrategyProbeMessages.banner("general-fake-tls-auto", error, hasResults = true),
        )
        assertFalse(StrategyProbeMessages.banner("general-fake-tls-auto", error, true).contains("/"))
    }

    @Test
    fun bannerUsesFriendlyTextForJvmInternalName() {
        val error = NoClassDefFoundError("zapret/domain/StrategyProbeReport")
        assertTrue(StrategyProbeMessages.isJvmInternalName("zapret/domain/StrategyProbeReport"))
        assertEquals(StrategyProbeMessages.FAILED, StrategyProbeMessages.forError(error))
        assertEquals(
            StrategyProbeMessages.FAILED,
            StrategyProbeMessages.banner(null, error, hasResults = false),
        )
    }

    @Test
    fun bannerKeepsDomainErrorText() {
        assertEquals(
            "Движок не установлен",
            StrategyProbeMessages.forError(IllegalStateException("Движок не установлен")),
        )
    }

    @Test
    fun bannerPrefersNoneFoundWhenRowsExistWithoutWinner() {
        val error = NoClassDefFoundError("zapret/domain/StrategyProbeReport")
        assertEquals(
            StrategyProbeMessages.NONE_FOUND,
            StrategyProbeMessages.banner(null, error, hasResults = true),
        )
    }

    @Test
    fun milderWinsOnEqualScore() {
        val metrics = HostProbeMetrics(2, 3, 120)
        val rows = listOf(
            row("mild", metrics, metrics, metrics, HostProbeMetrics(3, 3, 40)),
            row("harsh", metrics, metrics, metrics, HostProbeMetrics(3, 3, 40)),
        )
        var winner: String? = null
        var bestScore = Long.MIN_VALUE
        var bestIndex = Int.MAX_VALUE
        rows.forEachIndexed { index, probeRow ->
            if (probeRow.usable && probeRow.beats(bestScore, bestIndex, index)) {
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
        googlevideo: HostProbeMetrics,
        control: HostProbeMetrics,
    ) = StrategyProbeRow(id, discord, youtube, googlevideo, control)
}
