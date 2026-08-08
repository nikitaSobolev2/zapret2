package zapret.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class ShellConfigTextTest {

    @Test
    fun parseKeepsMultilineQuotedValues() {
        val text = """
            TPWS_ENABLE=0
            TPWS_OPT="
            --filter-tcp=80 --methodeol
            --filter-tcp=443 --disorder
            "
            MODE_FILTER=hostlist
        """.trimIndent()

        val vars = ShellConfigText.parse(text)
        assertEquals("0", vars["TPWS_ENABLE"])
        assertEquals("hostlist", vars["MODE_FILTER"])
        assertEquals(
            "\n--filter-tcp=80 --methodeol\n--filter-tcp=443 --disorder\n",
            vars["TPWS_OPT"],
        )
    }

    @Test
    fun renderUsesSingleQuotesSoShellMetacharactersStayLiteral() {
        val value = """$(curl evil)|`id`; "x""""
        val line = ShellConfigText.render("TPWS_OPT", value)
        assertEquals(true, line.startsWith("TPWS_OPT='"))
        val roundTrip = ShellConfigText.parse(line)["TPWS_OPT"]
        assertEquals(value, roundTrip)
    }

    @Test
    fun renderEscapesEmbeddedSingleQuotes() {
        val line = ShellConfigText.render("X", "it's")
        assertEquals("X='it'\\''s'", line)
        assertEquals("it's", ShellConfigText.parse(line)["X"])
    }

    @Test
    fun patchMultilineOptRoundTripsAsSingleQuoted() {
        val text = """
            TPWS_ENABLE=0
            TPWS_OPT=""
        """.trimIndent()
        val opt = "\n--filter-tcp=80 --methodeol\n--filter-tcp=443 --disorder\n"
        val patched = ShellConfigText.patch(text, mapOf("TPWS_OPT" to opt))
        assertEquals(true, patched.contains("TPWS_OPT='"))
        assertEquals(opt, ShellConfigText.parse(patched)["TPWS_OPT"])
    }

    @Test
    fun patchPreservesUnknownLinesAndComments() {
        val text = """
            # keep me
            TPWS_ENABLE=0
            CUSTOM_HOOK=/tmp/x
        """.trimIndent()

        val patched = ShellConfigText.patch(text, mapOf("TPWS_ENABLE" to "1"))
        assertEquals(true, patched.contains("# keep me"))
        assertEquals(true, patched.contains("CUSTOM_HOOK=/tmp/x"))
        assertEquals("1", ShellConfigText.parse(patched)["TPWS_ENABLE"])
    }

    @Test
    fun parseElapsedHandlesDaysAndHours() {
        assertEquals(125.seconds, ZapretService.parseElapsed("02:05"))
        assertEquals(3661.seconds, ZapretService.parseElapsed("01:01:01"))
        assertEquals((86_400 + 3600).seconds, ZapretService.parseElapsed("1-01:00:00"))
        assertNull(ZapretService.parseElapsed("bad"))
    }

    @Test
    fun wanInterfaceTreatsUtunAsTunnel() {
        assertEquals(true, WanInterface.isTunnel("utun4"))
        assertEquals(true, WanInterface.isTunnel("ppp0"))
        assertEquals(false, WanInterface.isTunnel("en0"))
    }

    @Test
    fun wanAllowlistAcceptsEthernetOnly() {
        assertEquals(true, WanInterface.isAllowedWan(""))
        assertEquals(true, WanInterface.isAllowedWan("en0"))
        assertEquals(true, WanInterface.isAllowedWan("en0 en1"))
        assertEquals(false, WanInterface.isAllowedWan("utun0"))
        assertEquals(false, WanInterface.isAllowedWan("en0;rm"))
        assertEquals(false, WanInterface.isAllowedWan("en0\$(id)"))
    }

    @Test
    fun configValidationRejectsBadPortsAndIface() {
        assertEquals(null, ConfigValidation.errorMessage(ZapretConfig()))
        assertEquals(
            true,
            ConfigValidation.errorMessage(ZapretConfig(ifaceWan = "utun0"))?.contains("IFACE_WAN") == true,
        )
        assertEquals(
            true,
            ConfigValidation.errorMessage(ZapretConfig(tpwsPort = "0"))?.contains("TPPORT") == true,
        )
        assertEquals(
            true,
            ConfigValidation.errorMessage(ZapretConfig(tpwsPorts = "80;443"))?.contains("TPWS_PORTS") == true,
        )
        assertEquals(
            true,
            ConfigValidation.errorMessage(ZapretConfig(tpwsOpt = "ok\u0000bad"))?.contains("TPWS_OPT") == true,
        )
    }

    @Test
    fun configValidationRejectsBadListsReload() {
        assertFailsWith<InstallFailed> {
            ConfigValidation.requireValidExtras(mapOf(ZapretConfig.LISTS_RELOAD to "/tmp/evil"))
        }
    }

    @Test
    fun envSourceOverrideIgnoredWhenPackaged() {
        assertEquals(
            "/tmp/evil",
            ZapretPaths.envSourceOverride("/tmp/evil", packaged = false)?.path,
        )
        assertNull(ZapretPaths.envSourceOverride("/tmp/evil", packaged = true))
        assertNull(ZapretPaths.envSourceOverride(null, packaged = false))
    }

    @Test
    fun prerequisitesReadyWhenInstalledWithWan() {
        val ready = Prerequisites(
            hasCompiler = true,
            hasSources = true,
            hasPrebuiltBinary = false,
            passwordlessControl = false,
            wanInterface = "en0",
            zapretInstalled = true,
        )
        assertEquals(true, ready.isReady)
        assertEquals(true, ready.canStart)
    }

    @Test
    fun prerequisitesNotReadyToInstallWithoutCompilerOrPrebuilt() {
        val blocked = Prerequisites(
            hasCompiler = false,
            hasSources = true,
            hasPrebuiltBinary = false,
            passwordlessControl = false,
            wanInterface = "en0",
            zapretInstalled = false,
        )
        assertEquals(false, blocked.canInstall)
        assertEquals(false, blocked.isReady)
    }

    @Test
    fun prerequisitesCanInstallWithPrebuiltWithoutCompiler() {
        val ready = Prerequisites(
            hasCompiler = false,
            hasSources = true,
            hasPrebuiltBinary = true,
            passwordlessControl = false,
            wanInterface = "en0",
            zapretInstalled = false,
        )
        assertEquals(true, ready.canInstall)
        assertEquals(true, ready.isReady)
    }

    @Test
    fun lastLinePrefersZapretDiagnostics() {
        val result = CommandResult(
            1,
            """
            successfully loaded PF anchors
            zapret2: no connectivity after applying PF. rolling back.
            zapret2: a VPN/tunnel on the default route can conflict with the transparent redirect.
            Stopping daemon 1: /opt/zapret2/tpws/tpws (PID=1)
            """.trimIndent(),
        )
        assertEquals(
            "zapret2: a VPN/tunnel on the default route can conflict with the transparent redirect.",
            result.lastLine(),
        )
    }
}
