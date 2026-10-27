package ink.tenqui.flowtone.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRefreshStateTest {
    @Test
    fun refreshStateIsScopedToSource() {
        val state = MusicUiState().startLibraryRefresh("local").startLibraryRefresh("extension:a")
        val finished = state.finishLibraryRefresh("local", null)
        assertEquals(setOf("extension:a"), finished.libraryRefreshingSources)
        assertEquals(null, finished.libraryRefreshFailure)
    }

    @Test
    fun failureFinishesOnlyItsSourceAndRetainsContent() {
        val before = MusicUiState().startLibraryRefresh("extension:a").startLibraryRefresh("extension:b")
        val after = before.finishLibraryRefresh("extension:a", 5L)
        assertEquals("extension:a" to 5L, after.libraryRefreshFailure)
        assertTrue("extension:b" in after.libraryRefreshingSources)
        assertEquals(before.providerSongs, after.providerSongs)
        assertEquals(before.providerAlbums, after.providerAlbums)
        assertEquals(before.providerUserPlaylists, after.providerUserPlaylists)
    }
}
