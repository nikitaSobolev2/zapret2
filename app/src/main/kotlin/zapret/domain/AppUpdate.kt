package zapret.domain

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val assetName: String,
)

enum class UpdatePhase {
    Idle,
    Checking,
    Available,
    Downloading,
    Applying,
}

/**
 * Checks GitHub Releases for a newer Zapret-*.dmg and installs it over the running .app.
 */
class AppUpdateService(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
    private val releasesUrl: String = RELEASES_LATEST,
) {
    private val cancelRequested = AtomicBoolean(false)

    fun cancel() {
        cancelRequested.set(true)
    }

    fun clearCancel() {
        cancelRequested.set(false)
    }

    fun checkLatest(currentVersion: String = AppVersion.current()): Result<UpdateInfo?> = runCatching {
        val body = get(releasesUrl)
        val info = parseLatestDmg(body) ?: return@runCatching null
        if (AppVersion.isNewer(info.version, currentVersion)) info else null
    }

    fun download(info: UpdateInfo, onProgress: (Long, Long?) -> Unit = { _, _ -> }): Result<File> = runCatching {
        clearCancel()
        val dest = File(AppPrefsPaths.cacheDir, info.assetName)
        val request = HttpRequest.newBuilder(URI.create(info.downloadUrl))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} downloading ${info.assetName}")
        }
        val total = response.headers().firstValueAsLong("Content-Length").orElse(-1L).takeIf { it > 0 }
        response.body().use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var readTotal = 0L
                while (true) {
                    if (cancelRequested.get()) error("cancelled")
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    readTotal += n
                    onProgress(readTotal, total)
                }
            }
        }
        dest
    }

    /**
     * Mounts [dmg], stages Zapret.app, spawns a helper that replaces [targetApp] after this process exits.
     * Caller should [kotlin.system.exitProcess] after a successful result.
     */
    fun applyAndRelaunch(dmg: File, targetApp: File = ZapretPaths.appBundle() ?: error(NOT_PACKAGED)): Result<Unit> =
        runCatching {
            if (!targetApp.name.endsWith(".app") || !targetApp.isDirectory) error(NOT_PACKAGED)
            clearCancel()
            val mount = mountDmg(dmg)
            val staged: File
            try {
                if (cancelRequested.get()) error("cancelled")
                val sourceApp = findZapretApp(mount) ?: error("Zapret.app not found in DMG")
                staged = stageAppCopy(sourceApp)
            } finally {
                unmountDmg(mount)
            }
            if (cancelRequested.get()) {
                staged.deleteRecursively()
                error("cancelled")
            }
            val script = writeReplaceScript(staged = staged, targetApp = targetApp, pid = ProcessHandle.current().pid())
            ProcessBuilder("/bin/bash", script.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            Unit
        }

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} from GitHub Releases")
        }
        return response.body()
    }

    private fun mountDmg(dmg: File): File {
        val result = Shell.run(
            "/usr/bin/hdiutil", "attach", "-nobrowse", "-readonly", "-plist", dmg.absolutePath,
            timeout = 60.seconds,
        )
        if (!result.ok) error("hdiutil attach failed: ${result.lastLine()}")
        val mountPoint = Regex("""<string>(/Volumes/[^<]+)</string>""").findAll(result.output)
            .map { it.groupValues[1] }
            .lastOrNull()
            ?: error("mount point not found")
        return File(mountPoint)
    }

    private fun unmountDmg(mount: File) {
        Shell.run("/usr/bin/hdiutil", "detach", mount.absolutePath, "-quiet", timeout = 30.seconds)
    }

    private fun findZapretApp(mount: File): File? =
        mount.walkTopDown().maxDepth(3).firstOrNull { it.name == "Zapret.app" && it.isDirectory }

    private fun stageAppCopy(sourceApp: File): File {
        val staged = File(AppPrefsPaths.cacheDir, "Zapret-update.app")
        if (staged.exists()) staged.deleteRecursively()
        Files.walk(sourceApp.toPath()).use { paths ->
            paths.forEach { path ->
                val rel = sourceApp.toPath().relativize(path)
                val dest = staged.toPath().resolve(rel)
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest)
                } else {
                    Files.createDirectories(dest.parent)
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
        return staged
    }

    private fun writeReplaceScript(staged: File, targetApp: File, pid: Long): File {
        val script = File(AppPrefsPaths.cacheDir, "replace-app.sh")
        script.writeText(
            """
            #!/bin/bash
            set -euo pipefail
            PID=$pid
            SRC="${staged.absolutePath}"
            DST="${targetApp.absolutePath}"
            for _ in ${'$'}(seq 1 60); do
              if ! kill -0 "${'$'}PID" 2>/dev/null; then
                break
              fi
              sleep 0.5
            done
            kill -0 "${'$'}PID" 2>/dev/null && kill -9 "${'$'}PID" 2>/dev/null || true
            sleep 0.5
            rm -rf "${'$'}DST"
            /usr/bin/ditto "${'$'}SRC" "${'$'}DST"
            /usr/bin/xattr -cr "${'$'}DST"
            /usr/bin/open "${'$'}DST"
            rm -rf "${'$'}SRC"
            """.trimIndent() + "\n",
        )
        script.setExecutable(true)
        return script
    }

    companion object {
        const val RELEASES_LATEST =
            "https://api.github.com/repos/nikitaSobolev2/zapret2/releases/latest"
        const val NOT_PACKAGED = "Обновление доступно только в установленном Zapret.app"
        private const val USER_AGENT = "Zapret-macOS-control"

        fun parseLatestDmg(json: String): UpdateInfo? {
            val tag = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
                ?: return null
            val versionFromTag = tag.removePrefix("v")
            val url = Regex(""""browser_download_url"\s*:\s*"(https://[^"]+/Zapret-[^"]+\.dmg)"""")
                .find(json)
                ?.groupValues
                ?.get(1)
                ?: return null
            val name = url.substringAfterLast('/')
            val versionFromName = Regex("""Zapret-(\d+(?:\.\d+){0,2})\.dmg""").find(name)?.groupValues?.get(1)
            return UpdateInfo(
                version = versionFromName ?: versionFromTag,
                downloadUrl = url,
                assetName = name,
            )
        }
    }
}
