package ink.tenqui.flowtone.data.online.runtime

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.data.online.network.ExtensionHttpResponse
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifest
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JavaScriptArtistAvatarExtensionTest {
    private lateinit var host: JavaScriptSandboxHost
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        assumeTrue(JavaScriptSandbox.isSupported())
        host = JavaScriptSandboxHost(context)
    }

    @After fun tearDown() {
        if (::host.isInitialized) host.close()
    }

    @Test fun messagePortHttpBridgeReturnsStandardAvatar() = runBlocking {
        val extension = extension(
            """
            globalThis.flowtoneExtension = {
              async findArtistAvatar(request) {
                const response = await flowtone.http.request({method:'GET',url:'https://example.com/avatar'});
                return JSON.parse(response.body);
              }
            };
            """.trimIndent(),
            object : ExtensionNetworkClient {
                override suspend fun execute(request: ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest) =
                    ExtensionHttpResponse(200, emptyMap(), "{\"type\":\"found\",\"imageUrl\":\"https://example.com/a.jpg\"}".encodeToByteArray())
            }
        )
        extension.start()
        assertEquals("https://example.com/a.jpg", extension.findArtistAvatar("Song", "Artist")?.imageUrl)
        extension.close()
    }

    @Test fun scriptFailureDoesNotEscapeRegistryContract() = runBlocking {
        val extension = extension(
            "globalThis.flowtoneExtension={async findArtistAvatar(){throw new Error('bad')}};",
            object : ExtensionNetworkClient {
                override suspend fun execute(request: ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest): ExtensionHttpResponse =
                    error("network must not run")
            }
        )
        extension.start()
        val registry = ink.tenqui.flowtone.data.online.ArtistAvatarExtensionRegistry().apply { install(extension) }
        assertNull(registry.findArtistAvatar("Song", "Artist"))
        extension.close()
    }

    private suspend fun extension(script: String, network: ExtensionNetworkClient): JavaScriptArtistAvatarExtension {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "js-extension").toFile()
        directory.resolve("main.js").writeText(script)
        val manifest = ExtensionManifest(
            1, "example.avatar", "Example", "1", "Test", "", "main.js",
            listOf("artist_avatar"), listOf("example.com")
        )
        return JavaScriptArtistAvatarExtension(
            InstalledExtension(manifest, directory, true),
            requireNotNull(host.createIsolate()),
            network,
            ExtensionResultCache()
        )
    }
}
