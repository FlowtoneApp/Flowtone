package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.online.ExtensionImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        val artistState = SecondaryNavigationState().push(artist)
        val artistEntry = artistState.currentEntry
        val state = artistState
            .push(SecondaryDestination.Album(1L, "Album"))
            .pop()

        assertEquals(artist, state.current)
        assertEquals(artistEntry, state.currentEntry)
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
        val initialState = SecondaryNavigationState().push(artist)
        val state = initialState
            .push(SecondaryDestination.Artist("a"))

        assertEquals(1, state.entries.size)
        assertEquals(initialState.currentEntry, state.currentEntry)
    }

    @Test
    fun localArtistKeepsLocalStableIdentity() {
        val destination = SecondaryDestination.Artist(" A ")

        assertEquals("local:a", destination.stableId)
        assertEquals(true, destination.identity.hasLocalContent)
    }

    @Test
    fun sameProviderArtistIsNotDuplicated() {
        val artist = SecondaryDestination.Artist(
            ArtistDestinationIdentity.Provider(
                providerId = "provider-a",
                artistId = "42",
                displayName = "Kou!",
                avatar = null
            )
        )
        val state = SecondaryNavigationState()
            .push(artist)
            .push(
                SecondaryDestination.Artist(
                    ArtistDestinationIdentity.Provider(
                        providerId = "provider-a",
                        artistId = "42",
                        displayName = "Updated display name",
                        avatar = null
                    )
                )
            )

        assertEquals(1, state.entries.size)
        assertEquals(artist, state.current)
    }

    @Test
    fun sameArtistNameFromDifferentProvidersHasDifferentIdentity() {
        val first = SecondaryDestination.Artist(
            ArtistDestinationIdentity.Provider("provider-a", "42", "Kou!", null)
        )
        val second = SecondaryDestination.Artist(
            ArtistDestinationIdentity.Provider("provider-b", "42", "Kou!", null)
        )

        assertNotEquals(first, second)
    }

    @Test
    fun providerArtistDestinationRetainsAvatarReference() {
        val avatar = ExtensionImage("provider-a", "https://example.test/kou.jpg")
        val destination = SecondaryDestination.Artist(
            ArtistDestinationIdentity.Provider("provider-a", "42", "Kou!", avatar)
        )

        assertEquals(avatar, destination.identity.avatar)
        assertEquals(false, destination.identity.hasLocalContent)
    }

    @Test
    fun reopeningPoppedArtistCreatesNewEntryIdentity() {
        val firstOpen = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val firstEntryId = firstOpen.currentEntry?.id
        val secondOpen = firstOpen
            .pop()
            .push(SecondaryDestination.Artist("A"))

        assertNotEquals(firstEntryId, secondOpen.currentEntry?.id)
    }

    @Test
    fun albumThenArtistPopRestoresOriginalAlbumEntry() {
        val albumState = SecondaryNavigationState()
            .push(SecondaryDestination.Album(1L, "Album"))
        val albumEntry = albumState.currentEntry
        val restored = albumState
            .push(SecondaryDestination.Artist("A"))
            .pop()

        assertEquals(albumEntry, restored.currentEntry)
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

    @Test
    fun artistAlbumTopBarRouteRetainsArtistEntryIdentity() {
        val artistState = SecondaryNavigationState()
            .push(SecondaryDestination.Artist("A"))
        val artistEntryId = artistState.currentEntry?.id
        val albumState = artistState
            .push(SecondaryDestination.Album(1L, "Album"))

        assertEquals(
            ArtistTopBarRoute(
                artistEntryId = requireNotNull(artistEntryId),
                artistName = "A",
                hasLocalContent = true,
                avatar = null,
                destinationTitle = "Album"
            ),
            artistTopBarRoute(albumState.entries)
        )
    }

    @Test
    fun directAlbumDoesNotCreateArtistTopBarRoute() {
        val state = SecondaryNavigationState()
            .push(SecondaryDestination.Album(1L, "Album"))

        assertEquals(null, artistTopBarRoute(state.entries))
    }
}
