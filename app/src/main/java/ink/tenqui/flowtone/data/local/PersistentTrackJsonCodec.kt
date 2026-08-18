package ink.tenqui.flowtone.data.local

import ink.tenqui.flowtone.core.model.PersistentTrack
import org.json.JSONArray
import org.json.JSONObject

object PersistentTrackJsonCodec {
    fun encode(track: PersistentTrack): JSONObject = when (track) {
        is PersistentTrack.Local -> JSONObject()
            .put("type", "local")
            .put("songId", track.songId)
            .putCachedMetadata(track)
        is PersistentTrack.Online -> JSONObject()
            .put("type", "online")
            .put("sourceHost", track.sourceHost)
            .put("persistentId", track.persistentId)
            .putCachedMetadata(track)
    }

    fun decode(json: JSONObject): PersistentTrack? = when (json.optString("type")) {
        "local" -> json.optString("songId").trim().takeIf(String::isNotEmpty)?.let { id ->
            PersistentTrack.Local(id, json.cachedTitle(), json.cachedArtist(), json.cachedDuration())
        }
        "online" -> {
            val source = json.optString("sourceHost").trim()
            val id = json.optString("persistentId").trim()
            if (source.isEmpty() || id.isEmpty()) null else PersistentTrack.Online(
                source, id, json.cachedTitle(), json.cachedArtist(), json.cachedDuration()
            )
        }
        else -> null
    }

    private fun JSONObject.putCachedMetadata(track: PersistentTrack): JSONObject =
        put("cachedTitle", track.cachedTitle)
            .put("cachedArtist", track.cachedArtist)
            .put("cachedDurationMs", track.cachedDurationMs)

    private fun JSONObject.cachedTitle() = optString("cachedTitle").trim()
    private fun JSONObject.cachedArtist() = optString("cachedArtist").trim()
    private fun JSONObject.cachedDuration() = optLong("cachedDurationMs", -1L).takeIf { it >= 0L }
}

/** Likes 与 Playlist 共用的 track record 列表格式。 */
object PersistentTrackListJsonCodec {
    private const val FormatVersion = 1

    fun encode(tracks: Collection<PersistentTrack>): String {
        val values = JSONArray()
        tracks.distinctBy(PersistentTrack::identityKey)
            .forEach { values.put(PersistentTrackJsonCodec.encode(it)) }
        return JSONObject()
            .put("formatVersion", FormatVersion)
            .put("tracks", values)
            .toString()
    }

    fun decode(raw: String): List<PersistentTrack> = runCatching {
        val values = if (raw.trimStart().startsWith("[")) {
            // 兼容本功能开发期间写入的无版本数组格式。
            JSONArray(raw)
        } else {
            JSONObject(raw).optJSONArray("tracks") ?: JSONArray()
        }
        buildList {
            repeat(values.length()) { index ->
                values.optJSONObject(index)
                    ?.let(PersistentTrackJsonCodec::decode)
                    ?.let(::add)
            }
        }.distinctBy(PersistentTrack::identityKey)
    }.getOrDefault(emptyList())
}
