package zapret.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppUpdateParseTest {

    @Test
    fun parseLatestDmgPicksZapretAsset() {
        val json = """
            {
              "tag_name": "v1.2.3",
              "assets": [
                {
                  "name": "zapret2-v1.2.3.tar.gz",
                  "browser_download_url": "https://github.com/nikitaSobolev2/zapret2/releases/download/v1.2.3/zapret2-v1.2.3.tar.gz"
                },
                {
                  "name": "Zapret-1.2.3.dmg",
                  "browser_download_url": "https://github.com/nikitaSobolev2/zapret2/releases/download/v1.2.3/Zapret-1.2.3.dmg"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateService.parseLatestDmg(json)
        assertNotNull(info)
        assertEquals("1.2.3", info.version)
        assertEquals("Zapret-1.2.3.dmg", info.assetName)
        assertEquals(
            "https://github.com/nikitaSobolev2/zapret2/releases/download/v1.2.3/Zapret-1.2.3.dmg",
            info.downloadUrl,
        )
    }

    @Test
    fun parseLatestDmgReturnsNullWithoutDmg() {
        val json = """
            {
              "tag_name": "v1.2.3",
              "assets": [
                {
                  "name": "zapret2-v1.2.3.zip",
                  "browser_download_url": "https://example.com/zapret2-v1.2.3.zip"
                }
              ]
            }
        """.trimIndent()
        assertNull(AppUpdateService.parseLatestDmg(json))
    }
}
