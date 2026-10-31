package ink.tenqui.flowtone.app

import android.net.testUri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastInteractionTestSelectionTest {
    @Test
    fun sameInputAndSeedProduceSameSelection() {
        val songs = (1L..20L).map(::song)

        assertEquals(
            selectFastInteractionTestSongs(songs, seed = 71L),
            selectFastInteractionTestSongs(songs, seed = 71L)
        )
    }

    @Test
    fun differentSeedsCanSelectAndOrderDifferentSongs() {
        val songs = (1L..30L).map(::song)
        val first = selectFastInteractionTestSongs(songs, seed = 1L).playlistSongs
        val second = selectFastInteractionTestSongs(songs, seed = 2L).playlistSongs

        assertTrue(first.map(Song::id) != second.map(Song::id))
    }

    @Test
    fun selectionUsesOnlyUniqueLocalSongsAndCapsAtTen() {
        val songs = (1L..14L).map(::song)
        val duplicate = songs.first()
        val duplicateId = duplicate.copy(uri = testUri("same-id-different-uri"))
        val online = song(99L, SourceType.Online)

        val selected = selectFastInteractionTestSongs(
            songs + duplicate + duplicateId + online,
            seed = 9L
        ).playlistSongs

        assertEquals(FastInteractionTestPlaylistSize, selected.size)
        assertEquals(selected.size, selected.map { it.sourceType to it.id }.distinct().size)
        assertTrue(selected.all { it.sourceType == SourceType.Local })
    }

    @Test
    fun smallAndEmptyLibrariesReturnAvailableSongsWithoutFabrication() {
        assertTrue(selectFastInteractionTestSongs(emptyList(), seed = 3L).playlistSongs.isEmpty())

        val onlySong = song(7L)
        assertEquals(
            listOf(onlySong),
            selectFastInteractionTestSongs(listOf(onlySong, onlySong), seed = 3L).playlistSongs
        )
    }

    private fun song(id: Long, source: SourceType = SourceType.Local) = Song(
        id = id,
        sourceType = source,
        title = "song-$id",
        artist = "artist",
        durationMs = 1L,
        uri = testUri("$source-$id")
    )
}
