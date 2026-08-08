package zapret.domain

object TgWsProxyValidation {

    fun errorMessage(config: TgWsProxyConfig): String? {
        if (!isHost(config.host)) return "TG proxy: некорректный host"
        if (!isPort(config.port)) return "TG proxy: порт 1–65535"
        if (!isSecret(config.secret)) return "TG proxy: secret — 32 hex-символа"
        if (!isPositiveInt(config.bufKb, min = 4)) return "TG proxy: buf_kb ≥ 4"
        if (!isNonNegativeInt(config.poolSize)) return "TG proxy: pool_size ≥ 0"
        config.dcIp.forEach { entry ->
            if (!isDcIp(entry)) return "TG proxy: dc_ip формат DC:IP ($entry)"
        }
        return null
    }

    fun requireValid(config: TgWsProxyConfig) {
        errorMessage(config)?.let { throw IllegalArgumentException(it) }
    }

    private fun isHost(value: String): Boolean {
        val host = value.trim()
        if (host.isEmpty() || host.length > 253) return false
        return host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' }
    }

    private fun isPort(value: String): Boolean =
        value.trim().toIntOrNull()?.let { it in 1..65535 } == true

    private fun isSecret(value: String): Boolean {
        val secret = value.trim()
        if (secret.length != 32) return false
        return secret.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun isPositiveInt(value: String, min: Int): Boolean =
        value.trim().toIntOrNull()?.let { it >= min } == true

    private fun isNonNegativeInt(value: String): Boolean =
        value.trim().toIntOrNull()?.let { it >= 0 } == true

    private fun isDcIp(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val parts = trimmed.split(':', limit = 2)
        if (parts.size != 2) return false
        val dc = parts[0].toIntOrNull() ?: return false
        if (dc !in 1..99999) return false
        return parts[1].isNotBlank()
    }
}
