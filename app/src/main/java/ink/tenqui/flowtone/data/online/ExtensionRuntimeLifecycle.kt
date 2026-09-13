package ink.tenqui.flowtone.data.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

/** 让扩展调用从属于单个 runtime generation，而不是调用方的长期 scope。 */
internal class ExtensionRuntimeLifecycle(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onStaleResult: (requestGeneration: Long, currentGeneration: Long) -> Unit = { _, _ -> }
) : AutoCloseable {
    private val lock = Any()
    private var generation = 0L
    private var runtimeScope: CoroutineScope? = newScope()

    fun currentGeneration(): Long = synchronized(lock) { generation }

    fun beginReload(): Long {
        val previousScope: CoroutineScope?
        val nextGeneration: Long
        synchronized(lock) {
            previousScope = runtimeScope
            runtimeScope = null
            generation += 1L
            nextGeneration = generation
        }
        previousScope?.cancel("Extension runtime generation replaced")
        return nextGeneration
    }

    fun completeReload(reloadGeneration: Long) {
        synchronized(lock) {
            if (generation == reloadGeneration && runtimeScope == null) {
                runtimeScope = newScope()
            }
        }
    }

    suspend fun <T> execute(block: suspend () -> T): T {
        val ticket = synchronized(lock) {
            val scope = runtimeScope
                ?: throw CancellationException("Extension runtime is reloading")
            Ticket(generation, scope)
        }
        val task = ticket.scope.async { block() }
        val result = try {
            task.await()
        } catch (error: CancellationException) {
            task.cancel(error)
            throw error
        }
        val current = currentGeneration()
        if (current != ticket.generation) {
            onStaleResult(ticket.generation, current)
            throw CancellationException(
                "Stale extension runtime result: request=${ticket.generation}, current=$current"
            )
        }
        return result
    }

    override fun close() {
        val scope = synchronized(lock) {
            runtimeScope.also { runtimeScope = null }
        }
        scope?.cancel("Extension runtime lifecycle closed")
    }

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    private data class Ticket(
        val generation: Long,
        val scope: CoroutineScope
    )
}
