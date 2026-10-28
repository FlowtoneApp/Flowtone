package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ExtensionRuntimeState
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLibraryCatalogCoordinatorTest {
    @Test
    fun normalLoadDeduplicatesUsesCachedStateAndChecksCapabilities() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.CatalogSongsList)
            val failures = mutableListOf<Throwable>()
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, error -> failures += error }
            )
            val state = coordinator.state

            coordinator.loadProviderEntityCollections(" a ")
            coordinator.loadProviderEntityCollections("a")
            yield()
            assertEquals(1, gateway.songCalls.size)
            assertFalse(gateway.songCalls.single().refresh)
            assertEquals(ProviderCatalogLoadState.Loading, state.value.providerSongCatalogLoadStates["a"])
            assertEquals(0, gateway.albumCalls)

            gateway.songCalls.single().result.complete(listOf(song("loaded")))
            yield()
            coordinator.loadProviderEntityCollections("a")
            assertEquals(1, gateway.songCalls.size)
            assertEquals(listOf(song("loaded")), state.value.providerSongs["a"])
            assertEquals(ProviderCatalogLoadState.Loaded, state.value.providerSongCatalogLoadStates["a"])
            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun refreshKeepsNormalRequestAliveAndRejectsItsLateResult() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.CatalogSongsList)
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, _ -> }
            )
            val state = coordinator.state

            coordinator.loadProviderEntityCollections("a")
            yield()
            val normal = gateway.songCalls.single()
            val version = coordinator.beginRefresh("a")
            val refresh = async { coordinator.refreshProviderLibrary("a", version) }
            yield()
            assertFalse(normal.result.isCancelled)
            assertEquals(listOf(false, true), gateway.songCalls.map { it.refresh })

            gateway.songCalls[1].result.complete(listOf(song("fresh")))
            assertTrue(refresh.await())
            normal.result.complete(listOf(song("stale")))
            yield()

            assertEquals(listOf(song("fresh")), state.value.providerSongs["a"])
        }
    }

    @Test
    fun failedRefreshRetainsExistingContentAndOtherSources() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.CatalogSongsList)
            val before = ProviderLibraryState(
                providerSongs = mapOf("a" to listOf(song("old")), "b" to listOf(song("other"))),
                providerSongCatalogLoadStates = mapOf("a" to ProviderCatalogLoadState.Loaded)
            )
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, _ -> },
                initialState = before
            )
            val state = coordinator.state
            val version = coordinator.beginRefresh("a")
            val refresh = async { coordinator.refreshProviderLibrary("a", version) }
            yield()
            gateway.songCalls.single().result.complete(null)

            assertFalse(refresh.await())
            assertEquals(before.providerSongs, state.value.providerSongs)
            assertEquals(ProviderCatalogLoadState.Loaded, state.value.providerSongCatalogLoadStates["a"])
            assertEquals(0, gateway.albumCalls)
            assertTrue(gateway.userPlaylistCalls.isEmpty())
        }
    }

    @Test
    fun runtimeChangeReloadsRequestedCatalogAndIgnoresOldFailure() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.CatalogSongsList)
            val failures = mutableListOf<Throwable>()
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, error -> failures += error }
            )
            val state = coordinator.state
            coordinator.loadProviderEntityCollections("a")
            yield()

            gateway.runtimeState.value = ExtensionRuntimeState(generation = 2)
            coordinator.invalidateRuntime()
            coordinator.reloadRequestedProviderData()
            yield()
            assertEquals(2, gateway.songCalls.size)

            gateway.songCalls[0].result.complete(null)
            yield()
            assertTrue(state.value.providerSongs.isEmpty())
            assertTrue(failures.isEmpty())

            gateway.songCalls[1].result.complete(listOf(song("new-runtime")))
            yield()
            assertEquals(listOf(song("new-runtime")), state.value.providerSongs["a"])
        }
    }

    @Test
    fun runtimeChangeRejectsRefreshCompletion() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.CatalogSongsList)
            val before = ProviderLibraryState(providerSongs = mapOf("a" to listOf(song("old"))))
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, _ -> },
                initialState = before
            )
            val state = coordinator.state
            val version = coordinator.beginRefresh("a")
            val refresh = async { coordinator.refreshProviderLibrary("a", version) }
            yield()
            gateway.runtimeState.value = ExtensionRuntimeState(generation = 2)
            gateway.songCalls.single().result.complete(listOf(song("obsolete")))

            assertFalse(refresh.await())
            assertEquals(before.providerSongs, state.value.providerSongs)
        }
    }

    @Test
    fun embeddedPlaylistSurvivesReloadWhenLegacyDetailReturnsNull() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway()
            val failures = mutableListOf<Throwable>()
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, error -> failures += error }
            )
            val state = coordinator.state
            val playlist = ProviderPlaylistSearchItem(
                identity = ProviderEntityIdentity("a", "playlist"),
                title = "Playlist",
                artist = "",
                songs = listOf(song("embedded"))
            )
            coordinator.loadProviderPlaylist(playlist)
            assertTrue(gateway.detailCalls.isEmpty())
            assertEquals(playlist.songs, state.value.providerPlaylistDetails[playlist.identity.stableKey]?.songs)

            gateway.runtimeState.value = ExtensionRuntimeState(generation = 2)
            coordinator.invalidateRuntime()
            coordinator.reloadRequestedProviderData()
            yield()
            gateway.detailCalls.single().result.complete(null)
            yield()

            val detail = state.value.providerPlaylistDetails.getValue(playlist.identity.stableKey)
            assertEquals(playlist.songs, detail.songs)
            assertFalse(detail.isLoading)
            assertFalse(detail.loadFailed)
            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun stalePlaylistDetailFromPreviousRuntimeCannotOverwriteReloadedDetail() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway()
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, _ -> }
            )
            val playlist = ProviderPlaylistSearchItem(
                identity = ProviderEntityIdentity("a", "playlist"),
                title = "Playlist",
                artist = ""
            )
            val detailKey = playlist.identity.stableKey
            coordinator.loadProviderPlaylist(playlist)
            yield()
            assertEquals(1, gateway.detailCalls.size)

            gateway.runtimeState.value = ExtensionRuntimeState(generation = 2)
            coordinator.invalidateRuntime()
            coordinator.reloadRequestedProviderData()
            yield()
            assertEquals(2, gateway.detailCalls.size)

            gateway.detailCalls[1].result.complete(listOf(song("runtime-b")))
            yield()
            assertEquals(
                listOf(song("runtime-b")),
                coordinator.state.value.providerPlaylistDetails.getValue(detailKey).songs
            )

            gateway.detailCalls[0].result.complete(listOf(song("runtime-a")))
            yield()
            assertEquals(
                listOf(song("runtime-b")),
                coordinator.state.value.providerPlaylistDetails.getValue(detailKey).songs
            )
        }
    }

    @Test
    fun userPlaylistsWaitForRefreshDeduplicateAndRetryAfterFailure() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.UserPlaylistsList)
            var refreshing = true
            val failures = mutableListOf<Throwable>()
            val coordinator = ProviderLibraryCatalogCoordinator(
                scope = this,
                gateway = gateway,
                onFailure = { _, _, error -> failures += error },
                isRefreshing = { refreshing }
            )
            val state = coordinator.state
            coordinator.loadProviderUserPlaylists("a")
            yield()
            assertTrue(gateway.userPlaylistCalls.isEmpty())

            refreshing = false
            coordinator.reloadRequestedProviderData()
            coordinator.loadProviderUserPlaylists("a")
            yield()
            assertEquals(1, gateway.userPlaylistCalls.size)
            gateway.userPlaylistCalls[0].complete(null)
            yield()
            assertEquals(ProviderCatalogLoadState.Failed, state.value.providerUserPlaylistLoadStates["a"])
            assertEquals(1, failures.size)

            coordinator.loadProviderUserPlaylists("a")
            yield()
            val playlists = listOf(ProviderPlaylist(ProviderEntityIdentity("a", "list"), "My playlist"))
            gateway.userPlaylistCalls[1].complete(playlists)
            yield()
            assertEquals(playlists, state.value.providerUserPlaylists["a"])
            assertEquals(ProviderCatalogLoadState.Loaded, state.value.providerUserPlaylistLoadStates["a"])
        }
    }

    private fun song(id: String) = ProviderSong(ExtensionTrackRef("a", id), id, "artist")

    private class FakeGateway(vararg capabilities: AtomicCapabilityId) : ProviderLibraryCatalogGateway {
        override val runtimeState = MutableStateFlow(ExtensionRuntimeState(generation = 1))
        private val capabilities = CanonicalAtomicCapabilitySet.of(*capabilities)
        val songCalls = mutableListOf<SongCall>()
        val userPlaylistCalls = mutableListOf<CompletableDeferred<List<ProviderPlaylist>?>>()
        val detailCalls = mutableListOf<DetailCall>()
        var albumCalls = 0

        override fun providerCapabilities(extensionId: String) = capabilities

        override fun isMusicProviderRuntimeAvailable(extensionId: String) = !runtimeState.value.isReloading

        override suspend fun getProviderSongs(extensionId: String, refresh: Boolean): List<ProviderSong>? {
            val call = SongCall(refresh)
            songCalls += call
            return call.result.await()
        }

        override suspend fun getProviderAlbums(extensionId: String, refresh: Boolean): List<ProviderAlbum>? {
            albumCalls++
            return emptyList()
        }

        override suspend fun getProviderUserPlaylists(extensionId: String): List<ProviderPlaylist>? {
            val result = CompletableDeferred<List<ProviderPlaylist>?>()
            userPlaylistCalls += result
            return result.await()
        }

        override suspend fun getProviderPlaylistSongs(extensionId: String, playlistId: String): List<ProviderSong>? {
            val call = DetailCall()
            detailCalls += call
            return call.result.await()
        }

        class SongCall(
            val refresh: Boolean,
            val result: CompletableDeferred<List<ProviderSong>?> = CompletableDeferred()
        )

        class DetailCall(val result: CompletableDeferred<List<ProviderSong>?> = CompletableDeferred())
    }
}
