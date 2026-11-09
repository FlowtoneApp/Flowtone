package ink.tenqui.flowtone.viewmodel

import android.util.Log
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ExtensionRuntimeState
import ink.tenqui.flowtone.data.online.LibraryProviderOption
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderLikedSongs
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.ProviderSongLikedStateIdentity
import ink.tenqui.flowtone.data.online.ProviderSongLikedStatus
import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProviderLibraryState(
    val providerSongs: Map<String, List<ProviderSong>> = emptyMap(),
    val providerAlbums: Map<String, List<ProviderAlbum>> = emptyMap(),
    val providerUserPlaylists: Map<String, List<ProviderPlaylist>> = emptyMap(),
    val providerPlaylistDetails: Map<String, ProviderPlaylistDetailState> = emptyMap(),
    val providerLikedSongs: Map<ProviderLikedSongs, ProviderLikedSongsState> = emptyMap(),
    val providerSongLikedStates: Map<ProviderSongLikedStateIdentity, ProviderSongLikedStatus> = emptyMap(),
    val likedStateRuntimeGeneration: Long = -1,
    val likedStateAccountGeneration: Long = -1,
    val libraryProviderOptions: List<LibraryProviderOption> = emptyList(),
    val libraryProviderSourcesResolved: Boolean = false,
    val providerSongCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerAlbumCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerUserPlaylistLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap()
)

private data class ProviderLikedSongStatusCacheKey(
    val providerId: String,
    val runtimeGeneration: Long,
    val accountContextGeneration: Long,
    val opaqueSongId: String
)

private data class CachedProviderLikedSongStatus(
    val status: ProviderSongLikedStatus,
    val lastAttemptAtMillis: Long
)

internal const val ProviderLikedSongStatusBatchSize = 50
private const val UnknownRetryCooldownMillis = 5_000L
private const val MaxCachedLikedSongStatuses = 500

/** 统一持有曲库请求的去重与刷新版本；Runtime 和集合缓存仍由 ExtensionManager 管理。 */
internal class ProviderLibraryCatalogCoordinator(
    private val scope: CoroutineScope,
    private val gateway: ProviderLibraryCatalogGateway,
    private val onFailure: (title: String, context: String, error: Throwable) -> Unit,
    initialState: ProviderLibraryState = ProviderLibraryState(),
    private val isRefreshing: (String) -> Boolean = { false },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<ProviderLibraryState> = _state.asStateFlow()
    private val providerCollectionLoads = mutableSetOf<String>()
    private val providerRefreshVersions = mutableMapOf<String, Long>()
    private val requestedProviderCollectionIds = mutableSetOf<String>()
    private val requestedProviderUserPlaylistIds = mutableSetOf<String>()
    private val requestedProviderPlaylists = mutableMapOf<String, ProviderPlaylistSearchItem>()
    private val requestedLikedSongs = mutableSetOf<ProviderLikedSongs>()
    private val likedSongsLoads = mutableMapOf<Pair<Long, ProviderLikedSongs>, Deferred<Boolean>>()
    private val providerLikedSongStatusCache = linkedMapOf<ProviderLikedSongStatusCacheKey, CachedProviderLikedSongStatus>()
    private val providerLikedSongStatusLoads = mutableSetOf<ProviderLikedSongStatusCacheKey>()
    private val requestedProviderLikedSongChecks = linkedMapOf<ProviderSongLikedStateIdentity, ProviderSong>()
    private val likedSongWrites = mutableSetOf<ProviderLikedSongStatusCacheKey>()
    private val likedSongWriteVersions = mutableMapOf<ProviderLikedSongStatusCacheKey, Long>()

    private fun ensureLikedStateContext(runtime: ExtensionRuntimeState) {
        _state.update { state ->
            if (state.likedStateRuntimeGeneration == runtime.generation &&
                state.likedStateAccountGeneration == runtime.accountContextGeneration) state
            else state.copy(
                providerSongLikedStates = emptyMap(),
                likedStateRuntimeGeneration = runtime.generation,
                likedStateAccountGeneration = runtime.accountContextGeneration
            )
        }
    }

    // 与 ViewModel 的 UI 状态更新一样，这些请求登记仅从主线程访问。
    fun beginRefresh(providerId: String): Long =
        ((providerRefreshVersions[providerId] ?: 0L) + 1L).also {
            providerRefreshVersions[providerId] = it
        }

    fun invalidateRuntime() {
        providerCollectionLoads.clear()
        likedSongsLoads.clear()
        _state.update {
            it.copy(
                providerSongs = emptyMap(),
                providerAlbums = emptyMap(),
                providerUserPlaylists = emptyMap(),
                providerPlaylistDetails = emptyMap(),
                providerLikedSongs = emptyMap(),
                providerSongLikedStates = emptyMap(),
                providerSongCatalogLoadStates = emptyMap(),
                providerAlbumCatalogLoadStates = emptyMap(),
                providerUserPlaylistLoadStates = emptyMap(),
                libraryProviderSourcesResolved = false
            )
        }
        providerLikedSongStatusCache.clear()
        providerLikedSongStatusLoads.clear()
        requestedProviderLikedSongChecks.clear()
    }

    /** Credential/configuration changes keep the runtime alive but invalidate account-scoped facts. */
    fun invalidateProviderLikedSongStates() {
        likedSongsLoads.values.toList().forEach { it.cancel() }
        likedSongsLoads.clear()
        providerLikedSongStatusCache.clear()
        providerLikedSongStatusLoads.clear()
        _state.update { it.copy(providerSongLikedStates = emptyMap(), providerLikedSongs = emptyMap(), likedStateRuntimeGeneration = -1, likedStateAccountGeneration = -1) }
        requestedProviderLikedSongChecks.values.toList()
            .groupBy(ProviderSong::providerId)
            .values
            .forEach { loadProviderLikedSongStates(it, refresh = true) }
        requestedLikedSongs.toList().forEach { loadProviderLikedSongs(it) }
    }

    fun loadProviderLikedSongStates(songs: List<ProviderSong>, refresh: Boolean = false) {
        songs.asSequence()
            .filter { it.trackRef.extensionId == it.providerId && it.id.isNotBlank() }
            .groupBy(ProviderSong::providerId)
            .forEach { (providerId, providerSongs) ->
                val runtime = gateway.runtimeState.value
                if (runtime.isReloading ||
                    AtomicCapabilityId.UserLikedSongsCheck !in gateway.providerCapabilities(providerId)
                ) return@forEach
                ensureLikedStateContext(runtime)

                val now = currentTimeMillis()
                val uniqueSongs = providerSongs.distinctBy(ProviderSong::identity)
                val keys = uniqueSongs.associateWith { song ->
                    ProviderLikedSongStatusCacheKey(
                        providerId = providerId,
                        runtimeGeneration = runtime.generation,
                        accountContextGeneration = runtime.accountContextGeneration,
                        opaqueSongId = song.trackRef.opaqueId
                    )
                }
                val requestSongs = mutableListOf<ProviderSong>()
                val visibleStatuses = mutableMapOf<ProviderSongLikedStateIdentity, ProviderSongLikedStatus>()
                uniqueSongs.forEach { song ->
                    val identity = ProviderSongLikedStateIdentity(song.trackRef)
                    val key = requireNotNull(keys[song])
                    val cached = providerLikedSongStatusCache[key]
                    if (cached != null) visibleStatuses[identity] = cached.status
                    val shouldQuery = when {
                        key in providerLikedSongStatusLoads -> false
                        refresh -> true
                        cached == null -> true
                        cached.status == ProviderSongLikedStatus.Unknown ->
                            now - cached.lastAttemptAtMillis >= UnknownRetryCooldownMillis
                        else -> false
                    }
                    if (shouldQuery) {
                        requestSongs += song
                        rememberProviderLikedSongCheck(identity, song)
                        visibleStatuses[identity] = ProviderSongLikedStatus.Unknown
                        cacheProviderLikedSongStatus(
                            key,
                            CachedProviderLikedSongStatus(
                                status = ProviderSongLikedStatus.Unknown,
                                lastAttemptAtMillis = now
                            )
                        )
                        providerLikedSongStatusLoads += key
                    }
                }
                if (visibleStatuses.isNotEmpty()) {
                    _state.update { state ->
                        state.copy(likedStateRuntimeGeneration = runtime.generation, likedStateAccountGeneration = runtime.accountContextGeneration, providerSongLikedStates = boundedProviderSongLikedStates(
                            state.providerSongLikedStates,
                            visibleStatuses
                        ))
                    }
                }
                if (requestSongs.isNotEmpty()) {
                    scope.launch {
                        val pendingSongs = requestSongs.toMutableSet()
                        try {
                            requestSongs.chunked(ProviderLikedSongStatusBatchSize).forEach { batch ->
                                queryProviderLikedSongStatusBatch(
                                    providerId = providerId,
                                    songs = batch,
                                    keys = keys,
                                    runtimeGeneration = runtime.generation,
                                    accountContextGeneration = runtime.accountContextGeneration
                                )
                                pendingSongs.removeAll(batch.toSet())
                            }
                        } finally {
                            pendingSongs.forEach { song ->
                                keys[song]?.let { key ->
                                    providerLikedSongStatusLoads.remove(key)
                                    if (providerLikedSongStatusCache[key]?.status == ProviderSongLikedStatus.Unknown) {
                                        providerLikedSongStatusCache.remove(key)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    /** Future write operations can update the read cache without coupling it to local likes. */
    fun updateProviderLikedSongState(song: ProviderSong, liked: Boolean) {
        val runtime = gateway.runtimeState.value
        if (runtime.isReloading ||
            AtomicCapabilityId.UserLikedSongsCheck !in gateway.providerCapabilities(song.providerId)
        ) return
        ensureLikedStateContext(runtime)
        val status = if (liked) ProviderSongLikedStatus.Liked else ProviderSongLikedStatus.NotLiked
        val key = ProviderLikedSongStatusCacheKey(
            providerId = song.providerId,
            runtimeGeneration = runtime.generation,
            accountContextGeneration = runtime.accountContextGeneration,
            opaqueSongId = song.trackRef.opaqueId
        )
        cacheProviderLikedSongStatus(key, CachedProviderLikedSongStatus(status, currentTimeMillis()))
        rememberProviderLikedSongCheck(ProviderSongLikedStateIdentity(song.trackRef), song)
        _state.update { state ->
            state.copy(
                providerSongLikedStates = boundedProviderSongLikedStates(
                    state.providerSongLikedStates,
                    mapOf(ProviderSongLikedStateIdentity(song.trackRef) to status)
                ),
                likedStateRuntimeGeneration = runtime.generation,
                likedStateAccountGeneration = runtime.accountContextGeneration
            )
        }
    }

    suspend fun setProviderLikedSongState(song: ProviderSong, liked: Boolean, expectedGeneration: Long? = null, expectedAccountGeneration: Long? = null): Boolean {
        val runtime = gateway.runtimeState.value
        if (runtime.isReloading || song.trackRef.extensionId != song.providerId ||
            song.trackRef.opaqueId.isBlank() ||
            (expectedGeneration != null && expectedGeneration != runtime.generation) ||
            (expectedAccountGeneration != null && expectedAccountGeneration != runtime.accountContextGeneration) ||
            AtomicCapabilityId.UserLikedSongsWrite !in gateway.providerCapabilities(song.providerId)
        ) return false
        val key = ProviderLikedSongStatusCacheKey(song.providerId, runtime.generation,
            runtime.accountContextGeneration, song.trackRef.opaqueId)
        if (!likedSongWrites.add(key)) return false
        try {
            val success = try {
                gateway.setProviderLikedSongState(song.providerId, song.trackRef, liked, runtime.generation, runtime.accountContextGeneration)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("FlowtoneLibrary", "Provider liked-song write failed", error)
                false
            }
            val current = gateway.runtimeState.value
            if (!success || current.isReloading || current.generation != runtime.generation ||
                current.accountContextGeneration != runtime.accountContextGeneration) return false
            likedSongWriteVersions[key] = (likedSongWriteVersions[key] ?: 0L) + 1L
            updateProviderLikedSongState(song, liked)
            val collection = ProviderLikedSongs(song.providerId)
            if (AtomicCapabilityId.UserLikedSongsList in gateway.providerCapabilities(song.providerId) &&
                (collection in requestedLikedSongs || collection in _state.value.providerLikedSongs)) {
                likedSongsLoads.remove(runtime.generation to collection)?.cancel()
                requestLikedSongs(collection, runtime.generation, reportFailure = true)
            }
            return true
        } finally {
            likedSongWrites.remove(key)
        }
    }

    private suspend fun queryProviderLikedSongStatusBatch(
        providerId: String,
        songs: List<ProviderSong>,
        keys: Map<ProviderSong, ProviderLikedSongStatusCacheKey>,
        runtimeGeneration: Long,
        accountContextGeneration: Long
    ) {
        val now = currentTimeMillis()
        val writeVersions = songs.associateWith { song -> likedSongWriteVersions[keys[song]] ?: 0L }
        val results = try {
            gateway.getProviderLikedSongStates(providerId, songs.map(ProviderSong::trackRef).distinct())
        } catch (cancelled: CancellationException) {
            songs.forEach { song ->
                keys[song]?.let { key ->
                    providerLikedSongStatusLoads.remove(key)
                    providerLikedSongStatusCache.remove(key)
                }
            }
            throw cancelled
        } catch (error: Throwable) {
            Log.w("FlowtoneLibrary", "Provider $providerId liked-song status check failed", error)
            null
        }
        val runtime = gateway.runtimeState.value
        val isCurrentContext = !runtime.isReloading &&
            runtime.generation == runtimeGeneration &&
            runtime.accountContextGeneration == accountContextGeneration
        songs.forEach { song -> keys[song]?.let(providerLikedSongStatusLoads::remove) }
        if (!isCurrentContext) return

        val resolved = buildMap {
            songs.forEach { song ->
                if ((likedSongWriteVersions[keys[song]] ?: 0L) != writeVersions[song]) return@forEach
                val identity = ProviderSongLikedStateIdentity(song.trackRef)
                val liked = results?.get(song.trackRef)
                val status = when (liked) {
                    true -> ProviderSongLikedStatus.Liked
                    false -> ProviderSongLikedStatus.NotLiked
                    null -> ProviderSongLikedStatus.Unknown
                }
                keys[song]?.let { key ->
                    cacheProviderLikedSongStatus(key, CachedProviderLikedSongStatus(status, now))
                    put(identity, status)
                }
            }
        }
        if (resolved.isNotEmpty()) {
            _state.update { state ->
                state.copy(likedStateRuntimeGeneration = runtime.generation, likedStateAccountGeneration = runtime.accountContextGeneration, providerSongLikedStates = boundedProviderSongLikedStates(
                    state.providerSongLikedStates,
                    resolved
                ))
            }
        }
    }

    private fun rememberProviderLikedSongCheck(identity: ProviderSongLikedStateIdentity, song: ProviderSong) {
        requestedProviderLikedSongChecks.remove(identity)
        requestedProviderLikedSongChecks[identity] = song
        while (requestedProviderLikedSongChecks.size > MaxCachedLikedSongStatuses) {
            val oldest = requestedProviderLikedSongChecks.keys.first()
            requestedProviderLikedSongChecks.remove(oldest)
            providerLikedSongStatusCache.keys.removeAll { key ->
                key.providerId == oldest.providerId && key.opaqueSongId == oldest.opaqueSongId
            }
            _state.update { state ->
                state.copy(providerSongLikedStates = state.providerSongLikedStates - oldest)
            }
        }
    }

    private fun cacheProviderLikedSongStatus(
        key: ProviderLikedSongStatusCacheKey,
        status: CachedProviderLikedSongStatus
    ) {
        providerLikedSongStatusCache.remove(key)
        providerLikedSongStatusCache[key] = status
        while (providerLikedSongStatusCache.size > MaxCachedLikedSongStatuses) {
            val oldestKey = providerLikedSongStatusCache.keys.first()
            providerLikedSongStatusCache.remove(oldestKey)
            val identity = ProviderSongLikedStateIdentity(oldestKey.providerId, oldestKey.opaqueSongId)
            requestedProviderLikedSongChecks.remove(identity)
            _state.update { state ->
                state.copy(providerSongLikedStates = state.providerSongLikedStates - identity)
            }
        }
    }

    private fun boundedProviderSongLikedStates(
        current: Map<ProviderSongLikedStateIdentity, ProviderSongLikedStatus>,
        updates: Map<ProviderSongLikedStateIdentity, ProviderSongLikedStatus>
    ): Map<ProviderSongLikedStateIdentity, ProviderSongLikedStatus> = LinkedHashMap<ProviderSongLikedStateIdentity, ProviderSongLikedStatus>().apply {
        putAll(current)
        putAll(updates)
        while (size > MaxCachedLikedSongStatuses) remove(keys.first())
    }

    fun updateSources(options: List<LibraryProviderOption>, resolved: Boolean) {
        _state.update {
            it.copy(
                libraryProviderOptions = if (resolved) options else it.libraryProviderOptions,
                libraryProviderSourcesResolved = resolved
            )
        }
    }

    private fun reportExtensionContentFailure(title: String, context: String, error: Throwable) {
        onFailure(title, context, error)
    }

    fun reloadRequestedProviderData() {
        requestedProviderCollectionIds.toList().forEach(::loadProviderEntityCollections)
        requestedProviderUserPlaylistIds.toList().forEach(::loadProviderUserPlaylists)
        requestedLikedSongs.toList().forEach { loadProviderLikedSongs(it) }
        requestedProviderPlaylists.values.toList().forEach { playlist ->
            if (gateway.isMusicProviderRuntimeAvailable(playlist.providerId)) {
                loadProviderPlaylist(playlist, preferEmbeddedSongs = false)
            } else {
                _state.update { state ->
                    state.copy(
                        providerPlaylistDetails = state.providerPlaylistDetails +
                            (playlist.identity.stableKey to ProviderPlaylistDetailState(loadFailed = true))
                    )
                }
            }
        }
    }

    suspend fun refreshProviderLibrary(providerId: String, version: Long): Boolean {
        val generation = gateway.runtimeState.value.generation
        if (!gateway.isMusicProviderRuntimeAvailable(providerId)) return false
        val capabilities = gateway.providerCapabilities(providerId)
        var success = true
        suspend fun <T> fetch(label: String, operation: suspend () -> T?): T? {
            return try {
                operation().also { if (it == null) success = false }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                success = false
                Log.w("FlowtoneLibrary", "Provider $providerId $label refresh failed", error)
                null
            }
        }
        val wantsSongs = AtomicCapabilityId.CatalogSongsList in capabilities
        val wantsAlbums = AtomicCapabilityId.CatalogAlbumsList in capabilities
        val wantsPlaylists = AtomicCapabilityId.UserPlaylistsList in capabilities
        val likedSongsRefresh = if (AtomicCapabilityId.UserLikedSongsList in capabilities) {
            val collection = ProviderLikedSongs(providerId)
            requestedLikedSongs += collection
            requestLikedSongs(collection, generation, reportFailure = false)
        } else null
        val songs = if (wantsSongs) fetch("songs") { gateway.getProviderSongs(providerId, refresh = true) } else null
        val albums = if (wantsAlbums) fetch("albums") { gateway.getProviderAlbums(providerId, refresh = true) } else null
        val playlists = if (wantsPlaylists) fetch("playlists") { gateway.getProviderUserPlaylists(providerId) } else null
        val likedSongsLoaded = likedSongsRefresh?.await() ?: true
        val runtimeState = gateway.runtimeState.value
        if (runtimeState.isReloading || runtimeState.generation != generation || providerRefreshVersions[providerId] != version) return false
        _state.update { state ->
            state.copy(
                providerSongs = if (songs != null) state.providerSongs + (providerId to songs) else state.providerSongs,
                providerAlbums = if (albums != null) state.providerAlbums + (providerId to albums) else state.providerAlbums,
                providerUserPlaylists = if (playlists != null) state.providerUserPlaylists + (providerId to playlists) else state.providerUserPlaylists,
                providerSongCatalogLoadStates = if (wantsSongs) state.providerSongCatalogLoadStates +
                    (providerId to if (songs != null || providerId in state.providerSongs) ProviderCatalogLoadState.Loaded else ProviderCatalogLoadState.Failed) else state.providerSongCatalogLoadStates,
                providerAlbumCatalogLoadStates = if (wantsAlbums) state.providerAlbumCatalogLoadStates +
                    (providerId to if (albums != null || providerId in state.providerAlbums) ProviderCatalogLoadState.Loaded else ProviderCatalogLoadState.Failed) else state.providerAlbumCatalogLoadStates,
                providerUserPlaylistLoadStates = if (wantsPlaylists) state.providerUserPlaylistLoadStates +
                    (providerId to if (playlists != null || providerId in state.providerUserPlaylists) ProviderCatalogLoadState.Loaded else ProviderCatalogLoadState.Failed) else state.providerUserPlaylistLoadStates
            )
        }
        return success && likedSongsLoaded
    }

    fun loadProviderLikedSongs(collection: ProviderLikedSongs, refresh: Boolean = false) {
        requestedLikedSongs += collection
        val runtime = gateway.runtimeState.value
        if (runtime.isReloading) return
        if (AtomicCapabilityId.UserLikedSongsList !in gateway.providerCapabilities(collection.providerId)) {
            updateLikedSongs(collection) { ProviderLikedSongsState(loadFailed = true) }
            return
        }
        val previous = _state.value.providerLikedSongs[collection]
        if (previous?.isLoading == true || (!refresh && previous?.songs != null)) return
        requestLikedSongs(collection, runtime.generation, reportFailure = refresh)
    }

    private fun requestLikedSongs(
        collection: ProviderLikedSongs,
        generation: Long,
        reportFailure: Boolean
    ): Deferred<Boolean> {
        val key = generation to collection
        likedSongsLoads[key]?.let { return it }
        val accountGeneration = gateway.runtimeState.value.accountContextGeneration
        updateLikedSongs(collection) { it.copy(isLoading = true, loadFailed = false) }
        lateinit var request: Deferred<Boolean>
        request = scope.async(start = CoroutineStart.LAZY) {
            var songs: List<ProviderSong>? = null
            var failure: Throwable? = null
            try {
                songs = gateway.getProviderLikedSongs(collection)
                songs != null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error
                false
            } finally {
                val registered = likedSongsLoads.remove(key, request)
                val runtime = gateway.runtimeState.value
                if (registered && !runtime.isReloading && runtime.generation == generation &&
                    runtime.accountContextGeneration == accountGeneration) {
                    updateLikedSongs(collection) { previous ->
                        ProviderLikedSongsState(songs = songs ?: previous.songs, loadFailed = songs == null)
                    }
                    if (songs == null && reportFailure) {
                        reportExtensionContentFailure(
                            title = "扩展喜欢列表读取失败",
                            context = "扩展 ID：${collection.providerId}",
                            error = failure ?: IllegalStateException("扩展没有返回喜欢的歌曲列表。")
                        )
                    }
                }
            }
        }
        likedSongsLoads[key] = request
        request.start()
        return request
    }

    private fun updateLikedSongs(
        collection: ProviderLikedSongs,
        update: (ProviderLikedSongsState) -> ProviderLikedSongsState
    ) {
        _state.update { state ->
            val previous = state.providerLikedSongs[collection] ?: ProviderLikedSongsState()
            state.copy(providerLikedSongs = state.providerLikedSongs + (collection to update(previous)))
        }
    }

    fun loadProviderEntityCollections(providerId: String) {
        val normalizedProviderId = providerId.trim()
        if (normalizedProviderId.isEmpty()) return
        requestedProviderCollectionIds += normalizedProviderId
        val requestRuntime = gateway.runtimeState.value
        if (requestRuntime.isReloading) return
        val capabilities = gateway.providerCapabilities(normalizedProviderId)
        val supportsSongCatalog = AtomicCapabilityId.CatalogSongsList in capabilities
        if (!supportsSongCatalog) {
            _state.update { state ->
                state.copy(
                    providerSongCatalogLoadStates = state.providerSongCatalogLoadStates +
                        (normalizedProviderId to ProviderCatalogLoadState.Unavailable)
                )
            }
        }
        if (isRefreshing("extension:$normalizedProviderId")) return
        val refreshVersion = providerRefreshVersions[normalizedProviderId] ?: 0L
        val runtimeGeneration = requestRuntime.generation

        if (supportsSongCatalog) {
            val loadKey = "$runtimeGeneration:song:$normalizedProviderId"
            if (normalizedProviderId in _state.value.providerSongs) {
                _state.update { state ->
                    state.copy(
                        providerSongCatalogLoadStates = state.providerSongCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                    )
                }
            } else if (providerCollectionLoads.add(loadKey)) {
                _state.update { state ->
                    state.copy(
                        providerSongCatalogLoadStates = state.providerSongCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loading)
                    )
                }
                scope.launch {
                    var songs: List<ProviderSong>? = null
                    var failure: Throwable? = null
                    var cancelled = false
                    try {
                        songs = gateway.getProviderSongs(normalizedProviderId)
                    } catch (error: CancellationException) {
                        cancelled = true
                        throw error
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        providerCollectionLoads.remove(loadKey)
                        val runtimeState = gateway.runtimeState.value
                        if (!cancelled && !runtimeState.isReloading &&
                            runtimeState.generation == runtimeGeneration &&
                            (providerRefreshVersions[normalizedProviderId] ?: 0L) == refreshVersion
                        ) {
                            if (songs == null) {
                                reportExtensionContentFailure(
                                    title = "扩展歌曲列表读取失败",
                                    context = "扩展 ID：$normalizedProviderId",
                                    error = failure ?: IllegalStateException("扩展声明了歌曲列表能力，但没有返回结果。")
                                )
                            }
                            _state.update { state ->
                                if (songs == null) {
                                    state.copy(
                                        providerSongCatalogLoadStates =
                                            state.providerSongCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Failed)
                                    )
                                } else {
                                    state.copy(
                                        providerSongs = state.providerSongs +
                                            (normalizedProviderId to songs),
                                        providerSongCatalogLoadStates =
                                            state.providerSongCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (AtomicCapabilityId.CatalogAlbumsList in capabilities) {
            val loadKey = "$runtimeGeneration:album:$normalizedProviderId"
            if (normalizedProviderId in _state.value.providerAlbums) {
                _state.update { state ->
                    state.copy(
                        providerAlbumCatalogLoadStates = state.providerAlbumCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                    )
                }
            } else if (providerCollectionLoads.add(loadKey)) {
                _state.update { state ->
                    state.copy(
                        providerAlbumCatalogLoadStates = state.providerAlbumCatalogLoadStates +
                            (normalizedProviderId to ProviderCatalogLoadState.Loading)
                    )
                }
                scope.launch {
                    var albums: List<ProviderAlbum>? = null
                    var failure: Throwable? = null
                    var cancelled = false
                    try {
                        albums = gateway.getProviderAlbums(normalizedProviderId)
                    } catch (error: CancellationException) {
                        cancelled = true
                        throw error
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        providerCollectionLoads.remove(loadKey)
                        val runtimeState = gateway.runtimeState.value
                        if (!cancelled && !runtimeState.isReloading &&
                            runtimeState.generation == runtimeGeneration &&
                            (providerRefreshVersions[normalizedProviderId] ?: 0L) == refreshVersion
                        ) {
                            if (albums == null) {
                                reportExtensionContentFailure(
                                    title = "扩展专辑列表读取失败",
                                    context = "扩展 ID：$normalizedProviderId",
                                    error = failure ?: IllegalStateException("扩展声明了专辑列表能力，但没有返回结果。")
                                )
                            }
                            _state.update { state ->
                                if (albums == null) {
                                    state.copy(
                                        providerAlbumCatalogLoadStates =
                                            state.providerAlbumCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Failed)
                                    )
                                } else {
                                    state.copy(
                                        providerAlbums = state.providerAlbums +
                                            (normalizedProviderId to albums),
                                        providerAlbumCatalogLoadStates =
                                            state.providerAlbumCatalogLoadStates +
                                                (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadProviderUserPlaylists(providerId: String) {
        val normalizedProviderId = providerId.trim()
        if (normalizedProviderId.isEmpty()) return
        requestedProviderUserPlaylistIds += normalizedProviderId
        if (isRefreshing("extension:$normalizedProviderId")) return
        val refreshVersion = providerRefreshVersions[normalizedProviderId] ?: 0L
        if (AtomicCapabilityId.UserPlaylistsList !in
            gateway.providerCapabilities(normalizedProviderId)
        ) return

        val runtimeGeneration = gateway.runtimeState.value.generation
        val loadKey = "$runtimeGeneration:user-playlists:$normalizedProviderId"
        if (normalizedProviderId in _state.value.providerUserPlaylists) {
            _state.update { state ->
                state.copy(
                    providerUserPlaylistLoadStates = state.providerUserPlaylistLoadStates +
                        (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                )
            }
        } else if (providerCollectionLoads.add(loadKey)) {
            _state.update { state ->
                state.copy(
                    providerUserPlaylistLoadStates = state.providerUserPlaylistLoadStates +
                        (normalizedProviderId to ProviderCatalogLoadState.Loading)
                )
            }
            scope.launch {
                var playlists: List<ProviderPlaylist>? = null
                var failure: Throwable? = null
                var cancelled = false
                try {
                    playlists = gateway.getProviderUserPlaylists(normalizedProviderId)
                } catch (error: CancellationException) {
                    cancelled = true
                    throw error
                } catch (error: Throwable) {
                    failure = error
                } finally {
                    providerCollectionLoads.remove(loadKey)
                    val runtimeState = gateway.runtimeState.value
                    if (!cancelled && !runtimeState.isReloading &&
                        runtimeState.generation == runtimeGeneration &&
                        (providerRefreshVersions[normalizedProviderId] ?: 0L) == refreshVersion
                    ) {
                        if (playlists == null) {
                            reportExtensionContentFailure(
                                title = "扩展歌单读取失败",
                                context = "扩展 ID：$normalizedProviderId",
                                error = failure ?: IllegalStateException("扩展声明了用户歌单能力，但没有返回结果。")
                            )
                        }
                        _state.update { state ->
                            if (playlists == null) {
                                state.copy(
                                    providerUserPlaylistLoadStates =
                                        state.providerUserPlaylistLoadStates +
                                            (normalizedProviderId to ProviderCatalogLoadState.Failed)
                                )
                            } else {
                                state.copy(
                                    providerUserPlaylists = state.providerUserPlaylists +
                                        (normalizedProviderId to playlists),
                                    providerUserPlaylistLoadStates =
                                        state.providerUserPlaylistLoadStates +
                                            (normalizedProviderId to ProviderCatalogLoadState.Loaded)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadProviderPlaylist(playlist: ProviderPlaylistSearchItem) {
        val detailKey = playlist.identity.stableKey
        requestedProviderPlaylists[detailKey] = playlist
        loadProviderPlaylist(playlist, preferEmbeddedSongs = true)
    }

    private fun loadProviderPlaylist(
        playlist: ProviderPlaylistSearchItem,
        preferEmbeddedSongs: Boolean
    ) {
        val detailKey = playlist.identity.stableKey
        if (detailKey in _state.value.providerPlaylistDetails) return
        val requestRuntime = gateway.runtimeState.value
        if (requestRuntime.isReloading) return
        if (preferEmbeddedSongs && playlist.songs.isNotEmpty()) {
            _state.update { state ->
                state.copy(
                    providerPlaylistDetails = state.providerPlaylistDetails +
                        (detailKey to ProviderPlaylistDetailState(songs = playlist.songs))
                )
            }
            return
        }
        val runtimeGeneration = requestRuntime.generation
        val loadKey = "$runtimeGeneration:playlist:$detailKey"
        if (!providerCollectionLoads.add(loadKey)) return
        _state.update { state ->
            state.copy(
                providerPlaylistDetails = state.providerPlaylistDetails +
                    (detailKey to ProviderPlaylistDetailState(songs = playlist.songs, isLoading = true))
            )
        }
        scope.launch {
            var songs: List<ProviderSong>? = null
            var failure: Throwable? = null
            var cancelled = false
            try {
                songs = gateway.getProviderPlaylistSongs(
                    playlist.providerId,
                    playlist.id
                )
            } catch (error: CancellationException) {
                cancelled = true
                throw error
            } catch (error: Throwable) {
                failure = error
            } finally {
                providerCollectionLoads.remove(loadKey)
                val runtimeState = gateway.runtimeState.value
                if (!cancelled && !runtimeState.isReloading &&
                    runtimeState.generation == runtimeGeneration
                ) {
                    val effectiveSongs = songs ?: playlist.songs.takeIf { it.isNotEmpty() }
                    val loadFailed = effectiveSongs == null
                    _state.update { state ->
                        state.copy(
                            providerPlaylistDetails = state.providerPlaylistDetails +
                                (detailKey to ProviderPlaylistDetailState(
                                    songs = effectiveSongs.orEmpty(),
                                    loadFailed = loadFailed
                                ))
                        )
                    }
                    if (failure != null || loadFailed) {
                        reportExtensionContentFailure(
                            title = "扩展歌单曲目读取失败",
                            context = "扩展 ID：${playlist.providerId}\n歌单 ID：${playlist.id}",
                            error = failure ?: IllegalStateException("扩展没有返回此歌单的曲目。")
                        )
                    }
                }
            }
        }
    }
}

/** 隔离 Android Runtime，便于验证共享请求和刷新结果的先后顺序。 */
internal interface ProviderLibraryCatalogGateway {
    val runtimeState: StateFlow<ExtensionRuntimeState>
    fun providerCapabilities(extensionId: String): CanonicalAtomicCapabilitySet
    fun isMusicProviderRuntimeAvailable(extensionId: String): Boolean
    suspend fun getProviderSongs(extensionId: String, refresh: Boolean = false): List<ProviderSong>?
    suspend fun getProviderAlbums(extensionId: String, refresh: Boolean = false): List<ProviderAlbum>?
    suspend fun getProviderUserPlaylists(extensionId: String): List<ProviderPlaylist>?
    suspend fun getProviderLikedSongs(collection: ProviderLikedSongs): List<ProviderSong>?
    suspend fun getProviderLikedSongStates(
        extensionId: String,
        trackRefs: List<ExtensionTrackRef>
    ): Map<ExtensionTrackRef, Boolean>? = null
    suspend fun setProviderLikedSongState(extensionId: String, trackRef: ExtensionTrackRef, liked: Boolean, generation: Long, accountContextGeneration: Long): Boolean = false
    suspend fun getProviderPlaylistSongs(extensionId: String, playlistId: String): List<ProviderSong>?
}

internal class ExtensionManagerProviderLibraryCatalogGateway(
    private val extensionManager: ExtensionManager
) : ProviderLibraryCatalogGateway {
    override val runtimeState: StateFlow<ExtensionRuntimeState> = extensionManager.runtimeState

    override fun providerCapabilities(extensionId: String): CanonicalAtomicCapabilitySet =
        extensionManager.providerCapabilities(extensionId)

    override fun isMusicProviderRuntimeAvailable(extensionId: String): Boolean =
        extensionManager.isMusicProviderRuntimeAvailable(extensionId)

    override suspend fun getProviderSongs(extensionId: String, refresh: Boolean): List<ProviderSong>? =
        extensionManager.getProviderSongs(extensionId, refresh)

    override suspend fun getProviderAlbums(extensionId: String, refresh: Boolean): List<ProviderAlbum>? =
        extensionManager.getProviderAlbums(extensionId, refresh)

    override suspend fun getProviderUserPlaylists(extensionId: String): List<ProviderPlaylist>? =
        extensionManager.getProviderUserPlaylists(extensionId)

    override suspend fun getProviderLikedSongs(collection: ProviderLikedSongs): List<ProviderSong>? =
        extensionManager.getProviderLikedSongs(collection)

    override suspend fun getProviderLikedSongStates(
        extensionId: String,
        trackRefs: List<ExtensionTrackRef>
    ): Map<ExtensionTrackRef, Boolean>? = extensionManager.getProviderLikedSongStates(extensionId, trackRefs)

    override suspend fun setProviderLikedSongState(extensionId: String, trackRef: ExtensionTrackRef, liked: Boolean, generation: Long, accountContextGeneration: Long): Boolean =
        extensionManager.setProviderLikedSongState(extensionId, trackRef, liked, generation, accountContextGeneration)

    override suspend fun getProviderPlaylistSongs(extensionId: String, playlistId: String): List<ProviderSong>? =
        extensionManager.getProviderPlaylistSongs(extensionId, playlistId)
}
