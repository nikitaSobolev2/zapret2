package zapret.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TgWsProxyPathsTest {

    @Test
    fun bundledBinaryRestoresExecuteBit() {
        val resources = kotlin.io.path.createTempDirectory("tg-ws-resources").toFile()
        val binary = File(resources, "tg-ws-proxy/tg-ws-proxy")
        binary.parentFile.mkdirs()
        binary.writeText("#!/bin/sh\n")
        binary.setReadable(true, false)
        binary.setWritable(true, false)
        binary.setExecutable(false, false)
        assertTrue(binary.isFile)
        assertTrue(!binary.canExecute())

        val previous = System.getProperty("compose.application.resources.dir")
        System.setProperty("compose.application.resources.dir", resources.absolutePath)
        try {
            val found = TgWsProxyPaths.bundledBinary()
            assertNotNull(found)
            assertEquals(binary.absolutePath, found.absolutePath)
            assertTrue(found.canExecute())
        } finally {
            if (previous == null) {
                System.clearProperty("compose.application.resources.dir")
            } else {
                System.setProperty("compose.application.resources.dir", previous)
            }
            resources.deleteRecursively()
        }
    }

    @Test
    fun bundledBinaryMissingWhenAbsent() {
        val resources = kotlin.io.path.createTempDirectory("tg-ws-empty").toFile()
        val previous = System.getProperty("compose.application.resources.dir")
        System.setProperty("compose.application.resources.dir", resources.absolutePath)
        try {
            assertNull(TgWsProxyPaths.bundledBinary())
        } finally {
            if (previous == null) {
                System.clearProperty("compose.application.resources.dir")
            } else {
                System.setProperty("compose.application.resources.dir", previous)
            }
            resources.deleteRecursively()
        }
    }
}
