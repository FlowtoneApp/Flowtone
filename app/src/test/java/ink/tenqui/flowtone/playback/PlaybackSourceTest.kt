package ink.tenqui.flowtone.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSourceTest {
    @Test
    fun userPlaylistKeyDoesNotDependOnDisplayName() {
        val first = PlaybackSource.userPlaylist("playlist-1", "夜晚")
        val renamed = PlaybackSource.userPlaylist("playlist-1", "深夜")

        assertEquals("playlist:playlist-1", first.key)
        assertEquals(first.key, renamed.key)
        assertEquals("深夜", renamed.displayName)
    }

    @Test
    fun fixedSourcesUseStableKeys() {
        assertEquals("liked_songs", PlaybackSource.LikedSongs.key)
        assertEquals("local_library", PlaybackSource.LocalLibrary.key)
        assertEquals("search", PlaybackSource.Search.key)
        assertEquals("\u641c\u7d22", PlaybackSource.Search.displayName)
    }

    @Test
    fun blankUserPlaylistFallsBackToUnknown() {
        assertEquals(PlaybackSource.Unknown, PlaybackSource.userPlaylist(" ", "空歌单"))
    }
}
