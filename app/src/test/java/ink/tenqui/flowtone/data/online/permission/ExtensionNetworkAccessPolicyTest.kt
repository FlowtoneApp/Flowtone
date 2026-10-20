package ink.tenqui.flowtone.data.online.permission

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionNetworkAccessPolicyTest {
    @Test
    fun defaultHttpsPermissionAllowsOnlyHttps443() {
        val policy = policy("https://example.com")

        policy.requireAllowed("https://example.com/path")
        policy.requireAllowed("https://example.com:443/path")
        assertThrows(SecurityException::class.java) {
            policy.requireAllowed("http://example.com/path")
        }
        assertThrows(SecurityException::class.java) {
            policy.requireAllowed("https://example.com:8443/path")
        }
    }

    @Test
    fun customPortPermissionDoesNotGrantDefaultPort() {
        val policy = policy("https://example.com:8443")

        policy.requireAllowed("https://example.com:8443/path")
        assertThrows(SecurityException::class.java) {
            policy.requireAllowed("https://example.com/path")
        }
    }

    @Test
    fun wildcardAllowsOnlyRealSubdomains() {
        val policy = policy("https://*.example.com")

        policy.requireAllowed("https://api.example.com/path")
        assertThrows(SecurityException::class.java) {
            policy.requireAllowed("https://example.com/path")
        }
        assertThrows(SecurityException::class.java) {
            policy.requireAllowed("https://evil-example.com/path")
        }
    }

    @Test
    fun declaredHttpOriginIsStillRejectedByRuntimeCleartextPolicy() {
        val error = assertThrows(SecurityException::class.java) {
            policy("http://example.com").requireAllowed("http://example.com/path")
        }

        assertTrue(error.message.orEmpty().contains("cleartext"))
    }

    @Test
    fun localAndExplicitPrivateAddressesRemainBlocked() {
        listOf("https://localhost", "https://127.0.0.1").forEach { origin ->
            assertThrows(SecurityException::class.java) {
                policy(origin).requireAllowed(origin)
            }
        }
    }

    private fun policy(vararg origins: String) = ExtensionNetworkAccessPolicy(
        origins.mapTo(linkedSetOf(), NetworkOriginPermissionParser::parse)
    )
}
