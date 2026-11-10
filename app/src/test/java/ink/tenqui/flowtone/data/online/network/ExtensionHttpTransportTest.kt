package ink.tenqui.flowtone.data.online.network

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExtensionHttpTransportTest {
    @Test fun methodsBodiesErrorsAndRedirects() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val seen = mutableListOf<Pair<String, String>>()
        server.createContext("/") { exchange ->
            seen += exchange.requestMethod to exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val path = exchange.requestURI.path
            val status = when (path) { "/redirect" -> 307; "/error" -> 403; else -> 200 }
            if (path == "/redirect") exchange.responseHeaders.add("Location", "/target")
            val body = if (path == "/error") "denied".toByteArray() else "ok".toByteArray()
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val transport = OkHttpExtensionTransport()
            transport.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, base), {})
            val json = "{\"title\":\"音乐\"}"
            for (method in listOf(ExtensionHttpMethod.Post, ExtensionHttpMethod.Put, ExtensionHttpMethod.Patch, ExtensionHttpMethod.Delete)) {
                transport.execute(ExtensionHttpRequest(method, base, mapOf("Content-Type" to "application/json"), json.toByteArray()), {})
            }
            assertEquals(listOf("GET", "POST", "PUT", "PATCH", "DELETE"), seen.map { it.first })
            assertTrue(seen.drop(1).all { it.second == json })
            val error = transport.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "$base/error"), {})
            assertEquals(403, error.statusCode)
            assertEquals("denied", error.body.decodeToString())
            val redirect = transport.execute(ExtensionHttpRequest(ExtensionHttpMethod.Post, "$base/redirect", body = "x".toByteArray()), {})
            assertEquals(307, redirect.statusCode)
            assertEquals(7, seen.size)
        } finally { server.stop(0) }
    }

    @Test fun responseAndRequestLimits() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = ByteArray(2 * 1024 * 1024 + 1)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/"
            val transport = OkHttpExtensionTransport()
            assertTrue(runCatching { transport.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, url), {}) }.isFailure)
            assertTrue(runCatching { transport.execute(ExtensionHttpRequest(ExtensionHttpMethod.Post, url, body = ByteArray(MaxExtensionRequestBytes + 1)), {}) }.isFailure)
        } finally { server.stop(0) }
    }

    @Test fun crossOriginRedirectDropsHeaders() = runBlocking {
        val target = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var authorization: String? = null
        target.createContext("/") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(200, -1); exchange.close()
        }
        target.start()
        val source = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        source.createContext("/") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        source.start()
        try {
            OkHttpExtensionTransport().execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "http://127.0.0.1:${source.address.port}/", mapOf("Authorization" to "secret")), {})
            assertNull(authorization)
        } finally { source.stop(0); target.stop(0) }
    }
}
