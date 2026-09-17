package ink.tenqui.flowtone.data.online.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun legacyWildcardKeepsSubdomainsOnlyScopeAndCurrentHttpsRuntimeMeaning() {
        val permission = LegacyNetworkPermissionCanonicalizer
            .canonicalize(listOf("*.Example.COM"))
            .single()

        assertEquals(NetworkScheme.Https, permission.origin.scheme)
        assertEquals("example.com", permission.origin.host)
        assertEquals(NetworkHostScope.SubdomainsOnly, permission.hostScope)
        assertEquals("https://*.example.com", permission.toString())
    }

    @Test
    fun duplicateCanonicalLegacyHostsCollapseToOnePermission() {
        val permissions = LegacyNetworkPermissionCanonicalizer.canonicalize(
            listOf("example.com", "EXAMPLE.COM.")
        )

        assertTrue(permissions.size == 1)
    }
}
