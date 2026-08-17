package ink.tenqui.flowtone.data.online.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.network.ExtensionHttpMethod
import ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import java.io.IOException
import okio.Buffer

/** 为扩展远程图片解析其已绑定的 Flowtone 网络客户端。 */
fun interface ExtensionImageNetworkHost {
    fun clientFor(extensionId: String): ExtensionNetworkClient?
}

/** Coil 仅对 [ExtensionImage] 使用该 Fetcher，绝不自行创建 HTTP 客户端。 */
class ExtensionImageFetcher private constructor(
    private val image: ExtensionImage,
    private val options: Options,
    private val host: ExtensionImageNetworkHost,
    private val diskCache: DiskCache?
) : Fetcher {
    override suspend fun fetch(): SourceFetchResult {
        readFromDiskCache()?.let { return it }

        val client = host.clientFor(image.extensionId)
            ?: throw IOException("扩展图片所属扩展未运行：${image.extensionId}")
        val response = client.execute(
            ExtensionHttpRequest(
                method = ExtensionHttpMethod.Get,
                url = image.url
            )
        )
        if (response.statusCode !in 200..299) {
            throw IOException("扩展图片 HTTP 状态：${response.statusCode}")
        }
        val mimeType = response.headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.substringBefore(';')
        return writeToDiskCache(response.body, mimeType)
            ?: SourceFetchResult(
                source = ImageSource(
                    source = Buffer().apply { write(response.body) },
                    fileSystem = options.fileSystem
                ),
                mimeType = mimeType,
                dataSource = DataSource.NETWORK
            )
    }

    private fun readFromDiskCache(): SourceFetchResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val cache = diskCache ?: return null
        val snapshot = cache.openSnapshot(diskCacheKey) ?: return null
        return SourceFetchResult(
            source = snapshot.toImageSource(cache, diskCacheKey),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    private fun writeToDiskCache(body: ByteArray, mimeType: String?): SourceFetchResult? {
        if (!options.diskCachePolicy.writeEnabled) return null
        val cache = diskCache ?: return null
        val editor = cache.openEditor(diskCacheKey) ?: return null
        return try {
            cache.fileSystem.write(editor.metadata) {}
            cache.fileSystem.write(editor.data) { write(body) }
            val snapshot = editor.commitAndOpenSnapshot() ?: return null
            SourceFetchResult(
                source = snapshot.toImageSource(cache, diskCacheKey),
                mimeType = mimeType,
                dataSource = DataSource.NETWORK
            )
        } catch (error: Exception) {
            editor.abort()
            throw error
        }
    }

    private fun DiskCache.Snapshot.toImageSource(cache: DiskCache, key: String): ImageSource = ImageSource(
        file = data,
        fileSystem = cache.fileSystem,
        diskCacheKey = key,
        closeable = this
    )

    private val diskCacheKey: String
        get() = options.diskCacheKey ?: ExtensionImageKeyer.key(image, options)

    class Factory(private val host: ExtensionImageNetworkHost) : Fetcher.Factory<ExtensionImage> {
        override fun create(
            data: ExtensionImage,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = ExtensionImageFetcher(data, options, host, imageLoader.diskCache)
    }
}

/** 将扩展 ID 纳入 Coil 缓存键，防止相同 URL 的权限上下文串用。 */
object ExtensionImageKeyer : Keyer<ExtensionImage> {
    override fun key(data: ExtensionImage, options: Options): String =
        "extension-image:${data.extensionId}:${data.url}"
}
