package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermissionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ExtensionNetworkAccessPresentationTest {
    @Test
    fun separatesHttpsAndHttpOriginsUsingCanonicalDisplayValues() {
        val presentation = extensionNetworkAccessPresentation(
            setOf(
                NetworkOriginPermissionParser.parse("http://192.168.1.20:8080"),
                NetworkOriginPermissionParser.parse("https://*.sndcdn.com"),
                NetworkOriginPermissionParser.parse("https://soundcloud.com")
            )
        )

        assertEquals(
            listOf("https://*.sndcdn.com", "https://soundcloud.com"),
            presentation.httpsOrigins.map(Any::toString)
        )
        assertEquals(
            listOf("http://192.168.1.20:8080"),
            presentation.httpOrigins.map(Any::toString)
        )
    }

    @Test
    fun omitsHttpGroupWhenThereAreNoHttpOrigins() {
        val presentation = extensionNetworkAccessPresentation(
            setOf(NetworkOriginPermissionParser.parse("https://soundcloud.com"))
        )

        assertTrue(presentation.httpOrigins.isEmpty())
    }

    @Test
    fun omitsHttpsGroupWhenThereAreNoHttpsOrigins() {
        val presentation = extensionNetworkAccessPresentation(
            setOf(NetworkOriginPermissionParser.parse("http://example.com"))
        )

        assertTrue(presentation.httpsOrigins.isEmpty())
    }

    @Test
    fun httpDeclarationIsShownButMarkedUnavailableInCurrentRuntime() {
        val rule = extensionNetworkRulePresentation(
            NetworkOriginPermissionParser.parse("http://example.com:8080")
        )

        assertEquals("http://example.com:8080", rule.origin)
        assertFalse(rule.runtimeSchemeSupported)
        assertEquals("当前运行时不支持 HTTP", rule.runtimeStatus)
        assertTrue(rule.scope.contains("端口 8080"))
    }

    @Test
    fun httpsRuleRetainsFullWildcardHostAndPort() {
        val host = "very-long-subdomain-name-for-provider.example.com"
        val rule = extensionNetworkRulePresentation(
            NetworkOriginPermissionParser.parse("https://*.$host:8443")
        )

        assertEquals("https://*.$host:8443", rule.origin)
        assertTrue(rule.runtimeSchemeSupported)
        assertTrue(rule.scope.contains("不包含主域名"))
        assertTrue(rule.scope.contains("端口 8443"))
        assertEquals("HTTPS 可按规则请求", rule.runtimeStatus)
    }

    @Test
    fun exactHostScopeDoesNotImplyWildcardAccess() {
        val rule = extensionNetworkRulePresentation(
            NetworkOriginPermissionParser.parse("https://example.com")
        )

        assertEquals("https://example.com", rule.origin)
        assertTrue(rule.scope.contains("仅匹配 example.com"))
        assertFalse(rule.scope.contains("子域名"))
    }
}
