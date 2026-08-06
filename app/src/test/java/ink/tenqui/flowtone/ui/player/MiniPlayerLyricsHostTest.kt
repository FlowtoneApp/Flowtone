package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.lyrics.LyricLine
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniPlayerLyricsHostTest {
    private val timedLyrics = LyricsState.Available(
        listOf(LyricLine(timestampMs = 1_000L, text = "有效歌词"))
    )
    private val pureMusic = LyricsState.Available(
        listOf(LyricLine(timestampMs = 0L, text = "纯音乐，请欣赏"))
    )

    @Test
    fun positionIsUsedOnlyWhenItBelongsToTheCurrentSong() {
        assertEquals(
            1_250L,
            playbackPositionForSong(
                songId = 2L,
                playbackPosition = PlaybackPositionSnapshot(
                    mediaId = "2",
                    positionMs = 1_250L
                )
            )
        )
    }

    @Test
    fun previousSongsTailPositionIsRejectedDuringSongTransition() {
        assertNull(
            playbackPositionForSong(
                songId = 2L,
                playbackPosition = PlaybackPositionSnapshot(
                    mediaId = "1",
                    positionMs = 240_000L
                )
            )
        )
    }

    @Test
    fun twoSongsWithTimedLyricsUseDifferentAnimatedContentKeys() {
        assertNotEquals(
            lyricsTrackSwitchContentKey(songId = 1L, state = timedLyrics),
            lyricsTrackSwitchContentKey(songId = 2L, state = timedLyrics)
        )
    }

    @Test
    fun normalLyricsAndSpecialContentUseDifferentKeys() {
        assertNotEquals(
            lyricsTrackSwitchContentKey(songId = 1L, state = timedLyrics),
            lyricsTrackSwitchContentKey(songId = 2L, state = pureMusic)
        )
        assertNotEquals(
            lyricsTrackSwitchContentKey(songId = 1L, state = pureMusic),
            lyricsTrackSwitchContentKey(songId = 2L, state = LyricsState.NotFound)
        )
    }

    @Test
    fun twoPureMusicSongsShareOneContentKey() {
        assertEquals(
            lyricsTrackSwitchContentKey(songId = 1L, state = pureMusic),
            lyricsTrackSwitchContentKey(songId = 2L, state = pureMusic)
        )
    }

    @Test
    fun twoSongsWithoutEffectiveLyricsShareOneContentKey() {
        assertEquals(
            lyricsTrackSwitchContentKey(songId = 1L, state = LyricsState.NotFound),
            lyricsTrackSwitchContentKey(
                songId = 2L,
                state = LyricsState.Available(emptyList())
            )
        )
    }
}
