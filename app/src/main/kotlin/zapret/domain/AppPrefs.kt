package zapret.domain

import java.io.File

data class AppPrefs(
    val autoUpdate: Boolean = true,
)

object AppPrefsPaths {
    private val supportRoot: File
        get() = File(
            System.getProperty("user.home"),
            "Library/Application Support/Zapret",
        ).also { it.mkdirs() }

    val prefsFile: File get() = File(supportRoot, "app-prefs.json")
    val cacheDir: File get() = File(System.getProperty("user.home"), "Library/Caches/Zapret").also { it.mkdirs() }
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
        file.parentFile?.mkdirs()
        file.writeText(encode(prefs))
    }

    companion object {
        fun encode(prefs: AppPrefs): String = """
            |{
            |  "autoUpdate": ${prefs.autoUpdate}
            |}
        """.trimMargin()

        fun parse(text: String): AppPrefs? = runCatching {
            val match = Regex(""""autoUpdate"\s*:\s*(true|false)""").find(text)
            AppPrefs(autoUpdate = match?.groupValues?.get(1) != "false")
        }.getOrNull()
    }
}
