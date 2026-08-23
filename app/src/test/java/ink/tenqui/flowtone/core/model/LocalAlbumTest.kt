package ink.tenqui.flowtone.core.model

import android.net.testUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAlbumTest {
    @Test
    fun groupsSongsByStableAlbumIdAndUsesAlbumMetadata() {
        val songs = listOf(
            song(
                id = 1L,
                albumId = 42L,
                albumTitle = "Blue",
                artist = "First",
                albumArtist = "Aimer",
                hasArtwork = true
            ),
            song(
                id = 2L,
                albumId = 42L,
                albumTitle = "Blue",
                artist = "Guest",
                albumArtist = "Aimer"
            ),
            song(id = 3L, albumId = null, albumTitle = null, artist = "Loose")
        )

        val album = localAlbumsFrom(songs).single()

        assertEquals(42L, album.id)
        assertEquals("Blue", album.title)
        assertEquals("Aimer", album.artist)
        assertEquals(listOf(1L, 2L), album.songs.map(Song::id))
        assertEquals("file:///artwork-1", album.artworkUri.toString())
    }

    @Test
    fun missingMetadataUsesNeutralFallbacks() {
        val album = localAlbumsFrom(
            listOf(
                song(1L, 8L, "<unknown>", "First"),
                song(2L, 8L, " ", "Second")
            )
        ).single()

        assertEquals("\u672a\u77e5\u4e13\u8f91", album.title)
        assertEquals("\u591a\u4f4d\u827a\u672f\u5bb6", album.artist)
        assertNull(album.artworkUri)
    }

    private fun song(
        id: Long,
        albumId: Long?,
        albumTitle: String?,
        artist: String,
        albumArtist: String? = null,
        hasArtwork: Boolean = false
    ) = Song(
        id = id,
        sourceType = SourceType.Local,
        title = "Song $id",
        artist = artist,
        durationMs = 100L,
        uri = testUri("song-$id"),
        albumId = albumId,
        albumTitle = albumTitle,
        albumArtist = albumArtist,
        artworkUri = if (hasArtwork) testUri("artwork-$id") else null
    )
}
