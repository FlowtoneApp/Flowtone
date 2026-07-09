package ink.tenqui.flowtone.ui.components

import android.net.testUri
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCardVisualStyleTest {
    @Test
    fun likedSongsArtworkSpecUsesStableSystemId() {
        val playlist = LibraryPlaylistCard(
            id = LikedSongsPlaylistId,
            title = "Renamed",
            order = 0,
            isSystem = true
        )
        val likedSong = song(id = 1, artworkUri = "content://artwork/liked")
        val unlikedSong = song(id = 2, artworkUri = "content://artwork/unliked")

        assertEquals(
            listOf("file:///content://artwork/liked"),
            buildPlaylistArtworkSpecs(
                playlists = listOf(playlist),
                songs = listOf(likedSong, unlikedSong),
                playlistSongEntries = emptyList(),
                likedSongKeys = listOf(likedSong.id.toString())
            ).single().samples.map { sample -> sample.artworkKey }
        )
    }

    @Test
    fun displayNameAloneDoesNotUseLikedSongsArtworkSpec() {
        val playlist = LibraryPlaylistCard(
            id = "user_playlist",
            title = "\u6211\u559c\u6b22\u7684\u97f3\u4e50",
            order = 0,
            isSystem = false
        )
        val likedSong = song(id = 1, artworkUri = "content://artwork/liked")

        assertEquals(
            emptyList<String>(),
            buildPlaylistArtworkSpecs(
                playlists = listOf(playlist),
                songs = listOf(likedSong),
                playlistSongEntries = emptyList(),
                likedSongKeys = listOf(likedSong.id.toString())
            ).single().samples.map { sample -> sample.artworkKey }
        )
    }

    @Test
    fun artworkSamplesAreDeduplicatedAndCapped() {
        val playlist = LibraryPlaylistCard(
            id = "playlist",
            title = "Playlist",
            order = 0
        )
        val songs = (1L..12L).map { id ->
            val artworkIndex = if (id <= 3L) {
                1L
            } else {
                id
            }
            song(id = id, artworkUri = "content://artwork/$artworkIndex")
        }
        val entries = songs.mapIndexed { index, song ->
            PlaylistSongEntry(
                id = "entry-${song.id}",
                playlistId = playlist.id,
                songId = song.id.toString(),
                addedAt = index.toLong()
            )
        }

        val samples = buildPlaylistArtworkSpecs(
            playlists = listOf(playlist),
            songs = songs,
            playlistSongEntries = entries,
            likedSongKeys = emptyList()
        ).single().samples.map { sample -> sample.artworkKey }

        assertEquals(
            listOf(
                "file:///content://artwork/1",
                "file:///content://artwork/4",
                "file:///content://artwork/5",
                "file:///content://artwork/6",
                "file:///content://artwork/7",
                "file:///content://artwork/8",
                "file:///content://artwork/9",
                "file:///content://artwork/10"
            ),
            samples
        )
    }

    private fun song(
        id: Long,
        artworkUri: String
    ): Song {
        return Song(
            id = id,
            sourceType = SourceType.Local,
            title = "Song $id",
            artist = "Artist",
            durationMs = 180_000L,
            uri = testUri("song-$id"),
            artworkUri = testUri(artworkUri)
        )
    }
}
