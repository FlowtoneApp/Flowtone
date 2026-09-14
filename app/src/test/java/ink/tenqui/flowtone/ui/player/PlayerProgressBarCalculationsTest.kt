package ink.tenqui.flowtone.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerProgressBarCalculationsTest {
    @Test
    fun `progress fraction clamps invalid buffered positions`() {
        assertEquals(0f, progressFraction(positionMs = -1L, durationMs = 1_000L))
        assertEquals(1f, progressFraction(positionMs = 1_500L, durationMs = 1_000L))
        assertEquals(0f, progressFraction(positionMs = 500L, durationMs = 0L))
    }

    @Test
    fun `buffered reset is selected only when song identity changes`() {
        assertFalse(playbackSongIdentityChanged(previousSongKey = 1L, currentSongKey = 1L))
        assertFalse(playbackSongIdentityChanged(previousSongKey = null, currentSongKey = null))
        assertTrue(playbackSongIdentityChanged(previousSongKey = 1L, currentSongKey = 2L))
        assertTrue(playbackSongIdentityChanged(previousSongKey = 1L, currentSongKey = null))
    }
}
