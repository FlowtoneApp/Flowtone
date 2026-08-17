package ink.tenqui.flowtone.data.online.network

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

        gateway.createClientFor("flowtone-bound-id", "artist_avatar", listOf("example.com")).execute(
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
            gateway.createClientFor("test-extension", "artist_avatar", listOf("example.com")).execute(
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
}
