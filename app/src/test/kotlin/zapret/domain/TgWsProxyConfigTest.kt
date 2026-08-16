package zapret.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TgWsProxyConfigTest {

    @Test
    fun cliArgsIncludeCoreFlags() {
        val config = TgWsProxyConfig(
            host = "127.0.0.1",
            port = "1443",
            secret = "0123456789abcdef0123456789abcdef",
            dcIp = listOf("2:149.154.167.220"),
            cfproxy = false,
            cfproxyUserDomains = listOf("example.com"),
            bufKb = "128",
            poolSize = "2",
            verbose = true,
        )
        val args = config.cliArgs()
        assertTrue(args.containsAll(listOf("--host", "127.0.0.1")))
        assertTrue(args.containsAll(listOf("--port", "1443")))
        assertTrue(args.containsAll(listOf("--secret", "0123456789abcdef0123456789abcdef")))
        assertTrue(args.containsAll(listOf("--dc-ip", "2:149.154.167.220")))
        assertTrue(args.contains("--no-cfproxy"))
        assertTrue(args.containsAll(listOf("--cfproxy-domain", "example.com")))
        assertTrue(args.contains("-v"))
    }

    @Test
    fun telegramProxyLinkUsesDdSecretPrefix() {
        val config = TgWsProxyConfig(
            host = "127.0.0.1",
            port = "1443",
            secret = "aabbccddeeff00112233445566778899",
        )
        assertEquals(
            "tg://proxy?server=127.0.0.1&port=1443&secret=ddaabbccddeeff00112233445566778899",
            config.telegramProxyLink(),
        )
    }

    @Test
    fun jsonRoundTripPreservesFields() {
        val original = TgWsProxyConfig(
            enabled = false,
            host = "127.0.0.1",
            port = "1555",
            secret = "0123456789abcdef0123456789abcdef",
            dcIp = listOf("2:1.2.3.4", "4:5.6.7.8"),
            cfproxy = true,
            cfproxyUserDomains = listOf("a.example"),
            cfproxyWorkerDomains = listOf("w.example"),
            bufKb = "64",
            poolSize = "1",
            verbose = true,
        )
        val parsed = TgWsProxyStore.parse(TgWsProxyStore.encode(original))
        assertEquals(original, parsed)
    }

    @Test
    fun validationRejectsBadSecret() {
        val bad = TgWsProxyConfig(secret = "short")
        assertTrue(TgWsProxyValidation.errorMessage(bad)?.contains("secret") == true)
        assertNull(
            TgWsProxyValidation.errorMessage(
                TgWsProxyConfig(secret = "0123456789abcdef0123456789abcdef"),
            ),
        )
    }

    @Test
    fun newSecretIs32Hex() {
        val secret = TgWsProxyConfig.newSecret()
        assertEquals(32, secret.length)
        assertTrue(secret.all { it in '0'..'9' || it in 'a'..'f' })
        assertFalse(secret == TgWsProxyConfig.newSecret())
    }

    @Test
    fun defaultIsDisabledSoFreshInstallDoesNotLaunchSidecar() {
        assertFalse(TgWsProxyConfig().enabled)
    }
}
