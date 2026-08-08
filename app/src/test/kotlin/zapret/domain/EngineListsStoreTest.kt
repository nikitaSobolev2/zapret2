package zapret.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineListsStoreTest {

    @Test
    fun strategyCatalogParsesTsv() {
        val entries = StrategyCatalog.parseTsv(
            """
            general	general
            general-simple-fake	general (SIMPLE FAKE)
            ../evil	bad
            """.trimIndent(),
        )
        assertEquals(2, entries.size)
        assertEquals("general-simple-fake", entries[1].id)
        assertEquals("general (SIMPLE FAKE)", entries[1].title)
    }

    @Test
    fun seedAndResetLists() {
        val root = File.createTempFile("zapret-lists", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val defaults = File(root, "defaults").apply { mkdirs() }
        File(defaults, "list-general.txt").writeText("example.com\n")
        File(defaults, "ipset-all.txt").writeText("1.2.3.4/32\n")
        for (name in EngineListsStore.LIST_FILES) {
            if (!File(defaults, name).isFile) File(defaults, name).writeText("")
        }

        val listsDir = File(root, "lists")
        val store = EngineListsStore(listsDir = listsDir, defaultsDir = { defaults })
        store.ensureSeeded()
        assertEquals("example.com\n", store.readList("list-general.txt"))

        store.writeList("list-general.txt", "custom.example\n")
        assertEquals("custom.example\n", store.readList("list-general.txt"))

        store.resetList("list-general.txt")
        assertEquals("example.com\n", store.readList("list-general.txt"))
        assertTrue(File(listsDir, "ipset-all.txt").isFile)
    }

    @Test
    fun ipsetModeRoundTrip() {
        assertEquals(IpsetMode.LOADED, IpsetMode.of("loaded"))
        assertEquals(IpsetMode.ANY, IpsetMode.of("any"))
        assertEquals(IpsetMode.NONE, IpsetMode.of("nope"))
    }

    @Test
    fun userDataRootAllowlist() {
        val home = System.getProperty("user.home")
        val ok = File(home, "Library/Application Support/Zapret")
        assertTrue(ZapretPaths.isValidUserDataRoot(ok))
        assertTrue(!ZapretPaths.isValidUserDataRoot(File("/tmp/Zapret")))
    }
}
