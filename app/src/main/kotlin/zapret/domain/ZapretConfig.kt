package zapret.domain

import java.io.File

enum class IpsetMode(val value: String, val label: String) {
    NONE("none", "Не использовать IP-список"),
    LOADED("loaded", "IP из ipset-all.txt"),
    ANY("any", "Все IPv4 (кроме exclude)");

    companion object {
        fun of(value: String?): IpsetMode =
            entries.firstOrNull { it.value == value?.trim() } ?: NONE
    }
}

/** User-facing engine settings (strategy + ipset mode). Lists live in [EngineListsStore]. */
data class ZapretConfig(
    val strategyId: String = DEFAULT_STRATEGY,
    val ipsetMode: IpsetMode = IpsetMode.NONE,
) {
    companion object {
        const val DEFAULT_STRATEGY = "general-simple-fake"
    }
}

data class StrategyEntry(
    val id: String,
    val title: String,
)

object StrategyCatalog {
    private val ID = Regex("""^general(-[a-z0-9]+)*$""")

    fun parseTsv(text: String): List<StrategyEntry> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                val id = if (tab < 0) line else line.substring(0, tab).trim()
                val title = if (tab < 0) id else line.substring(tab + 1).trim().ifEmpty { id }
                if (!ID.matches(id)) null else StrategyEntry(id, title)
            }
            .toList()

    fun load(payload: File?): List<StrategyEntry> {
        val file = payload?.let { java.io.File(it, "strategies.tsv") } ?: return emptyList()
        if (!file.isFile) return emptyList()
        return parseTsv(file.readText())
    }

    fun isValidId(id: String): Boolean = ID.matches(id.trim())
}
