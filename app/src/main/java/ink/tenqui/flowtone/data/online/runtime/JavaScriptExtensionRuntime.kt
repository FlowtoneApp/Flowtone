package ink.tenqui.flowtone.data.online.runtime

import android.util.Log
import androidx.javascriptengine.JavaScriptException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.Message
import androidx.javascriptengine.MessagePort
import androidx.javascriptengine.MessagePortClient
import ink.tenqui.flowtone.data.online.network.AdmissionAwareExtensionNetworkClient
import ink.tenqui.flowtone.data.online.network.ExtensionHttpMethod
import ink.tenqui.flowtone.data.online.network.ExtensionHttpRequest
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkClient
import ink.tenqui.flowtone.data.online.network.ExtensionNetworkResourceExhaustedException
import ink.tenqui.flowtone.data.online.network.GlobalExtensionNetworkLimiter
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 一个扩展一个 isolate；能力代理仅通过它调用 JS，Host API 与生命周期在这里统一管理。 */
class JavaScriptExtensionRuntime(
    val installed: InstalledExtension,
    private val isolate: JavaScriptIsolate,
    private val network: ExtensionNetworkClient,
    private val privateCache: ExtensionPrivateCache
) : AutoCloseable {
    val extensionId: String = installed.manifest.id
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private lateinit var port: MessagePort

    suspend fun start() {
        check(!closed.get()) { "扩展 runtime 已关闭" }
        port = isolate.createMessageChannel(PortName, callbackExecutor, MessagePortClient(::onMessage))
        evaluateStatements(bootstrapScript() + "\n" + installed.directory.resolve("main.js").readText())
    }

    suspend fun invokeObject(method: String, request: JSONObject): JSONObject {
        return JSONObject(invokeJson(method, request))
    }

    internal suspend fun invokeJson(method: String, request: JSONObject): String {
        require(MethodName.matches(method)) { "非法扩展方法" }
        return evaluateExpression("JSON.stringify(await globalThis.flowtoneExtension.$method(${request}))")
    }

    private fun onMessage(message: Message) {
        if (closed.get() || message.type != Message.TYPE_STRING) return
        val incoming = runCatching { JSONObject(message.string) }.getOrNull() ?: return
        val requestId = incoming.optString("id")
        if (requestId.isEmpty()) return
        val admission = if (incoming.optString("type") == "http.request") {
            val client = network as? AdmissionAwareExtensionNetworkClient
            val accepted = client?.tryAcquireAdmission()
            if (client != null && accepted == null) {
                reply(failure(requestId, "RESOURCE_EXHAUSTED", "Flowtone Host 网络资源已满"))
                return
            }
            accepted
        } else null
        scope.launch {
            val reply = when (incoming.optString("type")) {
                "http.request" -> handleHttpRequest(requestId, incoming.optJSONObject("payload"), admission)
                "cache.get" -> handleCacheGet(requestId, incoming.optJSONObject("payload"))
                "cache.set" -> handleCacheSet(requestId, incoming.optJSONObject("payload"))
                "cache.remove" -> handleCacheRemove(requestId, incoming.optJSONObject("payload"))
                "cache.clear" -> cacheRequest(requestId) { privateCache.clear(extensionId); success(requestId, JSONObject.NULL) }
                "log" -> success(requestId, JSONObject.NULL)
                else -> failure(requestId, "UNKNOWN_TYPE", "未知 RPC 类型")
            }
            reply(reply)
        }
    }

    private fun reply(value: JSONObject) {
        if (!closed.get() && ::port.isInitialized) {
            runCatching { port.postMessage(Message.createStringMessage(value.toString())) }
        }
    }

    private suspend fun handleHttpRequest(id: String, payload: JSONObject?, admission: GlobalExtensionNetworkLimiter.Admission?): JSONObject {
        var delegated = false
        return try {
            if (payload == null || payload.optString("method", "GET") != "GET") return failure(id, "INVALID_REQUEST", "只允许 GET 请求")
            val headers = payload.optJSONObject("headers")?.let { json -> json.keys().asSequence().associateWith { json.getString(it) } }.orEmpty()
            val request = ExtensionHttpRequest(ExtensionHttpMethod.Get, payload.getString("url"), headers)
            val response = if (admission != null && network is AdmissionAwareExtensionNetworkClient) {
                delegated = true; network.executeAdmitted(request, admission)
            } else network.execute(request)
            success(id, JSONObject().put("status", response.statusCode).put("headers", JSONObject(response.headers.mapValues { it.value.joinToString(",") })).put("body", response.body.decodeToString()))
        } catch (error: Exception) {
            val type = when (error) {
                is ExtensionNetworkResourceExhaustedException -> "RESOURCE_EXHAUSTED"
                is SocketTimeoutException, is TimeoutException -> "TIMEOUT"
                else -> "NETWORK_ERROR"
            }
            failure(id, type, error.javaClass.simpleName)
        } finally { if (!delegated) admission?.close() }
    }

    private fun handleCacheGet(id: String, payload: JSONObject?) = cacheRequest(id) {
        val key = payload?.stringOrNull("key") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache key 必须是字符串")
        success(id, JSONObject().put("value", privateCache.get(extensionId, key) ?: JSONObject.NULL))
    }
    private fun handleCacheSet(id: String, payload: JSONObject?) = cacheRequest(id) {
        val key = payload?.stringOrNull("key") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache key 必须是字符串")
        val value = payload.stringOrNull("value") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache value 必须是字符串")
        privateCache.set(extensionId, key, value); success(id, JSONObject.NULL)
    }
    private fun handleCacheRemove(id: String, payload: JSONObject?) = cacheRequest(id) {
        val key = payload?.stringOrNull("key") ?: return@cacheRequest failure(id, "INVALID_REQUEST", "cache key 必须是字符串")
        privateCache.remove(extensionId, key); success(id, JSONObject.NULL)
    }
    private inline fun cacheRequest(id: String, block: () -> JSONObject): JSONObject = try { block() } catch (error: Exception) { failure(id, "CACHE_ERROR", error.javaClass.simpleName) }
    private fun JSONObject.stringOrNull(name: String): String? = opt(name) as? String

    private suspend fun evaluateStatements(script: String): String = evaluateWrapped("(async()=>{$script})()")
    private suspend fun evaluateExpression(expression: String): String = evaluateWrapped("(async()=>{return ($expression);})()")
    private suspend fun evaluateWrapped(script: String): String = withContext(Dispatchers.IO) {
        try { isolate.evaluateJavaScriptAsync(script).get(EvaluationTimeoutSeconds, TimeUnit.SECONDS) }
        catch (error: Exception) {
            val cause = error.cause ?: error
            if (cause is JavaScriptException) Log.w(LogTag, "extension.js.failed extension=$extensionId type=${cause.javaClass.simpleName}")
            throw cause
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel(); if (::port.isInitialized) port.close(); isolate.close(); callbackExecutor.shutdownNow()
    }

    internal fun bootstrapScript(): String = """
        const __port = await android.getNamedPort('$PortName');
        const __pending = new Map(); let __sequence = 0;
        __port.onmessage = event => { let message; try { message = JSON.parse(event.data); } catch (_) { return; }
          const pending = __pending.get(message.id); if (!pending) return; __pending.delete(message.id);
          message.ok ? pending.resolve(message.result) : pending.reject(new Error(message.error?.type || 'HOST_ERROR')); };
        function __rpc(type, payload) { const id = 'req-' + (++__sequence) + '-' + Date.now(); return new Promise((resolve, reject) => { __pending.set(id, {resolve, reject}); __port.postMessage(JSON.stringify({id, type, payload})); }); }
        globalThis.flowtone = Object.freeze({http:Object.freeze({request:request=>__rpc('http.request',request)}),cache:Object.freeze({get:async key=>(await __rpc('cache.get',{key})).value,set:(key,value)=>__rpc('cache.set',{key,value}),remove:key=>__rpc('cache.remove',{key}),clear:()=>__rpc('cache.clear',{})}),log:Object.freeze({debug:message=>__rpc('log',{level:'debug',message:String(message)}),info:message=>__rpc('log',{level:'info',message:String(message)}),warn:message=>__rpc('log',{level:'warn',message:String(message)}),error:message=>__rpc('log',{level:'error',message:String(message)})})});
    """.trimIndent()

    private fun success(id: String, value: Any?) = JSONObject().put("id", id).put("ok", true).put("result", value)
    private fun failure(id: String, type: String, message: String) = JSONObject().put("id", id).put("ok", false).put("error", JSONObject().put("type", type).put("message", message))
    private companion object { val MethodName = Regex("[A-Za-z][A-Za-z0-9_]*"); const val PortName="flowtone-host"; const val EvaluationTimeoutSeconds=20L; const val LogTag="FlowtoneExtension" }
}
