package nl.kwillems.zeilersforum.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test fun versionCompare() {
        assertTrue(UpdateManager.isNewer("3.2.4", "3.2.3"))
        assertTrue(UpdateManager.isNewer("3.2.10", "3.2.9"))
        assertTrue(UpdateManager.isNewer("v3.3.0", "3.2.9"))
        assertTrue(UpdateManager.isNewer("4.0", "3.9.9"))
        assertFalse(UpdateManager.isNewer("3.2.3", "3.2.3"))
        assertFalse(UpdateManager.isNewer("3.2.3", "3.2.3-debug"))
        assertFalse(UpdateManager.isNewer("3.2.2", "3.2.3"))
    }
}
