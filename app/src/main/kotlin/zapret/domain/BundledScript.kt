package zapret.domain

import java.io.File

/** Extracts a shell script shipped inside the jar so that it can be executed as root. */
object BundledScript {

    fun extract(name: String): File {
        require(name.matches(SAFE_NAME)) { "некорректное имя скрипта: $name" }
        val text = requireNotNull(javaClass.getResourceAsStream("/scripts/$name")) {
            "встроенный скрипт $name отсутствует"
        }.use { it.readBytes().decodeToString() }

        val file = SecureTemp.file("zapret-", "-$name")
        file.writeText(text)
        SecureTemp.lockDown(file, executable = true)
        file.deleteOnExit()
        return file
    }

    private val SAFE_NAME = Regex("""^[A-Za-z0-9._-]+$""")
}
