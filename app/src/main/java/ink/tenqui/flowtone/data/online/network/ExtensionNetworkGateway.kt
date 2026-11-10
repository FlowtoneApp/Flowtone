package ink.tenqui.flowtone.data.online.network

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import ink.tenqui.flowtone.data.online.permission.ExtensionNetworkAccessPolicy
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import androidx.media3.common.C
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import ink.tenqui.flowtone.data.online.credential.CommittedCredentialHeaders
import ink.tenqui.flowtone.data.online.credential.CredentialResolutionError
import ink.tenqui.flowtone.data.online.credential.CredentialResolutionException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible

/** Flowtone 控制的扩展网络网关，也是唯一实际执行 HTTP 的位置。 */
class ExtensionNetworkGateway(
    private val transport: ExtensionHttpTransport = OkHttpExtensionTransport(),
    private val streamTransport: ExtensionStreamTransport = HttpUrlConnectionStreamTransport(),
    private val logger: ExtensionCoreLogger = AndroidExtensionCoreLogger,
    private val limiter: GlobalExtensionNetworkLimiter = GlobalExtensionNetworkLimiter()
) {
    fun createClientFor(
        extensionId: String,
        capability: String,
        allowedPermissions: Set<NetworkOriginPermission>
    ): ExtensionNetworkClient {
        return BoundExtensionNetworkClient(
            extensionId,
            capability,
            ExtensionNetworkAccessPolicy(allowedPermissions),
            transport,
            logger,
            limiter
        )
    }

    internal fun createCredentialClientFor(
        extensionId: String,
        capability: String,
        allowedPermissions: Set<NetworkOriginPermission>,
        commit: CredentialCommitter
    ): ExtensionNetworkClient = BoundExtensionNetworkClient(
        extensionId, capability, ExtensionNetworkAccessPolicy(allowedPermissions), transport, logger, limiter, commit
    )

    fun createStreamClientFor(
        extensionId: String,
        capability: String,
        allowedPermissions: Set<NetworkOriginPermission>
    ): ExtensionStreamClient {
        return BoundExtensionStreamClient(
            extensionId = extensionId,
            capability = capability,
            accessPolicy = ExtensionNetworkAccessPolicy(allowedPermissions),
            transport = streamTransport,
            logger = logger,
            limiter = limiter
        )
    }
}

internal fun interface CredentialCommitter {
    fun commit(admission: GlobalExtensionNetworkLimiter.Admission, requestId: String, targetUrl: String): CommittedCredentialHeaders
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
    private val accessPolicy: ExtensionNetworkAccessPolicy,
    private val transport: ExtensionHttpTransport,
    private val logger: ExtensionCoreLogger,
    private val limiter: GlobalExtensionNetworkLimiter,
    private val credentialCommitter: CredentialCommitter? = null
) : AdmissionAwareExtensionNetworkClient {
    override suspend fun execute(request: ExtensionHttpRequest): ExtensionHttpResponse {
        val admission = tryAcquireAdmission() ?: throw ExtensionNetworkResourceExhaustedException()
        return executeAdmitted(request, admission)
    }

    override fun tryAcquireAdmission(): GlobalExtensionNetworkLimiter.Admission? {
        val admission = limiter.tryAcquireInFlight()
        if (admission == null) {
            logger.log(
                "extension.network.limit.rejected",
                identityDetails() + " active=${limiter.snapshot().active} inFlight=${limiter.snapshot().inFlight} " +
                    "maxActive=${limiter.maxActiveRequests} maxInFlight=${limiter.maxInFlightRequests}"
            )
        }
        return admission
    }

    override suspend fun executeAdmitted(
        request: ExtensionHttpRequest,
        admission: GlobalExtensionNetworkLimiter.Admission
    ): ExtensionHttpResponse {
        val requestDetails = requestLogDetails(request)
        val startedAt = System.nanoTime()
        var committed: CommittedCredentialHeaders? = null
        var mergedHeaders: MutableMap<String, String>? = null
        return try {
            val credentialId = request.credentialRequestId
            if (credentialId != null) validateCredentialRequest(request)
            if (credentialId == null) {
                accessPolicy.requireAllowed(request.url)
            } else {
                try { accessPolicy.requireAllowed(request.url) }
                catch (_: SecurityException) { throw CredentialResolutionException(CredentialResolutionError.CREDENTIAL_ORIGIN_DENIED) }
            }
            logger.log("extension.http.prepared", identityDetails() + " $requestDetails")
            val queued = if (credentialId != null) runInterruptible(Dispatchers.IO) { admission.acquireActive() }
                else admission.acquireActive()
            if (queued) logQueued()
            currentCoroutineContext().ensureActive()
            val outgoing = if (credentialId != null) {
                val committer = credentialCommitter ?: throw CredentialResolutionException(CredentialResolutionError.CREDENTIAL_NOT_AUTHORIZED)
                committed = try {
                    committer.commit(admission, credentialId, request.url)
                } catch (error: CredentialResolutionException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    throw CredentialResolutionException(CredentialResolutionError.CREDENTIAL_UNAVAILABLE)
                }
                mergedHeaders = request.headers.toMutableMap().apply { putAll(committed!!.forTransport()) }
                request.copy(headers = mergedHeaders!!)
            } else request
            currentCoroutineContext().ensureActive()
            logger.log("extension.http.started", identityDetails() + " $requestDetails")
            val response = try {
                transport.execute(outgoing, accessPolicy::requireAllowed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (credentialId == null) throw error
                throw IOException("认证 HTTP 请求失败")
            }
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
        } finally {
            mergedHeaders?.clear()
            committed?.close()
            admission.close()
        }
    }

    private fun identityDetails(): String = "extension=$extensionId capability=$capability"

    private fun logQueued() {
        val snapshot = limiter.snapshot()
        logger.log(
            "extension.network.limit.queued",
            identityDetails() + " active=${snapshot.active} inFlight=${snapshot.inFlight} " +
                "maxActive=${limiter.maxActiveRequests} maxInFlight=${limiter.maxInFlightRequests}"
        )
    }
}

private fun validateCredentialRequest(request: ExtensionHttpRequest) {
    require(request.credentialRequestId?.matches(Regex("[a-zA-Z][a-zA-Z0-9._-]{0,63}")) == true) { "凭据请求 ID 非法" }
    require((request.body?.size ?: 0) <= MaxExtensionRequestBytes) { "请求体超过 256 KiB" }
    require(request.method != ExtensionHttpMethod.Get || request.body == null) { "GET 不支持请求体" }
    val forbidden = setOf("authorization", "cookie", "proxy-authorization", "host", "connection", "proxy-connection", "transfer-encoding", "content-length")
    request.headers.forEach { (name, value) ->
        require(name.lowercase(java.util.Locale.ROOT) !in forbidden) { "认证请求头冲突" }
        require(name.isNotEmpty() && name.all { it.code in 33..126 && it !in "()<>@,;:\\\"/[]?={} \t" } &&
            value.all { it == '\t' || it.code in 32..126 }) { "HTTP 请求头非法" }
    }
}

private class BoundExtensionStreamClient(
    private val extensionId: String,
    private val capability: String,
    private val accessPolicy: ExtensionNetworkAccessPolicy,
    private val transport: ExtensionStreamTransport,
    private val logger: ExtensionCoreLogger,
    private val limiter: GlobalExtensionNetworkLimiter
) : ExtensionStreamClient {
    override fun open(request: ExtensionStreamRequest): ExtensionStreamResponse {
        require(request.position >= 0L) { "流起始位置不能为负数" }
        require(request.length == C.LENGTH_UNSET.toLong() || request.length > 0L) {
            "流请求长度非法"
        }
        val identity = "extension=$extensionId capability=$capability"
        val startedAt = System.nanoTime()
        val admission = limiter.tryAcquireInFlight()
            ?: run {
                val snapshot = limiter.snapshot()
                logger.log(
                    "extension.network.limit.rejected",
                    "$identity active=${snapshot.active} inFlight=${snapshot.inFlight} " +
                        "maxActive=${limiter.maxActiveRequests} maxInFlight=${limiter.maxInFlightRequests}"
                )
                throw ExtensionNetworkResourceExhaustedException()
            }
        logger.log(
            "extension.media.open",
            "$identity ${safeUrlDetails(request.url)} position=${request.position} " +
                "requestedLength=${request.length}"
        )
        return try {
            accessPolicy.requireAllowed(request.url)
            val managedRequest = request.copy(headers = managedStreamHeaders(request))
            val details = streamRequestLogDetails(managedRequest)
            logger.log("extension.http.prepared", "$identity $details")
            val queued = admission.acquireActive()
            if (queued) {
                val snapshot = limiter.snapshot()
                logger.log(
                    "extension.network.limit.queued",
                    "$identity active=${snapshot.active} inFlight=${snapshot.inFlight} " +
                        "maxActive=${limiter.maxActiveRequests} maxInFlight=${limiter.maxInFlightRequests}"
                )
            }
            logger.log("extension.http.started", "$identity $details")
            val response = transport.open(managedRequest, accessPolicy::requireAllowed)
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
                    admission.close()
                    logger.log(
                        "extension.media.close",
                        "$identity bytesRead=${countingBody.bytesRead} durationMs=${elapsedMillis(startedAt)}"
                    )
                }
            )
        } catch (error: Exception) {
            admission.close()
            logger.log(
                "extension.http.failed",
                "$identity failure=${error.javaClass.simpleName} durationMs=${elapsedMillis(startedAt)}"
            )
            throw error
        }
    }
}

internal class OkHttpExtensionTransport : ExtensionHttpTransport {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    override suspend fun execute(request: ExtensionHttpRequest, authorizeUrl: (String) -> Unit): ExtensionHttpResponse =
        withContext(Dispatchers.IO) {
            require((request.body?.size ?: 0) <= MaxExtensionRequestBytes) { "请求体超过 256 KiB" }
            require(request.method != ExtensionHttpMethod.Get || request.body == null) { "GET 不支持请求体" }
            var currentUrl = request.url
            var headers = request.headers
            repeat(MaxRedirects + 1) { redirectCount ->
                authorizeUrl(currentUrl)
                val body = when (request.method) {
                    ExtensionHttpMethod.Get -> null
                    ExtensionHttpMethod.Post, ExtensionHttpMethod.Put, ExtensionHttpMethod.Patch ->
                        (request.body ?: ByteArray(0)).toRequestBody()
                    ExtensionHttpMethod.Delete -> request.body?.toRequestBody()
                }
                val builder = Request.Builder().url(currentUrl).method(request.method.name.uppercase(), body)
                headers.forEach { (name, value) -> builder.header(name, value) }
                client.newCall(builder.build()).execute().use { response ->
                    if (request.credentialRequestId == null && request.method == ExtensionHttpMethod.Get && response.code in RedirectStatuses) {
                        require(redirectCount < MaxRedirects) { "HTTP 重定向次数过多" }
                        val location = response.header("Location") ?: error("HTTP 重定向缺少 Location")
                        val nextUrl = URL(URL(currentUrl), location).toString()
                        authorizeUrl(nextUrl)
                        if (!sameOrigin(currentUrl, nextUrl)) headers = emptyMap()
                        currentUrl = nextUrl
                        return@repeat
                    }
                    val bytes = response.body?.byteStream()?.use { input ->
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
                    return@withContext ExtensionHttpResponse(response.code, response.headers.toMultimap(), bytes)
                }
            }
            error("HTTP 重定向失败")
        }

    private fun sameOrigin(first: String, second: String): Boolean {
        val a = URL(first); val b = URL(second)
        return a.protocol.equals(b.protocol, true) && a.host.equals(b.host, true) &&
            (if (a.port == -1) a.defaultPort else a.port) == (if (b.port == -1) b.defaultPort else b.port)
    }
    private companion object {
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
            "$name=****"
        }
        .orEmpty()
    return "method=${request.method} host=${uri.host.orEmpty()} path=${uri.rawPath.orEmpty()} " +
        "query=${if (query.isEmpty()) "<none>" else query} requestBytes=${request.body?.size ?: 0}"
}

private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
