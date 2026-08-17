package ink.tenqui.flowtone.data.online.network

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionStreamGatewayTest {
    @Test
    fun `stream request keeps bound identity and core logs`() {
        val logs = mutableListOf<String>()
        var transported: ExtensionStreamRequest? = null
        val limiter = GlobalExtensionNetworkLimiter(maxActiveRequests = 1, maxInFlightRequests = 2)
        val gateway = ExtensionNetworkGateway(
            streamTransport = ExtensionStreamTransport { request, authorize ->
                authorize(request.url)
                transported = request
                ExtensionStreamResponse(
                    statusCode = 206,
                    headers = mapOf("Content-Length" to listOf("8")),
                    body = ByteArrayInputStream(ByteArray(8)),
                    resolvedUrl = request.url
                )
            },
            logger = ExtensionCoreLogger { event, details -> logs += "$event $details" },
            limiter = limiter
        )

        val response = gateway.createStreamClientFor("bound.extension", "media_stream", listOf("media.example.com"))
            .open(
                ExtensionStreamRequest(
                    url = "https://media.example.com/audio?token=SECRET",
                    position = 100L,
                    length = 8L
                )
            )
        assertEquals(1, limiter.snapshot().active)
        assertEquals(1, limiter.snapshot().inFlight)
        response.use { it.body.readBytes() }
        assertEquals(0, limiter.snapshot().active)
        assertEquals(0, limiter.snapshot().inFlight)

        assertEquals("bytes=100-107", transported?.headers?.get("Range"))
        assertTrue(logs.any { it.startsWith("extension.media.open extension=bound.extension") })
        assertTrue(logs.any { it.startsWith("extension.http.response extension=bound.extension") })
        assertTrue(logs.any { it.startsWith("extension.media.close extension=bound.extension") })
        assertFalse(logs.any { "SECRET" in it })
    }

    @Test
    fun `Flowtone filters transport headers and owns Range`() {
        val headers = managedStreamHeaders(
            ExtensionStreamRequest(
                url = "https://media.example.com/audio",
                headers = mapOf(
                    "Host" to "evil.example",
                    "Connection" to "keep-alive",
                    "Range" to "bytes=0-999999",
                    "Referer" to "https://allowed.example"
                ),
                position = 256L,
                length = 32L
            )
        )

        assertFalse(headers.keys.any { it.equals("Host", ignoreCase = true) })
        assertFalse(headers.keys.any { it.equals("Connection", ignoreCase = true) })
        assertEquals("bytes=256-287", headers["Range"])
        assertEquals("https://allowed.example", headers["Referer"])
    }

    @Test
    fun `every streaming redirect is authorized`() {
        val logs = mutableListOf<String>()
        val gateway = ExtensionNetworkGateway(
            streamTransport = ExtensionStreamTransport { request, authorize ->
                authorize(request.url)
                authorize("https://evil.example/audio")
                error("unreachable")
            },
            logger = ExtensionCoreLogger { event, details -> logs += "$event $details" }
        )

        val failure = runCatching {
            gateway.createStreamClientFor(
                "bound.extension",
                "media_stream",
                listOf("media.example.com")
            ).open(ExtensionStreamRequest("https://media.example.com/audio"))
        }.exceptionOrNull()

        assertTrue(failure is SecurityException)
        assertTrue(logs.any { it.startsWith("extension.http.failed extension=bound.extension") })
    }
}

