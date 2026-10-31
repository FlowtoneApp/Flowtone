package ink.tenqui.flowtone.app

import android.net.testUri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGallerySelectionTest {
    private fun song(
        id: Long,
        album: Long? = null,
        source: SourceType = SourceType.Local
    ) = Song(
        id = id,
        sourceType = source,
        title = "song-$id",
        artist = "artist",
        durationMs = 1,
        uri = testUri("song-$id-$source"),
        albumId = album
    )

    private fun HomeGallerySelection.allSongs(): List<Song> =
        listOfNotNull(featuredSong) + discoverySongs + suggestedSongs

    @Test
    fun sameSeedProducesStableSelectionAndDifferentSeedsCanVaryIt() {
        val songs = (1L..40L).map { song(it, album = 7) }

        val first = selectHomeGallery(songs, seed = 42L)
        assertEquals(first, selectHomeGallery(songs, seed = 42L))
        assertTrue(
            (43L..52L).any { seed ->
                selectHomeGallery(songs, seed).featuredSong != first.featuredSong
            }
        )
    }

    @Test
    fun allThreeSectionsUseUniqueSongIdentitiesEvenWithinOneAlbum() {
        val songs = (1L..30L).map { song(it, album = 7) }
        val selection = selectHomeGallery(songs, seed = 99L)
        val selectedSongs = selection.allSongs()

        assertEquals(19, selectedSongs.size)
        assertEquals(selectedSongs.size, selectedSongs.map { it.sourceType to it.id }.distinct().size)
        assertTrue(selectedSongs.all { it.albumId == 7L })
        assertEquals(10, selection.discoverySongs.size)
        assertEquals(8, selection.suggestedSongs.size)
    }

    @Test
    fun duplicateSongIdsFromDifferentSourcesRemainDistinct() {
        val local = song(1L, album = 7L)
        val online = song(1L, album = 7L, source = SourceType.Online)

        val selection = selectHomeGallery(listOf(local, local, online), seed = 3L)

        assertEquals(2, selection.allSongs().size)
        assertEquals(
            setOf(local.sourceType to local.id, online.sourceType to online.id),
            selection.allSongs().map { it.sourceType to it.id }.toSet()
        )
    }

    @Test
    fun emptySingleAndSmallLibrariesDoNotRepeatSongs() {
        val empty = selectHomeGallery(emptyList(), seed = 1L)
        assertNull(empty.featuredSong)
        assertTrue(empty.discoverySongs.isEmpty())
        assertTrue(empty.suggestedSongs.isEmpty())

        val one = song(1L)
        val single = selectHomeGallery(listOf(one, one), seed = 1L)
        assertEquals(one, single.featuredSong)
        assertTrue(single.discoverySongs.isEmpty())
        assertTrue(single.suggestedSongs.isEmpty())

        val fewSongs = (1L..4L).map { song(it, album = 5L) }
        val few = selectHomeGallery(fewSongs, seed = 1L)
        assertEquals(
            fewSongs.map { it.sourceType to it.id }.toSet(),
            few.allSongs().map { it.sourceType to it.id }.toSet()
        )
        assertEquals(few.allSongs().size, few.allSongs().map { it.sourceType to it.id }.distinct().size)
    }

    @Test
    fun changedLibraryDropsRemovedSongsAndSelectsOnlyCurrentSongs() {
        val updatedSongs = listOf(song(100L), song(101L), song(102L))
        val updated = selectHomeGallery(updatedSongs, seed = 77L)

        assertEquals(
            updatedSongs.map { it.sourceType to it.id }.toSet(),
            updated.allSongs().map { it.sourceType to it.id }.toSet()
        )
        assertFalse(updated.allSongs().any { it.id in 1L..20L })
    }
}
