package zapret.domain

import java.io.File

/** Persists [TgWsProxyConfig] as a small fixed-schema JSON file. */
class TgWsProxyStore(
    private val file: File = TgWsProxyPaths.configFile,
) {

    fun read(): TgWsProxyConfig {
        if (!file.isFile) {
            val created = TgWsProxyConfig()
            write(created)
            return created
        }
        return parse(file.readText()) ?: TgWsProxyConfig().also(::write)
    }

    fun write(config: TgWsProxyConfig) {
        TgWsProxyValidation.requireValid(config)
        file.parentFile?.mkdirs()
        file.writeText(encode(config))
    }

    companion object {
        fun encode(config: TgWsProxyConfig): String = buildString {
            appendLine("{")
            appendLine("""  "enabled": ${config.enabled},""")
            appendLine("""  "host": ${jsonString(config.host)},""")
            appendLine("""  "port": ${jsonString(config.port)},""")
            appendLine("""  "secret": ${jsonString(config.secret)},""")
            appendLine("""  "dc_ip": ${jsonStringList(config.dcIp)},""")
            appendLine("""  "cfproxy": ${config.cfproxy},""")
            appendLine("""  "cfproxy_user_domain": ${jsonStringList(config.cfproxyUserDomains)},""")
            appendLine("""  "cfproxy_worker_domain": ${jsonStringList(config.cfproxyWorkerDomains)},""")
            appendLine("""  "buf_kb": ${jsonString(config.bufKb)},""")
            appendLine("""  "pool_size": ${jsonString(config.poolSize)},""")
            appendLine("""  "verbose": ${config.verbose}""")
            append("}")
        }

        fun parse(text: String): TgWsProxyConfig? {
            val defaults = TgWsProxyConfig()
            return runCatching {
                TgWsProxyConfig(
                    enabled = bool(text, "enabled", defaults.enabled),
                    host = string(text, "host", defaults.host),
                    port = string(text, "port", defaults.port),
                    secret = string(text, "secret", defaults.secret),
                    dcIp = stringList(text, "dc_ip", defaults.dcIp),
                    cfproxy = bool(text, "cfproxy", defaults.cfproxy),
                    cfproxyUserDomains = stringList(text, "cfproxy_user_domain", defaults.cfproxyUserDomains),
                    cfproxyWorkerDomains = stringList(text, "cfproxy_worker_domain", defaults.cfproxyWorkerDomains),
                    bufKb = string(text, "buf_kb", defaults.bufKb),
                    poolSize = string(text, "pool_size", defaults.poolSize),
                    verbose = bool(text, "verbose", defaults.verbose),
                )
            }.getOrNull()
        }

        private fun jsonString(value: String): String =
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        private fun jsonStringList(values: List<String>): String =
            values.joinToString(prefix = "[", postfix = "]") { jsonString(it) }

        private fun bool(text: String, key: String, default: Boolean): Boolean {
            val match = Regex(""""$key"\s*:\s*(true|false)""").find(text) ?: return default
            return match.groupValues[1] == "true"
        }

        private fun string(text: String, key: String, default: String): String {
            val match = Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").find(text) ?: return default
            return match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }

        private fun stringList(text: String, key: String, default: List<String>): List<String> {
            val match = Regex(""""$key"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(text)
                ?: return default
            val body = match.groupValues[1]
            val items = Regex(""""((?:\\.|[^"\\])*)"""").findAll(body)
                .map {
                    it.groupValues[1]
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                }
                .toList()
            return items.ifEmpty { default }
        }
    }
}
