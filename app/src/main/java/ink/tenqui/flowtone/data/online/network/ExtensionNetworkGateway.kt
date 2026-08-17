package ink.tenqui.flowtone.data.online.network

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import ink.tenqui.flowtone.data.online.packageformat.ExtensionHostPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import androidx.media3.common.C

/** Flowtone 控制的扩展网络网关，也是唯一实际执行 HTTP 的位置。 */
class ExtensionNetworkGateway(
    private val transport: ExtensionHttpTransport = HttpUrlConnectionTransport(),
    private val streamTransport: ExtensionStreamTransport = HttpUrlConnectionStreamTransport(),
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

    fun createStreamClientFor(
        extensionId: String,
        capability: String,
        allowedHosts: List<String>
    ): ExtensionStreamClient {
        return BoundExtensionStreamClient(
            extensionId = extensionId,
            capability = capability,
            hostPolicy = ExtensionHostPolicy(allowedHosts),
            transport = streamTransport,
            logger = logger
        )
    }
}

fun interface ExtensionHttpTransport {
    suspend fun execute(
        request: ExtensionHttpRequest,
        authorizeUrl: (String) -> Unit
    ): ExtensionHttpResponse
}

fun interface ExtensionStreamTransport {
    fun open(
        request: ExtensionStreamRequest,
        authorizeUrl: (String) -> Unit
    ): ExtensionStreamResponse
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

private class BoundExtensionStreamClient(
    private val extensionId: String,
    private val capability: String,
    private val hostPolicy: ExtensionHostPolicy,
    private val transport: ExtensionStreamTransport,
    private val logger: ExtensionCoreLogger
) : ExtensionStreamClient {
    override fun open(request: ExtensionStreamRequest): ExtensionStreamResponse {
        require(request.position >= 0L) { "流起始位置不能为负数" }
        require(request.length == C.LENGTH_UNSET.toLong() || request.length > 0L) {
            "流请求长度非法"
        }
        val identity = "extension=$extensionId capability=$capability"
        val startedAt = System.nanoTime()
        logger.log(
            "extension.media.open",
            "$identity ${safeUrlDetails(request.url)} position=${request.position} " +
                "requestedLength=${request.length}"
        )
        return try {
            hostPolicy.requireAllowed(request.url)
            val managedRequest = request.copy(headers = managedStreamHeaders(request))
            val details = streamRequestLogDetails(managedRequest)
            logger.log("extension.http.prepared", "$identity $details")
            logger.log("extension.http.started", "$identity $details")
            val response = transport.open(managedRequest, hostPolicy::requireAllowed)
            val durationToHeadersMs = elapsedMillis(startedAt)
            val contentLength = response.headerValue("Content-Length") ?: "<unknown>"
            logger.log(
                "extension.http.response",
                "$identity status=${response.statusCode} contentLength=$contentLength " +
                    "durationToHeadersMs=$durationToHeadersMs"
            )
            if (response.statusCode !in 200..299) {
                logger.log(
                    "extension.http.failed",
                    "$identity failure=HttpStatus status=${response.statusCode}"
                )
            }
            val countingBody = CoreLoggingInputStream(
                input = response.body,
                onFailure = { error ->
                    logger.log(
                        "extension.http.failed",
                        "$identity phase=body failure=${error.javaClass.simpleName}"
                    )
                }
            )
            ExtensionStreamResponse(
                statusCode = response.statusCode,
                headers = response.headers,
                body = countingBody,
                resolvedUrl = response.resolvedUrl,
                closeAction = {
                    response.close()
                    logger.log(
                        "extension.media.close",
                        "$identity bytesRead=${countingBody.bytesRead} durationMs=${elapsedMillis(startedAt)}"
                    )
                }
            )
        } catch (error: Exception) {
            logger.log(
                "extension.http.failed",
                "$identity failure=${error.javaClass.simpleName} durationMs=${elapsedMillis(startedAt)}"
            )
            throw error
        }
    }
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

private class HttpUrlConnectionStreamTransport : ExtensionStreamTransport {
    override fun open(
        request: ExtensionStreamRequest,
        authorizeUrl: (String) -> Unit
    ): ExtensionStreamResponse {
        var currentUrl = request.url
        var redirectCount = 0
        while (redirectCount <= MaxRedirects) {
            authorizeUrl(currentUrl)
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DefaultTimeoutMillis
                readTimeout = DefaultTimeoutMillis
                instanceFollowRedirects = false
                doInput = true
                request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                val statusCode = connection.responseCode
                if (statusCode in RedirectStatuses) {
                    check(redirectCount < MaxRedirects) { "HTTP 重定向次数过多" }
                    val location = connection.getHeaderField("Location")
                        ?: error("HTTP 重定向缺少 Location")
                    val redirectedUrl = URL(URL(currentUrl), location).toString()
                    authorizeUrl(redirectedUrl)
                    connection.disconnect()
                    currentUrl = redirectedUrl
                    redirectCount += 1
                    continue
                }
                val stream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
                }
                return ExtensionStreamResponse(
                    statusCode = statusCode,
                    headers = connection.headerFields
                        .filterKeys { it != null }
                        .mapKeys { it.key!! },
                    body = stream,
                    resolvedUrl = currentUrl,
                    closeAction = connection::disconnect
                )
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
        }
        error("HTTP 重定向失败")
    }

    private companion object {
        const val DefaultTimeoutMillis = 15_000
        const val MaxRedirects = 5
        val RedirectStatuses = setOf(301, 302, 303, 307, 308)
    }
}

private class CoreLoggingInputStream(
    input: InputStream,
    private val onFailure: (IOException) -> Unit
) : FilterInputStream(input) {
    var bytesRead: Long = 0L
        private set
    private var failureLogged = false

    override fun read(): Int = try {
        super.read().also { if (it >= 0) bytesRead += 1 }
    } catch (error: IOException) {
        logFailure(error)
        throw error
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
    } catch (error: IOException) {
        logFailure(error)
        throw error
    }

    private fun logFailure(error: IOException) {
        if (!failureLogged) {
            failureLogged = true
            onFailure(error)
        }
    }
}

internal fun managedStreamHeaders(request: ExtensionStreamRequest): Map<String, String> {
    val result = request.headers.filterKeys { name ->
        ForbiddenStreamHeaders.none { it.equals(name, ignoreCase = true) }
    }.toMutableMap()
    if (request.position > 0L || request.length != C.LENGTH_UNSET.toLong()) {
        val end = if (request.length == C.LENGTH_UNSET.toLong()) {
            ""
        } else {
            Math.addExact(request.position, request.length - 1L).toString()
        }
        result["Range"] = "bytes=${request.position}-$end"
    }
    return result
}

private fun streamRequestLogDetails(request: ExtensionStreamRequest): String {
    val range = request.headers.entries
        .firstOrNull { it.key.equals("Range", ignoreCase = true) }
        ?.value
        ?: "<none>"
    return "method=GET ${safeUrlDetails(request.url)} range=$range requestBytes=0"
}

private fun safeUrlDetails(url: String): String = requestLogDetails(
    ExtensionHttpRequest(ExtensionHttpMethod.Get, url)
).substringAfter("method=Get ")

private fun ExtensionStreamResponse.headerValue(name: String): String? = headers.entries
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value
    ?.firstOrNull()

private val ForbiddenStreamHeaders = setOf(
    "Host",
    "Content-Length",
    "Connection",
    "Transfer-Encoding",
    "Range"
)

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
