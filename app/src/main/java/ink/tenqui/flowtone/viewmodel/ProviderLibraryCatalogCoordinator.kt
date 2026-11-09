package ink.tenqui.flowtone.viewmodel

import android.util.Log
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ExtensionRuntimeState
import ink.tenqui.flowtone.data.online.LibraryProviderOption
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderLikedSongs
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSong
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
    val libraryProviderOptions: List<LibraryProviderOption> = emptyList(),
    val libraryProviderSourcesResolved: Boolean = false,
    val providerSongCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerAlbumCatalogLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap(),
    val providerUserPlaylistLoadStates: Map<String, ProviderCatalogLoadState> = emptyMap()
)

/** 统一持有曲库请求的去重与刷新版本；Runtime 和集合缓存仍由 ExtensionManager 管理。 */
internal class ProviderLibraryCatalogCoordinator(
    private val scope: CoroutineScope,
    private val gateway: ProviderLibraryCatalogGateway,
    private val onFailure: (title: String, context: String, error: Throwable) -> Unit,
    initialState: ProviderLibraryState = ProviderLibraryState(),
    private val isRefreshing: (String) -> Boolean = { false }
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
                providerSongCatalogLoadStates = emptyMap(),
                providerAlbumCatalogLoadStates = emptyMap(),
                providerUserPlaylistLoadStates = emptyMap(),
                libraryProviderSourcesResolved = false
            )
        }
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
        updateLikedSongs(collection) { it.copy(isLoading = true, loadFailed = false) }
        val request = scope.async(start = CoroutineStart.LAZY) {
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
                val registered = likedSongsLoads.remove(key) != null
                val runtime = gateway.runtimeState.value
                if (registered && !runtime.isReloading && runtime.generation == generation) {
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

    override suspend fun getProviderPlaylistSongs(extensionId: String, playlistId: String): List<ProviderSong>? =
        extensionManager.getProviderPlaylistSongs(extensionId, playlistId)
}
