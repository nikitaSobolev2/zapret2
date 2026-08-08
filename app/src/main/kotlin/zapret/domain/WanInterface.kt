package zapret.domain

import kotlin.time.Duration.Companion.seconds

/**
 * Picks the physical WAN interface that PF `pass out on …` should bind to.
 *
 * Binding the redirect to Wi‑Fi/Ethernet leaves L2TP/IPSec (utun) alone, so a
 * split-tunnel corporate VPN can coexist with transparent zapret.
 */
object WanInterface {

    private val TUNNEL = Regex("^(utun|ppp|ipsec|gif|stf|awdl|llw|anpi|bridge)\\d*$")
    private val PROBE = 5.seconds

    /** Default route's iface when it is physical, otherwise the first active `en*`. */
    fun detect(): String? = defaultRouteIface()?.takeUnless(::isTunnel) ?: firstActiveEthernet()

    fun defaultRouteIface(): String? =
        Shell.run("/sbin/route", "-n", "get", "default", timeout = PROBE)
            .takeIf { it.ok }
            ?.output
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("interface:") }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun firstActiveEthernet(): String? =
        Shell.run("/sbin/ifconfig", "-l", timeout = PROBE)
            .takeIf { it.ok }
            ?.output
            ?.split(Regex("\\s+"))
            ?.firstOrNull { it.startsWith("en") && isActiveWithInet(it) }

    fun isTunnel(name: String): Boolean = TUNNEL.containsMatchIn(name)

    private fun isActiveWithInet(name: String): Boolean {
        val text = Shell.run("/sbin/ifconfig", name, timeout = PROBE).takeIf { it.ok }?.output ?: return false
        return text.contains("status: active") && text.contains("inet ")
    }
}
