package ink.tenqui.flowtone.data.online.network

/** 在线扩展唯一允许使用的 HTTP 入口。调用者身份由 Flowtone 绑定。 */
interface ExtensionNetworkClient {
    suspend fun execute(request: ExtensionHttpRequest): ExtensionHttpResponse
}

data class ExtensionHttpRequest(
    val method: ExtensionHttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
)

enum class ExtensionHttpMethod { Get }

data class ExtensionHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray
)
