package ink.tenqui.flowtone.data.online

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
    private val runtimes = mutableMapOf<String, JavaScriptArtistAvatarExtension>()
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
        if (!installed.manifest.supportsArtistAvatar) return
        val isolate = sandboxHost.createIsolate() ?: return
        val networkClient = gateway.createClientFor(
            extensionId = installed.manifest.id,
            capability = "artist_avatar",
            allowedHosts = installed.manifest.networkHosts
        )
        val streamClient = gateway.createStreamClientFor(
            extensionId = installed.manifest.id,
            capability = "media_stream",
            allowedHosts = installed.manifest.networkHosts
        )
        val extension = JavaScriptArtistAvatarExtension(
            installed = installed,
            isolate = isolate,
            network = networkClient,
            privateCache = privateCache
        )
        runCatching { extension.start() }
            .onSuccess {
                runtimes[installed.manifest.id] = extension
                networkClients[installed.manifest.id] = networkClient
                streamClients[installed.manifest.id] = streamClient
                artistAvatarRegistry.install(extension)
            }
            .onFailure { extension.close() }
    }

    private fun stop(id: String, clearExtensionData: Boolean = false) {
        artistAvatarRegistry.uninstall(id, clearPersistentCache = clearExtensionData)
        runtimes.remove(id)?.close()
        networkClients.remove(id)
        streamClients.remove(id)
        playbackResources.clear(id)
        if (clearExtensionData) privateCache.deleteForUninstall(id)
    }

    private fun networkClientFor(extensionId: String): ExtensionNetworkClient? = networkClients[extensionId]

    private fun streamClientFor(extensionId: String): ExtensionStreamClient? = streamClients[extensionId]

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
        @Volatile private var instance: ExtensionManager? = null
        fun get(context: Context): ExtensionManager = instance ?: synchronized(this) {
            instance ?: ExtensionManager(context).also { instance = it }
        }
    }
}
