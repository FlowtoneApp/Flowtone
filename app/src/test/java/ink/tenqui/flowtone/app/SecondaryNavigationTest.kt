package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ArtistMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ArtistSongOrderInfo
import ink.tenqui.flowtone.data.online.ProviderArtist
import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import ink.tenqui.flowtone.ui.components.FullTitleOverlayBackResult
import ink.tenqui.flowtone.ui.components.canOpenFullTitleOverlay
import ink.tenqui.flowtone.ui.components.fullTitleOverlayBackResult
import ink.tenqui.flowtone.ui.library.ArtistHeroBackgroundKind

class SecondaryNavigationTest {
    @Test
    fun localAlbumDestinationKeepsLongIdentity() {
        val destination = SecondaryDestination.Album(42L, "Local")

        assertEquals(AlbumDestinationIdentity.Local(42L), destination.identity)
        assertEquals("local:42", destination.stableId)
    }

    @Test
    fun providerAlbumIdentityIncludesProviderAndRemoteId() {
        val first = SecondaryDestination.Album(
            ProviderAlbum(ProviderEntityIdentity("provider-a", "1"), "Album")
        )
        val second = SecondaryDestination.Album(
            ProviderAlbum(ProviderEntityIdentity("provider-b", "1"), "Album")
        )

        assertEquals("provider:provider-a\u00001", first.stableId)
        assertNotEquals(first, second)
    }
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
    fun providerArtistDestinationRetainsProfileMetadataWithoutChangingIdentity() {
        val metadata = ArtistMetadata(songCount = 11, albumCount = 10)
        val destination = SecondaryDestination.Artist(
            ArtistDestinationIdentity.Provider("provider-a", "42", "Kou!", null, metadata)
        )

        assertEquals("provider:provider-a\u000042", destination.stableId)
        assertEquals(metadata, destination.identity.profileMetadata)
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
    fun artistAlbumDestinationRetainsParentPresentationIdentity() {
        val artist = ArtistDestinationIdentity.Local("A")
        val album = SecondaryDestination.Album(1L, "Album", parentArtist = artist)

        assertEquals(artist, album.parentArtist)
        assertEquals(
            listOf("A", "Album"),
            secondaryDestinationBreadcrumbs(album, previous = null, nestedSegments = emptyList())
        )
    }

    @Test
    fun onlyActualVisualOverflowCanOpenFullTitleOverlay() {
        assertFalse(canOpenFullTitleOverlay(hasVisualOverflow = false))
        assertTrue(canOpenFullTitleOverlay(hasVisualOverflow = true))
    }

    @Test
    fun fullTitleOverlayConsumesBackBeforeNavigation() {
        assertEquals(
            FullTitleOverlayBackResult.DismissOverlay,
            fullTitleOverlayBackResult(overlayVisible = true)
        )
        assertEquals(
            FullTitleOverlayBackResult.NavigateBack,
            fullTitleOverlayBackResult(overlayVisible = false)
        )
    }

    @Test
    fun matchingArtistAlbumPairUsesReversiblePageTransition() {
        val artist = SecondaryDestination.Artist("A")
        val album = SecondaryDestination.Album(
            albumId = 1L,
            title = "Album",
            parentArtist = artist.identity
        )

        assertTrue(isArtistAlbumTransition(artist, album))
        assertTrue(isArtistAlbumTransition(album, artist))
        assertFalse(isArtistAlbumTransition(SecondaryDestination.Artist("B"), album))
    }

    @Test
    fun artistParentedAlbumKeepsArtistTopBarAsTopPresentationOwner() {
        val artist = ArtistDestinationIdentity.Local("A")

        assertEquals(
            SecondaryTopPresentationOwner.ArtistTopBar,
            secondaryTopPresentationOwner(SecondaryDestination.Artist(artist))
        )
        assertEquals(
            SecondaryTopPresentationOwner.ArtistTopBar,
            secondaryTopPresentationOwner(SecondaryDestination.ArtistAlbums(artist))
        )
        assertEquals(
            SecondaryTopPresentationOwner.ArtistTopBar,
            secondaryTopPresentationOwner(
                SecondaryDestination.Album(1L, "Album", parentArtist = artist)
            )
        )
    }

    @Test
    fun directAlbumKeepsStandardTopBarAsTopPresentationOwner() {
        assertEquals(
            SecondaryTopPresentationOwner.StandardTopBar,
            secondaryTopPresentationOwner(SecondaryDestination.Album(1L, "Album"))
        )
    }

    @Test
    fun artistTopBarOcclusionStaysWithEverySlotInTheActiveArtistLineage() {
        val artist = SecondaryDestination.Artist("A")
        val route = ArtistTopBarRoute(
            artistEntryKey = "artist-entry",
            artist = artist
        )

        assertTrue(artistTopBarOcclusionOwnsTransitionSlot(artist, route))
        assertTrue(
            artistTopBarOcclusionOwnsTransitionSlot(
                SecondaryDestination.ArtistSongs(artist.identity),
                route
            )
        )
        assertTrue(
            artistTopBarOcclusionOwnsTransitionSlot(
                SecondaryDestination.ArtistAlbums(artist.identity),
                route
            )
        )
        assertTrue(
            artistTopBarOcclusionOwnsTransitionSlot(
                SecondaryDestination.Album(1L, "Album", parentArtist = artist.identity),
                route
            )
        )
    }

    @Test
    fun artistTopBarOcclusionDoesNotEscapeTheActiveArtistLineage() {
        val artist = SecondaryDestination.Artist("A")
        val otherArtist = SecondaryDestination.Artist("B")
        val route = ArtistTopBarRoute(
            artistEntryKey = "artist-entry",
            artist = artist
        )

        assertFalse(
            artistTopBarOcclusionOwnsTransitionSlot(
                SecondaryDestination.ArtistSongs(otherArtist.identity),
                route
            )
        )
        assertFalse(
            artistTopBarOcclusionOwnsTransitionSlot(SecondaryDestination.Album(1L, "Album"), route)
        )
        assertFalse(
            artistTopBarOcclusionOwnsTransitionSlot(
                SecondaryDestination.Playlist("playlist", "Playlist"),
                route
            )
        )
        assertFalse(
            artistTopBarOcclusionOwnsTransitionSlot(
                SecondaryDestination.Standard(SecondaryPage.Settings),
                route
            )
        )
        // Search is not a SecondaryDestination, so it reaches this helper as no slot.
        assertFalse(artistTopBarOcclusionOwnsTransitionSlot(null, route))
    }

    @Test
    fun reopeningTheSameArtistCreatesAFreshNavigationEntrySession() {
        val artist = SecondaryDestination.Artist("A")
        val firstOpen = SecondaryNavigationState().push(artist)
        val reopened = firstOpen.pop().push(artist)

        assertEquals(artist, firstOpen.current)
        assertEquals(artist, reopened.current)
        assertNotEquals(firstOpen.currentEntry?.id, reopened.currentEntry?.id)
        assertNotEquals(
            firstOpen.currentEntry?.uiStateKey(),
            reopened.currentEntry?.uiStateKey()
        )
        assertNotEquals(
            firstOpen.currentEntry?.transitionIdentityKey(),
            reopened.currentEntry?.transitionIdentityKey()
        )
    }

    @Test
    fun artistTopBarRouteIsBoundToTheActualStackEntryRatherThanArtistIdentity() {
        val artist = SecondaryDestination.Artist("A")
        val firstOpen = SecondaryNavigationState().push(artist)
        val secondOpen = firstOpen.pop().push(artist)

        assertNotEquals(
            artistTopBarRoute(firstOpen.entries)?.artistEntryKey,
            artistTopBarRoute(secondOpen.entries)?.artistEntryKey
        )
    }

    @Test
    fun currentArtistOwnsTheTopBarWithoutAnAlbumSuffix() {
        val artist = SecondaryDestination.Artist("A")
        val state = SecondaryNavigationState().push(artist)
        val route = checkNotNull(artistTopBarRoute(state.entries))

        assertEquals(state.currentEntry?.uiStateKey(), route.artistEntryKey)
        assertEquals(artist, route.artist)
        assertEquals(null, route.pathEntryKey)
        assertEquals(emptyList<String>(), route.pathSegments)
    }

    @Test
    fun artistParentedAlbumKeepsTheSameArtistTopBarAndAddsItsSuffix() {
        val artistState = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val artist = artistState.current as SecondaryDestination.Artist
        val albumState = artistState.push(
            SecondaryDestination.Album(7L, "Album", parentArtist = artist.identity)
        )
        val route = checkNotNull(artistTopBarRoute(albumState.entries))

        assertEquals(artistState.currentEntry?.uiStateKey(), route.artistEntryKey)
        assertEquals(albumState.currentEntry?.uiStateKey(), route.pathEntryKey)
        assertEquals(listOf("Album"), route.pathSegments)
        assertTrue(artistTopBarIdentityVisible(route, scrollIdentityVisible = false))
    }

    @Test
    fun artistPageIdentityStillFollowsItsEntryScopedScrollState() {
        val state = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val route = checkNotNull(artistTopBarRoute(state.entries))

        assertFalse(artistTopBarIdentityVisible(route, scrollIdentityVisible = false))
        assertTrue(artistTopBarIdentityVisible(route, scrollIdentityVisible = true))
    }

    @Test
    fun bannerAndCloudTopBarsNeverDrawAHeroDerivedSurface() {
        assertEquals(
            ArtistTopBarSurfaceTreatment.Transparent,
            artistTopBarSurfaceTreatment(ArtistHeroBackgroundKind.Banner)
        )
        assertEquals(
            ArtistTopBarSurfaceTreatment.Transparent,
            artistTopBarSurfaceTreatment(ArtistHeroBackgroundKind.Cloud)
        )
    }

    @Test
    fun artistParentedAlbumFindsItsMatchingArtistEntry() {
        val artistAState = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val artistBState = artistAState.push(SecondaryDestination.Artist("B"))
        val artistA = artistAState.current as SecondaryDestination.Artist
        val albumState = artistBState.push(
            SecondaryDestination.Album(7L, "Album A", parentArtist = artistA.identity)
        )
        val route = checkNotNull(artistTopBarRoute(albumState.entries))

        assertEquals(artistAState.currentEntry?.uiStateKey(), route.artistEntryKey)
        assertEquals(artistA, route.artist)
    }

    @Test
    fun directAlbumDoesNotCreateAnArtistTopBarRoute() {
        val state = SecondaryNavigationState().push(SecondaryDestination.Album(7L, "Album"))

        assertEquals(null, artistTopBarRoute(state.entries))
    }

    @Test
    fun poppingArtistParentedAlbumRestoresTheSameArtistTopBarRoute() {
        val artistState = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val artist = artistState.current as SecondaryDestination.Artist
        val albumState = artistState.push(
            SecondaryDestination.Album(7L, "Album", artist.identity)
        )
        val albumRoute = checkNotNull(artistTopBarRoute(albumState.entries))
        val restoredRoute = checkNotNull(artistTopBarRoute(albumState.pop().entries))

        assertEquals(albumRoute.artistEntryKey, restoredRoute.artistEntryKey)
        assertEquals(emptyList<String>(), restoredRoute.pathSegments)
    }

    @Test
    fun providerArtistDestinationRetainsSanitizedSongOrderDescription() {
        val destination = checkNotNull(
            providerArtistDestination(
                ProviderArtist(
                    identity = ProviderEntityIdentity("provider-a", "42"),
                    title = "Kou!",
                    songOrder = ArtistSongOrderInfo("time", "  时间排序  ")
                )
            )
        )
        val identity = destination.identity as ArtistDestinationIdentity.Provider

        assertEquals(ArtistSongOrderInfo("time", "时间排序"), identity.songOrder)
    }

    @Test
    fun artistSongsAndAlbumsAreIndependentStackEntriesWithArtistBreadcrumbs() {
        val artistState = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val artist = artistState.current as SecondaryDestination.Artist
        val songsState = artistState.push(SecondaryDestination.ArtistSongs(artist.identity))
        val albumsState = artistState.push(SecondaryDestination.ArtistAlbums(artist.identity))

        assertEquals(listOf("全部歌曲"), artistTopBarRoute(songsState.entries)?.pathSegments)
        assertEquals(listOf("全部专辑"), artistTopBarRoute(albumsState.entries)?.pathSegments)
        assertEquals(artist, songsState.pop().current)
        assertEquals(artist, albumsState.pop().current)
    }

    @Test
    fun artistAlbumsOnlyChangesPathContentAndKeepsTheParentTopBarCompositionKey() {
        val artistState = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val artist = artistState.current as SecondaryDestination.Artist
        val albumsState = artistState.push(SecondaryDestination.ArtistAlbums(artist.identity))

        val overviewRoute = checkNotNull(artistTopBarRoute(artistState.entries))
        val albumsRoute = checkNotNull(artistTopBarRoute(albumsState.entries))

        assertEquals(overviewRoute.artistEntryKey, albumsRoute.artistEntryKey)
        assertEquals(overviewRoute.artist.identity, albumsRoute.artist.identity)
        assertEquals(emptyList<String>(), overviewRoute.pathSegments)
        assertEquals(listOf("全部专辑"), albumsRoute.pathSegments)
    }

    @Test
    fun albumOpenedFromArtistAlbumsKeepsTheDeeperArtistPathAndRestoresCollection() {
        val artistState = SecondaryNavigationState().push(SecondaryDestination.Artist("A"))
        val artist = artistState.current as SecondaryDestination.Artist
        val albumsState = artistState.push(SecondaryDestination.ArtistAlbums(artist.identity))
        val albumState = albumsState.push(
            SecondaryDestination.Album(
                albumId = 7L,
                title = "A Very Long Album Name",
                parentArtist = artist.identity
            )
        )

        assertEquals(
            listOf("全部专辑", "A Very Long Album Name"),
            artistTopBarRoute(albumState.entries)?.pathSegments
        )
        assertEquals(albumsState.current, albumState.pop().current)
        assertEquals(artist.identity, artistPathIdentity(albumsState.current))
    }

}
