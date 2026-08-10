package zapret.domain

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val assetName: String,
    val sha256: String? = null,
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
 *
 * Uses [HttpURLConnection] (java.base) — jpackage's trimmed runtime often omits java.net.http.
 * Only trusts assets from [TRUSTED_REPO] with optional SHA-256 verification.
 */
class AppUpdateService(
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
        val body = getText(releasesUrl)
        var info = parseLatestDmg(body) ?: return@runCatching null
        requireTrustedDownloadUrl(info.downloadUrl)
        if (info.sha256 == null) {
            info = info.copy(sha256 = fetchCaskSha256(info.version))
        }
        if (info.sha256 == null) error("release has no SHA-256 for ${info.assetName}")
        if (AppVersion.isNewer(info.version, currentVersion)) info else null
    }

    fun download(info: UpdateInfo, onProgress: (Long, Long?) -> Unit = { _, _ -> }): Result<File> = runCatching {
        clearCancel()
        requireTrustedDownloadUrl(info.downloadUrl)
        val expectedSha = info.sha256 ?: fetchCaskSha256(info.version)
            ?: error("missing SHA-256 for ${info.assetName}")
        val dest = File(AppPrefsPaths.cacheDir, info.assetName)
        val connection = open(info.downloadUrl, timeoutMs = 600_000)
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code downloading ${info.assetName}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.use { input ->
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
        } finally {
            connection.disconnect()
        }
        verifySha256(dest, expectedSha)
        dest
    }

    private fun fetchCaskSha256(version: String): String? {
        // Repo default branch is master; keep main as fallback for forks/renames.
        for (branch in listOf("master", "main")) {
            val url = "https://raw.githubusercontent.com/$TRUSTED_REPO/$branch/Casks/zapret.rb"
            val sha = runCatching {
                val text = getText(url)
                val caskVersion = Regex("""version\s+"([^"]+)"""").find(text)?.groupValues?.get(1)
                if (caskVersion != null && caskVersion != version) return@runCatching null
                Regex("""sha256\s+"([a-fA-F0-9]{64})"""").find(text)?.groupValues?.get(1)?.lowercase()
            }.getOrNull()
            if (sha != null) return sha
        }
        return null
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

    private fun getText(url: String): String {
        requireTrustedApiUrl(url)
        val connection = open(url, timeoutMs = 30_000)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from GitHub Releases")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, timeoutMs: Int): HttpURLConnection {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.requestMethod = "GET"
        return connection
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
        const val TRUSTED_REPO = "nikitaSobolev2/zapret2"
        const val RELEASES_LATEST =
            "https://api.github.com/repos/$TRUSTED_REPO/releases/latest"
        const val NOT_PACKAGED = "Обновление доступно только в установленном Zapret.app"
        private const val USER_AGENT = "Zapret-macOS-control"
        private val HEX_SHA256 = Regex("^[a-fA-F0-9]{64}$")

        fun requireTrustedApiUrl(url: String) {
            val uri = URI.create(url)
            if (uri.scheme != "https") error("untrusted update URL scheme")
            when (uri.host) {
                "api.github.com" -> {
                    if (!uri.path.contains("/repos/$TRUSTED_REPO/")) error("untrusted update repository")
                }
                "raw.githubusercontent.com" -> {
                    if (!uri.path.startsWith("/$TRUSTED_REPO/")) error("untrusted raw content path")
                }
                else -> error("untrusted update API host")
            }
        }

        fun requireTrustedDownloadUrl(url: String) {
            val uri = URI.create(url)
            if (uri.scheme != "https") error("update URL must be https")
            val host = uri.host ?: error("invalid update URL")
            val path = uri.path ?: ""
            val allowedHost = host == "github.com" || host == "objects.githubusercontent.com" ||
                host.endsWith(".githubusercontent.com")
            if (!allowedHost) error("untrusted download host: $host")
            if (host == "github.com" && !path.contains("/$TRUSTED_REPO/")) {
                error("untrusted download path")
            }
            if (!path.substringAfterLast('/').startsWith("Zapret-") || !path.endsWith(".dmg")) {
                error("untrusted asset name")
            }
        }

        fun verifySha256(file: File, expected: String) {
            val want = expected.removePrefix("sha256:").trim().lowercase()
            if (!HEX_SHA256.matches(want)) error("invalid SHA-256 digest in release metadata")
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            val got = digest.digest().joinToString("") { "%02x".format(it) }
            if (got != want) {
                file.delete()
                error("SHA-256 mismatch for ${file.name}")
            }
        }

        fun parseLatestDmg(json: String): UpdateInfo? {
            val tag = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
                ?: return null
            val versionFromTag = tag.removePrefix("v")
            val assetBlocks = Regex(""""browser_download_url"\s*:\s*"(https://[^"]+)"""")
                .findAll(json)
                .map { it.groupValues[1] }
                .toList()
            val dmgUrl = assetBlocks.firstOrNull { url ->
                val name = url.substringAfterLast('/')
                name.startsWith("Zapret-") && name.endsWith(".dmg") &&
                    runCatching { requireTrustedDownloadUrl(url); true }.getOrDefault(false)
            } ?: return null
            val name = dmgUrl.substringAfterLast('/')
            val versionFromName = Regex("""Zapret-(\d+(?:\.\d+){0,2})\.dmg""").find(name)?.groupValues?.get(1)
            val sha256 = extractSha256(json, name)
            return UpdateInfo(
                version = versionFromName ?: versionFromTag,
                downloadUrl = dmgUrl,
                assetName = name,
                sha256 = sha256,
            )
        }

        /** GitHub asset `digest` field or companion `*.dmg.sha256` / body checksum. */
        fun extractSha256(json: String, assetName: String): String? {
            // Prefer digest next to this asset's download URL (uploader blobs make name↔digest far apart).
            val nearUrl = Regex(
                """"digest"\s*:\s*"sha256:([a-fA-F0-9]{64})"[\s\S]{0,300}?"browser_download_url"\s*:\s*"https://[^"]+/${Regex.escape(assetName)}""",
            ).find(json)?.groupValues?.get(1)
            if (nearUrl != null) return nearUrl.lowercase()

            val urlThenDigest = Regex(
                """"browser_download_url"\s*:\s*"https://[^"]+/${Regex.escape(assetName)}"[\s\S]{0,300}?"digest"\s*:\s*"sha256:([a-fA-F0-9]{64})""",
            ).find(json)?.groupValues?.get(1)
            if (urlThenDigest != null) return urlThenDigest.lowercase()

            // name … digest can span the nested uploader object (~1–2 KB).
            val digestNearName = Regex(
                """"name"\s*:\s*"${Regex.escape(assetName)}"[\s\S]{0,4000}?"digest"\s*:\s*"sha256:([a-fA-F0-9]{64})""",
            ).find(json)?.groupValues?.get(1)
            if (digestNearName != null) return digestNearName.lowercase()

            val digestBeforeName = Regex(
                """"digest"\s*:\s*"sha256:([a-fA-F0-9]{64})"[\s\S]{0,4000}?"name"\s*:\s*"${Regex.escape(assetName)}""",
            ).find(json)?.groupValues?.get(1)
            if (digestBeforeName != null) return digestBeforeName.lowercase()

            val bodyHex = Regex(
                """(?i)(?:sha256|SHA-256)[=:\s]+([a-fA-F0-9]{64}).{0,80}${Regex.escape(assetName)}""",
            ).find(json)?.groupValues?.get(1)
            return bodyHex?.lowercase()
        }
    }
}
