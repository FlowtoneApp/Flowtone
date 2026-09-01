package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.search.SearchScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSearchPendingStateTest {
    @Test
    fun newDedicatedProviderQueryStartsWithItsSelectedCategoryPending() {
        val states = providerSearchCategoryStatesForNewQuery(
            scope = SearchScope.Provider("provider-b"),
            selectedCategory = ProviderSearchCategory.User
        )

        assertTrue(states.getValue(ProviderSearchCategory.User).isInitialLoading)
        assertFalse(states.getValue(ProviderSearchCategory.User).hasLoaded)
        assertTrue(states.getValue(ProviderSearchCategory.User).items.isEmpty())
        assertFalse(states.getValue(ProviderSearchCategory.Single).isInitialLoading)
    }

    @Test
    fun allScopeKeepsItsExistingDeferredProviderRequestBehavior() {
        val states = providerSearchCategoryStatesForNewQuery(
            scope = SearchScope.All,
            selectedCategory = ProviderSearchCategory.Single
        )

        assertFalse(states.getValue(ProviderSearchCategory.Single).isInitialLoading)
    }
}
