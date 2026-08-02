package ink.tenqui.flowtone.ui.library

import android.net.testUri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSongSortTest {

    @Test
    fun `date based criteria respect ascending and descending direction`() {
        val songs = listOf(
            entry(key = "old", dateAdded = 10L, fileTime = 300L),
            entry(key = "middle", dateAdded = 20L, fileTime = 200L),
            entry(key = "new", dateAdded = 30L, fileTime = 100L)
        )

        assertEquals(
            listOf("old", "middle", "new"),
            songs.sortedForPlaylist(
                PlaylistSongSort(PlaylistSongSortCriterion.DateAdded)
            ).keys()
        )
        assertEquals(
            listOf("new", "middle", "old"),
            songs.sortedForPlaylist(
                PlaylistSongSort(
                    criterion = PlaylistSongSortCriterion.DateAdded,
                    direction = PlaylistSongSortDirection.Descending
                )
            ).keys()
        )
        assertEquals(
            listOf("new", "middle", "old"),
            songs.sortedForPlaylist(
                PlaylistSongSort(PlaylistSongSortCriterion.FileTime)
            ).keys()
        )
    }

    @Test
    fun `duration direction controls final duration order`() {
        val songs = listOf(
            entry(key = "long", duration = 300L),
            entry(key = "short", duration = 100L),
            entry(key = "medium", duration = 200L)
        )

        assertEquals(
            listOf("short", "medium", "long"),
            songs.sortedForPlaylist(
                PlaylistSongSort(PlaylistSongSortCriterion.Duration)
            ).keys()
        )
        assertEquals(
            listOf("long", "medium", "short"),
            songs.sortedForPlaylist(
                PlaylistSongSort(
                    criterion = PlaylistSongSortCriterion.Duration,
                    direction = PlaylistSongSortDirection.Descending
                )
            ).keys()
        )
    }

    @Test
    fun `title priorities promote Chinese and symbol titles without changing direction model`() {
        val songs = listOf(
            entry(key = "latin", title = "Apple"),
            entry(key = "chinese", title = "中文"),
            entry(key = "symbol", title = "%Percent")
        )

        assertEquals(
            "chinese",
            songs.sortedForPlaylist(
                PlaylistSongSort(
                    titleCharacterPriority =
                        PlaylistSongTitleCharacterPriority.ChineseFirst
                )
            ).first().selectionKey
        )
        assertEquals(
            "symbol",
            songs.sortedForPlaylist(
                PlaylistSongSort(
                    titleCharacterPriority =
                        PlaylistSongTitleCharacterPriority.OtherFirst
                )
            ).first().selectionKey
        )
    }

    private fun List<SelectablePlaylistSong>.keys(): List<String> = map { it.selectionKey }

    private fun entry(
        key: String,
        title: String = key,
        duration: Long = 0L,
        dateAdded: Long = 0L,
        fileTime: Long = 0L
    ): SelectablePlaylistSong = SelectablePlaylistSong(
        selectionKey = key,
        song = Song(
            id = key.hashCode().toLong(),
            sourceType = SourceType.Local,
            title = title,
            artist = "artist",
            durationMs = duration,
            uri = testUri(key),
            dateAddedSeconds = dateAdded,
            dateModifiedSeconds = fileTime
        )
    )
}
