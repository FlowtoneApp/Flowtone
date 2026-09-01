package ink.tenqui.flowtone.ui.search

import ink.tenqui.flowtone.data.search.ProviderSearchCategoryState
import ink.tenqui.flowtone.data.search.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResultEmptyStateTest {
    @Test
    fun pendingProviderSearchWithNoResultsDoesNotShowEmptyState() {
        assertNull(emptyMessage(providerState = ProviderSearchCategoryState()))
        assertNull(emptyMessage(providerState = ProviderSearchCategoryState(isInitialLoading = true)))
    }

    @Test
    fun settledProviderSearchWithNoResultsShowsEmptyState() {
        assertEquals(
            "没有找到相关内容",
            emptyMessage(providerState = ProviderSearchCategoryState(hasLoaded = true))
        )
    }

    @Test
    fun settledProviderSearchWithResultsDoesNotShowEmptyState() {
        assertNull(
            searchResultEmptyMessage(
                scope = SearchScope.Provider("provider-a"),
                queryIsBlank = false,
                isSearching = false,
                providerState = ProviderSearchCategoryState(hasLoaded = true),
                hasCurrentResults = true
            )
        )
    }

    @Test
    fun freshProviderStateDoesNotInheritPreviousProviderSettlement() {
        val settledProviderA = ProviderSearchCategoryState(hasLoaded = true)
        val pendingProviderB = ProviderSearchCategoryState()

        assertEquals("没有找到相关内容", emptyMessage(providerState = settledProviderA))
        assertNull(emptyMessage(providerState = pendingProviderB))
    }

    @Test
    fun providerFailureKeepsExistingErrorMessageInsteadOfNoResults() {
        assertEquals(
            "IOException",
            emptyMessage(providerState = ProviderSearchCategoryState(error = "IOException"))
        )
    }

    private fun emptyMessage(providerState: ProviderSearchCategoryState): String? =
        searchResultEmptyMessage(
            scope = SearchScope.Provider("provider-a"),
            queryIsBlank = false,
            isSearching = false,
            providerState = providerState,
            hasCurrentResults = false
        )
}
