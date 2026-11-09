package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ExtensionRuntimeState
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import ink.tenqui.flowtone.data.online.ProviderLikedSongs
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderSongLikedStateIdentity
import ink.tenqui.flowtone.data.online.ProviderSongLikedStatus
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
    fun likedSongsShareInFlightRequestsAndKeepTheLastSuccessfulSnapshotOnRefreshFailure() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.UserLikedSongsList)
            val coordinator = ProviderLibraryCatalogCoordinator(this, gateway, { _, _, _ -> })
            val first = ProviderLikedSongs("a")
            val second = ProviderLikedSongs("b")
            val songs = listOf(song("liked"))
            coordinator.loadProviderLikedSongs(first)
            coordinator.loadProviderLikedSongs(first)
            coordinator.loadProviderLikedSongs(second)
            val initialRefresh = async { coordinator.refreshProviderLibrary("a", coordinator.beginRefresh("a")) }
            yield()

            assertEquals(listOf(first, second), gateway.likedCalls.map { it.first })
            assertEquals(ProviderLikedSongsState(isLoading = true), coordinator.state.value.providerLikedSongs[first])
            gateway.likedCalls[0].second.complete(songs)
            gateway.likedCalls[1].second.complete(emptyList())
            assertTrue(initialRefresh.await())
            yield()
            assertEquals(ProviderLikedSongsState(songs = emptyList()), coordinator.state.value.providerLikedSongs[second])

            val refresh = async { coordinator.refreshProviderLibrary("a", coordinator.beginRefresh("a")) }
            yield()
            assertEquals(ProviderLikedSongsState(songs, isLoading = true), coordinator.state.value.providerLikedSongs[first])
            yield()
            gateway.likedCalls.last().second.complete(null)
            assertFalse(refresh.await())
            assertEquals(ProviderLikedSongsState(songs, loadFailed = true), coordinator.state.value.providerLikedSongs[first])

            coordinator.loadProviderLikedSongs(first, refresh = true)
            coordinator.loadProviderLikedSongs(first, refresh = true)
            yield()
            assertEquals(4, gateway.likedCalls.size)
            assertEquals(ProviderLikedSongsState(songs, isLoading = true), coordinator.state.value.providerLikedSongs[first])
            gateway.likedCalls.last().second.complete(emptyList())
            yield()
            assertEquals(ProviderLikedSongsState(songs = emptyList()), coordinator.state.value.providerLikedSongs[first])
            assertTrue(gateway.userPlaylistCalls.isEmpty())
        }
    }

    @Test
    fun likedSongsRequireCapabilityAndPublishColdFailure() = runBlocking {
        withTimeout(5_000) {
            val collection = ProviderLikedSongs("a")
            val legacy = FakeGateway(AtomicCapabilityId.UserPlaylistsList)
            ProviderLibraryCatalogCoordinator(this, legacy, { _, _, _ -> }).loadProviderLikedSongs(collection)
            assertTrue(legacy.likedCalls.isEmpty())

            val gateway = FakeGateway(AtomicCapabilityId.UserLikedSongsList)
            val failures = mutableListOf<Throwable>()
            val coordinator = ProviderLibraryCatalogCoordinator(this, gateway, { _, _, error -> failures += error })
            coordinator.loadProviderLikedSongs(collection)
            yield()
            gateway.likedCalls.single().second.complete(null)
            yield()
            assertEquals(ProviderLikedSongsState(loadFailed = true), coordinator.state.value.providerLikedSongs[collection])
            assertTrue(failures.isEmpty())
        }
    }

    @Test
    fun likedSongsDiscardResultsFromThePreviousExtensionRuntime() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway(AtomicCapabilityId.UserLikedSongsList)
            val coordinator = ProviderLibraryCatalogCoordinator(this, gateway, { _, _, _ -> })
            val collection = ProviderLikedSongs("a")
            coordinator.loadProviderLikedSongs(collection)
            yield()

            gateway.runtimeState.value = ExtensionRuntimeState(generation = 2)
            coordinator.invalidateRuntime()
            coordinator.reloadRequestedProviderData()
            yield()
            gateway.likedCalls[1].second.complete(listOf(song("new")))
            yield()
            gateway.likedCalls[0].second.complete(listOf(song("old")))
            yield()

            assertEquals(listOf(song("new")), coordinator.state.value.providerLikedSongs[collection]?.songs)
        }
    }

    @Test
    fun likedSongStateChecksAreCapabilityGatedBatchedDeduplicatedAndUnknownOnMissingResults() = runBlocking {
        val unsupported = FakeGateway()
        val unsupportedSong = song("unavailable")
        ProviderLibraryCatalogCoordinator(this, unsupported, { _, _, _ -> })
            .loadProviderLikedSongStates(listOf(unsupportedSong))
        yield()
        assertTrue(unsupported.likedStateCalls.isEmpty())

        val gateway = FakeGateway(AtomicCapabilityId.UserLikedSongsCheck)
        val coordinator = ProviderLibraryCatalogCoordinator(this, gateway, { _, _, _ -> })
        val first = song("first")
        val second = song("second")
        coordinator.loadProviderLikedSongStates(listOf(first, second, first))
        coordinator.loadProviderLikedSongStates(listOf(first, second))
        yield()

        val call = gateway.likedStateCalls.single()
        assertEquals(listOf(first.trackRef, second.trackRef), call.trackRefs)
        call.result.complete(mapOf(first.trackRef to true))
        yield()
        assertEquals(
            ProviderSongLikedStatus.Liked,
            coordinator.state.value.providerSongLikedStates[ProviderSongLikedStateIdentity(first.trackRef)]
        )
        assertEquals(
            ProviderSongLikedStatus.Unknown,
            coordinator.state.value.providerSongLikedStates[ProviderSongLikedStateIdentity(second.trackRef)]
        )
        coordinator.loadProviderLikedSongStates(listOf(first, second))
        yield()
        assertEquals(1, gateway.likedStateCalls.size)
    }

    @Test
    fun likedSongStateCacheSeparatesProvidersAndCredentialContextsAndRefreshFailureStaysUnknown() = runBlocking {
        val gateway = FakeGateway(AtomicCapabilityId.UserLikedSongsCheck)
        val coordinator = ProviderLibraryCatalogCoordinator(this, gateway, { _, _, _ -> })
        val firstProviderSong = ProviderSong(ExtensionTrackRef("a", "same-id"), "A", "Artist")
        val secondProviderSong = ProviderSong(ExtensionTrackRef("b", "same-id"), "B", "Artist")
        coordinator.loadProviderLikedSongStates(listOf(firstProviderSong, secondProviderSong))
        yield()
        assertEquals(2, gateway.likedStateCalls.size)
        assertTrue(gateway.likedStateCalls.all { it.trackRefs.map(ExtensionTrackRef::extensionId).distinct().size == 1 })
        gateway.likedStateCalls[0].result.complete(mapOf(firstProviderSong.trackRef to true))
        gateway.likedStateCalls[1].result.complete(mapOf(secondProviderSong.trackRef to false))
        yield()
        assertEquals(
            ProviderSongLikedStatus.Liked,
            coordinator.state.value.providerSongLikedStates[ProviderSongLikedStateIdentity(firstProviderSong.trackRef)]
        )
        assertEquals(
            ProviderSongLikedStatus.NotLiked,
            coordinator.state.value.providerSongLikedStates[ProviderSongLikedStateIdentity(secondProviderSong.trackRef)]
        )

        gateway.runtimeState.value = ExtensionRuntimeState(generation = 1, accountContextGeneration = 2)
        coordinator.invalidateProviderLikedSongStates()
        yield()
        assertEquals(4, gateway.likedStateCalls.size)
        gateway.likedStateCalls[2].result.complete(null)
        gateway.likedStateCalls[3].result.complete(mapOf(secondProviderSong.trackRef to true))
        yield()
        assertEquals(
            ProviderSongLikedStatus.Unknown,
            coordinator.state.value.providerSongLikedStates[ProviderSongLikedStateIdentity(firstProviderSong.trackRef)]
        )
        assertEquals(
            ProviderSongLikedStatus.Liked,
            coordinator.state.value.providerSongLikedStates[ProviderSongLikedStateIdentity(secondProviderSong.trackRef)]
        )
    }

    @Test
    fun likedSongStateRefreshBypassesCacheAndBatchesAtFiftyItems() = runBlocking {
        var now = 10L
        val gateway = FakeGateway(AtomicCapabilityId.UserLikedSongsCheck)
        val coordinator = ProviderLibraryCatalogCoordinator(
            scope = this,
            gateway = gateway,
            onFailure = { _, _, _ -> },
            currentTimeMillis = { now }
        )
        val songs = (0..50).map { song("track-$it") }
        coordinator.loadProviderLikedSongStates(songs)
        yield()
        assertEquals(listOf(50), gateway.likedStateCalls.map { it.trackRefs.size })
        gateway.likedStateCalls[0].result.complete(gateway.likedStateCalls[0].trackRefs.associateWith { false })
        yield()
        assertEquals(listOf(50, 1), gateway.likedStateCalls.map { it.trackRefs.size })
        gateway.likedStateCalls[1].result.complete(gateway.likedStateCalls[1].trackRefs.associateWith { false })
        yield()
        coordinator.loadProviderLikedSongStates(songs)
        yield()
        assertEquals(2, gateway.likedStateCalls.size)

        coordinator.loadProviderLikedSongStates(songs.take(1), refresh = true)
        yield()
        assertEquals(3, gateway.likedStateCalls.size)
        gateway.likedStateCalls.last().result.complete(null)
        yield()
        assertEquals(
            ProviderSongLikedStatus.Unknown,
            coordinator.state.value.providerSongLikedStates[ProviderSongLikedStateIdentity(songs.first().trackRef)]
        )
        now += 5_000L
        coordinator.loadProviderLikedSongStates(songs.take(1))
        yield()
        assertEquals(4, gateway.likedStateCalls.size)
        gateway.likedStateCalls.last().result.complete(mapOf(songs.first().trackRef to true))
        yield()
    }

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
            val refreshingDetail = state.value.providerPlaylistDetails.getValue(playlist.identity.stableKey)
            assertTrue(refreshingDetail.isLoading)
            assertEquals(playlist.songs, refreshingDetail.songs)
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
    fun albumsFromSourcesWithoutSongCatalogFinishWithoutRequestingSongs() = runBlocking {
        val gateway = FakeGateway(AtomicCapabilityId.CatalogAlbumsList)
        val coordinator = ProviderLibraryCatalogCoordinator(this, gateway, { _, _, _ -> })

        coordinator.loadProviderEntityCollections("a")
        yield()

        assertEquals(ProviderCatalogLoadState.Unavailable, coordinator.state.value.providerSongCatalogLoadStates["a"])
        assertTrue(gateway.songCalls.isEmpty())
        assertEquals(1, gateway.albumCalls)
    }

    @Test
    fun playlistDetailDistinguishesSuccessfulEmptyFromFailedRead() = runBlocking {
        withTimeout(5_000) {
            val gateway = FakeGateway()
            val failures = mutableListOf<Throwable>()
            val coordinator = ProviderLibraryCatalogCoordinator(this, gateway, { _, _, error -> failures += error })
            val empty = ProviderPlaylistSearchItem(ProviderEntityIdentity("a", "empty"), "Empty", "")
            val failed = ProviderPlaylistSearchItem(ProviderEntityIdentity("a", "failed"), "Failed", "")

            coordinator.loadProviderPlaylist(empty)
            coordinator.loadProviderPlaylist(failed)
            val details = coordinator.state
            assertEquals(ProviderPlaylistDetailState(isLoading = true), details.value.providerPlaylistDetails[empty.identity.stableKey])
            assertEquals(ProviderPlaylistDetailState(isLoading = true), details.value.providerPlaylistDetails[failed.identity.stableKey])
            yield()

            gateway.detailCalls[0].result.complete(emptyList())
            gateway.detailCalls[1].result.complete(null)
            yield()

            assertEquals(ProviderPlaylistDetailState(), details.value.providerPlaylistDetails[empty.identity.stableKey])
            assertEquals(ProviderPlaylistDetailState(loadFailed = true), details.value.providerPlaylistDetails[failed.identity.stableKey])
            assertEquals(1, failures.size)
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
        val likedCalls = mutableListOf<Pair<ProviderLikedSongs, CompletableDeferred<List<ProviderSong>?>>>()
        val likedStateCalls = mutableListOf<LikedStateCall>()
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

        override suspend fun getProviderLikedSongs(collection: ProviderLikedSongs): List<ProviderSong>? {
            val result = CompletableDeferred<List<ProviderSong>?>()
            likedCalls += collection to result
            return result.await()
        }

        override suspend fun getProviderLikedSongStates(
            extensionId: String,
            trackRefs: List<ExtensionTrackRef>
        ): Map<ExtensionTrackRef, Boolean>? {
            val call = LikedStateCall(extensionId, trackRefs)
            likedStateCalls += call
            return call.result.await()
        }

        class SongCall(
            val refresh: Boolean,
            val result: CompletableDeferred<List<ProviderSong>?> = CompletableDeferred()
        )

        class DetailCall(val result: CompletableDeferred<List<ProviderSong>?> = CompletableDeferred())
        class LikedStateCall(
            val extensionId: String,
            val trackRefs: List<ExtensionTrackRef>,
            val result: CompletableDeferred<Map<ExtensionTrackRef, Boolean>?> = CompletableDeferred()
        )
    }
}
