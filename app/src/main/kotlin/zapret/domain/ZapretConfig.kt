package zapret.domain

enum class FilterMode(val value: String, val label: String) {
    NONE("none", "Выключен"),
    IPSET("ipset", "По списку IP"),
    HOSTLIST("hostlist", "По списку доменов"),
    AUTOHOSTLIST("autohostlist", "Автосписок доменов");

    companion object {
        fun of(value: String?): FilterMode = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/** The subset of the zapret2 config the app exposes. Everything else stays as the user left it. */
data class ZapretConfig(
    val tpwsEnable: Boolean = true,
    val tpwsPort: String = "988",
    val tpwsPorts: String = "80,443",
    val tpwsOpt: String = "",
    val socksEnable: Boolean = false,
    val socksPort: String = "987",
    val socksOpt: String = "",
    val filterMode: FilterMode = FilterMode.NONE,
    val disableIpv6: Boolean = true,
    val applyFirewall: Boolean = true,
) {
    fun toAssignments(): Map<String, String> = mapOf(
        TPWS_ENABLE to tpwsEnable.toFlag(),
        TPPORT to tpwsPort.trim(),
        TPWS_PORTS to tpwsPorts.trim(),
        TPWS_OPT to tpwsOpt.normalizeOptions(),
        TPWS_SOCKS_ENABLE to socksEnable.toFlag(),
        TPPORT_SOCKS to socksPort.trim(),
        TPWS_SOCKS_OPT to socksOpt.normalizeOptions(),
        MODE_FILTER to filterMode.value,
        DISABLE_IPV6 to disableIpv6.toFlag(),
        INIT_APPLY_FW to applyFirewall.toFlag(),
    )

    companion object {
        const val TPWS_ENABLE = "TPWS_ENABLE"
        const val TPPORT = "TPPORT"
        const val TPWS_PORTS = "TPWS_PORTS"
        const val TPWS_OPT = "TPWS_OPT"
        const val TPWS_SOCKS_ENABLE = "TPWS_SOCKS_ENABLE"
        const val TPPORT_SOCKS = "TPPORT_SOCKS"
        const val TPWS_SOCKS_OPT = "TPWS_SOCKS_OPT"
        const val MODE_FILTER = "MODE_FILTER"
        const val DISABLE_IPV6 = "DISABLE_IPV6"
        const val INIT_APPLY_FW = "INIT_APPLY_FW"
        const val GZIP_LISTS = "GZIP_LISTS"
        const val LISTS_RELOAD = "LISTS_RELOAD"

        fun from(vars: Map<String, String>): ZapretConfig = ZapretConfig(
            tpwsEnable = vars.flag(TPWS_ENABLE, default = false),
            tpwsPort = vars[TPPORT]?.trim().orEmpty().ifEmpty { "988" },
            tpwsPorts = vars[TPWS_PORTS]?.trim().orEmpty().ifEmpty { "80,443" },
            tpwsOpt = vars[TPWS_OPT]?.trim().orEmpty(),
            socksEnable = vars.flag(TPWS_SOCKS_ENABLE, default = false),
            socksPort = vars[TPPORT_SOCKS]?.trim().orEmpty().ifEmpty { "987" },
            socksOpt = vars[TPWS_SOCKS_OPT]?.trim().orEmpty(),
            filterMode = FilterMode.of(vars[MODE_FILTER]?.trim()),
            disableIpv6 = vars.flag(DISABLE_IPV6, default = true),
            applyFirewall = vars.flag(INIT_APPLY_FW, default = true),
        )

        private fun Map<String, String>.flag(name: String, default: Boolean): Boolean =
            this[name]?.trim()?.let { it == "1" } ?: default
    }
}

private fun Boolean.toFlag(): String = if (this) "1" else "0"

/** tpws options are one strategy profile per line, kept multi line the way config.default writes them. */
private fun String.normalizeOptions(): String {
    val profiles = lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    return if (profiles.isEmpty()) "" else profiles.joinToString("\n", prefix = "\n", postfix = "\n")
}
