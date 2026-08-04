package ink.tenqui.flowtone.lyrics

import android.content.Context
import android.net.Uri
import org.json.JSONArray

/** 持久化用户添加的歌词目录，列表顺序即歌词查找优先级。 */
class LyricsDirectoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getTreeUris(): List<Uri> {
        val storedUris = preferences.getString(KEY_TREE_URIS, null)
            ?.let(::decodeUriStrings)
            .orEmpty()
        if (storedUris.isNotEmpty()) {
            return storedUris.map(Uri::parse)
        }

        // 兼容 0.13.0 之前仅能保存一个目录的配置。
        return preferences.getString(LEGACY_KEY_TREE_URI, null)
            ?.let(Uri::parse)
            ?.let(::listOf)
            .orEmpty()
    }

    fun saveTreeUris(uris: List<Uri>) {
        val normalizedUris = uris.map(Uri::toString).distinct()
        preferences.edit()
            .putString(KEY_TREE_URIS, JSONArray(normalizedUris).toString())
            .remove(LEGACY_KEY_TREE_URI)
            .apply()
    }

    private fun decodeUriStrings(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFERENCES_NAME = "lyrics_directory"
        const val LEGACY_KEY_TREE_URI = "tree_uri"
        const val KEY_TREE_URIS = "tree_uris"
    }
}
