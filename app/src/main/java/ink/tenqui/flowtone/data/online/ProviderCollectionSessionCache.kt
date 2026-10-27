package ink.tenqui.flowtone.data.online

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/** 进程内 collection cache；同一 Provider/collection 的并发调用共享一次执行。 */
internal class ProviderCollectionSessionCache<T> {
    private val requests = ConcurrentHashMap<String, CompletableDeferred<List<T>?>>()

    suspend fun getOrLoad(key: String, loader: suspend () -> List<T>?): List<T>? {
        val ours = CompletableDeferred<List<T>?>()
        val existing = requests.putIfAbsent(key, ours)
        if (existing != null) return existing.await()

        return try {
            loader().also(ours::complete)
        } catch (error: Throwable) {
            ours.completeExceptionally(error)
            requests.remove(key, ours)
            throw error
        }
    }

    suspend fun refresh(key: String, loader: suspend () -> List<T>?): List<T>? {
        val fresh = CompletableDeferred<List<T>?>()
        val previous = requests.put(key, fresh)
        try {
            val result = loader()
            fresh.complete(result)
            if (result == null) restore(key, fresh, previous)
            return result
        } catch (error: Throwable) {
            fresh.completeExceptionally(error)
            restore(key, fresh, previous)
            throw error
        }
    }

    private fun restore(
        key: String,
        fresh: CompletableDeferred<List<T>?>,
        previous: CompletableDeferred<List<T>?>?
    ) {
        if (previous == null || previous.isCancelled) requests.remove(key, fresh)
        else requests.replace(key, fresh, previous)
    }

    fun clear(key: String) {
        requests.remove(key)?.cancel()
    }

    fun clearAll() {
        requests.values.forEach(CompletableDeferred<List<T>?>::cancel)
        requests.clear()
    }
}
