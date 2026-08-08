package zapret.domain

import java.io.File
import kotlin.time.Duration.Companion.minutes

class InstallFailed(message: String) : Exception(message)

/**
 * Installs zapret2: config is prepared in a private temp file, then a single privileged
 * script stages a sealed copy of the sources (using a prebuilt tpws when present),
 * installs, and starts the daemon.
 */
class InstallService(private val privileges: PrivilegeRunner) {

    fun install(onStep: (String) -> Unit): CommandResult {
        val source = ZapretPaths.sourceTree()
            ?: throw InstallFailed("Исходники zapret2 не найдены. Запустите приложение из репозитория или переустановите его.")

        onStep("Подготовка конфигурации")
        val config = writeConfigDraft(source)
        return try {
            onStep(if (ZapretPaths.hasPrebuiltTpws(source)) "Установка" else "Сборка и установка")
            privileges.runScriptFile(
                BundledScript.extract("install.sh"),
                args = listOf(source.absolutePath, ZapretPaths.base.absolutePath, config.absolutePath),
                timeout = 10.minutes,
            )
        } finally {
            config.delete()
        }
    }

    /**
     * The config the daemon will be started with. An existing config is kept and only the values
     * the macOS setup requires are forced.
     */
    private fun writeConfigDraft(source: File): File {
        val store = ConfigStore(defaultFile = File(source, "config.default"))
        val current = store.read() ?: ZapretConfig()
        val wan = current.ifaceWan.ifBlank { WanInterface.detect().orEmpty() }
        val resolved = current.copy(tpwsEnable = true, ifaceWan = wan)
        ConfigValidation.requireValid(resolved)

        val listsReload = "${ZapretPaths.initScript.absolutePath} reload-fw-tables"
        val extra = mapOf(
            ZapretConfig.GZIP_LISTS to "0",
            ZapretConfig.LISTS_RELOAD to listsReload,
        )
        ConfigValidation.requireValidExtras(extra)

        val text = store.edited(resolved, extra = extra)
            ?: throw InstallFailed("Не найден config.default в ${source.absolutePath}")

        return SecureTemp.file("zapret-config-", ".sh").apply { writeText(text) }
    }
}
