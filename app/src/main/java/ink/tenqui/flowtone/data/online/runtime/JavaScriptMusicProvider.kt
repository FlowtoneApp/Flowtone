package ink.tenqui.flowtone.data.online.runtime

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.MusicProvider
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.providerSearchCategoryFromWire
import ink.tenqui.flowtone.data.online.ProviderSearchLanding
import ink.tenqui.flowtone.data.online.SearchLandingAction
import ink.tenqui.flowtone.data.online.SearchLandingBlock
import ink.tenqui.flowtone.data.online.SearchLandingItem
import ink.tenqui.flowtone.core.model.normalizeMusicSourceHost
import org.json.JSONArray
import org.json.JSONObject

/** music_provider capability 的最小 JS bridge，不包含任何 Provider 专用协议。 */
class JavaScriptMusicProvider internal constructor(
    private val runtime: JavaScriptExtensionRuntime,
    override val musicSources: Set<String> = emptySet()
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

    override suspend fun getSearchLanding(): ProviderSearchLanding? {
        val raw = runCatching { runtime.invokeJson("getSearchLanding", JSONObject()) }.getOrNull()
            ?: return null
        return parseSearchLanding(raw)
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

    override suspend fun resolvePersistentSong(persistentId: String): ProviderSong? {
        val normalizedId = persistentId.trim()
        if (normalizedId.isEmpty()) return null
        val result = runtime.invokeObject(
            "resolvePersistentSong",
            JSONObject().put("persistentId", normalizedId)
        )
        return parseSong(result)
    }

    private fun parseSong(item: JSONObject): ProviderSong? {
        val opaqueId = item.optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val title = item.optString("title").trim().takeIf(String::isNotEmpty) ?: return null
        val artist = item.optString("artist").trim().takeIf(String::isNotEmpty) ?: return null
        val duration = item.optLong("durationMs", -1L).takeIf { it >= 0L }
        val artwork = item.optString("artworkUrl").trim().takeIf { it.startsWith("https://") }
            ?.let { ExtensionImage(runtime.extensionId, it) }
        val largeArtwork = item.optString("largeArtworkUrl").trim()
            .takeIf { it.startsWith("https://") }
            ?.let { ExtensionImage(runtime.extensionId, it) }
        val persistentId = item.optString("persistentId").trim().takeIf(String::isNotEmpty)
        val sourceHost = boundSourceHost(item)
        return ProviderSong(
            trackRef = ExtensionTrackRef(runtime.extensionId, opaqueId),
            title = title,
            artist = artist,
            durationMs = duration,
            artwork = artwork,
            largeArtwork = largeArtwork,
            persistentId = persistentId,
            sourceHost = sourceHost,
            searchCategory = providerSearchCategoryFromWire(item.optString("category"))
        )
    }

    private fun boundSourceHost(item: JSONObject): String? {
        val requested = normalizeMusicSourceHost(item.optString("sourceHost"))
        val normalizedSources = musicSources.mapTo(linkedSetOf(), ::normalizeMusicSourceHost)
        return when {
            requested in normalizedSources -> requested
            normalizedSources.size == 1 -> normalizedSources.single()
            else -> null
        }
    }

    private fun parseSearchLanding(raw: String): ProviderSearchLanding? {
        val blocks = runCatching { JSONObject(raw).optJSONArray("blocks") }.getOrNull() ?: return null
        return ProviderSearchLanding(
            blocks = buildList {
                repeat(blocks.length().coerceAtMost(MaxLandingBlocks)) { index ->
                    parseLandingBlock(blocks.optJSONObject(index))?.let(::add)
                }
            }
        )
    }

    private fun parseLandingBlock(json: JSONObject?): SearchLandingBlock? {
        json ?: return null
        val title = json.optString("title").trim().take(MaxTextLength).takeIf(String::isNotBlank)
        return when (json.optString("type").trim().lowercase()) {
            "chips" -> parseLandingItems(json).takeIf(List<SearchLandingItem>::isNotEmpty)
                ?.let { SearchLandingBlock.Chips(title, it) }
            "tile_grid", "tilegrid" -> parseLandingItems(json).takeIf(List<SearchLandingItem>::isNotEmpty)
                ?.let { SearchLandingBlock.TileGrid(title, it) }
            "media_row", "mediarow" -> parseLandingItems(json).takeIf(List<SearchLandingItem>::isNotEmpty)
                ?.let { SearchLandingBlock.MediaRow(title, it) }
            "text", "empty" -> json.optString("text").trim().take(MaxTextLength)
                .takeIf(String::isNotBlank)?.let { SearchLandingBlock.Text(title, it) }
            else -> null
        }
    }

    private fun parseLandingItems(block: JSONObject): List<SearchLandingItem> {
        val items = block.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(items.length().coerceAtMost(MaxItemsPerBlock)) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                val id = item.optString("id").trim().take(MaxTextLength).takeIf(String::isNotBlank)
                    ?: "item-$index"
                val title = item.optString("title").trim().take(MaxTextLength)
                    .takeIf(String::isNotBlank) ?: return@repeat
                val artwork = item.optString("artworkUrl").trim().takeIf { it.startsWith("https://") }
                    ?.let { ExtensionImage(runtime.extensionId, it) }
                add(
                    SearchLandingItem(
                        id = id,
                        title = title,
                        subtitle = item.optString("subtitle").trim().take(MaxTextLength)
                            .takeIf(String::isNotBlank),
                        artwork = artwork,
                        action = parseLandingAction(item.optJSONObject("action"))
                    )
                )
            }
        }
    }

    private fun parseLandingAction(json: JSONObject?): SearchLandingAction? = when (
        json?.optString("type")?.trim()?.lowercase()
    ) {
        "search" -> json.optString("query").trim().take(MaxTextLength)
            .takeIf(String::isNotBlank)?.let(SearchLandingAction::Search)
        "provider_action" -> json.optString("id").trim().take(MaxTextLength)
            .takeIf(String::isNotBlank)?.let(SearchLandingAction::ProviderAction)
        else -> null
    }

    private companion object {
        const val MaxLandingBlocks = 12
        const val MaxItemsPerBlock = 24
        const val MaxTextLength = 120
    }
}
