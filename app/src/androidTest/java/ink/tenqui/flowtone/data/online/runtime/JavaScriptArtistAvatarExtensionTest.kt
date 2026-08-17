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
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import kotlinx.coroutines.runBlocking
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
class JavaScriptArtistAvatarExtensionTest {
    private lateinit var host: JavaScriptSandboxHost
    private lateinit var privateCacheRoot: File
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        assumeTrue(JavaScriptSandbox.isSupported())
        host = JavaScriptSandboxHost(context)
        privateCacheRoot = Files.createTempDirectory(context.cacheDir.toPath(), "js-private-cache").toFile()
    }

    @After fun tearDown() {
        if (::host.isInitialized) host.close()
        if (::privateCacheRoot.isInitialized) privateCacheRoot.deleteRecursively()
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
        assertEquals("https://example.com/a.jpg", extension.findArtistAvatar("Song", "Artist")?.image?.url)
        extension.close()
    }

    @Test fun bootstrapDoesNotRequireJavaScriptTimersAndCleansCompletedRequests() = runBlocking {
        val extension = extension(
            "globalThis.flowtoneExtension={async findArtistAvatar(){return {type:'not_found'}}};",
            object : ExtensionNetworkClient {
                override suspend fun execute(request: ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest) =
                    error("network must not run")
            }
        )

        val bootstrap = extension.bootstrapScript()

        assertFalse(bootstrap.contains("setTimeout"))
        assertFalse(bootstrap.contains("clearTimeout"))
        assertTrue(bootstrap.contains("__pending.delete(message.id);"))
        extension.close()
    }

    @Test fun hostTimeoutRejectsPromiseAndDoesNotBlockNextRequest() = runBlocking {
        val requests = AtomicInteger()
        val extension = extension(
            """
            globalThis.flowtoneExtension = {
              async findArtistAvatar() {
                try {
                  await flowtone.http.request({method:'GET',url:'https://example.com/slow'});
                  throw new Error('expected timeout');
                } catch (error) {
                  if (error.message !== 'TIMEOUT') throw error;
                }
                const response = await flowtone.http.request({method:'GET',url:'https://example.com/retry'});
                return JSON.parse(response.body);
              }
            };
            """.trimIndent(),
            object : ExtensionNetworkClient {
                override suspend fun execute(request: ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest): ExtensionHttpResponse {
                    return if (requests.incrementAndGet() == 1) {
                        throw SocketTimeoutException("timeout")
                    } else {
                        ExtensionHttpResponse(
                            200,
                            emptyMap(),
                            "{\"type\":\"found\",\"imageUrl\":\"https://example.com/retry.jpg\"}".encodeToByteArray()
                        )
                    }
                }
            }
        )
        extension.start()

        assertEquals("https://example.com/retry.jpg", extension.findArtistAvatar("Song", "Artist")?.image?.url)
        assertEquals(2, requests.get())
        extension.close()
    }

    @Test fun messagePortCacheIsPersistentAndOnlyExposesCacheApi() = runBlocking {
        val writer = extension(
            """
            globalThis.flowtoneExtension = {
              async findArtistAvatar() {
                await flowtone.cache.set('foo', 'value-a');
                const value = await flowtone.cache.get('foo');
                await flowtone.cache.remove('foo');
                const removed = await flowtone.cache.get('foo');
                await flowtone.cache.set('foo', value);
                return value === 'value-a' && removed === null
                  ? {type:'found', imageUrl:'https://example.com/cache.jpg'}
                  : {type:'not_found'};
              }
            };
            """.trimIndent(),
            unusedNetwork()
        )
        writer.start()
        assertEquals("https://example.com/cache.jpg", writer.findArtistAvatar("Song", "Artist")?.image?.url)
        writer.close()

        val reader = extension(
            """
            globalThis.flowtoneExtension = {
              async findArtistAvatar() {
                const value = await flowtone.cache.get('foo');
                await flowtone.cache.clear();
                return value === 'value-a'
                  ? {type:'found', imageUrl:'https://example.com/persisted.jpg'}
                  : {type:'not_found'};
              }
            };
            """.trimIndent(),
            unusedNetwork()
        )
        reader.start()
        assertEquals("https://example.com/persisted.jpg", reader.findArtistAvatar("Different", "Request")?.image?.url)
        reader.close()
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
            ExtensionPrivateCache(privateCacheRoot)
        )
    }

    private fun unusedNetwork() = object : ExtensionNetworkClient {
        override suspend fun execute(request: ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest): ExtensionHttpResponse =
            error("network must not run")
    }
}
