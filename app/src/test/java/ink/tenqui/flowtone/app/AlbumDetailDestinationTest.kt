package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.model.LocalAlbum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumDetailDestinationTest {
    @Test
    fun equalityDependsOnlyOnAlbumIdWhileLiveMetadataCanUpdate() {
        val initial = album(id = 42L, title = "Blue")
        val destination = AlbumDetailDestination(albumId = 42L, initialAlbum = initial)
        val renamedDestination = AlbumDetailDestination(
            albumId = 42L,
            initialAlbum = album(id = 42L, title = "Blue (Deluxe)")
        )

        assertEquals(destination, renamedDestination)
        assertEquals(destination.hashCode(), renamedDestination.hashCode())
        assertNotEquals(
            destination,
            AlbumDetailDestination(albumId = 43L, initialAlbum = album(43L, "Other"))
        )

        destination.updateAlbum(album(id = 42L, title = "Blue (Deluxe)"))
        assertEquals("Blue (Deluxe)", destination.album?.title)
        assertEquals(destination, renamedDestination)
    }

    @Test
    fun searchAlbumObscuresSearchWhileDetailIsLive() {
        assertEquals(
            true,
            isSearchObscuredByAlbumDetail(
                searchActive = true,
                secondaryPage = SecondaryPage.Album,
                albumDetailCompositionActive = false
            )
        )
    }

    @Test
    fun outgoingSearchAlbumRemainsAboveSearchUntilCompositionEnds() {
        assertEquals(
            true,
            isSearchObscuredByAlbumDetail(
                searchActive = true,
                secondaryPage = null,
                albumDetailCompositionActive = true
            )
        )
    }

    @Test
    fun nonSearchOrNonAlbumDoesNotElevateDetailAboveSearch() {
        assertEquals(
            false,
            isSearchObscuredByAlbumDetail(
                searchActive = false,
                secondaryPage = SecondaryPage.Album,
                albumDetailCompositionActive = true
            )
        )
        assertEquals(
            false,
            isSearchObscuredByAlbumDetail(
                searchActive = true,
                secondaryPage = SecondaryPage.Playlist,
                albumDetailCompositionActive = false
            )
        )
    }

    @Test
    fun searchLayerStaysBetweenMainTabsAndAlbumInBothTransitionDirections() {
        val mainTabsZIndex = searchAwarePageZIndex(
            searchActive = true,
            isMainTabs = true,
            secondaryPage = null,
            defaultZIndex = 1f
        )
        val albumIncomingZIndex = searchAwarePageZIndex(
            searchActive = true,
            isMainTabs = false,
            secondaryPage = SecondaryPage.Album,
            defaultZIndex = 1f
        )
        val albumOutgoingZIndex = searchAwarePageZIndex(
            searchActive = true,
            isMainTabs = false,
            secondaryPage = SecondaryPage.Album,
            defaultZIndex = 0f
        )

        assertEquals(0f, mainTabsZIndex)
        assertEquals(1f, SearchOverlayPageLayerZIndex)
        assertEquals(2f, albumIncomingZIndex)
        assertEquals(2f, albumOutgoingZIndex)
    }

    @Test
    fun regularPageTransitionKeepsDefaultZIndex() {
        assertEquals(
            1f,
            searchAwarePageZIndex(
                searchActive = false,
                isMainTabs = false,
                secondaryPage = SecondaryPage.Album,
                defaultZIndex = 1f
            )
        )
    }

    private fun album(id: Long, title: String) = LocalAlbum(
        id = id,
        title = title,
        artist = "Aimer",
        artworkUri = null,
        songs = emptyList()
    )
}
