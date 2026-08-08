package zapret.domain

import java.io.File
import kotlin.time.Duration.Companion.minutes

class InstallFailed(message: String) : Exception(message)

/**
 * Installs zapret2 without asking anything: the sources are compiled in a private cache
 * directory, then a single privileged script puts the tree in place and starts the daemon.
 */
class InstallService(private val privileges: PrivilegeRunner) {

    fun install(onStep: (String) -> Unit): CommandResult {
        val source = ZapretPaths.sourceTree()
            ?: throw InstallFailed("Исходники zapret2 не найдены. Запустите приложение из репозитория или переустановите его.")

        onStep("Подготовка исходников")
        val build = copySources(source)

        onStep("Сборка tpws")
        compile(build)

        onStep("Установка и запуск")
        val config = writeConfigDraft(build)
        return try {
            privileges.runScriptFile(
                BundledScript.extract("install.sh"),
                args = listOf(build.absolutePath, ZapretPaths.base.absolutePath, config.absolutePath),
                timeout = 5.minutes,
            )
        } finally {
            config.delete()
        }
    }

    private fun copySources(source: File): File {
        val build = ZapretPaths.buildDir
        build.mkdirs()
        val result = Shell.run(
            "/usr/bin/rsync", "-a", "--delete", "--exclude=.git", "${source.absolutePath}/", "${build.absolutePath}/",
            timeout = 2.minutes,
        )
        if (!result.ok) throw InstallFailed("Не удалось скопировать исходники:\n${result.output}")
        return build
    }

    private fun compile(build: File) {
        val result = Shell.run("/usr/bin/make", "-C", build.absolutePath, "mac", timeout = 10.minutes)
        if (!result.ok) {
            val hint = if (Shell.run("/usr/bin/xcrun", "--find", "cc").ok) ""
            else "\n\nСначала установите инструменты разработчика: xcode-select --install"
            throw InstallFailed("Сборка не удалась:\n${result.lastLine()}$hint")
        }
    }

    /**
     * The config the daemon will be started with. An existing config is kept and only the values
     * the macOS setup requires are forced.
     */
    private fun writeConfigDraft(build: File): File {
        val store = ConfigStore(defaultFile = File(build, "config.default"))
        val current = store.read() ?: ZapretConfig()
        val wan = current.ifaceWan.ifBlank { WanInterface.detect().orEmpty() }
        val text = store.edited(
            current.copy(tpwsEnable = true, ifaceWan = wan),
            extra = mapOf(
                // PF reads its table files as plain text
                ZapretConfig.GZIP_LISTS to "0",
                ZapretConfig.LISTS_RELOAD to "${ZapretPaths.initScript.absolutePath} reload-fw-tables",
            ),
        ) ?: throw InstallFailed("Не найден config.default в ${build.absolutePath}")

        return File.createTempFile("zapret-config-", ".sh").apply { writeText(text) }
    }
}
