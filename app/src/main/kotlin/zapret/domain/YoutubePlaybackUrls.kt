package zapret.domain

/**
 * Pulls a googlevideo `videoplayback` URL out of YouTube innertube JSON.
 * Used by strategy probe so “YouTube works” means a real media chunk, not `/generate_204`.
 */
object YoutubePlaybackUrls {

    const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"

    /** First YouTube upload — short, public, stable id. */
    const val VIDEO_ID = "jNQXAC9IVRw"

    data class Client(val body: String, val userAgent: String)

    val ANDROID = Client(
        body = """{"context":{"client":{"clientName":"ANDROID","clientVersion":"20.10.38","hl":"en","androidSdkVersion":34}},"videoId":"$VIDEO_ID","contentCheckOk":true,"racyCheckOk":true}""",
        userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
    )

    val IOS = Client(
        body = """{"context":{"client":{"clientName":"IOS","clientVersion":"19.45.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"17.5.1.21F90","hl":"en"}},"videoId":"$VIDEO_ID","contentCheckOk":true,"racyCheckOk":true}""",
        userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X)",
    )

    val clients: List<Client> = listOf(ANDROID, IOS)

    fun firstFromPlayerJson(json: String): String? {
        val decoded = decodeJsonString(json)
        return PLAYBACK.find(decoded)?.value?.trimEnd('\\')
    }

    fun decodeJsonString(json: String): String =
        json
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u003f", "?")

    private val PLAYBACK = Regex("""https://[^\s"\\]+googlevideo\.com/videoplayback[^\s"\\]+""")
}
