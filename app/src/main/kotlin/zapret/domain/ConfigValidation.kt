package zapret.domain

object ConfigValidation {

    private val USER_NAME = Regex("""^[A-Za-z0-9._-]+$""")

    fun requireValid(config: ZapretConfig) {
        errorMessage(config)?.let { throw InstallFailed(it) }
    }

    fun errorMessage(config: ZapretConfig): String? {
        if (!StrategyCatalog.isValidId(config.strategyId)) {
            return "Стратегия: недопустимый идентификатор"
        }
        return null
    }

    fun isAllowedUsername(name: String): Boolean = USER_NAME.matches(name)
}
