package zapret.domain

import java.io.File

/** Extracts a shell script shipped inside the jar so that it can be executed as root. */
object BundledScript {

    fun extract(name: String): File {
        val text = requireNotNull(javaClass.getResourceAsStream("/scripts/$name")) {
            "встроенный скрипт $name отсутствует"
        }.use { it.readBytes().decodeToString() }

        val file = File.createTempFile("zapret-", "-$name")
        file.writeText(text)
        file.setExecutable(true, true)
        file.deleteOnExit()
        return file
    }
}
