package ink.tenqui.flowtone.data.online.runtime

import ink.tenqui.flowtone.data.online.network.ExtensionHttpMethod
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExtensionHttpRequestParsingTest {
    @Test fun acceptsTextAndDefaultsToGet() {
        assertEquals(ExtensionHttpMethod.Get, parseExtensionHttpRequest(JSONObject().put("url", "https://example.com")).method)
        val request = parseExtensionHttpRequest(JSONObject().put("url", "https://example.com").put("method", "POST").put("body", "音乐").put("headers", JSONObject().put("Content-Type", "application/json")))
        assertEquals("音乐", request.body!!.decodeToString())
    }
    @Test fun rejectsInvalidFields() {
        val cases = listOf(
            JSONObject().put("url", "https://example.com").put("method", "TRACE"),
            JSONObject().put("url", 1),
            JSONObject().put("url", "https://example.com").put("headers", "bad"),
            JSONObject().put("url", "https://example.com").put("headers", JSONObject().put("X-Test", 1)),
            JSONObject().put("url", "https://example.com").put("method", "POST").put("body", JSONObject()),
            JSONObject().put("url", "https://example.com").put("method", "POST").put("body", "音".repeat(90000)),
            JSONObject().put("url", "https://example.com").put("body", "x")
        )
        cases.forEach { assertTrue(runCatching { parseExtensionHttpRequest(it) }.isFailure) }
    }
}
