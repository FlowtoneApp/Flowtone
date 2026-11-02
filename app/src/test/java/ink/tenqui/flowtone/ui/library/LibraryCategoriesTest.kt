package ink.tenqui.flowtone.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCategoriesTest {
    @Test fun categoriesFollowSourceCapabilitiesAndRejectStaleSelection() {
        val source = LibrarySourceItem(LibrarySourceIdentity.Extension("provider"), "来源", "扩展", supportsPlaylists = true, supportsAlbums = true)
        val categories = libraryCategories(source.identity.key, source)
        assertEquals(listOf(LibraryCategory.Playlists, LibraryCategory.Favorites, LibraryCategory.Albums), categories)
        assertEquals(LibraryCategory.Playlists, validLibraryCategory(categories, LibraryCategory.Songs))
        assertEquals(LibraryCategory.Albums, validLibraryCategory(categories, LibraryCategory.Albums))
        assertEquals(listOf(LibraryCategory.Songs, LibraryCategory.Albums, LibraryCategory.Artists), libraryCategories("local", null))
        assertEquals(listOf(LibraryCategory.Playlists), libraryCategories("mine", null))
        assertEquals(emptyList<LibraryCategory>(), libraryCategories("extension:missing", null))
    }
}
