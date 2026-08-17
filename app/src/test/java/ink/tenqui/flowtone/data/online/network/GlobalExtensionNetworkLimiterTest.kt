package ink.tenqui.flowtone.data.online.network

import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalExtensionNetworkLimiterTest {
    @Test
    fun activeLimitIsGlobalAndQueuedRequestStartsAfterRelease() = runBlocking {
        val limiter = GlobalExtensionNetworkLimiter(maxActiveRequests = 12, maxInFlightRequests = 128)
        val entered = AtomicInteger()
        val started = AtomicInteger()
        val maxEntered = AtomicInteger()
        val firstTwelve = CompletableDeferred<Unit>()
        val thirteenth = CompletableDeferred<Unit>()
        val release = Channel<Unit>(capacity = 20)
        val gateway = ExtensionNetworkGateway(
            limiter = limiter,
            transport = ExtensionHttpTransport { _, _ ->
                val current = entered.incrementAndGet()
                val startedCount = started.incrementAndGet()
                maxEntered.updateAndGet { maxOf(it, current) }
                if (current == 12) firstTwelve.complete(Unit)
                if (startedCount == 13) thirteenth.complete(Unit)
                release.receive()
                entered.decrementAndGet()
                ExtensionHttpResponse(200, emptyMap(), ByteArray(0))
            },
            logger = silentLogger()
        )
        val a = gateway.createClientFor("test.extension.a", "artist_avatar", listOf("example.invalid"))
        val b = gateway.createClientFor("test.extension.b", "artist_avatar", listOf("example.invalid"))

        val jobs = List(8) { async(Dispatchers.Default, start = CoroutineStart.DEFAULT) { a.execute(request()) } } +
            List(8) { async(Dispatchers.Default, start = CoroutineStart.DEFAULT) { b.execute(request()) } }
        firstTwelve.await()
        assertEquals(12, entered.get())
        assertEquals(12, limiter.snapshot().active)
        assertEquals(12, maxEntered.get())

        release.send(Unit)
        thirteenth.await()
        assertEquals(12, limiter.snapshot().active)
        assertTrue(maxEntered.get() <= 12)

        repeat(15) { release.send(Unit) }
        jobs.awaitAll()
        assertEquals(0, limiter.snapshot().active)
        assertEquals(0, limiter.snapshot().inFlight)
    }

    @Test
    fun inFlightLimitRejectsImmediatelyWithoutTransportWork() = runBlocking {
        val limiter = GlobalExtensionNetworkLimiter(maxActiveRequests = 12, maxInFlightRequests = 128)
        val calls = AtomicInteger()
        val gateway = ExtensionNetworkGateway(
            limiter = limiter,
            transport = ExtensionHttpTransport { _, _ ->
                calls.incrementAndGet()
                ExtensionHttpResponse(200, emptyMap(), ByteArray(0))
            },
            logger = silentLogger()
        )
        val client = gateway.createClientFor("test.extension.a", "artist_avatar", listOf("example.invalid"))
        val admissionClient = client as AdmissionAwareExtensionNetworkClient
        val held = List(128) { requireNotNull(admissionClient.tryAcquireAdmission()) }

        val failure = runCatching { client.execute(request()) }.exceptionOrNull()
        assertTrue(failure is ExtensionNetworkResourceExhaustedException)
        assertEquals(0, calls.get())
        assertEquals(128, limiter.snapshot().inFlight)

        held.forEach { it.close() }
        assertEquals(0, limiter.snapshot().inFlight)
    }

    @Test
    fun successFailureAndCancellationReleaseAllPermits() = runBlocking {
        val limiter = GlobalExtensionNetworkLimiter(maxActiveRequests = 1, maxInFlightRequests = 2)
        val entered = CompletableDeferred<Unit>()
        val neverRelease = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val gateway = ExtensionNetworkGateway(
            limiter = limiter,
            transport = ExtensionHttpTransport { _, _ ->
                when (calls.incrementAndGet()) {
                    1 -> ExtensionHttpResponse(200, emptyMap(), ByteArray(0))
                    2 -> throw SocketTimeoutException("timeout")
                    else -> {
                        entered.complete(Unit)
                        neverRelease.await()
                        error("unreachable")
                    }
                }
            },
            logger = silentLogger()
        )
        val client = gateway.createClientFor("test.extension.a", "artist_avatar", listOf("example.invalid"))

        client.execute(request())
        assertEquals(0, limiter.snapshot().inFlight)
        runCatching { client.execute(request()) }
        assertEquals(0, limiter.snapshot().inFlight)

        val cancelled = async(Dispatchers.Default) { client.execute(request()) }
        entered.await()
        cancelled.cancelAndJoin()
        withTimeout(1_000) {
            while (limiter.snapshot().inFlight != 0) kotlinx.coroutines.yield()
        }
        assertEquals(0, limiter.snapshot().active)
        assertEquals(0, limiter.snapshot().inFlight)
    }

    private fun request() = ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.invalid/resource")

    private fun silentLogger() = ExtensionCoreLogger { _, _ -> }
}
