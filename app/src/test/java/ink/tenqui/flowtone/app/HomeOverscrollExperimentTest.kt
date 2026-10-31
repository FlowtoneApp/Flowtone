package ink.tenqui.flowtone.app

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOverscrollExperimentTest {
    @Test
    fun delegatesRenderingAndScrollWhileHidingProgress() = runBlocking {
        val original = RecordingOverscroll()
        val effect = HomeOverscrollExperiment(original)
        assertTrue(original.isInProgress)
        assertFalse(effect.isInProgress)
        assertSame(original.node, effect.node)

        val delta = Offset(0f, 12f)
        val source = NestedScrollSource.UserInput
        assertEquals(delta, effect.applyToScroll(delta, source) { it })
        assertEquals(delta, original.scrollDelta)
        assertEquals(source, original.scrollSource)
        assertEquals(1, original.scrollCalls)

        val velocity = Velocity(0f, 30f)
        var receivedVelocity = Velocity.Zero
        effect.applyToFling(velocity) { receivedVelocity = it; it }
        assertEquals(velocity, original.flingVelocity)
        assertEquals(velocity, receivedVelocity)
        assertEquals(1, original.flingCalls)
    }

    @Test
    fun baselineModeReportsOriginalProgress() {
        val original = RecordingOverscroll()
        assertTrue(HomeOverscrollExperiment(original, hideProgress = false).isInProgress)
    }

    private class RecordingOverscroll : OverscrollEffect {
        override val node = object : Modifier.Node() {}
        override val isInProgress = true
        var scrollDelta = Offset.Zero
        var scrollSource: NestedScrollSource? = null
        var scrollCalls = 0
        var flingVelocity = Velocity.Zero
        var flingCalls = 0

        override fun applyToScroll(
            delta: Offset,
            source: NestedScrollSource,
            performScroll: (Offset) -> Offset
        ): Offset {
            scrollCalls++
            scrollDelta = delta
            scrollSource = source
            return performScroll(delta)
        }

        override suspend fun applyToFling(
            velocity: Velocity,
            performFling: suspend (Velocity) -> Velocity
        ) {
            flingCalls++
            flingVelocity = velocity
            performFling(velocity)
        }
    }
}
