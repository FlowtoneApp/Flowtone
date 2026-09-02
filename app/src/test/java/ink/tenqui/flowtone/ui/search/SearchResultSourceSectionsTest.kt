package ink.tenqui.flowtone.ui.search

import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.search.SearchScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchResultSourceSectionsTest {
    @Test
    fun allScopeAlwaysPlacesLocalBeforeOnline() {
        assertEquals(
            listOf(SearchResultSourceSection.Local, SearchResultSourceSection.Online),
            searchResultSourceSections(
                scope = SearchScope.All,
                hasLocalResults = true,
                hasOnlineResults = true
            )
        )
    }

    @Test
    fun allScopeOmitsEmptySourceSections() {
        assertEquals(
            listOf(SearchResultSourceSection.Local),
            searchResultSourceSections(SearchScope.All, hasLocalResults = true, hasOnlineResults = false)
        )
        assertEquals(
            listOf(SearchResultSourceSection.Online),
            searchResultSourceSections(SearchScope.All, hasLocalResults = false, hasOnlineResults = true)
        )
        assertEquals(
            emptyList<SearchResultSourceSection>(),
            searchResultSourceSections(SearchScope.All, hasLocalResults = false, hasOnlineResults = false)
        )
    }

    @Test
    fun laterOnlineResultsAppendAfterExistingLocalSection() {
        val beforeOnlineReturns = searchResultSourceSections(
            SearchScope.All,
            hasLocalResults = true,
            hasOnlineResults = false
        )
        val afterOnlineReturns = searchResultSourceSections(
            SearchScope.All,
            hasLocalResults = true,
            hasOnlineResults = true
        )

        assertEquals(listOf(SearchResultSourceSection.Local), beforeOnlineReturns)
        assertEquals(SearchResultSourceSection.Local, afterOnlineReturns.first())
        assertEquals(SearchResultSourceSection.Online, afterOnlineReturns.last())
    }

    @Test
    fun nonAllScopesKeepTheirExistingUnsectionedPresentation() {
        assertEquals(
            emptyList<SearchResultSourceSection>(),
            searchResultSourceSections(SearchScope.Local, hasLocalResults = true, hasOnlineResults = true)
        )
        assertEquals(
            emptyList<SearchResultSourceSection>(),
            searchResultSourceSections(
                SearchScope.Provider("provider-a"),
                hasLocalResults = true,
                hasOnlineResults = true
            )
        )
    }

    @Test
    fun localAndProviderArtistKeysCannotCollideForTheSameDisplayIdentity() {
        val local = localSearchResultItemKey("artist", "Kou!")
        val provider = providerSearchResultItemKey(
            providerId = "provider-a",
            category = ProviderSearchCategory.User,
            identity = "Kou!"
        )

        assertNotEquals(local, provider)
    }
}
