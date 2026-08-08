package zapret.domain

import java.io.File

/**
 * Reads the installed config and produces its edited text.
 *
 * Writing is not done here: /opt/zapret2/config belongs to root, so the new text is handed to
 * [PrivilegeRunner] by the caller.
 */
class ConfigStore(
    private val configFile: File = ZapretPaths.config,
    private val defaultFile: File = ZapretPaths.configDefault,
) {

    fun read(): ZapretConfig? = readText()?.let { ZapretConfig.from(ShellConfigText.parse(it)) }

    fun edited(config: ZapretConfig, extra: Map<String, String> = emptyMap()): String? {
        val text = readText() ?: return null
        return ShellConfigText.patch(text, config.toAssignments() + extra)
    }

    private fun readText(): String? = sequenceOf(configFile, defaultFile)
        .firstOrNull { it.isFile }
        ?.runCatching { readText() }
        ?.getOrNull()
}
