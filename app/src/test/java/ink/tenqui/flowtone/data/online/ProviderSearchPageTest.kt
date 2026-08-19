package ink.tenqui.flowtone.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderSearchPageTest {
    @Test fun requestKeepsOpaqueCursorAndCategoryWireValue() {
        val request = ProviderSearchRequest(
            keyword = "miku",
            category = ProviderSearchCategory.Playlist,
            cursor = "provider:next_href?offset=20",
            limit = 20
        )

        assertEquals("provider:next_href?offset=20", request.cursor)
        assertEquals("playlist", request.category.toWireValue())
        assertEquals(20, request.limit)
    }

    @Test fun pageAllowsNormalEmptyResultWithoutCursor() {
        val page = ProviderSearchPage(emptyList())

        assertEquals(emptyList<ProviderSong>(), page.results)
        assertNull(page.nextCursor)
    }
}
