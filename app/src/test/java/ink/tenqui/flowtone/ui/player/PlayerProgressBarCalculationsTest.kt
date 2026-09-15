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

    @Test
    fun `new identity normal progress cannot reuse previous identity position`() {
        assertEquals(
            0f,
            playedProgressForIdentity(identityChanged = true, currentTrackProgress = 0.70f)
        )
    }

    @Test
    fun `late previous track position cannot render after identity changes`() {
        assertEquals(
            0f,
            playedProgressForIdentity(identityChanged = true, currentTrackProgress = 0.70f)
        )
    }

    @Test
    fun `current identity authoritative position is visible in normal state`() {
        assertEquals(
            0.05f,
            playedProgressForIdentity(
                identityChanged = false,
                currentTrackProgress = progressFraction(10_000L, 200_000L)
            )
        )
    }

    @Test
    fun `first current identity snapshot does not have to be exact zero`() {
        assertEquals(
            0.00016f,
            playedProgressForIdentity(
                identityChanged = false,
                currentTrackProgress = progressFraction(32L, 200_000L)
            )
        )
    }

    @Test
    fun `current identity authoritative snapshots advance from first nonzero sample`() {
        val progresses = listOf(32L, 64L, 96L).map { positionMs ->
            playedProgressForIdentity(
                identityChanged = false,
                currentTrackProgress = progressFraction(positionMs, 200_000L)
            )
        }

        assertTrue(progresses.zipWithNext().all { (previous, current) -> current > previous })
    }

    @Test
    fun `unresolved seek remains visible for the current track identity`() {
        assertEquals(
            0.50f,
            playedProgressForIdentity(identityChanged = false, currentTrackProgress = 0.50f)
        )
    }

    @Test
    fun `tap seek completion hands off to authoritative target position`() {
        val authoritativeProgress = progressFraction(120_000L, 240_000L)

        assertEquals(
            0.50f,
            playedProgressDuringTrackSwitchReset(
                resetIsAnimating = false,
                resetStartProgress = 0.70f,
                resetAnimationProgress = 1f,
                currentTrackProgress = authoritativeProgress
            )
        )
    }

    @Test
    fun `track switch reset smoothly animates previous progress to zero`() {
        assertEquals(
            0.35f,
            playedProgressDuringTrackSwitchReset(
                resetIsAnimating = true,
                resetStartProgress = 0.70f,
                resetAnimationProgress = 0.50f,
                currentTrackProgress = 0.01f
            )
        )
        assertEquals(
            0f,
            playedProgressDuringTrackSwitchReset(
                resetIsAnimating = true,
                resetStartProgress = 0.70f,
                resetAnimationProgress = 1f,
                currentTrackProgress = 0.02f
            )
        )
    }

    @Test
    fun `track switch reset target remains zero while new track advances`() {
        assertEquals(
            0.35f,
            playedProgressDuringTrackSwitchReset(
                resetIsAnimating = true,
                resetStartProgress = 0.70f,
                resetAnimationProgress = 0.50f,
                currentTrackProgress = 0.30f
            )
        )
    }

    @Test
    fun `current track seek takes over when track switch reset is cancelled`() {
        assertEquals(
            0.50f,
            playedProgressDuringTrackSwitchReset(
                resetIsAnimating = false,
                resetStartProgress = 0.70f,
                resetAnimationProgress = 0.50f,
                currentTrackProgress = 0.50f
            )
        )
    }

    @Test
    fun `authoritative position below seven seconds does not render seven seconds`() {
        val progress = playedProgressDuringTrackSwitchReset(
            resetIsAnimating = false,
            resetStartProgress = 0f,
            resetAnimationProgress = 0f,
            currentTrackProgress = progressFraction(positionMs = 6_990L, durationMs = 10_000L)
        )

        assertEquals(0.699f, progress)
        assertEquals("0:06", formatPlaybackTime(6_990L))
    }

    @Test
    fun `authoritative position at seven seconds renders seven seconds`() {
        val progress = playedProgressDuringTrackSwitchReset(
            resetIsAnimating = false,
            resetStartProgress = 0f,
            resetAnimationProgress = 0f,
            currentTrackProgress = progressFraction(positionMs = 7_010L, durationMs = 10_000L)
        )

        assertEquals(0.701f, progress)
        assertEquals("0:07", formatPlaybackTime(7_010L))
    }

    @Test
    fun `paused progress remains the final authoritative position`() {
        val pausedPositionMs = 6_990L

        assertEquals(0.699f, progressFraction(pausedPositionMs, durationMs = 10_000L))
        assertEquals("0:06", formatPlaybackTime(pausedPositionMs))
    }
}
