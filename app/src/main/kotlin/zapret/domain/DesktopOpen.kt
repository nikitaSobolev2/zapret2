package zapret.domain

import java.io.File
import java.net.URI

/** Spawns `/usr/bin/open` without a shell and without waiting on the UI thread. */
object DesktopOpen {

    private val urlSchemes = setOf("https", "http", "tg")

    fun url(url: String): Boolean {
        if (!isAllowedUrl(url)) return false
        return spawn("/usr/bin/open", "--", url)
    }

    fun textFile(file: File): Boolean {
        if (SafeFiles.isSymlink(file) || !file.isFile) return false
        if (file.name.startsWith("-")) return false
        return spawn("/usr/bin/open", "-t", "--", file.absolutePath)
    }

    fun isAllowedUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme !in urlSchemes) return false
        if (scheme == "tg") return uri.host == "proxy"
        return !uri.host.isNullOrBlank()
    }

    private fun spawn(vararg argv: String): Boolean =
        runCatching {
            ProcessBuilder(*argv)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        }.getOrDefault(false)
}
