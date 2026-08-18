package zapret.domain

import java.net.IDN

/** Turns pasted URLs and messy editor text into zapret list lines. */
object HostListText {

    fun normalizeHosts(text: String): String =
        joinLines(asLf(text).lineSequence().mapNotNull(::normalizeHostLine))

    fun normalizeIpset(text: String): String =
        joinLines(asLf(text).lineSequence().mapNotNull(::normalizeIpsetLine))

    private fun normalizeHostLine(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (isComment(trimmed)) return trimmed
        val strict = trimmed.startsWith('^')
        var host = (if (strict) trimmed.drop(1) else trimmed).trim()
        host = stripScheme(host)
        host = host.substringBefore('/').substringBefore('?').substringBefore('#')
        host = host.substringAfterLast('@')
        host = stripPort(host).trim().trim('.')
        if (host.startsWith("*.")) host = host.drop(2)
        if (host.isEmpty()) return null
        val ascii = asciiHost(host)
        if (ascii.isEmpty()) return null
        return if (strict) "^$ascii" else ascii
    }

    private fun normalizeIpsetLine(raw: String): String? =
        raw.trim().takeIf { it.isNotEmpty() }

    private fun joinLines(lines: Sequence<String>): String {
        val kept = lines.toList()
        if (kept.isEmpty()) return ""
        return kept.joinToString("\n", postfix = "\n")
    }

    private fun asLf(text: String): String =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    private fun isComment(line: String): Boolean {
        val first = line.first()
        return first == '#' || first == ';' || first == '/'
    }

    private fun stripScheme(value: String): String =
        value.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")

    private fun stripPort(host: String): String {
        if (host.startsWith("[")) {
            val end = host.indexOf(']')
            return if (end > 0) host.substring(1, end) else host.trim('[', ']')
        }
        if (host.count { it == ':' } != 1) return host
        val port = host.substringAfterLast(':')
        if (port.isNotEmpty() && port.all { it.isDigit() }) return host.substringBeforeLast(':')
        return host
    }

    private fun asciiHost(host: String): String =
        try {
            IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (_: IllegalArgumentException) {
            host.lowercase()
        }
}
