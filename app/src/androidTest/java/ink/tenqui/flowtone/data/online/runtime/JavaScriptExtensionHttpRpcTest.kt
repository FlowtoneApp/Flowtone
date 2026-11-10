package ink.tenqui.flowtone.data.online.runtime

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.data.online.network.ExtensionHttpMethod
import ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest
import ink.tenqui.flowtone.data.online.network.ExtensionHttpResponse
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JavaScriptExtensionHttpRpcTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var host: JavaScriptSandboxHost
    private lateinit var root: File
    private lateinit var runtime: JavaScriptExtensionRuntime
    private val requests = Collections.synchronizedList(mutableListOf<ExtensionHttpRequest>())

    @Before fun setUp() = runBlocking {
        assumeTrue(JavaScriptSandbox.isSupported())
        host = JavaScriptSandboxHost(context)
        root = Files.createTempDirectory(context.cacheDir.toPath(), "js-http-rpc").toFile()
        root.resolve("main.js").writeText(
            """
            globalThis.flowtoneExtension = {
              async request(options) {
                try {
                  const response = await flowtone.http.request(options);
                  return {response};
                } catch (error) {
                  return {error: error.message};
                }
              }
            };
            """.trimIndent()
        )
        val descriptor = ExtensionManifestParser.parseNormalized(
            """{"formatVersion":2,"id":"example.http","name":"Example","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"permissions":{"network":{"origins":["https://example.com"]}}}"""
        )
        val network = object : ExtensionNetworkClient {
            override suspend fun execute(request: ExtensionHttpRequest): ExtensionHttpResponse {
                requests.add(request)
                return ExtensionHttpResponse(403, mapOf("Content-Type" to listOf("application/json")), "拒绝访问".toByteArray(Charsets.UTF_8))
            }
        }
        runtime = JavaScriptExtensionRuntime(
            InstalledExtension(descriptor, root, true),
            requireNotNull(host.createIsolate()),
            network,
            ExtensionPrivateCache(root.resolve("cache"))
        )
        runtime.start()
    }

    @After fun tearDown() {
        if (::runtime.isInitialized) runtime.close()
        if (::host.isInitialized) host.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test fun postSendsUtf8JsonAndReturnsNonSuccessResponse() = runBlocking {
        val json = """{"title":"音乐"}"""
        val options = JSONObject().put("url", "https://example.com/api?item=1")
            .put("method", "POST")
            .put("headers", JSONObject().put("Content-Type", "application/json").put("X-Client", "flowtone"))
            .put("body", json)

        val response = runtime.invokeObject("request", options).getJSONObject("response")
        val request = requests.single()
        assertEquals(ExtensionHttpMethod.Post, request.method)
        assertEquals("https://example.com/api?item=1", request.url)
        assertEquals(mapOf("Content-Type" to "application/json", "X-Client" to "flowtone"), request.headers)
        assertArrayEquals(json.toByteArray(Charsets.UTF_8), request.body)
        assertEquals(403, response.getInt("status"))
        assertEquals("拒绝访问", response.getString("body"))
        assertEquals("application/json", response.getJSONObject("headers").getString("Content-Type"))
    }

    @Test fun patchAndDeleteReachHost() = runBlocking {
        for (method in listOf("PATCH", "DELETE")) {
            val result = runtime.invokeObject("request", JSONObject().put("url", "https://example.com/api").put("method", method))
            assertEquals(403, result.getJSONObject("response").getInt("status"))
        }
        assertEquals(listOf(ExtensionHttpMethod.Patch, ExtensionHttpMethod.Delete), requests.map { it.method })
    }

    @Test fun invalidRequestsAreRejectedBeforeNetwork() = runBlocking {
        val cases = listOf(
            JSONObject().put("method", "TRACE"),
            JSONObject().put("method", "POST").put("body", JSONObject().put("title", "invalid")),
            JSONObject().put("method", "POST").put("body", "音".repeat(90_000))
        )
        for (options in cases) {
            options.put("url", "https://example.com/api")
            assertEquals("INVALID_REQUEST", runtime.invokeObject("request", options).getString("error"))
        }
        assertEquals(0, requests.size)
    }

    @Test fun legacyGetDefaultsRemainCompatible() = runBlocking {
        val result = runtime.invokeObject("request", JSONObject().put("url", "https://example.com/legacy"))
        assertEquals(403, result.getJSONObject("response").getInt("status"))
        val request = requests.single()
        assertEquals(ExtensionHttpMethod.Get, request.method)
        assertEquals("https://example.com/legacy", request.url)
        assertEquals(emptyMap<String, String>(), request.headers)
        assertNull(request.body)
    }
}
