package zapret.domain

import java.security.SecureRandom

/**
 * Settings for the local MTProto → WSS bridge (Telegram Desktop).
 * Stored in Application Support JSON, not in `/opt/zapret2/config`.
 */
data class TgWsProxyConfig(
    val enabled: Boolean = false,
    val host: String = "127.0.0.1",
    val port: String = "1443",
    val secret: String = newSecret(),
    /** Entries like `2:149.154.167.220`. */
    val dcIp: List<String> = listOf("2:149.154.167.220", "4:149.154.167.220"),
    val cfproxy: Boolean = true,
    val cfproxyUserDomains: List<String> = emptyList(),
    val cfproxyWorkerDomains: List<String> = emptyList(),
    val bufKb: String = "256",
    val poolSize: String = "4",
    val verbose: Boolean = false,
) {
    fun telegramProxyLink(): String =
        "tg://proxy?server=${host.trim()}&port=${port.trim()}&secret=dd${secret.trim()}"

    /** Apply other draft fields without clobbering an already-persisted enable flag. */
    fun withLiveEnabled(enabled: Boolean): TgWsProxyConfig = copy(enabled = enabled)

    fun cliArgs(): List<String> {
        val args = mutableListOf(
            "--host", host.trim(),
            "--port", port.trim(),
            "--buf-kb", bufKb.trim(),
            "--pool-size", poolSize.trim(),
        )
        dcIp.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            args += listOf("--dc-ip", it)
        }
        if (!cfproxy) args += "--no-cfproxy"
        cfproxyUserDomains.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            args += listOf("--cfproxy-domain", it)
        }
        cfproxyWorkerDomains.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            args += listOf("--cfproxy-worker-domain", it)
        }
        if (verbose) args += "-v"
        return args
    }

    fun secretEnvironment(): Map<String, String> =
        mapOf(SECRET_ENV to secret.trim())

    companion object {
        const val SECRET_ENV = "TG_WS_PROXY_SECRET"
        fun newSecret(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
