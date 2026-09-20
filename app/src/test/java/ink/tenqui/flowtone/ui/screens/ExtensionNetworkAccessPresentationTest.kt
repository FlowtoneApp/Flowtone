package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermissionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
