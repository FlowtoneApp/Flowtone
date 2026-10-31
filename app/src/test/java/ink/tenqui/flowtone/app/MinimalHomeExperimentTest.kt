package ink.tenqui.flowtone.app

import android.net.testUri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimalHomeExperimentTest {
    @Test
    fun selectionIsStableByIdentityAndDoesNotDependOnInputOrder() {
        val duplicateId = song(2L).copy(uri = testUri("alternate-uri"), title = "alternate")
        val input = listOf(song(5L), song(2L), song(4L), duplicateId)

        val first = selectMinimalHomeSongs(input)
        val second = selectMinimalHomeSongs(input.reversed())

        assertEquals(listOf(2L, 4L, 5L), first.map(Song::id))
        assertEquals(first, second)
    }

    @Test
    fun selectionUsesOnlyRealLocalSongsAndHasABoundedSize() {
        val input = (1L..60L).map(::song) + song(100L, SourceType.Online)

        val selected = selectMinimalHomeSongs(input)

        assertEquals(MinimalHomeSongLimit, selected.size)
        assertTrue(selected.all { it.sourceType == SourceType.Local })
        assertEquals(selected.size, selected.map { it.sourceType to it.id }.distinct().size)
        assertEquals((1L..MinimalHomeSongLimit.toLong()).toList(), selected.map(Song::id))
    }

    @Test
    fun emptyAndSmallLibrariesStayEmptyOrUseAvailableSongs() {
        assertTrue(selectMinimalHomeSongs(emptyList()).isEmpty())

        val onlySong = song(7L)
        assertEquals(listOf(onlySong), selectMinimalHomeSongs(listOf(onlySong, onlySong)))
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
