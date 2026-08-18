package ink.tenqui.flowtone.data.online.runtime

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.MusicProvider
import ink.tenqui.flowtone.data.online.ProviderSong
import org.json.JSONArray
import org.json.JSONObject

/** music_provider capability 的最小 JS bridge，不包含任何 Provider 专用协议。 */
class JavaScriptMusicProvider internal constructor(
    private val runtime: JavaScriptExtensionRuntime
) : MusicProvider {
    override suspend fun searchSongs(keyword: String): List<ProviderSong> {
        if (keyword.isBlank()) return emptyList()
        val raw = runtime.invokeJson("searchSongs", JSONObject().put("keyword", keyword.trim()))
        val values = if (raw.trimStart().startsWith("[")) {
            JSONArray(raw)
        } else {
            val result = JSONObject(raw)
            when {
                result.has("songs") -> result.optJSONArray("songs")
                result.has("results") -> result.optJSONArray("results")
                else -> JSONArray().put(result)
            }
        } ?: return emptyList()
        return buildList {
            repeat(values.length()) { index ->
                val item = values.optJSONObject(index) ?: return@repeat
                parseSong(item)?.let(::add)
            }
        }
    }

    override suspend fun getPlaybackResource(song: ProviderSong): ExtensionPlaybackResource? {
        if (song.trackRef.extensionId != runtime.extensionId) return null
        val result = runtime.invokeObject("getPlaybackResource", JSONObject().put("id", song.trackRef.opaqueId))
        val type = when (result.optString("type").lowercase()) {
            "hls" -> ExtensionPlaybackResourceType.Hls
            "progressive" -> ExtensionPlaybackResourceType.Progressive
            else -> return null
        }
        val url = result.optString("url").trim().takeIf { it.startsWith("https://") } ?: return null
        val headers = result.optJSONObject("headers")?.let { json ->
            json.keys().asSequence().mapNotNull { key -> (json.opt(key) as? String)?.let { key to it } }.toMap()
        }.orEmpty()
        return ExtensionPlaybackResource(
            extensionId = runtime.extensionId,
            url = url,
            headers = headers,
            mimeType = result.optString("mimeType").trim().takeIf(String::isNotEmpty),
            type = type
        )
    }

    private fun parseSong(item: JSONObject): ProviderSong? {
        val opaqueId = item.optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val title = item.optString("title").trim().takeIf(String::isNotEmpty) ?: return null
        val artist = item.optString("artist").trim().takeIf(String::isNotEmpty) ?: return null
        val duration = item.optLong("durationMs", -1L).takeIf { it >= 0L }
        val artwork = item.optString("artworkUrl").trim().takeIf { it.startsWith("https://") }
            ?.let { ExtensionImage(runtime.extensionId, it) }
        return ProviderSong(ExtensionTrackRef(runtime.extensionId, opaqueId), title, artist, duration, artwork)
    }
}
