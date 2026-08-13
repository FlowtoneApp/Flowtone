package ink.tenqui.flowtone.data.online.packageformat

import org.junit.Assert.assertThrows
import org.junit.Test

class ExtensionHostPolicyTest {
    private val policy = ExtensionHostPolicy(listOf("api.example.com", "*.cdn.example.net"))

    @Test fun `精确和子域 host 被允许`() {
        policy.requireAllowed("https://api.example.com/api")
        policy.requireAllowed("https://p1.cdn.example.net/a")
        policy.requireAllowed("https://p9.cdn.example.net/a")
    }

    @Test fun `伪造后缀和本机地址被拒绝`() {
        listOf(
            "https://cdn.example.net.evil.com/a",
            "https://evil.com/a",
            "https://127.0.0.1/a",
            "http://api.example.com/a"
        ).forEach { url -> assertThrows(SecurityException::class.java) { policy.requireAllowed(url) } }
    }
}
