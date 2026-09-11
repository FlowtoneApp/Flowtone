package ink.tenqui.flowtone.data.online

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCollectionSessionCacheTest {
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
