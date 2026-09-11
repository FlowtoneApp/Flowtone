package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.pageElementVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailScreenTest {
    @Test
    fun albumSongKeysProvideStableElementOrder() {
        val keys = listOf("track-1", "track-2", "track-3")

        assertEquals(SongListAnimationOrder(0, 3), songListAnimationOrder("track-1", keys))
        assertEquals(SongListAnimationOrder(1, 3), songListAnimationOrder("track-2", keys))
    }

    @Test
    fun albumSongsUsePageMotionStaggerAndReversibleVisualState() {
        val keys = listOf("track-1", "track-2", "track-3")
        val firstOrder = songListAnimationOrder("track-1", keys)
        val secondOrder = songListAnimationOrder("track-2", keys)
        val pageProgress = 0.2f
        val firstProgress = PageMotion.elementProgress(
            pageProgress,
            firstOrder.order,
            firstOrder.orderCount
        )
        val secondProgress = PageMotion.elementProgress(
            pageProgress,
            secondOrder.order,
            secondOrder.orderCount
        )

        assertNotEquals(firstProgress, secondProgress)
        val incoming = pageElementVisualState(PageTransitionPhase.Incoming, firstProgress, 24f)
        val outgoing = pageElementVisualState(PageTransitionPhase.Outgoing, firstProgress, 24f)
        assertEquals(1f, incoming.alpha + outgoing.alpha, 0.0001f)
    }

    @Test
    fun retainedIncomingAlbumBodyExitsContinuouslyWhenMasterReverses() {
        val late = PageMotion.elementProgress(0.99f, order = 0, orderCount = 1)
        val middle = PageMotion.elementProgress(0.7f, order = 0, orderCount = 1)
        val end = PageMotion.elementProgress(0f, order = 0, orderCount = 1)
        val lateVisual = pageElementVisualState(PageTransitionPhase.Incoming, late, 24f)
        val middleVisual = pageElementVisualState(PageTransitionPhase.Incoming, middle, 24f)
        val endVisual = pageElementVisualState(PageTransitionPhase.Incoming, end, 24f)

        assertTrue(lateVisual.alpha > middleVisual.alpha)
        assertTrue(middleVisual.alpha > endVisual.alpha)
        assertEquals(0f, endVisual.alpha, 0.0001f)
        assertTrue(middleVisual.translationY in 0f..24f)
    }

    @Test
    fun currentAlbumBodyGetsTheStandardOutgoingExitOnBack() {
        val start = pageElementVisualState(
            PageTransitionPhase.Outgoing,
            PageMotion.elementProgress(0f, order = 0, orderCount = 1),
            24f
        )
        val middle = pageElementVisualState(
            PageTransitionPhase.Outgoing,
            PageMotion.elementProgress(0.5f, order = 0, orderCount = 1),
            24f
        )
        val end = pageElementVisualState(
            PageTransitionPhase.Outgoing,
            PageMotion.elementProgress(1f, order = 0, orderCount = 1),
            24f
        )

        assertEquals(1f, start.alpha, 0.0001f)
        assertTrue(middle.alpha in 0f..1f)
        assertEquals(0f, end.alpha, 0.0001f)
    }
}
