package zapret.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
                  "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "browser_download_url": "https://github.com/nikitaSobolev2/zapret2/releases/download/v1.2.3/Zapret-1.2.3.dmg"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateService.parseLatestDmg(json, arch = "arm64")
        assertNotNull(info)
        assertEquals("1.2.3", info.version)
        assertEquals("Zapret-1.2.3.dmg", info.assetName)
        assertEquals(
            "https://github.com/nikitaSobolev2/zapret2/releases/download/v1.2.3/Zapret-1.2.3.dmg",
            info.downloadUrl,
        )
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", info.sha256)
    }

    @Test
    fun extractSha256FindsDigestPastUploaderBlob() {
        val json = """
            {
              "tag_name": "v2.1.1",
              "assets": [{
                "name": "Zapret-2.1.1.dmg",
                "uploader": { "login": "github-actions[bot]", "id": 41898282, "type": "Bot",
                  "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                  "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                  "html_url": "https://github.com/apps/github-actions",
                  "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                  "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other}",
                  "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                  "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                  "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                  "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                  "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                  "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                  "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                  "site_admin": false },
                "content_type": "application/x-apple-diskimage",
                "state": "uploaded",
                "size": 96649532,
                "digest": "sha256:c7128a571bec0388b32fa2e358c0e8e35d15746a3132f4217052f203cbf04e14",
                "browser_download_url": "https://github.com/nikitaSobolev2/zapret2/releases/download/v2.1.1/Zapret-2.1.1.dmg"
              }]
            }
        """.trimIndent()
        val info = AppUpdateService.parseLatestDmg(json, arch = "arm64")
        assertNotNull(info)
        assertEquals("c7128a571bec0388b32fa2e358c0e8e35d15746a3132f4217052f203cbf04e14", info.sha256)
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
        assertNull(AppUpdateService.parseLatestDmg(json, arch = "arm64"))
    }

    @Test
    fun rejectsUntrustedDownloadHost() {
        assertFails {
            AppUpdateService.requireTrustedDownloadUrl("https://evil.example/Zapret-1.0.0.dmg")
        }
        assertFails {
            AppUpdateService.requireTrustedDownloadUrl(
                "https://github.com/other/repo/releases/download/v1/Zapret-1.0.0.dmg",
            )
        }
    }

    @Test
    fun verifySha256AcceptsMatchAndRejectsMismatch() {
        val file = File.createTempFile("zapret-dmg", ".bin")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val ok = "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a"
        AppUpdateService.verifySha256(file, ok)
        assertFails { AppUpdateService.verifySha256(file, "b".repeat(64)) }
        file.delete()
    }

    @Test
    fun parseLatestDmgPrefersMatchingArchAsset() {
        val json = """
            {
              "tag_name": "v2.4.0",
              "assets": [
                {
                  "name": "Zapret-2.4.0-x86_64.dmg",
                  "digest": "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "browser_download_url": "https://github.com/nikitaSobolev2/zapret2/releases/download/v2.4.0/Zapret-2.4.0-x86_64.dmg"
                },
                {
                  "name": "Zapret-2.4.0-arm64.dmg",
                  "digest": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "browser_download_url": "https://github.com/nikitaSobolev2/zapret2/releases/download/v2.4.0/Zapret-2.4.0-arm64.dmg"
                }
              ]
            }
        """.trimIndent()
        val arm = AppUpdateService.parseLatestDmg(json, arch = "arm64")
        assertNotNull(arm)
        assertEquals("Zapret-2.4.0-arm64.dmg", arm.assetName)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", arm.sha256)
        val intel = AppUpdateService.parseLatestDmg(json, arch = "x86_64")
        assertNotNull(intel)
        assertEquals("Zapret-2.4.0-x86_64.dmg", intel.assetName)
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", intel.sha256)
    }

    @Test
    fun intelDoesNotTakeLegacyArmOnlyDmg() {
        val json = """
            {
              "tag_name": "v2.3.0",
              "assets": [
                {
                  "name": "Zapret-2.3.0.dmg",
                  "browser_download_url": "https://github.com/nikitaSobolev2/zapret2/releases/download/v2.3.0/Zapret-2.3.0.dmg"
                }
              ]
            }
        """.trimIndent()
        assertNull(AppUpdateService.parseLatestDmg(json, arch = "x86_64"))
        assertEquals(
            "Zapret-2.3.0.dmg",
            AppUpdateService.parseLatestDmg(json, arch = "arm64")?.assetName,
        )
    }

    @Test
    fun parseCaskSha256ReadsArchHashes() {
        val cask = """
            cask "zapret" do
              arch arm: "arm64", intel: "x86_64"
              version "2.4.0"
              sha256 arm: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     intel: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
            end
        """.trimIndent()
        assertEquals(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            AppUpdateService.parseCaskSha256(cask, "2.4.0", "arm64"),
        )
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            AppUpdateService.parseCaskSha256(cask, "2.4.0", "x86_64"),
        )
        assertNull(AppUpdateService.parseCaskSha256(cask, "9.9.9", "arm64"))
    }
}
