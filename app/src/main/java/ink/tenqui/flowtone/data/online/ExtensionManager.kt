package ink.tenqui.flowtone.data.online

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.runtime.ExtensionResultCache
import ink.tenqui.flowtone.data.online.runtime.ExtensionPrivateCache
import ink.tenqui.flowtone.data.online.runtime.JavaScriptArtistAvatarExtension
import ink.tenqui.flowtone.data.online.runtime.JavaScriptExtensionRuntime
import ink.tenqui.flowtone.data.online.runtime.JavaScriptMusicProvider
import ink.tenqui.flowtone.data.online.runtime.JavaScriptSandboxHost
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType
import ink.tenqui.flowtone.data.online.playback.ExtensionMediaDataSource
import ink.tenqui.flowtone.data.online.playback.ExtensionMediaSourceFactory
import ink.tenqui.flowtone.data.online.playback.ExtensionPlaybackResourceStore
import ink.tenqui.flowtone.data.online.playback.ExtensionStreamNetworkHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import coil3.ImageLoader
import java.util.concurrent.ConcurrentHashMap

/** 安装、扫描、运行和卸载外部脚本扩展的应用级所有者。 */
class ExtensionManager private constructor(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val installer = ExtensionPackageInstaller(appContext.filesDir.resolve("extensions"))
    private val sandboxHost = JavaScriptSandboxHost(appContext)
    private val gateway = ExtensionNetworkGateway()
    private val avatarResultCache = ExtensionResultCache()
    private val persistentAvatarCache = ArtistAvatarPersistentCache(appContext.filesDir.resolve("extension-data"))
    private val privateCache = ExtensionPrivateCache(appContext.filesDir.resolve("extension-data"))
    private val mutex = Mutex()
    private val runtimes = mutableMapOf<String, JavaScriptExtensionRuntime>()
    private val musicProviders = ConcurrentHashMap<String, JavaScriptMusicProvider>()
    private val networkClients = ConcurrentHashMap<String, ExtensionNetworkClient>()
    private val streamClients = ConcurrentHashMap<String, ExtensionStreamClient>()
    private val playbackResources = ExtensionPlaybackResourceStore()
    private var initialized = false
    val artistAvatarRegistry = ArtistAvatarExtensionRegistry(
        resultCache = avatarResultCache,
        persistentCache = persistentAvatarCache
    )
    val extensionImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .components {
                add(ExtensionImageKeyer)
                add(ExtensionImageFetcher.Factory(ExtensionImageNetworkHost(::networkClientFor)))
            }
            .build()
    }

    suspend fun initialize() = mutex.withLock {
        if (initialized) return@withLock
        installer.scan().forEach { load(it) }
        initialized = true
    }

    suspend fun install(uri: Uri): InstalledExtension = mutex.withLock {
        val name = requireNotNull(displayName(uri)) { "无法确认扩展包文件名" }
        require(name.endsWith(".flowtone", ignoreCase = true)) { "请选择 .flowtone 扩展包" }
        val installed = withContext(Dispatchers.IO) {
            val input = requireNotNull(appContext.contentResolver.openInputStream(uri)) { "无法读取扩展包" }
            installer.install(name, input)
        }
        stop(installed.manifest.id)
        load(installed)
        installed.copy(runtimeAvailable = runtimes.containsKey(installed.manifest.id))
    }

    fun installedExtensions(): List<InstalledExtension> = installer.scan().map {
        it.copy(runtimeAvailable = runtimes.containsKey(it.manifest.id))
    }

    suspend fun uninstall(extensionId: String): Boolean = mutex.withLock {
        stop(extensionId, clearExtensionData = true)
        withContext(Dispatchers.IO) { installer.uninstall(extensionId) }
    }

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

    /** 在线曲目只保存 Host 绑定的 track ref；每次播放重新向所属 runtime 解析短期播放资源。 */
    internal suspend fun createPlaybackMediaItem(song: ProviderSong): MediaItem? {
        val provider = musicProviders[song.trackRef.extensionId] ?: return null
        val resource = runCatching { provider.getPlaybackResource(song) }
            .onFailure { error ->
                Log.w(LogTag, "extension.playback.resolve.failed extension=${song.trackRef.extensionId} type=${error.javaClass.simpleName}")
            }
            .getOrNull() ?: return null
        return createPlaybackMediaItem(
            extensionId = song.trackRef.extensionId,
            url = resource.url,
            headers = resource.headers,
            mimeType = resource.mimeType,
            type = resource.type,
            mediaId = song.trackRef.opaqueId
        )
    }

    internal suspend fun searchMusicProviders(keyword: String): List<ProviderSong> =
        musicProviders.values.flatMap { provider ->
            runCatching { provider.searchSongs(keyword) }
                .onFailure { error ->
                    Log.w(LogTag, "extension.music.search.failed type=${error.javaClass.simpleName}")
                }
                .getOrDefault(emptyList())
        }

    @UnstableApi
    fun extensionMediaSourceFactory(context: Context): MediaSource.Factory {
        val extensionDataSourceFactory = extensionMediaDataSourceFactory()
        val fallbackDataSourceFactory = DefaultDataSource.Factory(context, extensionDataSourceFactory)
        return ExtensionMediaSourceFactory(
            resources = playbackResources,
            extensionDataSourceFactory = extensionDataSourceFactory,
            fallbackFactory = DefaultMediaSourceFactory(fallbackDataSourceFactory)
        )
    }

    private suspend fun load(installed: InstalledExtension) {
        if (!installed.manifest.supportsArtistAvatar && !installed.manifest.supportsMusicProvider) return
        val isolate = sandboxHost.createIsolate() ?: return
        val networkClient = gateway.createClientFor(
            extensionId = installed.manifest.id,
            capability = if (installed.manifest.supportsMusicProvider) "music_provider" else "artist_avatar",
            allowedHosts = installed.manifest.networkHosts
        )
        val streamClient = gateway.createStreamClientFor(
            extensionId = installed.manifest.id,
            capability = "media_stream",
            allowedHosts = installed.manifest.networkHosts
        )
        val runtime = JavaScriptExtensionRuntime(installed, isolate, networkClient, privateCache)
        runCatching { runtime.start() }
            .onSuccess {
                runtimes[installed.manifest.id] = runtime
                networkClients[installed.manifest.id] = networkClient
                streamClients[installed.manifest.id] = streamClient
                if (installed.manifest.supportsArtistAvatar) {
                    artistAvatarRegistry.install(JavaScriptArtistAvatarExtension(runtime))
                }
                if (installed.manifest.supportsMusicProvider) {
                    musicProviders[installed.manifest.id] = JavaScriptMusicProvider(
                        runtime = runtime,
                        musicSources = installed.manifest.musicSources.toSet()
                    )
                }
            }
            .onFailure { runtime.close() }
    }

    private fun stop(id: String, clearExtensionData: Boolean = false) {
        artistAvatarRegistry.uninstall(id, clearPersistentCache = clearExtensionData)
        runtimes.remove(id)?.close()
        musicProviders.remove(id)
        networkClients.remove(id)
        streamClients.remove(id)
        playbackResources.clear(id)
        if (clearExtensionData) privateCache.deleteForUninstall(id)
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
    ): ProviderSong? {
        val provider = selectMusicProviderForSource(musicProviders, sourceHost) ?: return null
        return runCatching { provider.resolvePersistentSong(persistentId) }
            .onFailure { error ->
                Log.w(LogTag, "extension.music.persistent.resolve.failed type=${error.javaClass.simpleName}")
            }
            .getOrNull()
    }

    internal suspend fun resolvePersistentPlaylistSong(
        entry: ink.tenqui.flowtone.core.model.PersistentTrack.Online
    ): PersistentSongResolution = resolvePersistentSongWithProviders(musicProviders, entry)

    private fun displayName(uri: Uri): String? {
        return appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    override fun close() {
        runtimes.keys.toList().forEach(::stop)
        privateCache.flushDirty()
        extensionImageLoader.shutdown()
        sandboxHost.close()
    }

    companion object {
        private const val LogTag = "FlowtoneExtension"
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
    val song = runCatching { provider.resolvePersistentSong(track.persistentId) }.getOrNull()
    return song?.let(PersistentSongResolution::Resolved)
        ?: PersistentSongResolution.Unresolved(track).also {
            // TODO: Future persistent track recovery:
            // direct resolve -> cached metadata fuzzy search -> high-confidence rebind -> unavailable.
        }
}
