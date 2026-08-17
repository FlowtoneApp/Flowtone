package ink.tenqui.flowtone.data.online.network

import java.io.IOException
import java.util.concurrent.Semaphore

/** 扩展网络出口的全局资源保险丝；不包含任何 Provider 业务限流策略。 */
class GlobalExtensionNetworkLimiter(
    val maxActiveRequests: Int = GlobalMaxActiveRequests,
    val maxInFlightRequests: Int = GlobalMaxInFlightRequests
) {
    private val activePermits = Semaphore(maxActiveRequests, true)
    private var active = 0
    private var inFlight = 0

    init {
        require(maxActiveRequests > 0)
        require(maxInFlightRequests >= maxActiveRequests)
    }

    @Synchronized
    internal fun tryAcquireInFlight(): Admission? {
        if (inFlight >= maxInFlightRequests) return null
        inFlight += 1
        return Admission(this)
    }

    internal fun acquireActive(admission: Admission): Boolean {
        val queued = !activePermits.tryAcquire()
        if (queued) activePermits.acquire()
        synchronized(this) {
            admission.markActive()
            active += 1
        }
        return queued
    }

    internal fun release(admission: Admission) {
        synchronized(this) {
            if (admission.markClosed()) return
            if (admission.wasActive()) {
                active -= 1
                activePermits.release()
            }
            inFlight -= 1
        }
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(active = active, inFlight = inFlight)

    internal class Admission internal constructor(
        private val limiter: GlobalExtensionNetworkLimiter
    ) : AutoCloseable {
        private var active = false
        private var closed = false

        fun acquireActive(): Boolean = limiter.acquireActive(this)

        override fun close() = limiter.release(this)

        internal fun markActive() {
            check(!closed) { "网络 admission 已释放" }
            active = true
        }

        internal fun wasActive(): Boolean = active

        internal fun markClosed(): Boolean {
            if (closed) return true
            closed = true
            return false
        }
    }

    data class Snapshot(val active: Int, val inFlight: Int)

    companion object {
        const val GlobalMaxActiveRequests = 12
        const val GlobalMaxInFlightRequests = 128
    }
}

/** Flowtone Host 拒绝接收更多扩展网络工作时的明确失败类型。 */
class ExtensionNetworkResourceExhaustedException : IOException("RESOURCE_EXHAUSTED")

/** 仅供 Host bridge 在创建请求专属 coroutine 前完成 admission。 */
internal interface AdmissionAwareExtensionNetworkClient : ExtensionNetworkClient {
    fun tryAcquireAdmission(): GlobalExtensionNetworkLimiter.Admission?

    suspend fun executeAdmitted(
        request: ExtensionHttpRequest,
        admission: GlobalExtensionNetworkLimiter.Admission
    ): ExtensionHttpResponse
}
