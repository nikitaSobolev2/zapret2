package zapret.domain

import java.io.File

data class AppPrefs(
    val autoUpdate: Boolean = true,
    val passwordless: Boolean = true,
)

object AppPrefsPaths {
    private val supportRoot: File
        get() = SafeFiles.privateDirectory(
            File(System.getProperty("user.home"), "Library/Application Support/Zapret"),
        )

    val prefsFile: File get() = File(supportRoot, "app-prefs.json")
    val cacheDir: File get() = SafeFiles.privateDirectory(
        File(System.getProperty("user.home"), "Library/Caches/Zapret"),
    )
}

/** User-level Zapret.app preferences (not `/opt/zapret2/config`). */
class AppPrefsStore(
    private val file: File = AppPrefsPaths.prefsFile,
) {

    fun read(): AppPrefs {
        if (!file.isFile) {
            val created = AppPrefs()
            write(created)
            return created
        }
        return parse(file.readText()) ?: AppPrefs().also(::write)
    }

    fun write(prefs: AppPrefs) {
        file.parentFile?.let { SafeFiles.privateDirectory(it) }
        SafeFiles.deleteIfSymlink(file)
        file.writeText(encode(prefs))
        SecureTemp.lockDown(file)
    }

    companion object {
        fun encode(prefs: AppPrefs): String = """
            |{
            |  "autoUpdate": ${prefs.autoUpdate},
            |  "passwordless": ${prefs.passwordless}
            |}
        """.trimMargin()

        fun parse(text: String): AppPrefs? = runCatching {
            AppPrefs(
                autoUpdate = bool(text, "autoUpdate", default = true),
                passwordless = bool(text, "passwordless", default = true),
            )
        }.getOrNull()

        private fun bool(text: String, key: String, default: Boolean): Boolean {
            val match = Regex(""""$key"\s*:\s*(true|false)""").find(text) ?: return default
            return match.groupValues[1] == "true"
        }
    }
}
