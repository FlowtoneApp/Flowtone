package ink.tenqui.flowtone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridCardPageMotionTest {
    @Test
    fun twoColumnViewportUsesRowMajorOrder() {
        val items = listOf(
            GridViewportItem("D", 43, x = 200, y = 300),
            GridViewportItem("A", 40, x = 20, y = 100),
            GridViewportItem("F", 45, x = 200, y = 500),
            GridViewportItem("C", 42, x = 20, y = 300),
            GridViewportItem("B", 41, x = 200, y = 100),
            GridViewportItem("E", 44, x = 20, y = 500)
        )

        assertEquals(listOf("A", "B", "C", "D", "E", "F"), gridViewportKeys(items))
    }

    @Test
    fun enterAndExitUseViewportOrdinalsRatherThanGlobalIndexes() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5),
            (0 until 6).map { ordinal ->
                pageViewportStaggerOrder(PageTransitionPhase.Incoming, ordinal, 6)
            }
        )
        assertEquals(
            listOf(5, 4, 3, 2, 1, 0),
            (0 until 6).map { ordinal ->
                pageViewportStaggerOrder(PageTransitionPhase.Outgoing, ordinal, 6)
            }
        )
    }

    @Test
    fun gridKeysStayFrozenDuringTransitionAndClearWhenCurrent() {
        val initial = updatedPageViewportStaggerSnapshot(
            current = null,
            phase = PageTransitionPhase.Incoming,
            transitionId = 11,
            visibleKeys = listOf("A", "B", "C", "D"),
            pageProgress = 0.15f
        )
        val scrolled = updatedPageViewportStaggerSnapshot(
            current = initial,
            phase = PageTransitionPhase.Incoming,
            transitionId = 11,
            visibleKeys = listOf("C", "D", "E", "F"),
            pageProgress = 0.6f
        )

        assertEquals(initial, scrolled)
        assertNull(
            updatedPageViewportStaggerSnapshot(
                current = scrolled,
                phase = PageTransitionPhase.Current,
                transitionId = 11,
                visibleKeys = listOf("E", "F"),
                pageProgress = 1f
            )
        )
    }
}
