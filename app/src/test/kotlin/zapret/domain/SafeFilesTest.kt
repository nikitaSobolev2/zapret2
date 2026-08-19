package zapret.domain

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeFilesTest {

    @Test
    fun deleteTreeDoesNotFollowDirectorySymlink() {
        withTempDir { root ->
            val victim = File(root, "victim").apply { mkdirs() }
            val canary = File(victim, "keep.txt").apply { writeText("safe") }
            val tree = File(root, "tree").apply { mkdirs() }
            Files.createSymbolicLink(File(tree, "Applications").toPath(), victim.toPath())
            File(tree, "real.txt").writeText("x")
            SafeFiles.deleteTree(tree)
            assertTrue(canary.isFile)
            assertEquals("safe", canary.readText())
            assertFalse(tree.exists())
        }
    }

    @Test
    fun deleteTreeRemovesSymlinkRootWithoutTouchingTarget() {
        withTempDir { root ->
            val victim = File(root, "victim").apply { mkdirs() }
            File(victim, "keep.txt").writeText("safe")
            val link = File(root, "Zapret-update.app")
            Files.createSymbolicLink(link.toPath(), victim.toPath())
            SafeFiles.deleteTree(link)
            assertFalse(link.exists())
            assertTrue(File(victim, "keep.txt").isFile)
        }
    }

    @Test
    fun copyTreeKeepsSymlinksAndDeleteDoesNotFollowThem() {
        withTempDir { root ->
            val victim = File(root, "victim").apply { mkdirs() }
            File(victim, "keep.txt").writeText("safe")
            val source = File(root, "src").apply { mkdirs() }
            File(source, "Contents").mkdirs()
            Files.createSymbolicLink(File(source, "Applications").toPath(), victim.toPath())
            val dest = File(root, "dest")
            SafeFiles.copyTree(source, dest)
            assertTrue(SafeFiles.isSymlink(File(dest, "Applications")))
            SafeFiles.deleteTree(dest)
            assertTrue(File(victim, "keep.txt").isFile)
        }
    }

    private fun withTempDir(block: (File) -> Unit) {
        val root = Files.createTempDirectory("zapret-safe-files").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
