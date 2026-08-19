package ink.tenqui.flowtone.ui.search

import ink.tenqui.flowtone.data.search.SearchProviderOption
import ink.tenqui.flowtone.data.search.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSourceSwitcherTest {
    @Test
    fun currentScopeIsFirstAndNeverRepeated() {
        val current = SearchScope.Provider("provider.b")
        val items = searchSourceSwitcherItems(
            current,
            listOf(SearchProviderOption("provider.a", "A"), SearchProviderOption("provider.b", "B"))
        )

        assertEquals(current, items.first().scope)
        assertEquals(1, items.count { it.scope == current })
    }

    @Test
    fun allLocalAndEveryProviderAreSelectable() {
        val items = searchSourceSwitcherItems(
            SearchScope.All,
            listOf(SearchProviderOption("provider.a", "A"))
        )

        assertTrue(items.any { it.scope == SearchScope.All })
        assertTrue(items.any { it.scope == SearchScope.Local })
        assertTrue(items.any { it.scope == SearchScope.Provider("provider.a") })
    }

    @Test
    fun providerManifestColorIsKeptOnItsSwitcherRow() {
        val items = searchSourceSwitcherItems(
            SearchScope.All,
            listOf(SearchProviderOption("provider.a", "A", "#1A73E8"))
        )

        assertEquals("#1A73E8", items.first { it.scope == SearchScope.Provider("provider.a") }.color)
        assertEquals(null, items.first { it.scope == SearchScope.All }.color)
    }

    @Test
    fun onlyOneItemDoesNotNeedExpansion() {
        val items = listOf(SearchSourceSwitcherItem(SearchScope.Local, "本地"))

        assertFalse(items.size > 1)
    }
}
