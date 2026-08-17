package ink.tenqui.flowtone.data.online

import android.util.Log
import ink.tenqui.flowtone.core.online.ArtistAvatar
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Flowtone 自己理解的成功 artist_avatar 结果磁盘 cache，不保存 Provider 私有元数据。 */
class ArtistAvatarPersistentCache(
    private val root: File,
    private val logger: ExtensionCoreLogger = ExtensionCoreLogger { event, details ->
        Log.d(LogTag, "$event $details")
    }
) {
    private val namespaces = mutableMapOf<String, MutableMap<String, String>>()

    @Synchronized
    fun get(extensionId: String, songTitle: String, artistName: String): ArtistAvatar? {
        validateExtensionId(extensionId)
        val imageUrl = namespace(extensionId)[cacheKey(extensionId, songTitle, artistName)] ?: return null
        return ArtistAvatar(ExtensionImage(extensionId, imageUrl))
    }

    @Synchronized
    fun put(extensionId: String, songTitle: String, artistName: String, avatar: ArtistAvatar) {
        validateExtensionId(extensionId)
        require(avatar.image.extensionId == extensionId) { "ArtistAvatar 扩展身份不匹配" }
        require(avatar.image.url.startsWith("https://")) { "ArtistAvatar URL 必须为 HTTPS" }
        val namespace = namespace(extensionId)
        namespace[cacheKey(extensionId, songTitle, artistName)] = avatar.image.url
        persist(extensionId, namespace)
    }

    @Synchronized
    fun clear(extensionId: String) {
        validateExtensionId(extensionId)
        namespaces.remove(extensionId)
        File(root, extensionId).resolve(CacheDirectoryName).deleteRecursively()
    }

    private fun namespace(extensionId: String): MutableMap<String, String> = namespaces.getOrPut(extensionId) {
        load(extensionId)
    }

    private fun load(extensionId: String): MutableMap<String, String> {
        val file = cacheFile(extensionId)
        if (!file.isFile) return linkedMapOf()
        return runCatching {
            val rootJson = JSONObject(file.readText(Charsets.UTF_8))
            require(rootJson.optInt("format", -1) == FormatVersion) { "不支持的头像 cache 格式" }
            require(rootJson.optString("extensionId") == extensionId) { "头像 cache 扩展身份不匹配" }
            val values = rootJson.optJSONArray("entries") ?: JSONArray()
            linkedMapOf<String, String>().apply {
                repeat(values.length()) { index ->
                    val item = values.optJSONObject(index) ?: return@repeat
                    val key = item.optString("cacheKey")
                    val url = item.optString("imageUrl")
                    if (key.isNotBlank() && url.startsWith("https://")) put(key, url)
                }
            }
        }.getOrElse { error ->
            logger.log(
                "extension.artist_avatar.cache.corrupted",
                "extension=$extensionId failure=${error.javaClass.simpleName}"
            )
            file.delete()
            linkedMapOf()
        }
    }

    private fun persist(extensionId: String, entries: Map<String, String>) {
        val file = cacheFile(extensionId)
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("format", FormatVersion)
            .put("extensionId", extensionId)
            .put("entries", JSONArray().apply {
                entries.forEach { (key, imageUrl) ->
                    put(JSONObject().put("cacheKey", key).put("imageUrl", imageUrl))
                }
            })
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

    private fun cacheFile(extensionId: String): File =
        File(root, extensionId).resolve(CacheDirectoryName).resolve(CacheFileName)

    private fun validateExtensionId(extensionId: String) {
        require(SafeExtensionId.matches(extensionId) && ".." !in extensionId) { "扩展 ID 非法" }
    }

    companion object {
        private const val LogTag = "FlowtoneExtension"
        private const val FormatVersion = 1
        private const val CacheDirectoryName = "artist-avatar-results"
        private const val CacheFileName = "entries.json"
        private val SafeExtensionId = Regex("[a-zA-Z0-9._-]+")

        internal fun cacheKey(extensionId: String, songTitle: String, artistName: String): String =
            "$extensionId\nartist_avatar\n${normalize(songTitle)}\n${normalize(artistName)}"

        private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)
    }
}

