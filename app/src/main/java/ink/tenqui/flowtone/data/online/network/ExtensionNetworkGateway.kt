package ink.tenqui.flowtone.data.online.network

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import ink.tenqui.flowtone.data.online.packageformat.ExtensionHostPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Flowtone 控制的扩展网络网关，也是唯一实际执行 HTTP 的位置。 */
class ExtensionNetworkGateway(
    private val transport: ExtensionHttpTransport = HttpUrlConnectionTransport(),
    private val logger: ExtensionCoreLogger = AndroidExtensionCoreLogger
) {
    fun createClientFor(
        extensionId: String,
        capability: String,
        allowedHosts: List<String>
    ): ExtensionNetworkClient {
        return BoundExtensionNetworkClient(
            extensionId,
            capability,
            ExtensionHostPolicy(allowedHosts),
            transport,
            logger
        )
    }
}

fun interface ExtensionHttpTransport {
    suspend fun execute(
        request: ExtensionHttpRequest,
        authorizeUrl: (String) -> Unit
    ): ExtensionHttpResponse
}

fun interface ExtensionCoreLogger {
    fun log(event: String, details: String)
}

private object AndroidExtensionCoreLogger : ExtensionCoreLogger {
    override fun log(event: String, details: String) {
        Log.d("FlowtoneExtension", "$event $details")
    }
}

private class BoundExtensionNetworkClient(
    private val extensionId: String,
    private val capability: String,
    private val hostPolicy: ExtensionHostPolicy,
    private val transport: ExtensionHttpTransport,
    private val logger: ExtensionCoreLogger
) : ExtensionNetworkClient {
    override suspend fun execute(request: ExtensionHttpRequest): ExtensionHttpResponse {
        hostPolicy.requireAllowed(request.url)
        val requestDetails = requestLogDetails(request)
        logger.log("extension.http.prepared", identityDetails() + " $requestDetails")
        val startedAt = System.nanoTime()
        logger.log("extension.http.started", identityDetails() + " $requestDetails")
        return try {
            val response = transport.execute(request, hostPolicy::requireAllowed)
            val durationMs = elapsedMillis(startedAt)
            logger.log(
                "extension.http.response",
                identityDetails() + " status=${response.statusCode} durationMs=$durationMs " +
                    "requestBytes=${request.body?.size ?: 0} responseBytes=${response.body.size}"
            )
            response
        } catch (error: Exception) {
            logger.log(
                "extension.http.failed",
                identityDetails() + " failure=${error.javaClass.simpleName} durationMs=${elapsedMillis(startedAt)}"
            )
            throw error
        }
    }

    private fun identityDetails(): String = "extension=$extensionId capability=$capability"
}

private class HttpUrlConnectionTransport : ExtensionHttpTransport {
    override suspend fun execute(
        request: ExtensionHttpRequest,
        authorizeUrl: (String) -> Unit
    ): ExtensionHttpResponse = withContext(Dispatchers.IO) {
        var currentUrl = request.url
        repeat(MaxRedirects + 1) { redirectCount ->
            authorizeUrl(currentUrl)
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method.name.uppercase()
                connectTimeout = DefaultTimeoutMillis
                readTimeout = DefaultTimeoutMillis
                instanceFollowRedirects = false
                doInput = true
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                val statusCode = connection.responseCode
                if (statusCode in RedirectStatuses) {
                    require(redirectCount < MaxRedirects) { "HTTP 重定向次数过多" }
                    val location = connection.getHeaderField("Location")
                        ?: error("HTTP 重定向缺少 Location")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    authorizeUrl(currentUrl)
                    return@repeat
                }
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MaxResponseBytes) { "扩展 HTTP 响应超过 2 MiB" }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } ?: ByteArray(0)
                return@withContext ExtensionHttpResponse(
                    statusCode = statusCode,
                    headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key!! },
                    body = body
                )
            } finally {
                connection.disconnect()
            }
        }
        error("HTTP 重定向失败")
    }

    private companion object {
        const val DefaultTimeoutMillis = 10_000
        const val MaxRedirects = 5
        const val MaxResponseBytes = 2 * 1024 * 1024
        val RedirectStatuses = setOf(301, 302, 303, 307, 308)
    }
}

internal fun requestLogDetails(request: ExtensionHttpRequest): String {
    val uri = runCatching { URI(request.url) }.getOrNull()
    if (uri == null) return "method=${request.method} url=<invalid>"
    val query = uri.rawQuery
        ?.split("&")
        ?.joinToString("&") { part ->
            val name = part.substringBefore("=")
            if (isSensitiveName(name)) "$name=****" else part
        }
        .orEmpty()
    return "method=${request.method} host=${uri.host.orEmpty()} path=${uri.rawPath.orEmpty()} " +
        "query=${if (query.isEmpty()) "<none>" else query} requestBytes=${request.body?.size ?: 0}"
}

private fun isSensitiveName(name: String): Boolean {
    val normalized = name.lowercase()
    return listOf("api_key", "token", "access_token", "authorization", "cookie", "secret", "password", "key", "signature")
        .any { sensitive -> normalized == sensitive || normalized.contains(sensitive) }
}

private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
