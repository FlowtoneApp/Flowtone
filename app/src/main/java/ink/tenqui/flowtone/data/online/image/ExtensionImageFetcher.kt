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
import ink.tenqui.flowtone.data.online.network.ExtensionStreamClient
import ink.tenqui.flowtone.data.online.network.ExtensionStreamRequest
import ink.tenqui.flowtone.data.online.network.ExtensionStreamResponse
import java.io.IOException
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath

/** 为扩展远程图片解析其已绑定的 Flowtone 网络客户端。 */
fun interface ExtensionImageNetworkHost {
    fun clientFor(extensionId: String): ExtensionStreamClient?
}

/** Coil 仅对 [ExtensionImage] 使用该 Fetcher，绝不自行创建 HTTP 客户端。 */
class ExtensionImageFetcher private constructor(
    private val image: ExtensionImage,
    private val options: Options,
    private val host: ExtensionImageNetworkHost,
    private val diskCache: DiskCache?
) : Fetcher {
    @OptIn(InternalCoroutinesApi::class)
    override suspend fun fetch(): SourceFetchResult = withImageResourceHandoff { handoff ->
        readFromDiskCache(handoff)?.let { return@withImageResourceHandoff it }

        val client = host.clientFor(image.extensionId)
            ?: throw IOException("扩展图片所属扩展未运行：${image.extensionId}")
        val active = AtomicReference<ExtensionStreamResponse?>(null)
        val context = currentCoroutineContext()
        val job = context[Job]!!
        val cancellation = job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
            if (cause != null) active.getAndSet(null)?.close()
        }
        try {
        val response = client.open(ExtensionStreamRequest(url = image.url))
        active.set(response)
        currentCoroutineContext().ensureActive()
        response.use {
        if (response.statusCode !in 200..299) {
            throw IOException("扩展图片 HTTP 状态：${response.statusCode}")
        }
        val mimeType = response.headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.substringBefore(';')
        val contentLength = response.headers.entries.firstOrNull { it.key.equals("Content-Length", true) }
            ?.value?.firstOrNull()?.toLongOrNull()
        if (contentLength != null && contentLength > MaxImageResponseBytes) throw ImageResponseTooLargeException()
        val cache = diskCache.takeIf { options.diskCachePolicy.writeEnabled }
        val editor = cache?.openEditor(diskCacheKey)
        if (editor != null && cache != null) {
            var committed = false
            try {
                cache.fileSystem.write(editor.metadata) {}
                cache.fileSystem.write(editor.data) { copyImage(response.body, { context.ensureActive() }) { bytes, count -> write(bytes, 0, count) } }
                currentCoroutineContext().ensureActive()
                val snapshot = editor.commitAndOpenSnapshot() ?: throw IOException("图片缓存提交失败")
                committed = true
                handoff.own { snapshot.close() }
                val source = snapshot.toImageSource(cache, diskCacheKey)
                handoff.own { source.close() }
                return@withImageResourceHandoff SourceFetchResult(source, mimeType, DataSource.NETWORK)
            } finally {
                if (!committed) runCatching { editor.abort() }
            }
        }
        val file = File.createTempFile("extension-image-", ".tmp", options.context.cacheDir)
        handoff.own { file.delete() }
        file.outputStream().use { output -> copyImage(response.body, { context.ensureActive() }) { bytes, count -> output.write(bytes, 0, count) } }
        currentCoroutineContext().ensureActive()
        val source = ImageSource(file = file.toOkioPath(), fileSystem = options.fileSystem, closeable = Closeable { file.delete() })
        handoff.own { source.close() }
        return@withImageResourceHandoff SourceFetchResult(source, mimeType, DataSource.NETWORK)
        }
        } finally {
            cancellation.dispose()
            active.getAndSet(null)?.close()
        }
    }

    private fun readFromDiskCache(handoff: ImageResourceHandoff): SourceFetchResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val cache = diskCache ?: return null
        val snapshot = cache.openSnapshot(diskCacheKey) ?: return null
        handoff.own { snapshot.close() }
        val source = snapshot.toImageSource(cache, diskCacheKey)
        handoff.own { source.close() }
        return SourceFetchResult(
            source = source,
            mimeType = null,
            dataSource = DataSource.DISK
        )
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

internal class ImageResourceHandoff {
    private val cleanup = AtomicReference<(() -> Unit)?>(null)
    fun own(action: () -> Unit) { cleanup.set(action) }
    fun transfer() { cleanup.set(null) }
    fun close() { cleanup.getAndSet(null)?.invoke() }
}

internal suspend fun <T> withImageResourceHandoff(block: suspend (ImageResourceHandoff) -> T): T {
    val handoff = ImageResourceHandoff()
    try {
        val result = withContext(Dispatchers.IO) { block(handoff) }
        currentCoroutineContext().ensureActive()
        handoff.transfer()
        return result
    } finally {
        handoff.close()
    }
}

internal const val MaxImageResponseBytes = 16 * 1024 * 1024
internal class ImageResponseTooLargeException : IOException("扩展图片响应超过 16 MiB")

internal fun copyImage(input: java.io.InputStream, checkActive: () -> Unit = {}, write: (ByteArray, Int) -> Unit) {
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        checkActive()
        val count = input.read(buffer)
        checkActive()
        if (count < 0) break
        total += count
        if (total > MaxImageResponseBytes) throw ImageResponseTooLargeException()
        write(buffer, count)
    }
}

/** 将扩展 ID 纳入 Coil 缓存键，防止相同 URL 的权限上下文串用。 */
object ExtensionImageKeyer : Keyer<ExtensionImage> {
    fun cacheKey(data: ExtensionImage): String =
        "extension-image:${data.extensionId}:${data.url}"

    override fun key(data: ExtensionImage, options: Options): String = cacheKey(data)
}
