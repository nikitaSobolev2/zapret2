package zapret.domain

/**
 * Reads and edits `VAR=value` assignments in a shell file that is sourced by the init scripts.
 *
 * Editing is textual on purpose: everything the app does not understand — comments, unrelated
 * variables, the ordering — survives a write untouched.
 */
object ShellConfigText {

    fun parse(text: String): Map<String, String> {
        val lines = text.lines()
        return scan(lines)
            .filterNot { it.commented }
            .associate { it.name to it.value }
    }

    fun patch(text: String, updates: Map<String, String>): String {
        val lines = text.lines().toMutableList()
        for ((name, value) in updates) {
            val rendered = render(name, value)
            val target = scan(lines).filter { it.name == name }
                .let { found -> found.lastOrNull { !it.commented } ?: found.firstOrNull() }

            val renderedLines = rendered.lines()
            if (target == null) {
                lines.addAll(renderedLines)
            } else {
                lines.subList(target.from, target.to + 1).clear()
                lines.addAll(target.from, renderedLines)
            }
        }
        return lines.joinToString("\n")
    }

    /** Single-quoted so `$()`, backticks, and `"` in values stay literal when sourced. */
    fun render(name: String, value: String): String {
        val escaped = value.replace("'", "'\\''")
        return "$name='$escaped'"
    }

    private data class Assignment(
        val name: String,
        val value: String,
        val from: Int,
        val to: Int,
        val commented: Boolean,
    )

    private val ASSIGNMENT = Regex("""^(#*)\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$""")

    private fun scan(lines: List<String>): List<Assignment> {
        val result = mutableListOf<Assignment>()
        var i = 0
        while (i < lines.size) {
            val match = ASSIGNMENT.matchEntire(lines[i])
            if (match == null) {
                i++
                continue
            }
            val (hashes, name, raw) = match.destructured
            val quote = raw.firstOrNull()?.takeIf { it == '"' || it == '\'' }
            val body = StringBuilder()
            var last = i

            if (quote == null) {
                body.append(stripTrailingComment(raw))
            } else {
                last = readQuoted(lines, i, raw, quote, body)
            }

            val value = if (quote == '"') unescapeDoubleQuoted(body.toString()) else body.toString()
            result += Assignment(name, value, i, last, hashes.isNotEmpty())
            i = last + 1
        }
        return result
    }

    /**
     * Reads a `"…"` or `'…'` value, including multiline spans and POSIX `'\''` inside
     * single-quoted strings (close, literal quote, reopen).
     */
    private fun readQuoted(
        lines: List<String>,
        start: Int,
        raw: String,
        quote: Char,
        body: StringBuilder,
    ): Int {
        var last = start
        var rest = raw.substring(1)
        while (true) {
            val end = closingQuote(rest, quote)
            if (end < 0) {
                body.append(rest).append('\n')
                last++
                if (last >= lines.size) return last
                rest = lines[last]
                continue
            }
            body.append(rest, 0, end)
            rest = rest.substring(end + 1)
            if (quote == '\'' && rest.startsWith("\\'")) {
                body.append('\'')
                rest = rest.substring(2)
                if (rest.startsWith("'")) {
                    rest = rest.substring(1)
                    continue
                }
            }
            return last
        }
    }

    private fun closingQuote(text: String, quote: Char): Int {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quote == '"' && c == '\\') {
                i += 2
                continue
            }
            if (c == quote) return i
            i++
        }
        return -1
    }

    private fun stripTrailingComment(raw: String): String {
        val comment = raw.indexOf(" #")
        return (if (comment >= 0) raw.substring(0, comment) else raw).trim()
    }

    private fun unescapeDoubleQuoted(text: String): String =
        text.replace("\\\"", "\"").replace("\\\\", "\\")
}
