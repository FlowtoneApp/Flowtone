package ink.tenqui.flowtone.data.online.network

import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermissionParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionNetworkGatewayTest {
    @Test
    fun `核心网络日志由网关生成且使用绑定的扩展身份`() = kotlinx.coroutines.runBlocking {
        val logs = mutableListOf<String>()
        val gateway = ExtensionNetworkGateway(
            transport = ExtensionHttpTransport { _, _ ->
                ExtensionHttpResponse(200, emptyMap(), "ok".encodeToByteArray())
            },
            logger = ExtensionCoreLogger { event, details -> logs += "$event $details" }
        )

        gateway.createClientFor("flowtone-bound-id", "host_api", permissions("https://example.com")).execute(
            ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/avatar?artist=Aimer")
        )

        assertTrue(logs.any { it.startsWith("extension.http.prepared extension=flowtone-bound-id") })
        assertTrue(logs.any { it.startsWith("extension.http.started extension=flowtone-bound-id") })
        assertTrue(logs.any { it.startsWith("extension.http.response extension=flowtone-bound-id") })
    }

    @Test
    fun `请求失败仍由核心记录失败日志`() = kotlinx.coroutines.runBlocking {
        val logs = mutableListOf<String>()
        val gateway = ExtensionNetworkGateway(
            transport = ExtensionHttpTransport { _, _ -> throw java.net.SocketTimeoutException("timeout") },
            logger = ExtensionCoreLogger { event, details -> logs += "$event $details" }
        )

        runCatching {
            gateway.createClientFor("test-extension", "host_api", permissions("https://example.com")).execute(
                ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com")
            )
        }

        assertTrue(logs.any { it.startsWith("extension.http.failed extension=test-extension") })
    }

    @Test
    fun `敏感查询参数会被统一脱敏`() {
        val details = requestLogDetails(
            ExtensionHttpRequest(
                ExtensionHttpMethod.Get,
                "https://example.com/api?api_key=SECRET&artist=Aimer&token=TOKEN"
            )
        )

        assertTrue(details.contains("api_key=****"))
        assertTrue(details.contains("token=****"))
        assertFalse(details.contains("SECRET"))
        assertFalse(details.contains("TOKEN"))
    }

    @Test
    fun redirectToUnapprovedPortIsRejected() = kotlinx.coroutines.runBlocking {
        val gateway = ExtensionNetworkGateway(
            transport = ExtensionHttpTransport { _, authorize ->
                authorize("https://example.com:8443/redirected")
                error("unreachable")
            },
            logger = ExtensionCoreLogger { _, _ -> }
        )
        val client = gateway.createClientFor(
            "test-extension",
            "host_api",
            permissions("https://example.com")
        )

        val error = runCatching {
            client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/start"))
        }.exceptionOrNull()

        assertTrue(error.toString(), error is SecurityException)
    }

    @Test
    fun httpsRedirectToDeclaredHttpOriginIsRejectedAsUnsupportedCleartext() =
        kotlinx.coroutines.runBlocking {
            val gateway = ExtensionNetworkGateway(
                transport = ExtensionHttpTransport { _, authorize ->
                    authorize("http://example.com/redirected")
                    error("unreachable")
                },
                logger = ExtensionCoreLogger { _, _ -> }
            )
            val client = gateway.createClientFor(
                "test-extension",
                "host_api",
                permissions("https://example.com", "http://example.com")
            )

            val error = runCatching {
                client.execute(ExtensionHttpRequest(ExtensionHttpMethod.Get, "https://example.com/start"))
            }.exceptionOrNull()

            assertTrue(error.toString(), error is SecurityException)
            assertTrue(error?.message.orEmpty().contains("cleartext"))
        }

    private fun permissions(vararg origins: String) =
        origins.mapTo(linkedSetOf(), NetworkOriginPermissionParser::parse)
}
