package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.playback.PlaybackPositionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniPlayerLyricsHostTest {
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
}
