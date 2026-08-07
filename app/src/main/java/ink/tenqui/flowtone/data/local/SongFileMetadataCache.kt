package ink.tenqui.flowtone.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONObject

internal data class CachedSongFileMetadata(
    val metadata: SongFileMetadata?
)

internal class SongFileMetadataCache(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun find(
        uri: Uri,
        dateModifiedSeconds: Long,
        sizeBytes: Long
    ): CachedSongFileMetadata? {
        val value = preferences.getString(uri.toString(), null) ?: return null
        return runCatching {
            val json = JSONObject(value)
            if (!isSameFileVersion(
                    cachedDateModifiedSeconds = json.getLong(KEY_DATE_MODIFIED),
                    cachedSizeBytes = json.getLong(KEY_SIZE),
                    currentDateModifiedSeconds = dateModifiedSeconds,
                    currentSizeBytes = sizeBytes
                )
            ) {
                return null
            }
            CachedSongFileMetadata(
                metadata = if (json.getBoolean(KEY_READ_SUCCEEDED)) {
                    SongFileMetadata(
                        title = json.nullableString(KEY_TITLE),
                        artist = json.nullableString(KEY_ARTIST)
                    )
                } else {
                    null
                }
            )
        }.getOrNull()
    }

    fun update(
        validUris: Set<String>,
        changedEntries: Map<String, SongFileMetadataCacheEntry>
    ) {
        if (changedEntries.isEmpty() && preferences.all.keys.all(validUris::contains)) {
            return
        }
        preferences.edit().apply {
            changedEntries.forEach { (uri, entry) ->
                putString(uri, entry.toJson().toString())
            }
            preferences.all.keys
                .filterNot(validUris::contains)
                .forEach(::remove)
        }.apply()
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (isNull(key)) null else getString(key)
    }

    private companion object {
        const val PREFERENCES_NAME = "song_file_metadata_cache_v1"
        const val KEY_DATE_MODIFIED = "dateModified"
        const val KEY_SIZE = "size"
        const val KEY_READ_SUCCEEDED = "readSucceeded"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
    }
}

internal fun isSameFileVersion(
    cachedDateModifiedSeconds: Long,
    cachedSizeBytes: Long,
    currentDateModifiedSeconds: Long,
    currentSizeBytes: Long
): Boolean {
    return cachedDateModifiedSeconds == currentDateModifiedSeconds &&
        cachedSizeBytes == currentSizeBytes
}

internal data class SongFileMetadataCacheEntry(
    val dateModifiedSeconds: Long,
    val sizeBytes: Long,
    val metadata: SongFileMetadata?
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("dateModified", dateModifiedSeconds)
            .put("size", sizeBytes)
            .put("readSucceeded", metadata != null)
            .put("title", metadata?.title ?: JSONObject.NULL)
            .put("artist", metadata?.artist ?: JSONObject.NULL)
    }
}
