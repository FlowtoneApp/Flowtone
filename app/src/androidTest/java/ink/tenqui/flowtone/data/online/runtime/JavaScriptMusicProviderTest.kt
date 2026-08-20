package ink.tenqui.flowtone.data.online.runtime

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchRequest
import ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest
import ink.tenqui.flowtone.data.online.network.ExtensionHttpResponse
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifest
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JavaScriptMusicProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var host: JavaScriptSandboxHost
    private lateinit var cacheRoot: File
    private val runtimes = mutableListOf<JavaScriptExtensionRuntime>()

    @Before fun setUp() {
        assumeTrue(JavaScriptSandbox.isSupported())
        host = JavaScriptSandboxHost(context)
        cacheRoot = Files.createTempDirectory(context.cacheDir.toPath(), "js-music-cache").toFile()
    }

    @After fun tearDown() {
        runtimes.forEach { it.close() }
        if (::host.isInitialized) host.close()
        if (::cacheRoot.isInitialized) cacheRoot.deleteRecursively()
    }

    @Test fun searchPageSendsRequestAndParsesPage() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchPage(request) {
              return { results:[{id:'playlist-1',title:request.keyword + '|' + request.category + '|' + request.cursor + '|' + request.limit,artist:'Creator',category:'playlist',metadata:[{type:'track_count',value:24},{type:'creator',text:'Creator'}]}], nextCursor:'opaque-next' };
            }};
        """.trimIndent())

        val page = provider.searchPage(ProviderSearchRequest("miku", ProviderSearchCategory.Playlist, null, 20))

        assertEquals(listOf("playlist-1"), page.results.map { it.id })
        assertEquals("opaque-next", page.nextCursor)
        assertEquals("miku|playlist|null|20", page.results.single().title)
        assertEquals(
            listOf("track_count", "creator"),
            page.results.single().metadata?.map { it.type }
        )
        assertEquals(24L, page.results.single().metadata?.first()?.value)
    }

    @Test fun searchPageAcceptsNullCursorAndNormalEmptyResults() = runBlocking {
        val provider = provider("""globalThis.flowtoneExtension = { async searchPage() { return {results:[],nextCursor:null}; } };""")
        val page = provider.searchPage(ProviderSearchRequest("miku", ProviderSearchCategory.Single, "opaque"))
        assertTrue(page.results.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test fun searchPageIgnoresMalformedAndWrongCategoryItems() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchPage() { return { results:[
              {id:'wrong',title:'Wrong',artist:'Artist',category:'single'},
              {id:'bad',title:'',artist:'Artist',category:'playlist'},
              {id:'good',title:'Good',artist:'Artist',category:'playlist'}
            ], nextCursor:null }; } };
        """.trimIndent())
        val page = provider.searchPage(ProviderSearchRequest("miku", ProviderSearchCategory.Playlist))
        assertEquals(listOf("good"), page.results.map { it.id })
    }

    @Test fun metadataMissingAndEmptyRemainDistinguishable() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchPage() { return { results:[
              {id:'missing',title:'Missing',artist:'Creator',category:'playlist'},
              {id:'empty',title:'Empty',artist:'Creator',category:'playlist',metadata:[]}
            ], nextCursor:null }; } };
        """.trimIndent())

        val page = provider.searchPage(ProviderSearchRequest("miku", ProviderSearchCategory.Playlist))

        assertEquals(null, page.results.first { it.id == "missing" }.metadata)
        assertEquals(emptyList<Any>(), page.results.first { it.id == "empty" }.metadata)
    }

    @Test fun malformedMetadataItemsAreSkippedAndOrderIsKept() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchPage() { return { results:[
              {id:'playlist',title:'Playlist',artist:'Creator',category:'playlist',metadata:[
                {type:'track_count',value:24}, {}, {type:'creator',text:'Creator'},
                {type:'text',text:'custom'}, {type:'ignored',value:1}
              ]}
            ], nextCursor:null }; } };
        """.trimIndent())

        val result = provider.searchPage(ProviderSearchRequest("miku", ProviderSearchCategory.Playlist)).results.single()

        assertEquals(listOf("track_count", "creator", "text"), result.metadata?.map { it.type })
    }

    private suspend fun provider(script: String): JavaScriptMusicProvider {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "js-music-provider").toFile()
        directory.resolve("main.js").writeText(script)
        val manifest = ExtensionManifest(1, "example.music", "Example", "1", "Test", "", "main.js", listOf("music_provider"), listOf("example.com"), listOf("example.com"))
        val runtime = JavaScriptExtensionRuntime(InstalledExtension(manifest, directory, true), requireNotNull(host.createIsolate()), unusedNetwork(), ExtensionPrivateCache(cacheRoot))
        runtime.start()
        runtimes += runtime
        return JavaScriptMusicProvider(runtime, manifest.musicSources.toSet())
    }

    private fun unusedNetwork() = object : ExtensionNetworkClient {
        override suspend fun execute(request: ExtensionHttpRequest) = ExtensionHttpResponse(200, emptyMap(), ByteArray(0))
    }
}
