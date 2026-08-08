package zapret.domain

import java.io.File

/** Installs an edited config over the root owned one and restarts zapret2 so it takes effect. */
class ConfigWriter(
    private val privileges: PrivilegeRunner,
    private val store: ConfigStore = ConfigStore(),
) {

    fun apply(config: ZapretConfig): CommandResult {
        val text = store.edited(config) ?: throw InstallFailed("zapret2 не установлен")
        val draft = File.createTempFile("zapret-config-", ".sh").apply { writeText(text) }
        return try {
            privileges.runScript(
                SCRIPT,
                args = listOf(draft.absolutePath, ZapretPaths.config.absolutePath, ZapretPaths.initScript.absolutePath),
            )
        } finally {
            draft.delete()
        }
    }

    private companion object {
        val SCRIPT = """
            #!/bin/sh
            set -e
            PATH="/usr/sbin:/sbin:/usr/bin:/bin"
            export PATH
            install -m 644 -o root -g wheel "${'$'}1" "${'$'}2"
            "${'$'}3" restart
        """.trimIndent()
    }
}
