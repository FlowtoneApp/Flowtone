package ink.tenqui.flowtone.data.online.network

import androidx.media3.common.C
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Flowtone 内部用于大体积扩展资源的流式网络入口。调用者身份由宿主绑定。 */
interface ExtensionStreamClient {
    @Throws(Exception::class)
    fun open(request: ExtensionStreamRequest): ExtensionStreamResponse
}

data class ExtensionStreamRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val position: Long = 0L,
    val length: Long = C.LENGTH_UNSET.toLong()
)

class ExtensionStreamResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: InputStream,
    val resolvedUrl: String,
    private val closeAction: () -> Unit = {}
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { body.close() }
        closeAction()
    }
}

