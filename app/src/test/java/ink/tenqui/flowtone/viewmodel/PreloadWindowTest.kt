package ink.tenqui.flowtone.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class PreloadWindowTest {
    @Test
    fun `supported strengths split twenty percent previous and eighty percent next`() {
        assertEquals(PreloadWindowCounts(previous = 0, next = 1), preloadWindowCounts(1))
        assertEquals(PreloadWindowCounts(previous = 1, next = 2), preloadWindowCounts(3))
        assertEquals(PreloadWindowCounts(previous = 1, next = 4), preloadWindowCounts(5))
        assertEquals(PreloadWindowCounts(previous = 1, next = 6), preloadWindowCounts(7))
        assertEquals(PreloadWindowCounts(previous = 2, next = 8), preloadWindowCounts(10))
    }

    @Test
    fun `window prioritizes upcoming items then nearest previous items`() {
        val queue = (0..10).toList()

        assertEquals(
            listOf(6, 7, 8, 9, 4),
            queueItemsInPreloadWindow(
                items = queue,
                currentIndex = 5,
                totalCount = 5
            )
        )
    }

    @Test
    fun `window stays inside queue boundaries`() {
        val queue = (0..4).toList()

        assertEquals(
            listOf(3, 4, 1),
            queueItemsInPreloadWindow(
                items = queue,
                currentIndex = 2,
                totalCount = 5
            )
        )
    }
}
