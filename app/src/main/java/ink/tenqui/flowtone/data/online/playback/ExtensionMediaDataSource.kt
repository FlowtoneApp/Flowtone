package ink.tenqui.flowtone.data.online.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.data.online.network.ExtensionStreamClient
import ink.tenqui.flowtone.data.online.network.ExtensionStreamRequest
import ink.tenqui.flowtone.data.online.network.ExtensionStreamResponse
import java.io.EOFException
import java.io.IOException

fun interface ExtensionStreamNetworkHost {
    fun clientFor(extensionId: String): ExtensionStreamClient?
}

/** Media3 专用扩展数据源。它只接受 Flowtone 生成的不透明扩展资源 URI。 */
@UnstableApi
class ExtensionMediaDataSource private constructor(
    private val resources: ExtensionPlaybackResourceStore,
    private val host: ExtensionStreamNetworkHost,
    private val boundResource: ExtensionPlaybackResource? = null
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var openedDataSpec: DataSpec? = null
    private var response: ExtensionStreamResponse? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var resolvedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        check(openedDataSpec == null) { "DataSource 已打开" }
        listeners.forEach { it.onTransferInitializing(this, dataSpec, true) }
        val resource = boundResource ?: resources.resolve(dataSpec.uri)
            ?: throw IOException("不是已注册的 Flowtone 扩展播放资源")
        val requestUrl = requestUrlFor(dataSpec.uri, resource)
        val client = host.clientFor(resource.extensionId)
            ?: throw IOException("扩展未运行或已卸载：${resource.extensionId}")
        val streamRequest = ExtensionStreamRequest(
            url = requestUrl,
            headers = resource.headers,
            position = dataSpec.position,
            length = dataSpec.length
        )
        val openedResponse = try {
            client.open(streamRequest)
        } catch (error: Exception) {
            throw IOException("无法打开扩展音频资源", error)
        }
        try {
            if (openedResponse.statusCode != 200 && openedResponse.statusCode != 206) {
                throw IOException("扩展音频 HTTP 状态：${openedResponse.statusCode}")
            }
            if (openedResponse.statusCode == 206) {
                contentRange(openedResponse)?.let { range ->
                    if (range.first != dataSpec.position) {
                        throw IOException(
                            "Content-Range 起点 ${range.first} 与请求位置 ${dataSpec.position} 不一致"
                        )
                    }
                }
            }
            if (dataSpec.position > 0L && openedResponse.statusCode == 200) {
                skipFully(openedResponse, dataSpec.position)
            }
            response = openedResponse
            openedDataSpec = dataSpec
            resolvedUri = Uri.parse(openedResponse.resolvedUrl)
            bytesRemaining = resolvedLength(dataSpec, openedResponse)
            listeners.forEach { it.onTransferStart(this, dataSpec, true) }
            return bytesRemaining
        } catch (error: Exception) {
            openedResponse.close()
            throw if (error is IOException) error else IOException("扩展音频响应非法", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val currentResponse = response ?: throw IOException("DataSource 尚未打开")
        val requested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesRemaining).toInt()
        }
        val read = currentResponse.body.read(buffer, offset, requested)
        if (read < 0) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        val dataSpec = checkNotNull(openedDataSpec)
        listeners.forEach { it.onBytesTransferred(this, dataSpec, true, read) }
        return read
    }

    override fun getUri(): Uri? = resolvedUri

    override fun getResponseHeaders(): Map<String, List<String>> = response?.headers.orEmpty()

    override fun close() {
        val dataSpec = openedDataSpec
        val wasOpened = dataSpec != null
        try {
            response?.close()
        } finally {
            response = null
            openedDataSpec = null
            bytesRemaining = C.LENGTH_UNSET.toLong()
            resolvedUri = null
            if (wasOpened) listeners.forEach { it.onTransferEnd(this, dataSpec!!, true) }
        }
    }

    private fun skipFully(response: ExtensionStreamResponse, byteCount: Long) {
        var remaining = byteCount
        val scratch = ByteArray(8 * 1024)
        while (remaining > 0L) {
            val read = response.body.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("服务器忽略 Range，且响应短于请求位置")
            remaining -= read
        }
    }

    private fun resolvedLength(dataSpec: DataSpec, response: ExtensionStreamResponse): Long {
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) return dataSpec.length
        val contentLength = response.headers.entries
            .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.toLongOrNull()
        return if (response.statusCode == 200) {
            contentLength?.let { (it - dataSpec.position).coerceAtLeast(0L) }
                ?: C.LENGTH_UNSET.toLong()
        } else {
            contentLength ?: contentRange(response)?.let { it.last - it.first + 1L }
            ?: C.LENGTH_UNSET.toLong()
        }
    }

    private fun contentRange(response: ExtensionStreamResponse): LongRange? {
        val value = response.headers.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?: return null
        val range = ContentRange.matchEntire(value.trim()) ?: return null
        val start = range.groupValues[1].toLongOrNull() ?: return null
        val end = range.groupValues[2].toLongOrNull() ?: return null
        return if (end >= start) start..end else null
    }

    private fun requestUrlFor(uri: Uri, resource: ExtensionPlaybackResource): String {
        val registeredResource = resources.resolve(uri)
        if (registeredResource != null) {
            if (boundResource != null && registeredResource != boundResource) {
                throw IOException("HLS playback session 不允许切换到其他 opaque 资源")
            }
            return resource.url
        }
        if (boundResource == null) {
            throw IOException("扩展播放资源 URI 无效")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IOException("HLS 子资源必须使用 HTTPS")
        }
        return uri.toString()
    }

    class Factory(
        private val resources: ExtensionPlaybackResourceStore,
        private val host: ExtensionStreamNetworkHost,
        private val boundResource: ExtensionPlaybackResource? = null
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ExtensionMediaDataSource(resources, host, boundResource)

        /** HLS 的 playlist、segment 与 key 共用已绑定的 extension playback session。 */
        fun forResource(resource: ExtensionPlaybackResource): Factory {
            return Factory(resources, host, boundResource = resource)
        }
    }

    private companion object {
        val ContentRange = Regex("bytes\\s+(\\d+)-(\\d+)/(?:\\d+|\\*)", RegexOption.IGNORE_CASE)
    }
}
