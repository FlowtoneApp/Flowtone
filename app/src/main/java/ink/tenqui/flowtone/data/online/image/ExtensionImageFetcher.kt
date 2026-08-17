package ink.tenqui.flowtone.data.online.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
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
    private val host: ExtensionImageNetworkHost
) : Fetcher {
    override suspend fun fetch(): SourceFetchResult {
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
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(response.body) },
                fileSystem = options.fileSystem
            ),
            mimeType = response.headers.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?.firstOrNull()
                ?.substringBefore(';'),
            dataSource = DataSource.NETWORK
        )
    }

    class Factory(private val host: ExtensionImageNetworkHost) : Fetcher.Factory<ExtensionImage> {
        override fun create(
            data: ExtensionImage,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = ExtensionImageFetcher(data, options, host)
    }
}

/** 将扩展 ID 纳入 Coil 缓存键，防止相同 URL 的权限上下文串用。 */
object ExtensionImageKeyer : Keyer<ExtensionImage> {
    override fun key(data: ExtensionImage, options: Options): String =
        "extension-image:${data.extensionId}:${data.url}"
}
