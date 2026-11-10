package ink.tenqui.flowtone.data.online.network

import com.sun.net.httpserver.HttpServer
import ink.tenqui.flowtone.data.online.credential.CommittedCredentialHeaders
import ink.tenqui.flowtone.data.online.credential.CredentialResolutionError
import ink.tenqui.flowtone.data.online.credential.CredentialResolutionException
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermissionParser
import java.net.InetSocketAddress
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CredentialHttpGatewayTest {
    private val permissions = setOf(NetworkOriginPermissionParser.parse("https://example.com"))

    @Test fun credentialMethodsHeadersAndRedirectUseLocalTransport() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val seen = mutableListOf<Triple<String, String?, String?>>()
        server.createContext("/") { exchange ->
            seen += Triple(exchange.requestMethod, exchange.requestHeaders.getFirst("Cookie"), exchange.requestHeaders.getFirst("Authorization"))
            exchange.responseHeaders.add("Location", "/target")
            val body = "denied".toByteArray()
            exchange.sendResponseHeaders(if (exchange.requestURI.path == "/redirect") 302 else 403, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val logs = mutableListOf<String>()
        val actual = OkHttpExtensionTransport()
        val gateway = ExtensionNetworkGateway(
            transport = ExtensionHttpTransport { request, _ ->
                actual.execute(request.copy(url = "http://127.0.0.1:${server.address.port}" + java.net.URI(request.url).path), {})
            }, logger = ExtensionCoreLogger { event, detail -> logs += "$event $detail" }
        )
        var last: CommittedCredentialHeaders? = null
        val client = gateway.createCredentialClientFor("test.extension", "host", permissions) { admission, _, _ ->
            admission.requireActive()
            CommittedCredentialHeaders(mutableMapOf("Cookie" to "a=first; b=second", "Authorization" to "Bearer test-secret")).also { last = it }
        }
        try {
            for (method in listOf(ExtensionHttpMethod.Get, ExtensionHttpMethod.Post, ExtensionHttpMethod.Patch)) {
                val response = client.execute(ExtensionHttpRequest(method, "https://example.com/redirect", credentialRequestId = "session"))
                assertEquals(302, response.statusCode)
                assertTrue(last!!.forTransport().isEmpty())
            }
            assertEquals(listOf("GET", "POST", "PATCH"), seen.map { it.first })
            assertTrue(seen.all { it.second == "a=first; b=second" && it.third == "Bearer test-secret" })
            val error = client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/error", credentialRequestId = "session"))
            assertEquals(403, error.statusCode)
            assertEquals("denied", error.body.decodeToString())
            assertFalse(logs.joinToString().contains("test-secret"))
        } finally { server.stop(0) }
    }

    @Test fun conflictsAndResolverErrorsNeverSendAndExceptionsAreSafe() = runBlocking {
        val calls = AtomicInteger(); val resolutions = AtomicInteger(); var last: CommittedCredentialHeaders? = null
        val limiter = GlobalExtensionNetworkLimiter(1, 4)
        val gateway = ExtensionNetworkGateway(limiter = limiter, logger = ExtensionCoreLogger { _, _ -> }, transport = ExtensionHttpTransport { _, _ ->
            calls.incrementAndGet(); throw IOException("test-secret")
        })
        val client = gateway.createCredentialClientFor("test", "host", permissions) { _, id, _ ->
            resolutions.incrementAndGet()
            if (id == "denied") throw CredentialResolutionException(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
            CommittedCredentialHeaders(mutableMapOf("Authorization" to "Bearer test-secret")).also { last = it }
        }
        for (name in listOf("authorization", "CoOkIe", "PROXY-AUTHORIZATION", "hOsT", "Content-Length")) {
            assertTrue(runCatching { client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/", mapOf(name to "test-secret"), credentialRequestId = "ok")) }.isFailure)
        }
        assertEquals(0, resolutions.get())
        val denied = runCatching { client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/", credentialRequestId = "denied")) }.exceptionOrNull()
        assertEquals(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED, (denied as CredentialResolutionException).code)
        assertEquals(0, calls.get())
        val failure = runCatching { client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/", credentialRequestId = "ok")) }.exceptionOrNull()!!
        assertFalse(failure.toString().contains("test-secret"))
        assertTrue(last!!.forTransport().isEmpty())
        assertEquals(0, limiter.snapshot().inFlight)
    }

    @Test fun queuedCancellationReleasesAdmissionAndNeverResolves() = runBlocking {
        val limiter = GlobalExtensionNetworkLimiter(1, 4)
        val held = limiter.tryAcquireInFlight()!!.also { it.acquireActive() }
        val calls = AtomicInteger()
        val gateway = ExtensionNetworkGateway(limiter = limiter, logger = ExtensionCoreLogger { _, _ -> }, transport = ExtensionHttpTransport { _, _ -> error("unexpected") })
        val client = gateway.createCredentialClientFor("test", "host", permissions) { _, _, _ ->
            calls.incrementAndGet(); CommittedCredentialHeaders(mutableMapOf())
        }
        val task = async(Dispatchers.Default) { client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/", credentialRequestId = "ok")) }
        withTimeout(5000) { while (limiter.snapshot().inFlight != 2) yield() }
        task.cancelAndJoin()
        assertEquals(1, limiter.snapshot().inFlight)
        assertEquals(0, calls.get())
        held.close()
        assertEquals(0, limiter.snapshot().inFlight)
    }

    @Test fun queuedRevocationIsObservedAfterActiveAdmission() = runBlocking {
        val limiter = GlobalExtensionNetworkLimiter(1, 4)
        val held = limiter.tryAcquireInFlight()!!.also { it.acquireActive() }
        val revoked = AtomicBoolean(false)
        val sent = AtomicInteger()
        val gateway = ExtensionNetworkGateway(limiter = limiter, logger = ExtensionCoreLogger { _, _ -> },
            transport = ExtensionHttpTransport { _, _ -> sent.incrementAndGet(); ExtensionHttpResponse(200, emptyMap(), ByteArray(0)) })
        val client = gateway.createCredentialClientFor("test", "host", permissions) { admission, _, _ ->
            admission.requireActive()
            if (revoked.get()) throw CredentialResolutionException(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
            CommittedCredentialHeaders(mutableMapOf())
        }
        val task = async(Dispatchers.Default) { runCatching { client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/", credentialRequestId = "ok")) } }
        withTimeout(5000) { while (limiter.snapshot().inFlight != 2) yield() }
        revoked.set(true)
        held.close()
        val failure = task.await().exceptionOrNull() as CredentialResolutionException
        assertEquals(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED, failure.code)
        assertEquals(0, sent.get())
        assertEquals(0, limiter.snapshot().inFlight)
    }

    @Test fun cancellationDuringTransportClosesCredentials() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val limiter = GlobalExtensionNetworkLimiter(1, 4)
        lateinit var headers: CommittedCredentialHeaders
        val gateway = ExtensionNetworkGateway(limiter = limiter, logger = ExtensionCoreLogger { _, _ -> },
            transport = ExtensionHttpTransport { _, _ -> entered.complete(Unit); awaitCancellation() })
        val client = gateway.createCredentialClientFor("test", "host", permissions) { _, _, _ ->
            CommittedCredentialHeaders(mutableMapOf("Cookie" to "secret")).also { headers = it }
        }
        val task = async(Dispatchers.Default) { client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/", credentialRequestId = "ok")) }
        entered.await()
        task.cancelAndJoin()
        assertTrue(headers.forTransport().isEmpty())
        assertEquals(0, limiter.snapshot().inFlight)
    }

    @Test fun credentialTransportDoesNotRetryDisconnectedRequest() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = AtomicInteger()
        server.createContext("/") { exchange -> calls.incrementAndGet(); exchange.close() }
        server.start()
        try {
            val result = runCatching { OkHttpExtensionTransport().execute(
                ExtensionHttpRequest(ExtensionHttpMethod.Get, "http://127.0.0.1:${server.address.port}/", credentialRequestId = "ok"), {}
            ) }
            assertTrue(result.isFailure)
            assertEquals(1, calls.get())
        } finally { server.stop(0) }
    }
}
