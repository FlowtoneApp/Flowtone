package ink.tenqui.flowtone.data.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCollectionSessionCacheTest {
    @Test
    fun refreshDoesNotCancelExistingRequestAndKeepsNewestResult() = runBlocking {
        val cache = ProviderCollectionSessionCache<String>()
        val release = CompletableDeferred<Unit>()
        val old = async { cache.getOrLoad("provider") { release.await(); listOf("old") } }
        yield()
        val shared = async { cache.getOrLoad("provider") { error("must share") } }
        yield()
        assertEquals(listOf("new"), cache.refresh("provider") { listOf("new") })
        release.complete(Unit)
        assertEquals(listOf("old"), old.await())
        assertEquals(listOf("old"), shared.await())
        assertEquals(listOf("new"), cache.getOrLoad("provider") { error("must use refreshed cache") })
    }

    @Test
    fun failedRefreshRetainsPreviousResult() = runBlocking {
        val cache = ProviderCollectionSessionCache<String>()
        cache.getOrLoad("provider") { listOf("old") }
        try {
            cache.refresh("provider") { throw IllegalStateException("failure") }
        } catch (_: IllegalStateException) {
        }
        assertEquals(listOf("old"), cache.getOrLoad("provider") { error("must retain cache") })
        assertEquals(null, cache.refresh("provider") { null })
        assertEquals(listOf("old"), cache.getOrLoad("provider") { error("must retain cache") })
    }

    @Test
    fun concurrentAndRepeatedLoadsShareOneExecution() = runBlocking {
        val cache = ProviderCollectionSessionCache<String>()
        val release = CompletableDeferred<Unit>()
        var executions = 0
        val first = async {
            cache.getOrLoad("provider") {
                executions += 1
                release.await()
                listOf("song")
            }
        }
        yield()
        val second = async {
            cache.getOrLoad("provider") {
                executions += 1
                listOf("duplicate")
            }
        }

        release.complete(Unit)

        assertEquals(listOf("song"), first.await())
        assertEquals(listOf("song"), second.await())
        assertEquals(listOf("song"), cache.getOrLoad("provider") { listOf("later") })
        assertEquals(1, executions)
    }
}
