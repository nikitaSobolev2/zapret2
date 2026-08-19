package zapret.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPrefsStoreTest {

    @Test
    fun jsonRoundTrip() {
        val dir = File.createTempFile("zapret-prefs", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val store = AppPrefsStore(File(dir, "app-prefs.json"))
        store.write(AppPrefs(autoUpdate = false, passwordless = false))
        val read = store.read()
        assertFalse(read.autoUpdate)
        assertFalse(read.passwordless)
        store.write(AppPrefs(autoUpdate = true, passwordless = true))
        assertTrue(store.read().autoUpdate)
        assertTrue(store.read().passwordless)
    }

    @Test
    fun parseDefaultsMissingFlagsToTrue() {
        val parsed = AppPrefsStore.parse("""{}""")
        assertEquals(true, parsed?.autoUpdate)
        assertEquals(true, parsed?.passwordless)
        assertEquals(false, AppPrefsStore.parse("""{"autoUpdate": false}""")?.autoUpdate)
        assertEquals(true, AppPrefsStore.parse("""{"autoUpdate": false}""")?.passwordless)
        assertEquals(false, AppPrefsStore.parse("""{"passwordless": false}""")?.passwordless)
        assertEquals(true, AppPrefsStore.parse("""{"passwordless": false}""")?.autoUpdate)
    }
}
