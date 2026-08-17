package ink.tenqui.flowtone.data.online.runtime

import android.util.Log
import androidx.javascriptengine.JavaScriptException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.Message
import androidx.javascriptengine.MessagePort
import androidx.javascriptengine.MessagePortClient
import ink.tenqui.flowtone.core.online.ArtistAvatar
import ink.tenqui.flowtone.core.online.ArtistAvatarExtension
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.network.ExtensionHttpMethod
import ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.network.AdmissionAwareExtensionNetworkClient
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkResourceExhaustedException
import ink.tenqui.flowtone.data.online.network.GlobalExtensionNetworkLimiter
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 所有 artist_avatar 脚本共用的 Kotlin SPI 代理；不包含任何服务商业务。 */
class JavaScriptArtistAvatarExtension(
    private val installed: InstalledExtension,
    private val isolate: JavaScriptIsolate,
    private val network: ExtensionNetworkClient,
    private val privateCache: ExtensionPrivateCache
) : ArtistAvatarExtension, AutoCloseable {
    override val id: String = installed.manifest.id
    override val displayName: String = installed.manifest.name
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private lateinit var port: MessagePort

    suspend fun start() {
        port = isolate.createMessageChannel(PortName, callbackExecutor, MessagePortClient(::onMessage))
        evaluateStatements(bootstrapScript() + "\n" + installed.directory.resolve("main.js").readText())
    }

    override suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
        val request = JSONObject().put("songTitle", songTitle).put("artistName", artistName)
        val raw = evaluateExpression(
            "JSON.stringify(await globalThis.flowtoneExtension.findArtistAvatar(${request}))"
        )
        val result = JSONObject(raw)
        return when (result.optString("type")) {
            "found" -> result.optString("imageUrl").trim().takeIf { it.startsWith("https://") }
                ?.let { imageUrl -> ArtistAvatar(ExtensionImage(extensionId = id, url = imageUrl)) }
            else -> null
        }
    }

    private fun onMessage(message: Message) {
        if (message.type != Message.TYPE_STRING) return
        val raw = runCatching { message.string }.getOrNull() ?: return
        val incoming = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val requestId = incoming.optString("id")
        if (requestId.isEmpty()) return
        val type = incoming.optString("type")
        val admission = if (type == "http.request") {
            val client = network as? AdmissionAwareExtensionNetworkClient
            val accepted = client?.tryAcquireAdmission()
            if (client != null && accepted == null) {
                port.postMessage(Message.createStringMessage(failure(requestId, "RESOURCE_EXHAUSTED", "Flowtone Host 网络资源已满").toString()))
                return
            }
            accepted
        } else {
            null
        }
        scope.launch {
            Log.d(LogTag, "extension.rpc.received extension=$id type=${incoming.optString("type")}")
            val reply = when (incoming.optString("type")) {
                "http.request" -> handleHttpRequest(requestId, incoming.optJSONObject("payload"), admission)
                "cache.get" -> handleCacheGet(requestId, incoming.optJSONObject("payload"))
                "cache.set" -> handleCacheSet(requestId, incoming.optJSONObject("payload"))
                "cache.remove" -> handleCacheRemove(requestId, incoming.optJSONObject("payload"))
                "cache.clear" -> handleCacheClear(requestId)
                "log" -> {
                    Log.d(LogTag, "extension.log extension=$id ${incoming.optJSONObject("payload")}")
                    success(requestId, JSONObject.NULL)
                }
                else -> failure(requestId, "UNKNOWN_TYPE", "未知 RPC 类型")
            }
            port.postMessage(Message.createStringMessage(reply.toString()))
        }
    }

    private suspend fun handleHttpRequest(
        id: String,
        payload: JSONObject?,
        admission: GlobalExtensionNetworkLimiter.Admission?
    ): JSONObject {
        var delegatedAdmission = false
        return try {
            if (payload == null) return failure(id, "INVALID_REQUEST", "缺少请求参数")
            val method = payload.optString("method", "GET")
            if (method != "GET") return failure(id, "METHOD_NOT_ALLOWED", "当前只允许 GET")
            val headers = payload.optJSONObject("headers")?.let { json ->
                json.keys().asSequence().associateWith { json.getString(it) }
            }.orEmpty()
            val request = ExtensionHttpRequest(ExtensionHttpMethod.Get, payload.getString("url"), headers)
            val response = if (admission != null && network is AdmissionAwareExtensionNetworkClient) {
                delegatedAdmission = true
                network.executeAdmitted(request, admission)
            } else {
                network.execute(request)
            }
            success(id, JSONObject()
                .put("status", response.statusCode)
                .put("headers", JSONObject(response.headers.mapValues { it.value.joinToString(",") }))
                .put("body", response.body.decodeToString()))
        } catch (error: Exception) {
            val type = when (error) {
                is ExtensionNetworkResourceExhaustedException -> "RESOURCE_EXHAUSTED"
                is SocketTimeoutException, is TimeoutException -> "TIMEOUT"
                else -> "NETWORK_ERROR"
            }
            failure(id, type, error.javaClass.simpleName)
        } finally {
            if (!delegatedAdmission) admission?.close()
        }
    }

    private fun handleCacheGet(id: String, payload: JSONObject?): JSONObject = cacheRequest(id) {
        val key = payload?.stringOrNull("key") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache key 必须是字符串")
        success(id, JSONObject().put("value", privateCache.get(this.id, key) ?: JSONObject.NULL))
    }

    private fun handleCacheSet(id: String, payload: JSONObject?): JSONObject = cacheRequest(id) {
        val key = payload?.stringOrNull("key") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache key 必须是字符串")
        val value = payload.stringOrNull("value") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache value 必须是字符串")
        privateCache.set(this.id, key, value)
        success(id, JSONObject.NULL)
    }

    private fun handleCacheRemove(id: String, payload: JSONObject?): JSONObject = cacheRequest(id) {
        val key = payload?.stringOrNull("key") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache key 必须是字符串")
        privateCache.remove(this.id, key)
        success(id, JSONObject.NULL)
    }

    private fun handleCacheClear(id: String): JSONObject = cacheRequest(id) {
        privateCache.clear(this.id)
        success(id, JSONObject.NULL)
    }

    private inline fun cacheRequest(id: String, block: () -> JSONObject): JSONObject = try {
        block()
    } catch (error: IllegalArgumentException) {
        failure(id, "CACHE_LIMIT", error.message ?: "cache 请求超出限制")
    } catch (error: Exception) {
        failure(id, "CACHE_ERROR", error.javaClass.simpleName)
    }

    private suspend fun evaluateStatements(script: String): String = evaluateWrapped("(async()=>{$script})()")

    private suspend fun evaluateExpression(expression: String): String =
        evaluateWrapped("(async()=>{return ($expression);})()")

    private suspend fun evaluateWrapped(script: String): String = withContext(Dispatchers.IO) {
        try {
            isolate.evaluateJavaScriptAsync(script).get(EvaluationTimeoutSeconds, TimeUnit.SECONDS)
        } catch (error: Exception) {
            val cause = error.cause ?: error
            if (cause is JavaScriptException) Log.w(LogTag, "extension.js.failed extension=$id type=${cause.javaClass.simpleName}")
            throw cause
        }
    }

    override fun close() {
        scope.cancel()
        if (::port.isInitialized) port.close()
        isolate.close()
        callbackExecutor.shutdownNow()
    }

    private fun success(id: String, value: Any?): JSONObject =
        JSONObject().put("id", id).put("ok", true).put("result", value)

    private fun failure(id: String, type: String, message: String): JSONObject = JSONObject()
        .put("id", id).put("ok", false)
        .put("error", JSONObject().put("type", type).put("message", message))

    private fun JSONObject.stringOrNull(name: String): String? = opt(name) as? String

    internal fun bootstrapScript(): String = """
        const __port = await android.getNamedPort('$PortName');
        const __pending = new Map();
        let __sequence = 0;
        __port.onmessage = event => {
          let message;
          try { message = JSON.parse(event.data); } catch (_) { return; }
          const pending = __pending.get(message.id);
          if (!pending) return;
          __pending.delete(message.id);
          message.ok ? pending.resolve(message.result) : pending.reject(new Error(message.error?.type || 'HOST_ERROR'));
        };
        function __rpc(type, payload) {
          const id = 'req-' + (++__sequence) + '-' + Date.now();
          return new Promise((resolve, reject) => {
            __pending.set(id, {resolve, reject});
            __port.postMessage(JSON.stringify({id, type, payload}));
          });
        }
        globalThis.flowtone = Object.freeze({
          http: Object.freeze({request: request => __rpc('http.request', request)}),
          cache: Object.freeze({
            get: async key => (await __rpc('cache.get', {key})).value,
            set: (key, value) => __rpc('cache.set', {key, value}),
            remove: key => __rpc('cache.remove', {key}),
            clear: () => __rpc('cache.clear', {})
          }),
          log: Object.freeze({
            debug: message => __rpc('log', {level:'debug', message:String(message)}),
            info: message => __rpc('log', {level:'info', message:String(message)}),
            warn: message => __rpc('log', {level:'warn', message:String(message)}),
            error: message => __rpc('log', {level:'error', message:String(message)})
          })
        });
    """.trimIndent()

    private companion object {
        const val PortName = "flowtone-host"
        const val EvaluationTimeoutSeconds = 20L
        const val LogTag = "FlowtoneExtension"
    }
}
