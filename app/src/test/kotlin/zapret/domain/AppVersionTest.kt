package zapret.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun isNewerComparesSemver() {
        assertTrue(AppVersion.isNewer("1.1.1", "1.0.0"))
        assertTrue(AppVersion.isNewer("1.2.0", "1.1.9"))
        assertTrue(AppVersion.isNewer("2.0.0", "1.9.9"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppVersion.isNewer("1.0.0", "1.1.0"))
        assertTrue(AppVersion.isNewer("v1.1.0", "1.0.9"))
    }

    @Test
    fun parseAcceptsShortVersions() {
        assertEquals(listOf(1, 0, 0), AppVersion.parse("1"))
        assertEquals(listOf(1, 2, 0), AppVersion.parse("1.2"))
        assertEquals(listOf(1, 2, 3), AppVersion.parse("v1.2.3"))
    }
}
