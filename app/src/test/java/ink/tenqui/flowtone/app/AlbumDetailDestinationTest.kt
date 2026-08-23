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

    private fun album(id: Long, title: String) = LocalAlbum(
        id = id,
        title = title,
        artist = "Aimer",
        artworkUri = null,
        songs = emptyList()
    )
}
