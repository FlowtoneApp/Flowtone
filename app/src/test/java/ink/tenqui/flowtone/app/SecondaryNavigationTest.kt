package ink.tenqui.flowtone.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryNavigationTest {
    @Test
    fun artistPushThenPopReturnsToEmpty() {
        val state = SecondaryNavigationState()
            .push(SecondaryDestination.Artist("A"))
            .pop()

        assertEquals(null, state.current)
    }

    @Test
    fun artistThenAlbumPopsBackToArtist() {
        val artist = SecondaryDestination.Artist("A")
        val state = SecondaryNavigationState()
            .push(artist)
            .push(SecondaryDestination.Album(1L, "Album"))
            .pop()

        assertEquals(artist, state.current)
    }

    @Test
    fun albumThenArtistPopsBackToAlbum() {
        val album = SecondaryDestination.Album(1L, "Album")
        val state = SecondaryNavigationState()
            .push(album)
            .push(SecondaryDestination.Artist("A"))
            .pop()

        assertEquals(album, state.current)
    }

    @Test
    fun directAlbumPopReturnsToEmpty() {
        val state = SecondaryNavigationState()
            .push(SecondaryDestination.Album(1L, "Album"))
            .pop()

        assertEquals(null, state.current)
    }

    @Test
    fun repeatedCurrentArtistIsNotDuplicated() {
        val artist = SecondaryDestination.Artist(" A ")
        val state = SecondaryNavigationState()
            .push(artist)
            .push(SecondaryDestination.Artist("a"))

        assertEquals(1, state.entries.size)
    }

    @Test
    fun differentArtistsCanReturnToPreviousArtist() {
        val first = SecondaryDestination.Artist("A")
        val state = SecondaryNavigationState()
            .push(first)
            .push(SecondaryDestination.Artist("B"))
            .pop()

        assertEquals(first, state.current)
    }

    @Test
    fun artistAlbumBreadcrumbUsesActualParent() {
        assertEquals(
            listOf("A", "Album"),
            secondaryDestinationBreadcrumbs(
                current = SecondaryDestination.Album(1L, "Album"),
                previous = SecondaryDestination.Artist("A"),
                nestedSegments = emptyList()
            )
        )
    }

    @Test
    fun directAlbumBreadcrumbContainsOnlyAlbum() {
        assertEquals(
            listOf("Album"),
            secondaryDestinationBreadcrumbs(
                current = SecondaryDestination.Album(1L, "Album"),
                previous = null,
                nestedSegments = emptyList()
            )
        )
    }
}
