package zapret.domain

/**
 * Validates values the app writes into a shell config that root will source.
 * Fail here so bad UI input never reaches /opt/zapret2/config.
 */
object ConfigValidation {

    private val WAN_TOKEN = Regex("""^en\d+$""")
    private val USER_NAME = Regex("""^[A-Za-z0-9._-]+$""")
    private val PORT = Regex("""^\d{1,5}$""")

    fun requireValid(config: ZapretConfig) {
        errorMessage(config)?.let { throw InstallFailed(it) }
    }

    fun requireValidExtras(extra: Map<String, String>) {
        extra[ZapretConfig.LISTS_RELOAD]?.let { path ->
            val expected = "${ZapretPaths.initScript.absolutePath} reload-fw-tables"
            if (path != expected) {
                throw InstallFailed("Некорректный LISTS_RELOAD")
            }
        }
    }

    fun errorMessage(config: ZapretConfig): String? {
        if (!WanInterface.isAllowedWan(config.ifaceWan)) {
            return "IFACE_WAN: допустимы пустое значение или en0, en1, …"
        }
        if (!isPort(config.tpwsPort)) return "TPPORT: нужен порт 1–65535"
        if (!isPort(config.socksPort)) return "TPPORT_SOCKS: нужен порт 1–65535"
        if (!isPortList(config.tpwsPorts)) return "TPWS_PORTS: список портов через запятую"
        if (!isSafeOptions(config.tpwsOpt)) return "TPWS_OPT: недопустимые управляющие символы"
        if (!isSafeOptions(config.socksOpt)) return "TPWS_SOCKS_OPT: недопустимые управляющие символы"
        return null
    }

    fun isAllowedUsername(name: String): Boolean = USER_NAME.matches(name)

    fun isPort(text: String): Boolean {
        val n = text.trim().toIntOrNull() ?: return false
        return n in 1..65535 && PORT.matches(text.trim())
    }

    fun isPortList(text: String): Boolean {
        val parts = text.trim().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        return parts.all(::isPort)
    }

    /** Printable options; newlines/tabs allowed for multi-line strategy profiles. */
    fun isSafeOptions(text: String): Boolean =
        text.none { ch -> ch.code < 32 && ch != '\n' && ch != '\t' }
}
