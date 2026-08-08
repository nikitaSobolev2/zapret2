package zapret.domain

/** Runtime app version from packaged `version.txt` (Gradle `appVersion` / `packageVersion`). */
object AppVersion {

    fun current(): String =
        runCatching {
            AppVersion::class.java.classLoader
                .getResourceAsStream("version.txt")
                ?.bufferedReader()
                ?.use { it.readText().trim() }
        }.getOrNull().orEmpty().ifBlank { "0.0.0" }

    /** True when [candidate] is a higher MAJOR.MINOR.PATCH than [current]. */
    fun isNewer(candidate: String, current: String = current()): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return true
        return compare(a, b) > 0
    }

    fun parse(text: String): List<Int>? {
        val cleaned = text.trim().removePrefix("v")
        if (!cleaned.matches(Regex("""\d+(\.\d+){0,2}"""))) return null
        val parts = cleaned.split('.').map { it.toIntOrNull() ?: return null }
        return (parts + listOf(0, 0, 0)).take(3)
    }

    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until 3) {
            val d = a[i].compareTo(b[i])
            if (d != 0) return d
        }
        return 0
    }
}
