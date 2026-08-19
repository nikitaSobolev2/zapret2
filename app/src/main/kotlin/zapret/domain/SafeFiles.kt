package zapret.domain

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/** Copy/delete trees without following symbolic links (`File.deleteRecursively` follows them). */
object SafeFiles {

    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)

    fun isSymlink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

    fun deleteIfSymlink(file: File): Boolean {
        val path = file.toPath()
        if (!Files.isSymbolicLink(path)) return false
        Files.delete(path)
        return true
    }

    fun deleteTree(root: File) {
        val path = root.toPath()
        if (!Files.exists(path, *noFollow)) return
        if (Files.isSymbolicLink(path)) {
            Files.delete(path)
            return
        }
        Files.walkFileTree(
            path,
            emptySet(),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    fun copyTree(source: File, dest: File) {
        val from = source.toPath()
        val to = dest.toPath()
        if (Files.isSymbolicLink(from)) error("refusing to copy symlink ${source.name}")
        if (!Files.isDirectory(from, *noFollow)) error("copy source is not a directory")
        if (Files.exists(to, *noFollow)) deleteTree(dest)
        Files.walkFileTree(
            from,
            emptySet(),
            Int.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.createDirectories(to.resolve(from.relativize(dir)))
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val target = to.resolve(from.relativize(file))
                    Files.createDirectories(target.parent)
                    Files.copy(
                        file,
                        target,
                        StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS,
                    )
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    fun privateDirectory(dir: File): File {
        deleteIfSymlink(dir)
        dir.mkdirs()
        dir.setReadable(false, false)
        dir.setWritable(false, false)
        dir.setExecutable(false, false)
        dir.setReadable(true, true)
        dir.setWritable(true, true)
        dir.setExecutable(true, true)
        return dir
    }
}
