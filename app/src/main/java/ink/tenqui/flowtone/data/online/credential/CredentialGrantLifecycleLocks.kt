package ink.tenqui.flowtone.data.online.credential

/** Coordinates the short file-store transactions that cross grant, source, and install lifecycles. */
internal object CredentialGrantLifecycleLocks {
    private val locks = Array(64) { Any() }

    fun <T> withExtension(extensionId: String, block: () -> T): T =
        synchronized(lock("extension:$extensionId"), block)

    fun <T> withSource(sourceId: String, block: () -> T): T =
        synchronized(lock("source:$sourceId"), block)

    fun <T> withGrantBinding(extensionId: String, sourceId: String, block: () -> T): T {
        val extensionLock = lock("extension:$extensionId")
        val sourceLock = lock("source:$sourceId")
        if (extensionLock === sourceLock) return synchronized(extensionLock, block)
        val first = if (lockIndex("extension:$extensionId") < lockIndex("source:$sourceId")) {
            extensionLock
        } else {
            sourceLock
        }
        val second = if (first === extensionLock) sourceLock else extensionLock
        return synchronized(first) { synchronized(second, block) }
    }

    private fun lock(key: String): Any = locks[lockIndex(key)]

    private fun lockIndex(key: String): Int = (key.hashCode() and Int.MAX_VALUE) % locks.size
}
