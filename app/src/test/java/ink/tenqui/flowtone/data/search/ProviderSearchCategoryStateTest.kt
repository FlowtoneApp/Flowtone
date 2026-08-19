package ink.tenqui.flowtone.data.search

import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchPage
import ink.tenqui.flowtone.data.online.ProviderSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSearchCategoryStateTest {
    @Test fun nextPageAppendsAndDeduplicatesByProviderIdentity() {
        val first = ProviderSearchCategoryState().startRequest(null).acceptPage(
            ProviderSearchPage(listOf(song("a"), song("b")), "cursor-2"), true
        )
        val next = first.startRequest("cursor-2").acceptPage(
            ProviderSearchPage(listOf(song("b"), song("c"))), true
        )
        assertEquals(listOf("a", "b", "c"), next.items.map { it.id })
        assertFalse(next.isLoadingMore)
    }

    @Test fun nextPageFailureKeepsItemsAndCursorForRetry() {
        val state = ProviderSearchCategoryState().acceptPage(
            ProviderSearchPage(listOf(song("a"), song("b")), "opaque-cursor"), true
        ).startRequest("opaque-cursor").acceptFailure("IOException")
        assertEquals(listOf("a", "b"), state.items.map { it.id })
        assertEquals("opaque-cursor", state.nextCursor)
        assertFalse(state.isLoadingMore)
        assertEquals("IOException", state.error)
    }

    @Test fun categoryStatesRemainIndependent() {
        val states = ProviderSearchCategory.entries.associateWith { ProviderSearchCategoryState() }.toMutableMap()
        states[ProviderSearchCategory.User] = states.getValue(ProviderSearchCategory.User).startRequest(null)
        assertTrue(states.getValue(ProviderSearchCategory.User).isInitialLoading)
        assertFalse(states.getValue(ProviderSearchCategory.Single).isInitialLoading)
        assertFalse(states.getValue(ProviderSearchCategory.Single).isLoadingMore)
    }

    private fun song(id: String) = ProviderSong(
        trackRef = ExtensionTrackRef("test.provider", id), title = id, artist = "artist",
        searchCategory = ProviderSearchCategory.Single
    )
}
