package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.data.online.LibraryProviderOption
import ink.tenqui.flowtone.data.online.ProviderLikedSongs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryCategoriesTest {
    @Test fun categoriesFollowSourceCapabilitiesAndRejectStaleSelection() {
        val source = LibrarySourceItem(LibrarySourceIdentity.Extension("provider"), "来源", "扩展", supportsPlaylists = true, supportsAlbums = true)
        val categories = libraryCategories(source.identity.key, source)
        assertEquals(listOf(LibraryCategory.Playlists, LibraryCategory.Albums), categories)
        assertEquals(LibraryCategory.Playlists, validLibraryCategory(categories, LibraryCategory.Songs))
        assertEquals(LibraryCategory.Albums, validLibraryCategory(categories, LibraryCategory.Albums))
        assertEquals(listOf(LibraryCategory.Songs, LibraryCategory.Albums, LibraryCategory.Artists), libraryCategories("local", null))
        assertEquals(listOf(LibraryCategory.Playlists), libraryCategories("mine", null))
        assertEquals(emptyList<LibraryCategory>(), libraryCategories("extension:missing", null))
    }

    @Test fun onlyDeclaredLikedSongsAppearInThePlaylistCategory() {
        val base = LibraryProviderOption("example.music", "来源", null, null, null, false, false)
        val legacy = librarySourceItems(listOf(base.copy(supportsPlaylists = true))).last()
        val likesOnly = librarySourceItems(listOf(base.copy(supportsLikedSongs = true))).last()

        assertNull(libraryProviderLikedSongs(legacy))
        assertNull(libraryProviderLikedSongs(librarySourceItems(emptyList()).single()))
        assertEquals(ProviderLikedSongs("example.music"), libraryProviderLikedSongs(likesOnly))
        assertEquals(listOf(LibraryCategory.Playlists), libraryCategories(likesOnly.identity.key, likesOnly))
    }
}
