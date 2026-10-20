package ink.tenqui.flowtone.data.online.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionNetworkPermissionTest {
    @Test
    fun httpsDefaultPortIsCanonicalized() {
        assertEquals(
            NetworkOrigin.of("https", "example.com"),
            NetworkOrigin.of("https", "example.com", 443)
        )
    }

    @Test
    fun httpDefaultPortIsCanonicalized() {
        assertEquals(
            NetworkOrigin.of("http", "example.com"),
            NetworkOrigin.of("http", "example.com", 80)
        )
    }

    @Test
    fun httpAndHttpsAreDifferentOrigins() {
        assertNotEquals(
            NetworkOrigin.of("https", "example.com"),
            NetworkOrigin.of("http", "example.com")
        )
    }

    @Test
    fun nonDefaultPortIsDifferentFromDefaultPort() {
        assertNotEquals(
            NetworkOrigin.of("https", "example.com"),
            NetworkOrigin.of("https", "example.com", 8443)
        )
    }

    @Test
    fun schemeAndHostAreNormalized() {
        val origin = NetworkOrigin.of(" HTTPS ", " Example.COM. ", 443)

        assertEquals("https://example.com", origin.toString())
        assertEquals(NetworkSecurity.Secure, origin.security)
    }

    @Test
    fun httpIsClassifiedAsInsecure() {
        assertEquals(NetworkSecurity.Insecure, NetworkOrigin.of("http", "example.com").security)
    }

    @Test
    fun v2OriginParserSupportsCustomPortAndWildcardWithoutGrantingRootHost() {
        val permission = NetworkOriginPermissionParser.parse("HTTPS://*.Example.COM:8443")

        assertEquals("https://*.example.com:8443", permission.toString())
        assertEquals(NetworkHostScope.SubdomainsOnly, permission.hostScope)
        assertEquals(8443, permission.origin.effectivePort)
    }

    @Test
    fun v2OriginParserNormalizesDefaultPorts() {
        assertEquals(
            NetworkOriginPermissionParser.parse("https://example.com"),
            NetworkOriginPermissionParser.parse("https://example.com:443")
        )
        assertEquals(
            NetworkOriginPermissionParser.parse("http://example.com"),
            NetworkOriginPermissionParser.parse("http://example.com:80")
        )
    }

    @Test
    fun originPermissionRejectsUnsupportedSchemeAndUserInfo() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkOriginPermissionParser.parse("ftp://example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkOriginPermissionParser.parse("https://user:pass@example.com")
        }
    }

    @Test
    fun originPermissionRejectsPathQueryAndFragment() {
        listOf(
            "https://example.com/path",
            "https://example.com?a=b",
            "https://example.com/#x"
        ).forEach { declaration ->
            assertThrows(IllegalArgumentException::class.java) {
                NetworkOriginPermissionParser.parse(declaration)
            }
        }
    }
}
