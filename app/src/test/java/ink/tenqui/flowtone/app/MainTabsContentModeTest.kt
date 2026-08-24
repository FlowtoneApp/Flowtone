package ink.tenqui.flowtone.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MainTabsContentModeTest {
    @Test
    fun searchActiveSelectsMainTabsSearchMode() {
        assertEquals(MainTabsContentMode.Normal, mainTabsContentMode(searchActive = false))
        assertEquals(MainTabsContentMode.Search, mainTabsContentMode(searchActive = true))
    }
}
