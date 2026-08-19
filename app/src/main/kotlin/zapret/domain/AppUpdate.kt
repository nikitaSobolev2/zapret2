package zapret.domain

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
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
            info = info.copy(sha256 = fetchCaskSha256(info.version, currentMacDmgArch()))
        }
        if (info.sha256 == null) error("release has no SHA-256 for ${info.assetName}")
        if (AppVersion.isNewer(info.version, currentVersion)) info else null
    }

    fun download(info: UpdateInfo, onProgress: (Long, Long?) -> Unit = { _, _ -> }): Result<File> = runCatching {
        clearCancel()
        requireTrustedDownloadUrl(info.downloadUrl)
        val expectedSha = info.sha256 ?: fetchCaskSha256(info.version, currentMacDmgArch())
            ?: error("missing SHA-256 for ${info.assetName}")
        val dest = File(AppPrefsPaths.cacheDir, info.assetName)
        SafeFiles.deleteIfSymlink(dest)
        val canonicalDest = dest.canonicalFile
        require(canonicalDest.parentFile == AppPrefsPaths.cacheDir.canonicalFile) { "untrusted asset path" }
        val connection = open(info.downloadUrl, timeoutMs = 600_000) { current, hop ->
            if (hop == 0) requireTrustedDownloadUrl(current) else requireTrustedDownloadHost(current)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code downloading ${info.assetName}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            if (total != null && total > MAX_DMG_BYTES) error("download too large")
            connection.inputStream.use { input ->
                canonicalDest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        if (cancelRequested.get()) error("cancelled")
                        val n = input.read(buffer)
                        if (n < 0) break
                        readTotal += n
                        if (readTotal > MAX_DMG_BYTES) {
                            canonicalDest.delete()
                            error("download too large")
                        }
                        output.write(buffer, 0, n)
                        onProgress(readTotal, total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        verifySha256(canonicalDest, expectedSha)
        canonicalDest
    }

    private fun fetchCaskSha256(version: String, arch: String): String? {
        // Repo default branch is master; keep main as fallback for forks/renames.
        for (branch in listOf("master", "main")) {
            val url = "https://raw.githubusercontent.com/$TRUSTED_REPO/$branch/Casks/zapret.rb"
            val sha = runCatching { parseCaskSha256(getText(url), version, arch) }.getOrNull()
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
            if (!isZapretAppBundle(targetApp)) error(NOT_PACKAGED)
            requireReplaceAppPath(targetApp.absolutePath)
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
                SafeFiles.deleteTree(staged)
                error("cancelled")
            }
            requireAbsoluteAppPath(staged.absolutePath)
            requireReplaceAppPath(targetApp.absolutePath)
            val script = writeReplaceScript()
            ProcessBuilder(
                "/bin/bash",
                script.absolutePath,
                ProcessHandle.current().pid().toString(),
                staged.absolutePath,
                targetApp.absolutePath,
            )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            Unit
        }

    private fun getText(url: String): String {
        requireTrustedApiUrl(url)
        val connection = open(
            url,
            timeoutMs = 30_000,
            extraHeaders = mapOf("Accept" to "application/vnd.github+json"),
        ) { current, _ -> requireTrustedApiUrl(current) }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from GitHub Releases")
            return readBounded(connection, MAX_API_BYTES)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        url: String,
        timeoutMs: Int,
        extraHeaders: Map<String, String> = emptyMap(),
        trust: (String, Int) -> Unit,
    ): HttpURLConnection {
        var current = url
        var hop = 0
        while (true) {
            if (hop > MAX_REDIRECTS) error("too many redirects")
            trust(current, hop)
            val connection = newConnection(current, timeoutMs, extraHeaders)
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: error("redirect without Location")
                connection.disconnect()
                current = resolveRedirect(current, location)
                hop++
                continue
            }
            return connection
        }
    }

    private fun newConnection(
        url: String,
        timeoutMs: Int,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpURLConnection {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", USER_AGENT)
        extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.requestMethod = "GET"
        return connection
    }

    private fun readBounded(connection: HttpURLConnection, maxBytes: Long): String {
        connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) error("response too large")
                out.write(buffer, 0, n)
            }
            return out.toString(Charsets.UTF_8)
        }
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

    private fun findZapretApp(mount: File): File? = locateZapretApp(mount)

    private fun stageAppCopy(sourceApp: File): File {
        if (!isZapretAppBundle(sourceApp)) error("Zapret.app not found in DMG")
        val staged = File(AppPrefsPaths.cacheDir, "Zapret-update.app")
        SafeFiles.copyTree(sourceApp, staged)
        return staged
    }

    private fun writeReplaceScript(): File {
        val script = File(AppPrefsPaths.cacheDir, "replace-app.sh")
        SafeFiles.deleteIfSymlink(script)
        script.writeText(
            """
            #!/bin/bash
            set -euo pipefail
            PID="${'$'}1"
            SRC="${'$'}2"
            DST="${'$'}3"
            case "${'$'}PID" in
              ''|*[!0-9]*) exit 1 ;;
            esac
            case "${'$'}SRC" in
              /*.app) ;;
              *) exit 1 ;;
            esac
            case "${'$'}DST" in
              /*.app) ;;
              *) exit 1 ;;
            esac
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
            /usr/bin/open -- "${'$'}DST"
            rm -rf "${'$'}SRC"
            """.trimIndent() + "\n",
        )
        SecureTemp.lockDown(script, executable = true)
        return script
    }

    companion object {
        const val TRUSTED_REPO = "nikitaSobolev2/zapret2"
        const val RELEASES_LATEST =
            "https://api.github.com/repos/$TRUSTED_REPO/releases/latest"
        const val NOT_PACKAGED = "Обновление доступно только в установленном Zapret.app"
        const val MAX_REDIRECTS = 5
        const val MAX_API_BYTES = 2L * 1024 * 1024
        const val MAX_DMG_BYTES = 512L * 1024 * 1024
        private const val USER_AGENT = "Zapret-macOS-control"
        private val HEX_SHA256 = Regex("^[a-fA-F0-9]{64}$")
        private val DMG_ASSET = Regex("""^Zapret-(\d+(?:\.\d+){0,2})(?:-(arm64|x86_64))?\.dmg$""")

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

        fun requireTrustedDownloadHost(url: String) {
            val uri = URI.create(url)
            if (uri.scheme != "https") error("update URL must be https")
            val host = uri.host ?: error("invalid update URL")
            val allowedHost = host == "github.com" || host == "objects.githubusercontent.com" ||
                host.endsWith(".githubusercontent.com")
            if (!allowedHost) error("untrusted download host: $host")
        }

        fun requireTrustedDownloadUrl(url: String) {
            requireTrustedDownloadHost(url)
            val uri = URI.create(url)
            val path = uri.path ?: ""
            if (uri.host == "github.com" && !path.contains("/$TRUSTED_REPO/")) {
                error("untrusted download path")
            }
            if (!path.substringAfterLast('/').startsWith("Zapret-") || !path.endsWith(".dmg")) {
                error("untrusted asset name")
            }
        }

        fun resolveRedirect(fromUrl: String, location: String): String {
            if (location.isBlank()) error("redirect without Location")
            val resolved = URI.create(fromUrl).resolve(location)
            if (!resolved.isAbsolute) error("invalid redirect")
            return resolved.toASCIIString()
        }

        fun requireAbsoluteAppPath(path: String) {
            val file = File(path)
            if (path != file.absolutePath) error("app path must be absolute")
            if (path.any { it < ' ' || it == '"' || it == '$' || it == '`' }) error("unsafe app path")
            if (!file.name.endsWith(".app")) error("path must be an .app bundle")
        }

        fun requireReplaceAppPath(path: String) {
            requireAbsoluteAppPath(path)
            if (File(path).name != "Zapret.app") error("target must be Zapret.app")
        }

        fun isZapretAppBundle(file: File): Boolean {
            if (file.name != "Zapret.app") return false
            if (SafeFiles.isSymlink(file)) return false
            return Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                File(file, "Contents").isDirectory
        }

        fun locateZapretApp(mount: File): File? {
            val direct = File(mount, "Zapret.app")
            if (isZapretAppBundle(direct)) return direct
            val children = mount.listFiles() ?: return null
            for (child in children) {
                if (SafeFiles.isSymlink(child)) continue
                if (isZapretAppBundle(child)) return child
                val nested = File(child, "Zapret.app")
                if (isZapretAppBundle(nested)) return nested
            }
            return null
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

        fun currentMacDmgArch(): String {
            val arch = System.getProperty("os.arch").orEmpty().lowercase()
            return if (arch == "aarch64" || arch == "arm64") "arm64" else "x86_64"
        }

        fun parseCaskSha256(caskText: String, version: String, arch: String): String? {
            val caskVersion = Regex("""version\s+"([^"]+)"""").find(caskText)?.groupValues?.get(1)
            if (caskVersion != null && caskVersion != version) return null
            val collapsed = caskText.replace(Regex("\\s+"), " ")
            val dual = Regex(
                """sha256 arm:\s+"([a-fA-F0-9]{64})"\s*,\s*intel:\s+"([a-fA-F0-9]{64})"""",
            ).find(collapsed)
            if (dual != null) {
                val hex = if (arch == "arm64") dual.groupValues[1] else dual.groupValues[2]
                return hex.lowercase()
            }
            return Regex("""sha256\s+"([a-fA-F0-9]{64})"""").find(caskText)?.groupValues?.get(1)?.lowercase()
        }

        fun parseLatestDmg(json: String, arch: String = currentMacDmgArch()): UpdateInfo? {
            val tag = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
                ?: return null
            val versionFromTag = tag.removePrefix("v")
            val dmgUrls = Regex(""""browser_download_url"\s*:\s*"(https://[^"]+)"""")
                .findAll(json)
                .map { it.groupValues[1] }
                .filter { url ->
                    val name = url.substringAfterLast('/')
                    DMG_ASSET.matches(name) &&
                        runCatching { requireTrustedDownloadUrl(url); true }.getOrDefault(false)
                }
                .toList()
            val preferred = dmgUrls.firstOrNull { it.substringAfterLast('/').endsWith("-$arch.dmg") }
            val legacy = if (arch == "arm64") {
                dmgUrls.firstOrNull { url ->
                    val name = url.substringAfterLast('/')
                    !name.endsWith("-arm64.dmg") && !name.endsWith("-x86_64.dmg")
                }
            } else {
                null
            }
            val dmgUrl = preferred ?: legacy ?: return null
            val name = dmgUrl.substringAfterLast('/')
            val versionFromName = DMG_ASSET.find(name)?.groupValues?.get(1)
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
            val escaped = Regex.escape(assetName)
            val sameAsset = """(?:(?!"browser_download_url")[\s\S]){0,4000}"""
            val digestThenUrl = Regex(
                """"digest"\s*:\s*"sha256:([a-fA-F0-9]{64})"$sameAsset"browser_download_url"\s*:\s*"https://[^"]+/$escaped"""",
            ).find(json)?.groupValues?.get(1)
            if (digestThenUrl != null) return digestThenUrl.lowercase()

            val urlThenDigest = Regex(
                """"browser_download_url"\s*:\s*"https://[^"]+/$escaped"$sameAsset"digest"\s*:\s*"sha256:([a-fA-F0-9]{64})"""",
            ).find(json)?.groupValues?.get(1)
            if (urlThenDigest != null) return urlThenDigest.lowercase()

            val nameThenDigest = Regex(
                """"name"\s*:\s*"$escaped"(?:(?!"name"\s*:)[\s\S]){0,4000}?"digest"\s*:\s*"sha256:([a-fA-F0-9]{64})"""",
            ).find(json)?.groupValues?.get(1)
            if (nameThenDigest != null) return nameThenDigest.lowercase()

            val bodyHex = Regex(
                """(?i)(?:sha256|SHA-256)[=:\s]+([a-fA-F0-9]{64}).{0,80}${Regex.escape(assetName)}""",
            ).find(json)?.groupValues?.get(1)
            return bodyHex?.lowercase()
        }
    }
}
