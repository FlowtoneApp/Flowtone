package ink.tenqui.flowtone.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchScopeTest {
    @Test
    fun providerScopeIsIdentifiedByExtensionIdRatherThanMusicSourceHost() {
        val providerA = SearchScope.Provider("provider.a")
        val providerB = SearchScope.Provider("provider.b")

        assertNotEquals(providerA, providerB)
        assertEquals("provider.a", providerA.extensionId)
        assertEquals(SearchScope.All, SearchScope.All)
        assertEquals(SearchScope.Local, SearchScope.Local)
    }
}
