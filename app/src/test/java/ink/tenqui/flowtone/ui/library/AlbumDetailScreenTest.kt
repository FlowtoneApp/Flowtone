package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.pageElementVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumDetailScreenTest {
    @Test
    fun albumSongKeysProvideStableElementOrder() {
        val keys = listOf("track-1", "track-2", "track-3")

        assertEquals(AlbumSongAnimationOrder(0, 3), albumSongAnimationOrder("track-1", keys))
        assertEquals(AlbumSongAnimationOrder(1, 3), albumSongAnimationOrder("track-2", keys))
    }

    @Test
    fun albumSongsUsePageMotionStaggerAndReversibleVisualState() {
        val keys = listOf("track-1", "track-2", "track-3")
        val firstOrder = albumSongAnimationOrder("track-1", keys)
        val secondOrder = albumSongAnimationOrder("track-2", keys)
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
}
