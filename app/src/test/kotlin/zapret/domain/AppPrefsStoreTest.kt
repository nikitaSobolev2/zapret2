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
        store.write(AppPrefs(autoUpdate = false))
        assertFalse(store.read().autoUpdate)
        store.write(AppPrefs(autoUpdate = true))
        assertTrue(store.read().autoUpdate)
    }

    @Test
    fun parseDefaultsMissingFlagToTrue() {
        assertEquals(true, AppPrefsStore.parse("""{}""")?.autoUpdate)
        assertEquals(false, AppPrefsStore.parse("""{"autoUpdate": false}""")?.autoUpdate)
    }
}
