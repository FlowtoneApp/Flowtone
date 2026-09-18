package ink.tenqui.flowtone.data.online

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import ink.tenqui.flowtone.BuildConfig
import ink.tenqui.flowtone.core.model.normalizeMusicSourceHost
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkGateway
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.network.ExtensionStreamClient
import ink.tenqui.flowtone.data.online.image.ExtensionImageFetcher
import ink.tenqui.flowtone.data.online.image.ExtensionImageKeyer
import ink.tenqui.flowtone.data.online.image.ExtensionImageNetworkHost
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageInstaller
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageInspector
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageSnapshotHandle
import ink.tenqui.flowtone.data.online.packageformat.ExtensionProviderVisualResolver
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import ink.tenqui.flowtone.data.online.capability.ExtensionRuntimeCapabilityPolicy
import ink.tenqui.flowtone.data.online.runtime.ExtensionResultCache
import ink.tenqui.flowtone.data.online.runtime.ExtensionPrivateCache
import ink.tenqui.flowtone.data.online.runtime.JavaScriptArtistAvatarExtension
import ink.tenqui.flowtone.data.online.runtime.JavaScriptArtistMetadataExtension
import ink.tenqui.flowtone.data.online.runtime.JavaScriptExtensionRuntime
import ink.tenqui.flowtone.data.online.runtime.JavaScriptMusicProvider
import ink.tenqui.flowtone.data.search.SearchProviderOption
import ink.tenqui.flowtone.data.online.runtime.JavaScriptSandboxHost
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType
import ink.tenqui.flowtone.core.online.ArtistAvatar
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.data.online.playback.ExtensionMediaDataSource
import ink.tenqui.flowtone.data.online.playback.ExtensionMediaSourceFactory
import ink.tenqui.flowtone.data.online.playback.ExtensionPlaybackContentCache
import ink.tenqui.flowtone.data.online.playback.ExtensionPlaybackResourceStore
import ink.tenqui.flowtone.data.online.playback.ExtensionStreamNetworkHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import coil3.ImageLoader
import coil3.svg.SvgDecoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

enum class ExtensionRuntimeReloadReason(val logValue: String) {
    Initialize("initialize"),
    Install("install"),
    Update("update"),
    Delete("delete"),
    Manual("manual")
}

data class ExtensionRuntimeState(
    val generation: Long = 0L,
    val isReloading: Boolean = false,
    val installedExtensionCount: Int = 0,
    val providerIds: Set<String> = emptySet(),
    val lastReloadErrorType: String? = null
)

/** 安装、扫描、运行和卸载外部脚本扩展的应用级所有者。 */
class ExtensionManager private constructor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val installer = ExtensionPackageInstaller(appContext.filesDir.resolve("extensions"))
    private val inspector = ExtensionPackageInspector(
        appContext.cacheDir.resolve("extension-install-previews")
    )
    private val sandboxHost = JavaScriptSandboxHost(appContext)
    private val gateway = ExtensionNetworkGateway()
    private val avatarResultCache = ExtensionResultCache()
    private val persistentAvatarCache = ArtistAvatarPersistentCache(appContext.filesDir.resolve("extension-data"))
    private val persistentArtistMetadataCache = ArtistMetadataPersistentCache(
        appContext.filesDir.resolve("extension-data")
    )
    private val privateCache = ExtensionPrivateCache(appContext.filesDir.resolve("extension-data"))
    private val mutex = Mutex()
    private val runtimeLifecycle = ExtensionRuntimeLifecycle { requestGeneration, currentGeneration ->
        Log.d(
            LogTag,
            "extension.runtime.result.discarded requestGeneration=$requestGeneration currentGeneration=$currentGeneration"
        )
    }
    private val _runtimeState = MutableStateFlow(ExtensionRuntimeState())
    private val runtimes = mutableMapOf<String, JavaScriptExtensionRuntime>()
    private val musicProviders = ConcurrentHashMap<String, JavaScriptMusicProvider>()
    private val networkClients = ConcurrentHashMap<String, ExtensionNetworkClient>()
    private val streamClients = ConcurrentHashMap<String, ExtensionStreamClient>()
    private val playbackResources = ExtensionPlaybackResourceStore()
    @UnstableApi
    private val playbackContentCacheDelegate = lazy {
        runCatching {
            ExtensionPlaybackContentCache(appContext, extensionMediaDataSourceFactory())
        }.onFailure { error ->
            Log.w(LogTag, "extension.playback.cache.unavailable", error)
        }.getOrNull()
    }
    private val playbackContentCache by playbackContentCacheDelegate
    private val presentationCache = ConcurrentHashMap<String, ProviderSong>()
    private val searchLandingCache = ConcurrentHashMap<String, ProviderSearchLanding>()
    private val songCollectionCache = ProviderCollectionSessionCache<ProviderSong>()
    private val albumCollectionCache = ProviderCollectionSessionCache<ProviderAlbum>()
    private var initialized = false
    val runtimeState: StateFlow<ExtensionRuntimeState> = _runtimeState.asStateFlow()
    private val artistAvatarRegistry = ArtistAvatarExtensionRegistry(
        resultCache = avatarResultCache,
        persistentCache = persistentAvatarCache
    )
    private val artistMetadataRegistry = ArtistMetadataExtensionRegistry(
        persistentCache = persistentArtistMetadataCache
    )
    val extensionImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .components {
                add(SvgDecoder.Factory())
                add(ExtensionImageKeyer)
                add(ExtensionImageFetcher.Factory(ExtensionImageNetworkHost(::networkClientFor)))
            }
            .build()
    }

    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        installBundledUiTestExtensions()
        check(reloadRuntimeLocked(ExtensionRuntimeReloadReason.Initialize)) {
            "扩展运行环境初始化失败"
        }
        initialized = true
    }

    suspend fun install(uri: Uri): InstalledExtension = mutex.withLock {
        val name = requireNotNull(displayName(uri)) { "无法确认扩展包文件名" }
        require(name.endsWith(".flowtone", ignoreCase = true)) { "请选择 .flowtone 扩展包" }
        withContext(NonCancellable) {
            val installedIdsBefore = installer.scan().mapTo(mutableSetOf()) { it.manifest.id }
            val installed = withContext(Dispatchers.IO) {
                requireNotNull(appContext.contentResolver.openInputStream(uri)) { "无法读取扩展包" }
                    .use { input -> installer.install(name, input) }
            }
            val reason = if (installed.manifest.id in installedIdsBefore) {
                ExtensionRuntimeReloadReason.Update
            } else {
                ExtensionRuntimeReloadReason.Install
            }
            check(reloadRuntimeLocked(reason)) { "扩展已写入，但运行环境重载失败" }
            installed.copy(runtimeAvailable = runtimes.containsKey(installed.manifest.id))
        }
    }

    suspend fun inspect(uri: Uri): ExtensionInstallPreview = mutex.withLock {
        val name = requireNotNull(displayName(uri)) { "无法确认扩展包文件名" }
        require(name.endsWith(".flowtone", ignoreCase = true)) { "请选择 .flowtone 扩展包" }
        withContext(Dispatchers.IO) {
            val installed = installer.scan().map { it.descriptor }
            requireNotNull(appContext.contentResolver.openInputStream(uri)) { "无法读取扩展包" }
                .use { input -> inspector.inspect(name, input, installed) }
        }
    }

    /** 安装 inspect 时生成的同一份不可变快照，不再访问原始 DocumentsProvider URI。 */
    suspend fun install(handle: ExtensionPackageSnapshotHandle): InstalledExtension = mutex.withLock {
        withContext(NonCancellable) {
            val installedIdsBefore = installer.scan().mapTo(mutableSetOf()) { it.manifest.id }
            val installed = withContext(Dispatchers.IO) {
                inspector.consumeSnapshot(handle) { fileName, input ->
                    installer.install(fileName, input)
                }
            }
            val reason = if (installed.manifest.id in installedIdsBefore) {
                ExtensionRuntimeReloadReason.Update
            } else {
                ExtensionRuntimeReloadReason.Install
            }
            check(reloadRuntimeLocked(reason)) { "扩展已写入，但运行环境重载失败" }
            installed.copy(runtimeAvailable = runtimes.containsKey(installed.manifest.id))
        }
    }

    fun discardInstallPreview(handle: ExtensionPackageSnapshotHandle) {
        inspector.discard(handle)
    }

    fun installedExtensions(): List<InstalledExtension> = installer.scan().map {
        it.copy(runtimeAvailable = runtimes.containsKey(it.manifest.id))
    }

    suspend fun uninstall(extensionId: String): Boolean = mutex.withLock {
        withContext(NonCancellable) {
            val removed = withContext(Dispatchers.IO) { installer.uninstall(extensionId) }
            if (!removed) return@withContext false
            check(
                reloadRuntimeLocked(
                    reason = ExtensionRuntimeReloadReason.Delete,
                    clearExtensionDataFor = setOf(extensionId)
                )
            ) { "扩展已删除，但运行环境重载失败" }
            true
        }
    }

    suspend fun reloadRuntime(
        reason: ExtensionRuntimeReloadReason = ExtensionRuntimeReloadReason.Manual
    ): Boolean = mutex.withLock { reloadRuntimeLocked(reason) }

    /** 由 Flowtone Host 调用；JS 只提供资源字段，扩展身份不从 JS 参数读取。 */
    internal fun createPlaybackMediaItem(
        extensionId: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        mimeType: String? = null,
        type: ExtensionPlaybackResourceType = ExtensionPlaybackResourceType.Progressive,
        mediaId: String = url
    ): MediaItem {
        require(streamClients.containsKey(extensionId)) { "扩展未运行：$extensionId" }
        val resourceUri = playbackResources.register(
            ExtensionPlaybackResource(
                extensionId = extensionId,
                url = url,
                headers = headers,
                mimeType = mimeType,
                type = type
            )
        )
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(resourceUri)
            .setMimeType(
                mimeType ?: if (type == ExtensionPlaybackResourceType.Hls) {
                    MimeTypes.APPLICATION_M3U8
                } else {
                    null
                }
            )
            .build()
    }

    fun extensionMediaDataSourceFactory(): ExtensionMediaDataSource.Factory {
        return ExtensionMediaDataSource.Factory(
            resources = playbackResources,
            host = ExtensionStreamNetworkHost(::streamClientFor)
        )
    }

    /** 在线曲目只保存 Host 绑定的 track ref；播放资源由所属 runtime 按需解析。 */
    internal suspend fun resolvePlaybackResource(
        song: ProviderSong
    ): ExtensionPlaybackResource? =
        runtimeLifecycle.execute {
            val provider = musicProviders[song.trackRef.extensionId] ?: return@execute null
            if (AtomicCapabilityId.PlaybackResourceResolve !in provider.capabilities) {
                return@execute null
            }
            providerCall { provider.getPlaybackResource(song) }
                .onFailure { error ->
                    Log.w(LogTag, "extension.playback.resolve.failed extension=${song.trackRef.extensionId} type=${error.javaClass.simpleName}")
                }
                .getOrNull() ?: return@execute null
        }

    internal fun createPlaybackMediaItem(
        song: ProviderSong,
        resource: ExtensionPlaybackResource
    ): MediaItem? {
        if (resource.extensionId != song.trackRef.extensionId) return null
        return runCatching {
            createPlaybackMediaItem(
                extensionId = song.trackRef.extensionId,
                url = resource.url,
                headers = resource.headers,
                mimeType = resource.mimeType,
                type = resource.type,
                mediaId = song.trackRef.opaqueId
            )
        }.onFailure { error ->
            Log.w(
                LogTag,
                "extension.playback.mediaItem.failed extension=${song.trackRef.extensionId} " +
                    "type=${error.javaClass.simpleName}"
            )
        }.getOrNull()
    }

    internal suspend fun createPlaybackMediaItem(song: ProviderSong): MediaItem? {
        val resource = resolvePlaybackResource(song) ?: return null
        return createPlaybackMediaItem(song, resource)
    }

    @UnstableApi
    internal suspend fun preloadPlaybackContent(
        mediaItem: MediaItem,
        resource: ExtensionPlaybackResource,
        percentage: Int
    ): Long {
        if (resource.type != ExtensionPlaybackResourceType.Progressive) return 0L
        return playbackContentCache?.preloadPrefix(mediaItem, percentage) ?: 0L
    }

    /** All 仅合并每个 Provider 的第一页；跨 Provider cursor 留待后续设计。 */
    internal suspend fun searchMusicProviders(request: ProviderSearchRequest): ProviderSearchCallResult =
        runtimeLifecycle.execute {
            val calls = musicProviders.toMap()
                .filterValues { provider ->
                    ExtensionRuntimeCapabilityPolicy.supportsSearch(
                        provider.capabilities,
                        request.category
                    )
                }
                .map { (extensionId, provider) ->
                    extensionId to providerCall { provider.searchPage(request) }
                }
            val pages = calls.mapNotNull { (extensionId, result) ->
                result.onFailure { error ->
                    Log.w(LogTag, "extension.music.search.page.failed extension=$extensionId category=${request.category} type=${error.javaClass.simpleName}")
                }.getOrNull()?.also { page ->
                    Log.d(
                        LogTag,
                        "extension.music.search.page.success extension=$extensionId " +
                            "category=${request.category} results=${page.results.size} " +
                            "hasNextCursor=${page.nextCursor != null} " +
                            "nextCursorLength=${page.nextCursor?.length ?: 0}"
                    )
                }
            }
            if (pages.isEmpty() && calls.isNotEmpty()) {
                val failure = calls.firstNotNullOfOrNull { it.second.exceptionOrNull() }
                if (failure != null) return@execute ProviderSearchCallResult.Failure(failure)
            }
            ProviderSearchCallResult.Success(
                ProviderSearchPage(results = pages.flatMap(ProviderSearchPage::results))
            )
        }

    internal suspend fun searchMusicProvider(
        extensionId: String,
        request: ProviderSearchRequest
    ): ProviderSearchCallResult = runtimeLifecycle.execute {
        val provider = musicProviders[extensionId]
            ?: return@execute ProviderSearchCallResult.Failure(NoSuchElementException("Provider not found"))
        if (!ExtensionRuntimeCapabilityPolicy.supportsSearch(provider.capabilities, request.category)) {
            return@execute ProviderSearchCallResult.Success(ProviderSearchPage(emptyList()))
        }
        providerCall { provider.searchPage(request) }
            .fold(
                onSuccess = { page ->
                    Log.d(
                        LogTag,
                        "extension.music.search.page.success extension=$extensionId " +
                            "category=${request.category} results=${page.results.size} " +
                            "hasNextCursor=${page.nextCursor != null} " +
                            "nextCursorLength=${page.nextCursor?.length ?: 0}"
                    )
                    ProviderSearchCallResult.Success(page)
                },
                onFailure = { error ->
                    Log.w(LogTag, "extension.music.search.page.failed extension=$extensionId category=${request.category} type=${error.javaClass.simpleName}")
                    ProviderSearchCallResult.Failure(error)
                }
            )
    }

    /** 当前已运行且声明至少一项 MusicProvider Atomic capability 的 Provider。 */
    internal fun availableMusicProviderOptions(): List<SearchProviderOption> =
        installedExtensions()
            .asSequence()
            .filter { installed ->
                val provider = musicProviders[installed.manifest.id]
                installed.runtimeAvailable && provider != null &&
                    ExtensionRuntimeCapabilityPolicy.requiresMusicProviderRuntime(provider.capabilities)
            }
            .map {
                SearchProviderOption(
                    extensionId = it.manifest.id,
                    name = it.manifest.name,
                    color = it.manifest.color,
                    visual = ExtensionProviderVisualResolver.resolve(it)
                )
            }
            .sortedBy(SearchProviderOption::name)
            .toList()

    internal suspend fun getSearchLanding(extensionId: String): ProviderSearchLanding? =
        runtimeLifecycle.execute {
            searchLandingCache[extensionId]?.let { return@execute it }
            val provider = musicProviders[extensionId] ?: return@execute null
            if (AtomicCapabilityId.SearchLandingGet !in provider.capabilities) return@execute null
            provider.getSearchLanding()?.also { searchLandingCache[extensionId] = it }
        }

    internal fun providerCapabilities(extensionId: String): CanonicalAtomicCapabilitySet =
        if (_runtimeState.value.isReloading) CanonicalAtomicCapabilitySet.Empty
        else musicProviders[extensionId]?.capabilities ?: CanonicalAtomicCapabilitySet.Empty

    internal suspend fun getProviderSongs(extensionId: String): List<ProviderSong>? =
        runtimeLifecycle.execute {
            val provider = musicProviders[extensionId] ?: return@execute null
            if (AtomicCapabilityId.CatalogSongsList !in provider.capabilities) return@execute null
            try {
                songCollectionCache.getOrLoad(extensionId) {
                    provider.getSongs()?.let(::dedupeProviderSongs)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(LogTag, "extension.music.songs.failed extension=$extensionId type=${error.javaClass.simpleName}")
                null
            }
        }

    internal suspend fun getProviderAlbums(extensionId: String): List<ProviderAlbum>? =
        runtimeLifecycle.execute {
            val provider = musicProviders[extensionId] ?: return@execute null
            if (AtomicCapabilityId.CatalogAlbumsList !in provider.capabilities) return@execute null
            try {
                albumCollectionCache.getOrLoad(extensionId) {
                    provider.getAlbums()?.let(::dedupeProviderAlbums)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(LogTag, "extension.music.albums.failed extension=$extensionId type=${error.javaClass.simpleName}")
                null
            }
        }

    internal suspend fun getProviderPlaylistSongs(
        extensionId: String,
        playlistId: String
    ): List<ProviderSong>? = runtimeLifecycle.execute {
        val provider = musicProviders[extensionId] ?: return@execute null
        if (AtomicCapabilityId.PlaylistSongsRead !in provider.capabilities) return@execute null
        try {
            provider.getPlaylistSongs(playlistId)?.let(::dedupeProviderSongs)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w(
                LogTag,
                "extension.music.playlist.failed extension=$extensionId type=${error.javaClass.simpleName}"
            )
            null
        }
    }

    internal suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? =
        runtimeLifecycle.execute { artistAvatarRegistry.findArtistAvatar(songTitle, artistName) }

    internal suspend fun findArtistMetadata(artistName: String): ArtistMetadata? =
        runtimeLifecycle.execute { artistMetadataRegistry.findArtistMetadata(artistName) }

    @UnstableApi
    fun extensionMediaSourceFactory(context: Context): MediaSource.Factory {
        val extensionDataSourceFactory = extensionMediaDataSourceFactory()
        val fallbackDataSourceFactory = DefaultDataSource.Factory(
            context,
            playbackContentCache?.playbackDataSourceFactory ?: extensionDataSourceFactory
        )
        return ExtensionMediaSourceFactory(
            resources = playbackResources,
            extensionDataSourceFactory = extensionDataSourceFactory,
            fallbackFactory = DefaultMediaSourceFactory(fallbackDataSourceFactory)
        )
    }

    private suspend fun reloadRuntimeLocked(
        reason: ExtensionRuntimeReloadReason,
        clearExtensionDataFor: Set<String> = emptySet()
    ): Boolean = withContext(NonCancellable) {
        val oldProviderCount = musicProviders.size
        val generation = runtimeLifecycle.beginReload()
        _runtimeState.value = ExtensionRuntimeState(
            generation = generation,
            isReloading = true
        )
        Log.i(
            LogTag,
            "extension.runtime.reload.started generation=$generation reason=${reason.logValue} oldProviders=$oldProviderCount"
        )
        try {
            disposeRuntime(clearExtensionDataFor, clearPlaybackResources = false)
            sandboxHost.close()
            Log.i(
                LogTag,
                "extension.runtime.dispose.completed generation=$generation oldProviders=$oldProviderCount"
            )
            val installed = withContext(Dispatchers.IO) { installer.scan() }
            var createdRuntimes = 0
            installed.forEach { extension ->
                if (load(extension)) createdRuntimes += 1
            }
            runtimeLifecycle.completeReload(generation)
            _runtimeState.value = ExtensionRuntimeState(
                generation = generation,
                installedExtensionCount = installed.size,
                providerIds = musicProviders.keys.toSet()
            )
            Log.i(
                LogTag,
                "extension.runtime.reload.succeeded generation=$generation reason=${reason.logValue} " +
                    "scanned=${installed.size} runtimes=$createdRuntimes providers=${musicProviders.size}"
            )
            true
        } catch (error: Throwable) {
            disposeRuntime(clearExtensionDataFor = emptySet(), clearPlaybackResources = false)
            sandboxHost.close()
            runtimeLifecycle.completeReload(generation)
            _runtimeState.value = ExtensionRuntimeState(
                generation = generation,
                lastReloadErrorType = error.javaClass.simpleName
            )
            Log.e(
                LogTag,
                "extension.runtime.reload.failed generation=$generation reason=${reason.logValue} " +
                    "type=${error.javaClass.simpleName}"
            )
            false
        }
    }

    private fun disposeRuntime(
        clearExtensionDataFor: Set<String>,
        clearPlaybackResources: Boolean
    ) {
        val extensionIds = buildSet {
            addAll(runtimes.keys)
            addAll(musicProviders.keys)
            addAll(networkClients.keys)
            addAll(streamClients.keys)
            addAll(clearExtensionDataFor)
        }
        extensionIds.forEach { id ->
            runCatching {
                stop(
                    id = id,
                    clearExtensionData = id in clearExtensionDataFor,
                    clearPlaybackResources = clearPlaybackResources
                )
            }.onFailure { error ->
                Log.w(
                    LogTag,
                    "extension.runtime.dispose.item.failed extension=$id type=${error.javaClass.simpleName}"
                )
            }
        }
    }

    private suspend fun load(installed: InstalledExtension): Boolean {
        val capabilities = installed.descriptor.canonicalCapabilities
        val requirements = ExtensionRuntimeCapabilityPolicy.registrationRequirements(capabilities)
        if (!requirements.requiresRuntime) return false
        val isolate = sandboxHost.createIsolate() ?: return false
        val networkClient = gateway.createClientFor(
            extensionId = installed.manifest.id,
            capability = "host_api",
            allowedPermissions = installed.descriptor.networkPermissions
        )
        val streamClient = gateway.createStreamClientFor(
            extensionId = installed.manifest.id,
            capability = "media_stream",
            allowedPermissions = installed.descriptor.networkPermissions
        )
        val runtime = JavaScriptExtensionRuntime(installed, isolate, networkClient, privateCache)
        return runCatching { runtime.start() }
            .onSuccess {
                runtimes[installed.manifest.id] = runtime
                networkClients[installed.manifest.id] = networkClient
                streamClients[installed.manifest.id] = streamClient
                if (requirements.artistAvatar) {
                    artistAvatarRegistry.install(JavaScriptArtistAvatarExtension(runtime))
                }
                if (requirements.artistMetadata) {
                    artistMetadataRegistry.install(JavaScriptArtistMetadataExtension(runtime))
                }
                if (requirements.musicProvider) {
                    musicProviders[installed.manifest.id] = JavaScriptMusicProvider(
                        runtime = runtime,
                        musicSources = installed.manifest.musicSources.toSet(),
                        capabilities = capabilities
                    )
                }
            }
            .onFailure { error ->
                Log.w(
                    LogTag,
                    "extension.runtime.load.failed extension=${installed.manifest.id} type=${error.javaClass.simpleName}"
                )
                runtime.close()
            }
            .isSuccess
    }

    private fun stop(
        id: String,
        clearExtensionData: Boolean = false,
        clearPlaybackResources: Boolean = true
    ) {
        disposeStep(id, "artistAvatar") {
            artistAvatarRegistry.uninstall(id, clearPersistentCache = clearExtensionData)
        }
        disposeStep(id, "artistMetadata") {
            artistMetadataRegistry.uninstall(id, clearPersistentCache = clearExtensionData)
        }
        musicProviders.remove(id)
        networkClients.remove(id)
        streamClients.remove(id)
        disposeStep(id, "runtime") { runtimes.remove(id)?.close() }
        if (clearPlaybackResources) playbackResources.clear(id)
        presentationCache.entries.removeIf { (_, song) -> song.trackRef.extensionId == id }
        searchLandingCache.remove(id)
        songCollectionCache.clear(id)
        albumCollectionCache.clear(id)
        if (clearExtensionData) {
            disposeStep(id, "privateCache") { privateCache.deleteForUninstall(id) }
        }
    }

    private inline fun disposeStep(id: String, step: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.w(
                LogTag,
                "extension.runtime.dispose.step.failed extension=$id step=$step type=${error.javaClass.simpleName}"
            )
        }
    }

    private fun networkClientFor(extensionId: String): ExtensionNetworkClient? = networkClients[extensionId]

    private fun streamClientFor(extensionId: String): ExtensionStreamClient? = streamClients[extensionId]

    /**
     * 以 manifest 的 musicSources 选择当前 Provider。排序后的 extension ID 是当前简单且确定的
     * 选择规则；未来可在此处替换为用户偏好或显式优先级。
     */
    internal suspend fun resolvePersistentSong(
        sourceHost: String,
        persistentId: String
    ): ProviderSong? = runtimeLifecycle.execute {
        val provider = selectMusicProviderForSource(musicProviders.toMap(), sourceHost)
            ?: return@execute null
        if (AtomicCapabilityId.SongPersistentResolve !in provider.capabilities) {
            return@execute null
        }
        providerCall { provider.resolvePersistentSong(persistentId) }
            .onFailure { error ->
                Log.w(LogTag, "extension.music.persistent.resolve.failed type=${error.javaClass.simpleName}")
            }
            .getOrNull()
    }

    internal suspend fun resolvePersistentPlaylistSong(
        entry: ink.tenqui.flowtone.core.model.PersistentTrack.Online
    ): PersistentSongResolution = runtimeLifecycle.execute {
        resolvePersistentSongWithProviders(musicProviders.toMap(), entry)
    }

    /** 只 hydrate presentation，不会获取播放资源。 */
    internal suspend fun hydratePersistentPresentation(
        entry: ink.tenqui.flowtone.core.model.PersistentTrack.Online
    ): ProviderSong? = runtimeLifecycle.execute {
        presentationCache[entry.identityKey]?.let { return@execute it }
        val resolution = resolvePersistentSongWithProviders(musicProviders.toMap(), entry)
        (resolution as? PersistentSongResolution.Resolved)?.song?.also {
            presentationCache[entry.identityKey] = it
        }
    }

    /** Runtime provider 引用只能在所属扩展仍运行时复用。 */
    internal fun isMusicProviderRuntimeAvailable(extensionId: String): Boolean =
        !_runtimeState.value.isReloading && musicProviders.containsKey(extensionId)

    private suspend fun <T> providerCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun displayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    /** Debug APK 将随包携带的 fixture 先走正常安装器安装，再由下方既有扫描流程加载。 */
    private fun installBundledUiTestExtensions() {
        if (!BuildConfig.UI_TEST_FIXTURE_ENABLED) return
        runCatching {
            appContext.assets.open(BundledArtistProfileFixturePackage).use { input ->
                installer.install(BundledArtistProfileFixturePackage, input)
            }
        }.onFailure { error ->
            Log.w(
                LogTag,
                "extension.ui_test_fixture.install.failed type=${error.javaClass.simpleName}"
            )
        }
    }

    override fun close() {
        runtimeLifecycle.close()
        disposeRuntime(clearExtensionDataFor = emptySet(), clearPlaybackResources = true)
        privateCache.flushDirty()
        extensionImageLoader.shutdown()
        if (playbackContentCacheDelegate.isInitialized()) playbackContentCache?.close()
        sandboxHost.close()
    }

    companion object {
        private const val LogTag = "FlowtoneExtension"
        private const val BundledArtistProfileFixturePackage =
            "provider-artist-profile-fixture.flowtone"
        @Volatile private var instance: ExtensionManager? = null
        fun get(context: Context): ExtensionManager = instance ?: synchronized(this) {
            instance ?: ExtensionManager(context).also { instance = it }
        }
    }
}

internal fun selectMusicProviderForSource(
    providers: Map<String, MusicProvider>,
    sourceHost: String
): MusicProvider? {
    val normalizedSource = normalizeMusicSourceHost(sourceHost)
    return providers.entries
        .sortedBy(Map.Entry<String, MusicProvider>::key)
        .firstOrNull { (_, provider) ->
            AtomicCapabilityId.SongPersistentResolve in provider.capabilities &&
                normalizedSource in provider.musicSources.map(::normalizeMusicSourceHost)
        }
        ?.value
}

internal suspend fun resolvePersistentSongWithProviders(
    providers: Map<String, MusicProvider>,
    track: ink.tenqui.flowtone.core.model.PersistentTrack.Online
): PersistentSongResolution {
    val provider = selectMusicProviderForSource(providers, track.sourceHost)
        ?: return PersistentSongResolution.ProviderMissing(track)
    val song = try {
        provider.resolvePersistentSong(track.persistentId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }
    return song?.let(PersistentSongResolution::Resolved)
        ?: PersistentSongResolution.Unresolved(track).also {
            // TODO: Future persistent track recovery:
            // direct resolve -> cached metadata fuzzy search -> high-confidence rebind -> unavailable.
        }
}
