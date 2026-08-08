package zapret.domain

import java.io.File

/** Temp files that only the creating user can read or write. */
object SecureTemp {

    fun file(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix).also { lockDown(it) }

    fun lockDown(file: File, executable: Boolean = false) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        if (executable) file.setExecutable(true, true)
    }
}
