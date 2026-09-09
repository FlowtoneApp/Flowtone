package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AlbumCloudArtworkSourceTest {
    @Test
    fun localAlbumUsesTheResolvedLocalArtwork() {
        val localArtwork = Any()
        val destination = SecondaryDestination.Album(albumId = 7L, title = "Local")

        assertSame(localArtwork, albumCloudArtworkData(destination, localArtwork))
    }

    @Test
    fun providerAlbumUsesItsExtensionArtworkFromEveryArtistPath() {
        val artwork = ExtensionImage("provider", "https://example.test/album.jpg")
        val album = ProviderAlbum(
            identity = ProviderEntityIdentity("provider", "album"),
            title = "Album",
            artwork = artwork
        )
        val destination = SecondaryDestination.Album(album)

        assertEquals(artwork, albumCloudArtworkData(destination, localArtwork = null))
        assertEquals(artwork, albumCloudArtworkData(destination, localArtwork = Any()))
    }

    @Test
    fun missingArtworkKeepsTheCanonicalPaletteFallbackPath() {
        val album = ProviderAlbum(
            identity = ProviderEntityIdentity("provider", "album"),
            title = "Album"
        )

        assertNull(
            albumCloudArtworkData(
                destination = SecondaryDestination.Album(album),
                localArtwork = null
            )
        )
        assertNull(albumCloudArtworkData(destination = null, localArtwork = null))
    }

    @Test
    fun artistPathColorIsTheSharedTransitionBaseInBothDirections() {
        val main = Color(0xFF112233)
        val artist = Color(0xFF445566)

        assertEquals(artist, resolveSharedCloudBaseAccent(main, artist))
        assertEquals(main, resolveSharedCloudBaseAccent(main, artistPathAccent = null))
    }
}
