package ink.tenqui.flowtone.data.online.runtime

import android.util.Log
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * Flowtone 管理的扩展私有持久 cache。
 *
 * value 对宿主没有业务语义；它可被淘汰、卸载时删除，扩展必须能够处理 cache miss。
 */
class ExtensionPrivateCache(
    private val root: File,
    private val quotaBytes: Int = DefaultQuotaBytes,
    private val maxKeyBytes: Int = DefaultMaxKeyBytes,
    private val maxValueBytes: Int = DefaultMaxValueBytes,
    private val logger: ExtensionCoreLogger = ExtensionCoreLogger { event, details ->
        Log.d(LogTag, "$event $details")
    }
) {
    private val namespaces = mutableMapOf<String, Namespace>()

    @Synchronized
    fun get(extensionId: String, key: String): String? {
        validateKey(extensionId, key)
        val namespace = namespace(extensionId)
        val entry = namespace.entries[key]
        if (entry == null) {
            logger.log("extension.cache.miss", "extension=$extensionId keyBytes=${utf8Size(key)}")
            return null
        }
        entry.lastAccess = namespace.nextAccess()
        persist(extensionId, namespace)
        logger.log("extension.cache.hit", "extension=$extensionId keyBytes=${utf8Size(key)}")
        return entry.value
    }

    @Synchronized
    fun set(extensionId: String, key: String, value: String) {
        validateKey(extensionId, key)
        val keyBytes = utf8Size(key)
        val valueBytes = utf8Size(value)
        require(valueBytes <= maxValueBytes) { "cache value 超过单项上限" }
        require(keyBytes + valueBytes <= quotaBytes) { "cache 项超过扩展容量上限" }
        val namespace = namespace(extensionId)
        namespace.entries[key] = Entry(value, namespace.nextAccess())
        evictToQuota(extensionId, namespace)
        persist(extensionId, namespace)
        logger.log(
            "extension.cache.set",
            "extension=$extensionId keyBytes=$keyBytes valueBytes=$valueBytes totalBytes=${namespace.sizeBytes()}"
        )
    }

    @Synchronized
    fun remove(extensionId: String, key: String) {
        validateKey(extensionId, key)
        val namespace = namespace(extensionId)
        if (namespace.entries.remove(key) != null) {
            persist(extensionId, namespace)
        }
        logger.log("extension.cache.remove", "extension=$extensionId keyBytes=${utf8Size(key)}")
    }

    @Synchronized
    fun clear(extensionId: String) {
        validateExtensionId(extensionId)
        namespaces.remove(extensionId)
        File(root, extensionId).deleteRecursively()
        logger.log("extension.cache.clear", "extension=$extensionId")
    }

    /** 明确卸载扩展时调用；正常同 ID 更新不会调用，因此会保留 cache。 */
    fun deleteForUninstall(extensionId: String) = clear(extensionId)

    private fun namespace(extensionId: String): Namespace = namespaces.getOrPut(extensionId) {
        load(extensionId)
    }

    private fun load(extensionId: String): Namespace {
        val file = cacheFile(extensionId)
        if (!file.isFile) return Namespace()
        return runCatching {
            val values = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("entries") ?: JSONArray()
            val entries = linkedMapOf<String, Entry>()
            var lastAccess = 0L
            repeat(values.length()) { index ->
                val item = values.optJSONObject(index) ?: return@repeat
                val key = item.optString("key")
                val value = item.optString("value")
                val access = item.optLong("lastAccess", 0L)
                if (key.isNotEmpty() && utf8Size(key) <= maxKeyBytes && utf8Size(value) <= maxValueBytes) {
                    entries[key] = Entry(value, access)
                    lastAccess = maxOf(lastAccess, access)
                }
            }
            Namespace(entries, lastAccess)
        }.getOrElse {
            logger.log("extension.cache.load_failed", "extension=$extensionId failure=${it.javaClass.simpleName}")
            Namespace()
        }.also { namespace ->
            evictToQuota(extensionId, namespace)
        }
    }

    private fun evictToQuota(extensionId: String, namespace: Namespace) {
        while (namespace.sizeBytes() > quotaBytes) {
            val oldest = namespace.entries.minByOrNull { it.value.lastAccess } ?: break
            namespace.entries.remove(oldest.key)
            logger.log(
                "extension.cache.evict",
                "extension=$extensionId keyBytes=${utf8Size(oldest.key)} valueBytes=${utf8Size(oldest.value.value)}"
            )
        }
    }

    private fun persist(extensionId: String, namespace: Namespace) {
        val file = cacheFile(extensionId)
        file.parentFile?.mkdirs()
        val json = JSONObject().put("format", FormatVersion).put(
            "entries",
            JSONArray().apply {
                namespace.entries.forEach { (key, entry) ->
                    put(JSONObject()
                        .put("key", key)
                        .put("value", entry.value)
                        .put("lastAccess", entry.lastAccess))
                }
            }
        )
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.toString(), Charsets.UTF_8)
        runCatching {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun cacheFile(extensionId: String): File = File(root, extensionId).resolve("cache").resolve("entries.json")

    private fun validateKey(extensionId: String, key: String) {
        validateExtensionId(extensionId)
        require(key.isNotEmpty() && utf8Size(key) <= maxKeyBytes) { "cache key 非法或超过单项上限" }
    }

    private fun validateExtensionId(extensionId: String) {
        require(SafeExtensionId.matches(extensionId) && ".." !in extensionId) { "扩展 ID 非法" }
    }

    private fun utf8Size(value: String): Int = value.toByteArray(StandardCharsets.UTF_8).size

    private data class Entry(val value: String, var lastAccess: Long)

    private class Namespace(
        val entries: MutableMap<String, Entry> = linkedMapOf(),
        private var accessCounter: Long = System.currentTimeMillis()
    ) {
        fun nextAccess(): Long = maxOf(System.currentTimeMillis(), accessCounter + 1L).also { accessCounter = it }
        fun sizeBytes(): Int = entries.entries.sumOf { (key, entry) ->
            key.toByteArray(StandardCharsets.UTF_8).size + entry.value.toByteArray(StandardCharsets.UTF_8).size
        }
    }

    private companion object {
        const val LogTag = "FlowtoneExtension"
        const val FormatVersion = 1
        const val DefaultQuotaBytes = 5 * 1024 * 1024
        const val DefaultMaxKeyBytes = 512
        const val DefaultMaxValueBytes = 256 * 1024
        val SafeExtensionId = Regex("[a-zA-Z0-9._-]+")
    }
}

