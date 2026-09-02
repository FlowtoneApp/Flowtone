package ink.tenqui.flowtone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTransitionHostTest {
    @Test fun reversingAtPartialEntryUsesOnlyCurrentRemainingProgress() {
        assertEquals(0.4f, pageTransitionRemainingFraction(0.4f, 0f), 0.0001f)
        assertEquals(0.6f, pageTransitionRemainingFraction(0.4f, 1f), 0.0001f)
    }

    @Test fun profileCardEntersFromNegativeYAndSettlesAtZero() {
        val start = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0f,
            signedOffsetYPx = -9.6f
        )
        val end = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 1f,
            signedOffsetYPx = -9.6f
        )

        assertTrue(start.translationY < 0f)
        assertEquals(0f, end.translationY, 0.0001f)
        assertEquals(0f, start.alpha, 0.0001f)
        assertEquals(1f, end.alpha, 0.0001f)
    }

    @Test fun artistBackButtonUsesTheSameNegativeYDirection() {
        val start = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0f,
            signedOffsetYPx = -8f
        )
        val reversing = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0.4f,
            signedOffsetYPx = -8f
        )

        assertEquals(-8f, start.translationY, 0.0001f)
        assertTrue(reversing.translationY in -8f..0f)
        assertEquals(0.4f, reversing.alpha, 0.0001f)
    }
}
