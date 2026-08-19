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
    fun writeListNormalizesPastedUserHost() {
        val root = File.createTempFile("zapret-lists", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val listsDir = File(root, "lists")
        val store = EngineListsStore(listsDir = listsDir, defaultsDir = { null })
        store.writeList(EngineListsStore.USER_HOST_LIST, "https://My.Site.dev/watch\n")
        assertEquals("my.site.dev\n", store.readList(EngineListsStore.USER_HOST_LIST))
    }

    @Test
    fun seedCreatesEmptyFilesWhenDefaultsMissing() {
        val root = File.createTempFile("zapret-lists", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val listsDir = File(root, "lists")
        val store = EngineListsStore(listsDir = listsDir, defaultsDir = { null })
        store.ensureSeeded()
        for (name in EngineListsStore.LIST_FILES) {
            assertTrue(File(listsDir, name).isFile, name)
            assertEquals("", store.readList(name), name)
        }
    }

    @Test
    fun applyListDraftsSkipsOversizedUnlessReplaced() {
        val drafts = mapOf(
            "list-general.txt" to "a.example\n",
            "ipset-all.txt" to "",
        )
        assertEquals(
            setOf("list-general.txt"),
            EngineListsStore.applyListDrafts(drafts, setOf("ipset-all.txt")).keys,
        )
        assertEquals(
            setOf("list-general.txt", "ipset-all.txt"),
            EngineListsStore.applyListDrafts(drafts, setOf("ipset-all.txt"), setOf("ipset-all.txt")).keys,
        )
    }

    @Test
    fun tooLargeForEditorUsesByteLength() {
        val root = File.createTempFile("zapret-lists", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val listsDir = File(root, "lists").apply { mkdirs() }
        val store = EngineListsStore(listsDir = listsDir, defaultsDir = { null })
        store.ensureSeeded()
        File(listsDir, "ipset-all.txt").writeBytes(ByteArray(EngineListsStore.EDITOR_MAX_BYTES.toInt() + 1))
        assertTrue(store.tooLargeForEditor("ipset-all.txt"))
        assertTrue(!store.tooLargeForEditor("list-general.txt"))
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
