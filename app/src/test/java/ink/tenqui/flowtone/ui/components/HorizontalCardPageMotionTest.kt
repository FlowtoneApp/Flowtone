package ink.tenqui.flowtone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizontalCardPageMotionTest {
    @Test
    fun enterUsesLeftToRightViewportOrderWithoutGlobalIndexes() {
        assertEquals(
            listOf(0, 1, 2, 3),
            (0..3).map { ordinal ->
                pageViewportStaggerOrder(PageTransitionPhase.Incoming, ordinal, 4)
            }
        )
        assertEquals(
            listOf(0, 1, 2),
            listOf(30, 31, 32).indices.map { ordinal ->
                pageViewportStaggerOrder(PageTransitionPhase.Incoming, ordinal, 3)
            }
        )
    }

    @Test
    fun exitUsesRightToLeftViewportOrder() {
        assertEquals(
            listOf(3, 2, 1, 0),
            (0..3).map { ordinal ->
                pageViewportStaggerOrder(PageTransitionPhase.Outgoing, ordinal, 4)
            }
        )
    }

    @Test
    fun enterMovesUpAndFadesIn() {
        val start = pageViewportStaggerVisualState(
            PageTransitionPhase.Incoming, 0f, 0, 4, 24f
        )
        val end = pageViewportStaggerVisualState(
            PageTransitionPhase.Incoming, 1f, 0, 4, 24f
        )

        assertEquals(0f, start.alpha, 0f)
        assertTrue(start.translationY > 0f)
        assertEquals(1f, end.alpha, 0f)
        assertEquals(0f, end.translationY, 0f)
    }

    @Test
    fun exitMovesDownAndFadesOut() {
        val start = pageViewportStaggerVisualState(
            PageTransitionPhase.Outgoing, 0f, 3, 4, 24f
        )
        val end = pageViewportStaggerVisualState(
            PageTransitionPhase.Outgoing, 1f, 3, 4, 24f
        )

        assertEquals(1f, start.alpha, 0f)
        assertEquals(0f, start.translationY, 0f)
        assertEquals(0f, end.alpha, 0f)
        assertTrue(end.translationY > 0f)
    }

    @Test
    fun viewportKeysRemainFrozenForTheActiveTransition() {
        val initial = updatedPageViewportStaggerSnapshot(
            current = null,
            phase = PageTransitionPhase.Incoming,
            transitionId = 7,
            visibleKeys = listOf("album-30", "album-31", "album-32"),
            pageProgress = 0.2f
        )
        val afterLayoutChange = updatedPageViewportStaggerSnapshot(
            current = initial,
            phase = PageTransitionPhase.Incoming,
            transitionId = 7,
            visibleKeys = listOf("album-31", "album-32"),
            pageProgress = 0.4f
        )

        assertEquals(initial, afterLayoutChange)
        assertEquals(listOf("album-30", "album-31", "album-32"), afterLayoutChange?.keys)
    }

    @Test
    fun currentPhaseClearsFreezeAndAlwaysUsesFinalPresentation() {
        val frozen = PageViewportStaggerSnapshot(4, listOf("album"), 0.1f)
        assertNull(
            updatedPageViewportStaggerSnapshot(
                current = frozen,
                phase = PageTransitionPhase.Current,
                transitionId = 4,
                visibleKeys = listOf("album", "newly-scrolled-album"),
                pageProgress = 1f
            )
        )

        val current = pageViewportStaggerVisualState(
            PageTransitionPhase.Current, 0f, 0, 1, 24f
        )
        assertEquals(1f, current.alpha, 0f)
        assertEquals(0f, current.translationY, 0f)
    }
}
