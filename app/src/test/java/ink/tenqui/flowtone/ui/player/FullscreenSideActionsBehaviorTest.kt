package ink.tenqui.flowtone.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenSideActionsBehaviorTest {
    @Test
    fun `collapsed More shows button and hides capsule`() {
        val visibility = fullscreenSideActionsVisibility(
            hasCurrentSong = true,
            moreMenuExpanded = false
        )

        assertTrue(visibility.moreButtonVisible)
        assertFalse(visibility.moreMenuVisible)
    }

    @Test
    fun `expanded More replaces button with capsule`() {
        val visibility = fullscreenSideActionsVisibility(
            hasCurrentSong = true,
            moreMenuExpanded = true
        )

        assertFalse(visibility.moreButtonVisible)
        assertTrue(visibility.moreMenuVisible)
    }

    @Test
    fun `More button and capsule both disappear without a current song`() {
        val visibility = fullscreenSideActionsVisibility(
            hasCurrentSong = false,
            moreMenuExpanded = true
        )

        assertFalse(visibility.moreButtonVisible)
        assertFalse(visibility.moreMenuVisible)
    }

    @Test
    fun `song info and add to playlist use the original playback content exit motion`() {
        val metrics = layoutMetrics(contentExitProgress = 0.5f)

        assertEquals(0.5f, metrics.fullscreenContentExitSharedProgress, 0.0001f)
        assertEquals(0.5f, metrics.playbackContentAlpha, 0.0001f)
        assertEquals(16.dp, metrics.playbackContentOffsetY)
    }

    @Test
    fun `artist placeholder shares the same exit motion and owns the larger progress`() {
        val metrics = layoutMetrics(
            contentExitProgress = 0.25f,
            artistExitProgress = 0.75f
        )

        assertEquals(0.75f, metrics.fullscreenContentExitSharedProgress, 0.0001f)
        assertEquals(0.25f, metrics.playbackContentAlpha, 0.0001f)
        assertEquals(24.dp, metrics.playbackContentOffsetY)
    }

    @Test
    fun `returning to playback restores actions without residual exit motion`() {
        val metrics = layoutMetrics()

        assertEquals(0f, metrics.fullscreenContentExitSharedProgress, 0.0001f)
        assertEquals(1f, metrics.playbackContentAlpha, 0.0001f)
        assertEquals(0.dp, metrics.playbackContentOffsetY)
    }

    private fun layoutMetrics(
        contentExitProgress: Float = 0f,
        artistExitProgress: Float = 0f
    ): MiniPlayerFullscreenLayoutMetrics = miniPlayerFullscreenLayoutMetrics(
        playerWidth = 360.dp,
        visualPanelHeight = 780.dp,
        fullscreenCoverCenterY = 240.dp,
        addToPlaylistStatusBarsTop = 24.dp,
        fullscreenContentExitProgress = contentExitProgress,
        artistPlaceholderProgress = artistExitProgress,
        addToPlaylistProgress = 0f
    )
}
