package ink.tenqui.flowtone.data.online

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderSearchCategoryTest {
    @Test
    fun `wire categories map to host categories`() {
        assertEquals(ProviderSearchCategory.Single, providerSearchCategoryFromWire("song"))
        assertEquals(ProviderSearchCategory.Album, providerSearchCategoryFromWire("album"))
        assertEquals(ProviderSearchCategory.User, providerSearchCategoryFromWire("user"))
        assertEquals(ProviderSearchCategory.User, providerSearchCategoryFromWire("artist"))
    }

    @Test
    fun `unknown or missing category stays backward compatible as single`() {
        assertEquals(ProviderSearchCategory.Single, providerSearchCategoryFromWire(""))
        assertEquals(ProviderSearchCategory.Single, providerSearchCategoryFromWire("unknown"))
    }
}
