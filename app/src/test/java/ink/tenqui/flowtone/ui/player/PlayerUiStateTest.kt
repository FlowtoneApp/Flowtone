package ink.tenqui.flowtone.ui.player

import android.net.testUri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {
    @Test
    fun `pending online target is shown without reusing confirmed progress`() {
        val confirmedSong = song(1L, SourceType.Local, "Confirmed")
        val pendingSong = song(2L, SourceType.Online, "Pending")
        val state = PlaybackState(
            currentSong = confirmedSong,
            isPlaying = true,
            playWhenReady = true,
            positionMs = 500L,
            bufferedPositionMs = 800L,
            durationMs = 1_000L
        )

        val uiState = PlayerUiState.from(
            playbackState = state,
            pendingSong = pendingSong,
            pendingPlayWhenReady = true
        )

        assertSame(pendingSong, uiState.currentSong)
        assertEquals(0L, uiState.positionMs)
        assertEquals(0L, uiState.bufferedPositionMs)
        assertFalse(uiState.isPlaying)
        assertTrue(uiState.playWhenReady)
        assertTrue(uiState.isPlaybackWaitingForData)
        assertTrue(uiState.hasCurrentSong)
    }

    @Test
    fun `paused pending target is not waiting for data`() {
        val pendingSong = song(2L, SourceType.Online, "Pending")

        val uiState = PlayerUiState.from(
            playbackState = PlaybackState(),
            pendingSong = pendingSong,
            pendingPlayWhenReady = false
        )

        assertFalse(uiState.playWhenReady)
        assertFalse(uiState.isPlaybackWaitingForData)
    }

    @Test
    fun `buffering player waits only while playback intent is active`() {
        val confirmedSong = song(1L, SourceType.Online, "Confirmed")
        val playingIntent = PlayerUiState.from(
            PlaybackState(
                currentSong = confirmedSong,
                playWhenReady = true,
                isBuffering = true
            )
        )
        val pausedIntent = PlayerUiState.from(
            PlaybackState(
                currentSong = confirmedSong,
                playWhenReady = false,
                isBuffering = true
            )
        )

        assertTrue(playingIntent.isPlaybackWaitingForData)
        assertFalse(pausedIntent.isPlaybackWaitingForData)
    }

    @Test
    fun `clearing pending target restores confirmed player projection`() {
        val confirmedSong = song(1L, SourceType.Local, "Confirmed")
        val state = PlaybackState(
            currentSong = confirmedSong,
            isPlaying = true,
            playWhenReady = true,
            positionMs = 500L,
            bufferedPositionMs = 800L,
            durationMs = 1_000L
        )

        val uiState = PlayerUiState.from(state)

        assertSame(confirmedSong, uiState.currentSong)
        assertEquals(500L, uiState.positionMs)
        assertEquals(800L, uiState.bufferedPositionMs)
        assertTrue(uiState.isPlaying)
        assertTrue(uiState.playWhenReady)
        assertFalse(uiState.isPlaybackWaitingForData)
    }

    private fun song(id: Long, sourceType: SourceType, title: String) = Song(
        id = id,
        sourceType = sourceType,
        title = title,
        artist = "Artist",
        durationMs = 1_000L,
        uri = testUri("song-$id")
    )
}
