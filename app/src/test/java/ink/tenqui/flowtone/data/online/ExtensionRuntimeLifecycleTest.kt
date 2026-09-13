package ink.tenqui.flowtone.data.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExtensionRuntimeLifecycleTest {
    @Test
    fun `reload increments generation and remains repeatable`() {
        val lifecycle = ExtensionRuntimeLifecycle()

        val first = lifecycle.beginReload().also(lifecycle::completeReload)
        val second = lifecycle.beginReload().also(lifecycle::completeReload)

        assertEquals(1L, first)
        assertEquals(2L, second)
        assertEquals(second, lifecycle.currentGeneration())
        lifecycle.close()
    }

    @Test
    fun `calls after reload resolve the new runtime instance`() = runBlocking {
        val lifecycle = ExtensionRuntimeLifecycle()
        var currentProvider = Any()
        val oldProvider = lifecycle.execute { currentProvider }

        val generation = lifecycle.beginReload()
        currentProvider = Any()
        lifecycle.completeReload(generation)
        val newProvider = lifecycle.execute { currentProvider }

        assertNotSame(oldProvider, newProvider)
        lifecycle.close()
    }

    @Test
    fun `old generation task cannot return after reload`() = runBlocking {
        val lifecycle = ExtensionRuntimeLifecycle()
        val started = CompletableDeferred<Unit>()
        val releaseOldResult = CompletableDeferred<String>()
        val oldRequest = async {
            lifecycle.execute {
                started.complete(Unit)
                releaseOldResult.await()
            }
        }
        started.await()

        val generation = lifecycle.beginReload()
        lifecycle.completeReload(generation)
        releaseOldResult.complete("old")

        try {
            oldRequest.await()
            fail("旧 generation 不应返回结果")
        } catch (_: CancellationException) {
            assertTrue(oldRequest.isCancelled)
        }
        lifecycle.close()
    }

    @Test
    fun `calls are rejected while reload is incomplete`() = runBlocking {
        val lifecycle = ExtensionRuntimeLifecycle()
        val generation = lifecycle.beginReload()

        try {
            lifecycle.execute { "unreachable" }
            fail("reload 期间不应接受扩展调用")
        } catch (_: CancellationException) {
            // Expected: no old or partially loaded runtime may accept work.
        }

        lifecycle.completeReload(generation)
        assertEquals("current", lifecycle.execute { "current" })
        lifecycle.close()
    }
}
