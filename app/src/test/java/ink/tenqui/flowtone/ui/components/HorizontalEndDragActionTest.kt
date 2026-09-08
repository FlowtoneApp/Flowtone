package ink.tenqui.flowtone.ui.components

import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizontalEndDragActionTest {
    @Test
    fun disabledOrNotAtEndNeverAccumulates() {
        val state = HorizontalEndDragActionState(thresholdPx = 64f)
        state.onDragStart()

        assertFalse(state.onDragDelta(false, true, NestedScrollSource.UserInput, -80f))
        assertEquals(0f, state.accumulatedDistanceForTest(), 0f)
        assertFalse(state.onDragDelta(true, false, NestedScrollSource.UserInput, -80f))
        assertEquals(0f, state.accumulatedDistanceForTest(), 0f)
    }

    @Test
    fun activePointerDragAccumulatesOnlyTheLeftwardEndDelta() {
        val state = HorizontalEndDragActionState(thresholdPx = 64f)
        state.onDragStart()

        assertFalse(state.onDragDelta(true, true, NestedScrollSource.UserInput, -30f))
        assertEquals(30f, state.accumulatedDistanceForTest(), 0f)
        assertFalse(state.onDragDelta(true, true, NestedScrollSource.UserInput, 10f))
        assertEquals(0f, state.accumulatedDistanceForTest(), 0f)
    }

    @Test
    fun thresholdTriggersExactlyOncePerDrag() {
        val state = HorizontalEndDragActionState(thresholdPx = 64f)
        state.onDragStart()

        assertFalse(state.onDragDelta(true, true, NestedScrollSource.UserInput, -40f))
        assertTrue(state.onDragDelta(true, true, NestedScrollSource.UserInput, -24f))
        assertFalse(state.onDragDelta(true, true, NestedScrollSource.UserInput, -200f))
    }

    @Test
    fun dragStopCancelAndFlingResetWithoutTriggering() {
        val state = HorizontalEndDragActionState(thresholdPx = 64f)
        state.onDragStart()
        state.onDragDelta(true, true, NestedScrollSource.UserInput, -50f)
        state.onDragEnd()
        assertFalse(state.onDragDelta(true, true, NestedScrollSource.UserInput, -80f))

        state.onDragStart()
        state.onDragDelta(true, true, NestedScrollSource.UserInput, -50f)
        state.onFling()
        assertFalse(state.onDragDelta(true, true, NestedScrollSource.SideEffect, -80f))
        assertEquals(0f, state.accumulatedDistanceForTest(), 0f)
    }

    @Test
    fun nonPointerNestedScrollCannotTriggerEvenDuringDragLifecycle() {
        val state = HorizontalEndDragActionState(thresholdPx = 64f)
        state.onDragStart()

        assertFalse(state.onDragDelta(true, true, NestedScrollSource.SideEffect, -100f))
        assertEquals(0f, state.accumulatedDistanceForTest(), 0f)
        assertEquals(64f, HorizontalEndDragActionThreshold.value, 0f)
    }
}
