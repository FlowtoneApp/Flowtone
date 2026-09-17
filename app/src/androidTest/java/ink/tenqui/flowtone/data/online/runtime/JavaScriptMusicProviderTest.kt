package ink.tenqui.flowtone.data.online.runtime

import android.content.Context
import androidx.javascriptengine.JavaScriptSandbox
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderArtist
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSearchRequest
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest
import ink.tenqui.flowtone.data.online.network.ExtensionHttpResponse
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifestParser
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test fun songSearchCapabilityDoesNotAuthorizeOtherCategories() = runBlocking {
        val provider = provider(
            script = """
                globalThis.flowtoneExtension = { async searchPage(request) {
                  if (request.category !== 'single') throw new Error('unexpected category');
                  return {results:[{id:'song-1',title:'Song',artist:'Artist',category:'single'}],nextCursor:null};
                }};
            """.trimIndent(),
            capabilities = CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.SearchSongPage)
        )

        assertEquals(1, provider.searchPage(request(ProviderSearchCategory.Single)).results.size)
        assertTrue(provider.searchPage(request(ProviderSearchCategory.Album)).results.isEmpty())
        assertTrue(provider.searchPage(request(ProviderSearchCategory.Playlist)).results.isEmpty())
        assertTrue(provider.searchPage(request(ProviderSearchCategory.User)).results.isEmpty())
    }

    @Test fun albumSearchCapabilityDoesNotAuthorizeSongSearch() = runBlocking {
        val provider = provider(
            script = """
                globalThis.flowtoneExtension = { async searchPage(request) {
                  if (request.category !== 'album') throw new Error('unexpected category');
                  return {results:[{id:'album-1',title:'Album',artist:'Artist',category:'album'}],nextCursor:null};
                }};
            """.trimIndent(),
            capabilities = CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.SearchAlbumPage)
        )

        assertEquals(1, provider.searchPage(request(ProviderSearchCategory.Album)).results.size)
        assertTrue(provider.searchPage(request(ProviderSearchCategory.Single)).results.isEmpty())
    }

    @Test fun undeclaredProviderMethodsAreNotInvoked() = runBlocking {
        val provider = provider(
            script = """
                globalThis.flowtoneExtension = {
                  async getSearchLanding() { throw new Error('landing invoked'); },
                  async getSongs() { throw new Error('songs invoked'); },
                  async getAlbums() { throw new Error('albums invoked'); },
                  async getPlaylistSongs() { throw new Error('playlist invoked'); },
                  async resolvePersistentSong() { throw new Error('persistent invoked'); },
                  async getPlaybackResource() { throw new Error('playback invoked'); }
                };
            """.trimIndent(),
            capabilities = CanonicalAtomicCapabilitySet.Empty
        )

        assertNull(provider.getSearchLanding())
        assertNull(provider.getSongs())
        assertNull(provider.getAlbums())
        assertNull(provider.getPlaylistSongs("playlist-1"))
        assertNull(provider.resolvePersistentSong("persistent-1"))
        assertNull(provider.getPlaybackResource(testSong()))
    }

    @Test fun declaredProviderMethodsAreInvoked() = runBlocking {
        val provider = provider(
            script = """
                globalThis.flowtoneExtension = {
                  async getSearchLanding() { return {blocks:[]}; },
                  async getSongs() { return [{id:'song-1',title:'Song',artist:'Artist'}]; },
                  async getAlbums() { return [{id:'album-1',title:'Album',artist:'Artist'}]; },
                  async getPlaylistSongs() { return [{id:'song-2',title:'Playlist Song',artist:'Artist'}]; },
                  async resolvePersistentSong() { return {id:'song-3',title:'Persistent Song',artist:'Artist'}; },
                  async getPlaybackResource() { return {type:'progressive',url:'https://example.com/song.mp3'}; }
                };
            """.trimIndent(),
            capabilities = CanonicalAtomicCapabilitySet.of(
                AtomicCapabilityId.SearchLandingGet,
                AtomicCapabilityId.CatalogSongsList,
                AtomicCapabilityId.CatalogAlbumsList,
                AtomicCapabilityId.PlaylistSongsRead,
                AtomicCapabilityId.SongPersistentResolve,
                AtomicCapabilityId.PlaybackResourceResolve
            )
        )

        assertNotNull(provider.getSearchLanding())
        assertEquals("song-1", provider.getSongs()?.single()?.id)
        assertEquals("album-1", provider.getAlbums()?.single()?.id)
        assertEquals("song-2", provider.getPlaylistSongs("playlist-1")?.single()?.id)
        assertEquals("song-3", provider.resolvePersistentSong("persistent-1")?.id)
        assertNotNull(provider.getPlaybackResource(testSong()))
    }

    @Test fun providerItemMayOmitArtwork() = runBlocking {
        val provider = provider("""
            globalThis.flowtoneExtension = { async searchPage() { return { results:[
              {id:'no-artwork',title:'No artwork',artist:'Creator',category:'user'}
            ], nextCursor:null }; } };
        """.trimIndent())

        val artist = provider.searchPage(ProviderSearchRequest("miku", ProviderSearchCategory.User))
            .results.single() as ProviderArtist
        assertNull(artist.artwork)
        assertNull(artist.largeArtwork)
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

    @Test fun entityCollectionsUseNoArgumentMethodsAndStructuredModels() = runBlocking {
        val provider = provider(
            script = """
                globalThis.flowtoneExtension = {
                  async getSongs() { return [
                    {id:'song-1',title:'Song',artists:[{id:'artist-1',name:'Artist'}],album:{id:'album-1',title:'Album'},durationMs:1234},
                    {id:'song-1',title:'Duplicate'}
                  ]; },
                  async getAlbums() { return [
                    {id:'album-1',title:'Album',artists:[{id:'artist-1',name:'Artist'}],songCount:1},
                    {id:'album-1',title:'Duplicate'}
                  ]; }
                };
            """.trimIndent(),
            capabilities = CanonicalAtomicCapabilitySet.of(
                AtomicCapabilityId.CatalogSongsList,
                AtomicCapabilityId.CatalogAlbumsList
            )
        )

        val song = provider.getSongs()?.single() as ProviderSong
        val album = provider.getAlbums()?.single() as ProviderAlbum

        assertEquals("song-1", song.id)
        assertEquals("artist-1", song.artists.single().remoteId)
        assertEquals("album-1", song.album?.remoteId)
        assertEquals(1234L, song.durationMs)
        assertEquals("album-1", album.id)
        assertEquals("artist-1", album.artists.single().remoteId)
        assertEquals(1, album.songCount)
    }

    @Test fun unavailableCollectionsDoNotInvokeMissingMethods() = runBlocking {
        val provider = provider("globalThis.flowtoneExtension = {};")

        assertNull(provider.getSongs())
        assertNull(provider.getAlbums())
    }

    @Test fun searchAndCollectionMergeEntityMetadataByField() = runBlocking {
        val provider = provider(
            script = """
                globalThis.flowtoneExtension = {
                  async searchPage() { return {results:[
                    {id:'song-1',title:'Song',artist:'Artist',artworkUrl:'https://example.com/song.jpg',category:'single'}
                  ],nextCursor:null}; },
                  async getSongs() { return [
                    {id:'song-1',title:'Song',artist:'Artist',durationMs:9876}
                  ]; }
                };
            """.trimIndent(),
            capabilities = CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.CatalogSongsList)
        )

        val searchSong = provider.searchPage(
            ProviderSearchRequest("song", ProviderSearchCategory.Single)
        ).results.single() as ProviderSong
        val collectionSong = provider.getSongs()?.single() as ProviderSong

        assertEquals(searchSong.identity, collectionSong.identity)
        assertEquals(searchSong.artwork, collectionSong.artwork)
        assertEquals(9876L, collectionSong.durationMs)
    }

    private fun request(category: ProviderSearchCategory) =
        ProviderSearchRequest(keyword = "query", category = category)

    private fun testSong() = ProviderSong(
        trackRef = ExtensionTrackRef("example.music", "song-1"),
        title = "Song",
        artist = "Artist"
    )

    private suspend fun provider(
        script: String,
        capabilities: CanonicalAtomicCapabilitySet = DefaultCapabilities
    ): JavaScriptMusicProvider {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "js-music-provider").toFile()
        directory.resolve("main.js").writeText(script)
        val capabilityJson = capabilities.values.joinToString(",") { "\"${it.value}\"" }
        val descriptor = ExtensionManifestParser.parseNormalized(
            """
                {"formatVersion":2,"id":"example.music","name":"Example","version":"1","author":"Test",
                "entry":"main.js","capabilities":[$capabilityJson],"musicSources":["example.com"],
                "permissions":{"network":{"origins":["https://example.com"]}}}
            """.trimIndent()
        )
        val runtime = JavaScriptExtensionRuntime(InstalledExtension(descriptor, directory, true), requireNotNull(host.createIsolate()), unusedNetwork(), ExtensionPrivateCache(cacheRoot))
        runtime.start()
        runtimes += runtime
        return JavaScriptMusicProvider(
            runtime = runtime,
            musicSources = descriptor.manifest.musicSources.toSet(),
            capabilities = capabilities
        )
    }

    private fun unusedNetwork() = object : ExtensionNetworkClient {
        override suspend fun execute(request: ExtensionHttpRequest) = ExtensionHttpResponse(200, emptyMap(), ByteArray(0))
    }

    private companion object {
        val DefaultCapabilities = CanonicalAtomicCapabilitySet.of(
            AtomicCapabilityId.SearchSongPage,
            AtomicCapabilityId.SearchAlbumPage,
            AtomicCapabilityId.SearchPlaylistPage,
            AtomicCapabilityId.SearchArtistPage,
            AtomicCapabilityId.SearchLandingGet,
            AtomicCapabilityId.PlaylistSongsRead,
            AtomicCapabilityId.SongPersistentResolve,
            AtomicCapabilityId.PlaybackResourceResolve
        )
    }
}
