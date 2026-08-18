package ink.tenqui.flowtone.data.online.runtime

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSong
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

    @After fun tearDown() { runtimes.forEach { it.close() }; if (::host.isInitialized) host.close(); if (::cacheRoot.isInitialized) cacheRoot.deleteRecursively() }

    @Test fun searchBindsHostIdentityAndArtworkInsteadOfJsProviderFields() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchSongs() { return [{id:'track-1', providerId:'forged', extensionId:'other.extension', title:'Title', artist:'Artist', durationMs:1234, artworkUrl:'https://images.example/cover.jpg', largeArtworkUrl:'https://images.example/cover-large.jpg'}]; }, async getPlaybackResource(){ return {}; } };
        """.trimIndent())
        val songs = provider.searchSongs("hello")
        assertEquals(1, songs.size)
        assertEquals("example.music", songs.single().trackRef.extensionId)
        assertEquals("track-1", songs.single().trackRef.opaqueId)
        assertEquals("example.music", songs.single().artwork?.extensionId)
        assertEquals("https://images.example/cover.jpg", songs.single().artwork?.url)
        assertEquals("example.music", songs.single().largeArtwork?.extensionId)
        assertEquals("https://images.example/cover-large.jpg", songs.single().largeArtwork?.url)
    }

    @Test fun missingLargeArtworkFallsBackToRegularArtwork() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchSongs() { return [{id:'track-1', title:'Title', artist:'Artist', artworkUrl:'https://images.example/cover.jpg'}]; }, async getPlaybackResource(){ return {}; } };
        """.trimIndent())

        val song = provider.searchSongs("hello").single()
        assertNull(song.largeArtwork)
        assertEquals(song.artwork, song.nowPlayingArtwork)
    }

    @Test fun persistentIdIsPassedThroughWithoutHostParsing() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = {
              async searchSongs() { return [{id:'runtime-id', persistentId:'opaque://provider/long-lived-id', title:'Title', artist:'Artist'}]; },
              async resolvePersistentSong(request) { return {id:'resolved-id', persistentId:request.persistentId, title:'Resolved', artist:'Artist'}; },
              async getPlaybackResource(){ return {}; }
            };
        """.trimIndent())

        val searched = provider.searchSongs("hello").single()
        assertEquals("opaque://provider/long-lived-id", searched.persistentId)
        assertEquals("example.music", searched.trackRef.extensionId)
        assertEquals("example.com", searched.sourceHost)
        val resolved = provider.resolvePersistentSong(searched.persistentId!!)
        assertEquals("resolved-id", resolved?.trackRef?.opaqueId)
        assertEquals(searched.persistentId, resolved?.persistentId)
    }

    @Test fun playbackMapsHlsAndProgressiveWithoutUrlSuffixInference() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = {
              async searchSongs(){ return []; },
              async getPlaybackResource(request) { return request.id === 'hls' ? {type:'hls',url:'https://media.example/live',headers:{Referer:'https://provider.example'},mimeType:'application/x-mpegURL'} : {type:'progressive',url:'https://media.example/audio',mimeType:'audio/mpeg'}; }
            };
        """.trimIndent())
        val hls = provider.getPlaybackResource(song("hls"))
        val progressive = provider.getPlaybackResource(song("progressive"))
        assertEquals(ExtensionPlaybackResourceType.Hls, hls?.type)
        assertEquals("https://media.example/live", hls?.url)
        assertEquals("https://provider.example", hls?.headers?.get("Referer"))
        assertEquals(ExtensionPlaybackResourceType.Progressive, progressive?.type)
    }

    @Test fun invalidResourceAndForeignTrackAreRejected() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchSongs(){ return []; }, async getPlaybackResource(){ return {type:'dash',url:''}; } };
        """.trimIndent())
        assertNull(provider.getPlaybackResource(song("bad")))
        assertNull(provider.getPlaybackResource(ProviderSong(ExtensionTrackRef("other.extension", "bad"), "t", "a")))
    }

    @Test fun malformedSearchEntriesAreIgnored() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchSongs(){ return [{id:'',title:'bad',artist:'a'},{id:'ok',title:'',artist:'a'},{id:'good',title:'Title',artist:'Artist'}]; }, async getPlaybackResource(){return {}; } };
        """.trimIndent())
        assertEquals(listOf("good"), provider.searchSongs("q").map { it.id })
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

    private fun song(id: String) = ProviderSong(ExtensionTrackRef("example.music", id), "Title", "Artist")
    private fun unusedNetwork() = object : ExtensionNetworkClient { override suspend fun execute(request: ExtensionHttpRequest) = ExtensionHttpResponse(200, emptyMap(), ByteArray(0)) }
}
