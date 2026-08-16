package zapret.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YoutubePlaybackUrlsTest {

    @Test
    fun firstFromPlayerJsonReadsEscapedPlaybackUrl() {
        val json =
            "{\"url\":\"https:\\/\\/rr3---sn-5goeenes.googlevideo.com\\/videoplayback?expire=1\\u0026id=abc\\u003d1\"}"
        val url = YoutubePlaybackUrls.firstFromPlayerJson(json)
        assertEquals(
            "https://rr3---sn-5goeenes.googlevideo.com/videoplayback?expire=1&id=abc=1",
            url,
        )
    }

    @Test
    fun firstFromPlayerJsonIgnoresUnrelatedHosts() {
        val json = """{"url":"https://www.youtube.com/watch?v=jNQXAC9IVRw"}"""
        assertNull(YoutubePlaybackUrls.firstFromPlayerJson(json))
    }

    @Test
    fun playerClientsTargetStablePublicVideo() {
        assertTrue(YoutubePlaybackUrls.ANDROID.body.contains(YoutubePlaybackUrls.VIDEO_ID))
        assertTrue(YoutubePlaybackUrls.IOS.body.contains(YoutubePlaybackUrls.VIDEO_ID))
        assertEquals(2, YoutubePlaybackUrls.clients.size)
    }
}
