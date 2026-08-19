package zapret.domain

import java.io.File

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

    /** Same source as `install.sh`: last component of `$HOME`, not `user.name`. */
    fun accountName(
        home: String? = System.getProperty("user.home"),
        userName: String = System.getProperty("user.name").orEmpty(),
    ): String {
        val fromHome = home?.let { File(it).name }?.takeIf { it.isNotEmpty() }
        return fromHome ?: userName
    }
}
