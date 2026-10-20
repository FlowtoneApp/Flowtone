package ink.tenqui.flowtone.data.online.runtime

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.configuration.ExtensionConfigStore
import ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest
import ink.tenqui.flowtone.data.online.network.ExtensionHttpResponse
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JavaScriptExtensionConfigurationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var host: JavaScriptSandboxHost
    private lateinit var root: java.io.File

    @Before fun setUp() {
        assumeTrue(JavaScriptSandbox.isSupported())
        host = JavaScriptSandboxHost(context)
        root = Files.createTempDirectory(context.cacheDir.toPath(), "js-config").toFile()
    }

    @After fun tearDown() {
        if (::host.isInitialized) host.close()
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test fun configGetUsesBoundNamespaceTypedValuesDefaultsAndRefresh() = runBlocking {
        val store = ExtensionConfigStore(root.resolve("config"))
        val extension = installed("extension.a")
        store.save(
            extension.manifest.id,
            extension.descriptor.configurationSchema,
            mapOf(
                "text" to ConfigurationValue.StringValue("text-value"),
                "url" to ConfigurationValue.StringValue("https://example.com"),
                "toggle" to ConfigurationValue.BooleanValue(true),
                "choice" to ConfigurationValue.StringValue("a")
            )
        )
        store.save(
            "extension.b",
            extension.descriptor.configurationSchema,
            mapOf("text" to ConfigurationValue.StringValue("other-extension"))
        )
        val runtime = JavaScriptExtensionRuntime(
            installed = extension,
            isolate = requireNotNull(host.createIsolate()),
            network = unusedNetwork(),
            privateCache = ExtensionPrivateCache(root.resolve("cache")),
            configStore = store
        )
        try {
            runtime.start()
            val result = runtime.invokeObject("readConfig", JSONObject())
            assertEquals("text-value", result.getString("text"))
            assertEquals("https://example.com", result.getString("url"))
            assertTrue(result.getBoolean("toggle"))
            assertEquals("a", result.getString("choice"))
            assertEquals("default-value", result.getString("defaulted"))
            assertTrue(result.isNull("missing"))
            assertEquals("CONFIG_FIELD_NOT_DECLARED", result.getString("unknownError"))
            assertEquals("SECRET_CONFIGURATION_UNAVAILABLE", result.getString("secretError"))
            assertEquals("INVALID_CONFIG_REQUEST", result.getString("namespaceError"))
            assertFalse(result.getBoolean("hasSet"))

            store.save(
                extension.manifest.id,
                extension.descriptor.configurationSchema,
                mapOf("text" to ConfigurationValue.StringValue("saved-later"))
            )
            assertTrue(runtime.refreshConfiguration())
            assertEquals("saved-later", runtime.invokeObject("readText", JSONObject()).getString("value"))
        } finally {
            runtime.close()
        }
    }

    private fun installed(id: String): InstalledExtension {
        val directory = root.resolve(id).also { it.mkdirs() }
        directory.resolve("main.js").writeText(
            """
                globalThis.flowtoneExtension = {
                  async readConfig() {
                    const result = {
                      text: flowtone.config.get('text'),
                      url: flowtone.config.get('url'),
                      toggle: flowtone.config.get('toggle'),
                      choice: flowtone.config.get('choice'),
                      defaulted: flowtone.config.get('defaulted'),
                      missing: flowtone.config.get('missing'),
                      hasSet: typeof flowtone.config.set !== 'undefined'
                    };
                    try { flowtone.config.get('unknown'); } catch (error) { result.unknownError = error.message; }
                    try { flowtone.config.get('secret'); } catch (error) { result.secretError = error.message; }
                    try { flowtone.config.get('extension.b', 'text'); } catch (error) { result.namespaceError = error.message; }
                    return result;
                  },
                  async readText() { return {value: flowtone.config.get('text')}; }
                };
            """.trimIndent()
        )
        val descriptor = ExtensionManifestParser.parseNormalized(
            """{"formatVersion":2,"id":"$id","name":"Example","version":"1","author":"Test","entry":"main.js","capabilities":["artist.avatar.lookup"],"configuration":{"fields":[{"id":"text","type":"text","label":"Text"},{"id":"url","type":"url","label":"Url"},{"id":"toggle","type":"toggle","label":"Toggle"},{"id":"choice","type":"choice","label":"Choice","choices":[{"value":"a","label":"A"}]},{"id":"defaulted","type":"text","label":"Defaulted","defaultValue":"default-value"},{"id":"missing","type":"text","label":"Missing"},{"id":"secret","type":"secret","label":"Secret"}],"actions":[]},"credentialRequests":[],"permissions":{"network":{"origins":["https://example.com"]}}}"""
        )
        return InstalledExtension(descriptor, directory, true)
    }

    private fun unusedNetwork() = object : ExtensionNetworkClient {
        override suspend fun execute(request: ExtensionHttpRequest): ExtensionHttpResponse =
            error("network must not run")
    }
}
