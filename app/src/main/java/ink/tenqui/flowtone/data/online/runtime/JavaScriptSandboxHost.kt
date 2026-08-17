package ink.tenqui.flowtone.data.online.runtime

import android.content.Context
import android.util.Log
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Flowtone 进程只持有一个 sandbox；每个已安装扩展由它创建独立 isolate。 */
class JavaScriptSandboxHost(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var sandbox: JavaScriptSandbox? = null

    fun isRuntimeSupported(): Boolean = JavaScriptSandbox.isSupported()

    suspend fun createIsolate(): JavaScriptIsolate? {
        if (!isRuntimeSupported()) return null
        val shared = mutex.withLock {
            sandbox ?: withContext(Dispatchers.IO) {
                JavaScriptSandbox.createConnectedInstanceAsync(appContext).get(10, TimeUnit.SECONDS)
            }.also { sandbox = it }
        }
        if (!shared.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_MESSAGE_PORTS) ||
            !shared.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_PROMISE_RETURN)
        ) return null
        val parameters = IsolateStartupParameters().apply {
            setMaxEvaluationReturnSizeBytes(MaxReturnBytes)
            if (shared.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
                setMaxHeapSizeBytes(MaxHeapBytes)
            }
        }
        return shared.createIsolate(parameters)
    }

    fun supportsConsole(): Boolean =
        sandbox?.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_CONSOLE_MESSAGING) == true

    override fun close() {
        runCatching { sandbox?.close() }.onFailure { Log.w(LogTag, "sandbox close failed", it) }
        sandbox = null
    }

    private companion object {
        const val LogTag = "FlowtoneExtension"
        const val MaxHeapBytes = 24L * 1024 * 1024
        const val MaxReturnBytes = 2 * 1024 * 1024
    }
}
