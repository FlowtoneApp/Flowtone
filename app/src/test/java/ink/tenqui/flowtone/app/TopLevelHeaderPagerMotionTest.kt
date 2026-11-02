package ink.tenqui.flowtone.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelHeaderPagerMotionTest {
    @Test fun libraryMotionIsContinuousAcrossPagerCurrentPageChangeAndReversal() {
        val approaching = TopLevelPage.Library.index - topLevelHeaderPagePosition(0, 0.49f)
        val crossed = TopLevelPage.Library.index - topLevelHeaderPagePosition(1, -0.49f)
        assertEquals(0.51f, approaching, 0.001f)
        assertEquals(0.49f, crossed, 0.001f)
        assertEquals(0.49f, topLevelHeaderAlpha(approaching), 0.001f)
        assertEquals(0.51f, topLevelHeaderAlpha(crossed), 0.001f)
        assertEquals(0f, topLevelHeaderAlpha(1f), 0.001f)
        assertEquals(1f, topLevelHeaderAlpha(0f), 0.001f)
        assertEquals(topLevelHeaderAlpha(approaching), topLevelHeaderAlpha(1f - topLevelHeaderPagePosition(0, 0.49f)), 0.001f)
    }
}
