package ink.tenqui.flowtone.data.online.runtime

import android.util.Log
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResource
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.MusicProvider
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ArtistSongOrderInfo
import ink.tenqui.flowtone.data.online.ProviderAlbumRef
import ink.tenqui.flowtone.data.online.ProviderArtist
import ink.tenqui.flowtone.data.online.ProviderArtistRef
import ink.tenqui.flowtone.data.online.ProviderEntityCapability
import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import ink.tenqui.flowtone.data.online.ProviderPlaylistSearchItem
import ink.tenqui.flowtone.data.online.ProviderSearchItem
import ink.tenqui.flowtone.data.online.ProviderSearchPage
import ink.tenqui.flowtone.data.online.ProviderSearchRequest
import ink.tenqui.flowtone.data.online.ProviderSearchMetadata
import ink.tenqui.flowtone.data.online.ProviderSearchCategory
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.sanitizedFor
import ink.tenqui.flowtone.data.online.providerSearchCategoryFromWire
import ink.tenqui.flowtone.data.online.toWireValue
import ink.tenqui.flowtone.data.online.ProviderSearchLanding
import ink.tenqui.flowtone.data.online.SearchLandingAction
import ink.tenqui.flowtone.data.online.SearchLandingBlock
import ink.tenqui.flowtone.data.online.SearchLandingItem
import ink.tenqui.flowtone.core.model.normalizeMusicSourceHost
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** music_provider capability 的最小 JS bridge，不包含任何 Provider 专用协议。 */
class JavaScriptMusicProvider internal constructor(
    private val runtime: JavaScriptExtensionRuntime,
    override val musicSources: Set<String> = emptySet(),
    override val entityCapabilities: Set<ProviderEntityCapability> = emptySet()
) : MusicProvider {
    private val songEntities = ConcurrentHashMap<String, ProviderSong>()
    private val albumEntities = ConcurrentHashMap<String, ProviderAlbum>()

    override suspend fun searchPage(request: ProviderSearchRequest): ProviderSearchPage {
        require(request.keyword.isNotBlank()) { "keyword must not be blank" }
        val raw = runtime.invokeJson(
            "searchPage",
            JSONObject()
                .put("keyword", request.keyword.trim())
                .put("category", request.category.toWireValue())
                .put("cursor", request.cursor ?: JSONObject.NULL)
                .put("limit", request.limit)
        )
        val response = JSONObject(raw)
        val values = response.optJSONArray("results")
            ?: throw IllegalArgumentException("searchPage results must be an array")
        val results = buildList {
            repeat(values.length()) { index ->
                val item = values.optJSONObject(index) ?: return@repeat
                val result = parseSearchItem(item) ?: return@repeat
                if (result.searchCategory == request.category) {
                    add(result)
                } else {
                    Log.w(
                        "FlowtoneExtension",
                        "search.page.category_mismatch extension=${runtime.extensionId} expected=${request.category} actual=${result.searchCategory}"
                    )
                }
            }
        }
        val nextCursor = response.opt("nextCursor") as? String
        Log.d(
            "FlowtoneExtension",
            "extension.music.search.page.bridge extension=${runtime.extensionId} " +
                "category=${request.category} results=${results.size} " +
                "hasNextCursor=${nextCursor != null} nextCursorLength=${nextCursor?.length ?: 0}"
        )
        return ProviderSearchPage(results = results, nextCursor = nextCursor)
    }

    override suspend fun getSearchLanding(): ProviderSearchLanding? {
        val raw = runCatching { runtime.invokeJson("getSearchLanding", JSONObject()) }.getOrNull()
            ?: return null
        return parseSearchLanding(raw)
    }

    override suspend fun getSongs(): List<ProviderSong>? {
        if (ProviderEntityCapability.Song !in entityCapabilities) return null
        val values = JSONArray(runtime.invokeJson("getSongs"))
        return buildList {
            repeat(values.length()) { index ->
                parseSong(values.optJSONObject(index), ProviderSearchCategory.Single)?.let(::add)
            }
        }.distinctBy(ProviderSong::identity)
    }

    override suspend fun getAlbums(): List<ProviderAlbum>? {
        if (ProviderEntityCapability.Album !in entityCapabilities) return null
        val values = JSONArray(runtime.invokeJson("getAlbums"))
        return buildList {
            repeat(values.length()) { index ->
                parseAlbum(values.optJSONObject(index))?.let(::add)
            }
        }.distinctBy(ProviderAlbum::identity)
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
        return parseSong(result, ProviderSearchCategory.Single)
    }

    private fun parseSearchItem(item: JSONObject): ProviderSearchItem? {
        return when (val category = providerSearchCategoryFromWire(item.optString("category"))) {
            ProviderSearchCategory.Single -> parseSong(item, category)
            ProviderSearchCategory.Album -> parseAlbum(item)
            ProviderSearchCategory.User -> parseArtist(item)
            ProviderSearchCategory.Playlist -> parsePlaylist(item)
        }
    }

    private fun parseSong(item: JSONObject?, category: ProviderSearchCategory): ProviderSong? {
        item ?: return null
        val opaqueId = item.optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val title = item.optString("title").trim().takeIf(String::isNotEmpty) ?: return null
        val artists = parseArtists(item)
        val artist = displayArtist(item, artists)
        val duration = item.optLong("durationMs", -1L).takeIf { it >= 0L }
        val artwork = item.optString("artworkUrl").trim().takeIf { it.startsWith("https://") }
            ?.let { ExtensionImage(runtime.extensionId, it) }
        val largeArtwork = item.optString("largeArtworkUrl").trim()
            .takeIf { it.startsWith("https://") }
            ?.let { ExtensionImage(runtime.extensionId, it) }
        val persistentId = item.optString("persistentId").trim().takeIf(String::isNotEmpty)
        val sourceHost = boundSourceHost(item)
        val incoming = ProviderSong(
            trackRef = ExtensionTrackRef(runtime.extensionId, opaqueId),
            title = title,
            artist = artist,
            durationMs = duration,
            artwork = artwork,
            largeArtwork = largeArtwork,
            persistentId = persistentId,
            sourceHost = sourceHost,
            searchCategory = category,
            metadata = parseMetadata(item),
            artists = artists,
            album = parseAlbumRef(item)
        )
        return songEntities.compute(opaqueId) { _, existing -> mergeProviderSong(existing, incoming) }
    }

    private fun parseAlbum(item: JSONObject?): ProviderAlbum? {
        item ?: return null
        val remoteId = item.optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val title = item.optString("title").trim().takeIf(String::isNotEmpty) ?: return null
        val artists = parseArtists(item)
        val incoming = ProviderAlbum(
            identity = ProviderEntityIdentity(runtime.extensionId, remoteId),
            title = title,
            artist = displayArtist(item, artists),
            artists = artists,
            artwork = parseArtwork(item, "artworkUrl"),
            songCount = item.optNonNegativeInt("songCount")
                ?: parseMetadata(item)?.firstOrNull { it.type == "track_count" }?.value?.toInt(),
            releaseMetadata = item.optString("releaseMetadata").trim().takeIf(String::isNotEmpty),
            metadata = parseMetadata(item)
        )
        return albumEntities.compute(remoteId) { _, existing -> mergeProviderAlbum(existing, incoming) }
    }

    private fun parseArtist(item: JSONObject): ProviderArtist? {
        val remoteId = item.optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val title = item.optString("title").trim().takeIf(String::isNotEmpty) ?: return null
        return ProviderArtist(
            identity = ProviderEntityIdentity(runtime.extensionId, remoteId),
            title = title,
            artist = item.optString("artist").trim(),
            artwork = parseArtwork(item, "artworkUrl"),
            largeArtwork = parseArtwork(item, "largeArtworkUrl"),
            metadata = parseMetadata(item),
            profileMetadata = providerArtistMetadataFromJson(item, title, runtime.extensionId),
            songOrder = providerArtistSongOrderFromJson(item)
        )
    }

    private fun parsePlaylist(item: JSONObject): ProviderPlaylistSearchItem? {
        val remoteId = item.optString("id").trim().takeIf(String::isNotEmpty) ?: return null
        val title = item.optString("title").trim().takeIf(String::isNotEmpty) ?: return null
        return ProviderPlaylistSearchItem(
            identity = ProviderEntityIdentity(runtime.extensionId, remoteId),
            title = title,
            artist = item.optString("artist").trim(),
            artwork = parseArtwork(item, "artworkUrl"),
            metadata = parseMetadata(item)
        )
    }

    private fun parseArtists(item: JSONObject): List<ProviderArtistRef> {
        val values = item.optJSONArray("artists")
        if (values != null) {
            return buildList {
                repeat(values.length()) { index ->
                    when (val raw = values.opt(index)) {
                        is String -> raw.trim().takeIf(String::isNotEmpty)?.let { add(ProviderArtistRef(name = it)) }
                        is JSONObject -> {
                            val name = raw.optString("name").trim().takeIf(String::isNotEmpty) ?: return@repeat
                            add(ProviderArtistRef(raw.optString("id").trim().takeIf(String::isNotEmpty), name))
                        }
                    }
                }
            }
        }
        val artistObject = item.optJSONObject("artist")
        if (artistObject != null) {
            val name = artistObject.optString("name").trim().takeIf(String::isNotEmpty) ?: return emptyList()
            return listOf(ProviderArtistRef(artistObject.optString("id").trim().takeIf(String::isNotEmpty), name))
        }
        return emptyList()
    }

    private fun displayArtist(item: JSONObject, artists: List<ProviderArtistRef>): String =
        (item.opt("artist") as? String)?.trim()?.takeIf(String::isNotEmpty)
            ?: artists.joinToString(" / ", transform = ProviderArtistRef::name)

    private fun parseAlbumRef(item: JSONObject): ProviderAlbumRef? {
        val album = item.optJSONObject("album")
        if (album != null) {
            return ProviderAlbumRef(
                remoteId = album.optString("id").trim().takeIf(String::isNotEmpty),
                title = album.optString("title").trim().takeIf(String::isNotEmpty)
            ).takeIf { it.remoteId != null || it.title != null }
        }
        return ProviderAlbumRef(
            remoteId = item.optString("albumId").trim().takeIf(String::isNotEmpty),
            title = item.optString("albumTitle").trim().takeIf(String::isNotEmpty)
        ).takeIf { it.remoteId != null || it.title != null }
    }

    private fun parseArtwork(item: JSONObject, field: String): ExtensionImage? =
        item.optString(field).trim().takeIf { it.startsWith("https://") }
            ?.let { ExtensionImage(runtime.extensionId, it) }

    private fun mergeProviderSong(existing: ProviderSong?, incoming: ProviderSong): ProviderSong {
        existing ?: return incoming
        return incoming.copy(
            artist = incoming.artist.ifBlank { existing.artist },
            durationMs = incoming.durationMs ?: existing.durationMs,
            artwork = incoming.artwork ?: existing.artwork,
            largeArtwork = incoming.largeArtwork ?: existing.largeArtwork,
            persistentId = incoming.persistentId ?: existing.persistentId,
            sourceHost = incoming.sourceHost ?: existing.sourceHost,
            metadata = incoming.metadata ?: existing.metadata,
            artists = incoming.artists.ifEmpty { existing.artists },
            album = incoming.album ?: existing.album
        )
    }

    private fun mergeProviderAlbum(existing: ProviderAlbum?, incoming: ProviderAlbum): ProviderAlbum {
        existing ?: return incoming
        return incoming.copy(
            artist = incoming.artist.ifBlank { existing.artist },
            artists = incoming.artists.ifEmpty { existing.artists },
            artwork = incoming.artwork ?: existing.artwork,
            songCount = incoming.songCount ?: existing.songCount,
            releaseMetadata = incoming.releaseMetadata ?: existing.releaseMetadata,
            metadata = incoming.metadata ?: existing.metadata
        )
    }

    private fun parseMetadata(item: JSONObject): List<ProviderSearchMetadata>? {
        if (!item.has("metadata")) return null
        val values = item.optJSONArray("metadata") ?: return null
        return buildList {
            repeat(values.length().coerceAtMost(MaxMetadataItems)) { index ->
                val metadata = parseMetadataItem(values.optJSONObject(index))
                if (metadata != null) add(metadata)
            }
        }
    }

    private fun parseMetadataItem(item: JSONObject?): ProviderSearchMetadata? {
        item ?: return null
        val type = (item.opt("type") as? String)?.trim()?.lowercase()
            ?.take(MaxMetadataTypeLength)
            ?.takeIf { it.isNotBlank() } ?: return null
        val value = item.opt("value")?.let { raw ->
            if (raw === JSONObject.NULL || raw !is Number) return@let null
            val number = raw.toDouble()
            if (!number.isFinite() || number < Long.MIN_VALUE || number > Long.MAX_VALUE ||
                number != number.toLong().toDouble()
            ) null else number.toLong()
        }
        val text = (item.opt("text") as? String)?.trim()?.take(MaxTextLength)
            ?.takeIf { it.isNotBlank() }
        when (type) {
            "track_count", "play_count" -> if (value == null || value < 0L) return null
            "creator", "text" -> if (text == null) return null
            else -> if (text == null) return null
        }
        return ProviderSearchMetadata(type = type, value = value, text = text)
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
        const val MaxMetadataItems = 8
        const val MaxMetadataTypeLength = 40
        const val MaxTextLength = 120
    }
}

internal fun providerArtistMetadataFromJson(
    item: JSONObject,
    displayName: String,
    extensionId: String = ""
): ArtistMetadata? {
    val aliases = item.optJSONArray("aliases")?.let { values ->
        buildList {
            repeat(values.length().coerceAtMost(MaxProviderArtistAliases)) { index ->
                add(values.optString(index).take(MaxProviderArtistTextLength))
            }
        }
    }.orEmpty()
    val biography = item.optString("biography")
        .trim()
        .take(MaxProviderArtistBiographyLength)
        .takeIf(String::isNotEmpty)
    return ArtistMetadata(
        aliases = aliases,
        biography = biography,
        songCount = item.optNonNegativeInt("songCount"),
        albumCount = item.optNonNegativeInt("albumCount"),
        banner = item.optString("bannerUrl").trim().takeIf { it.startsWith("https://") }
            ?.takeIf { extensionId.isNotBlank() }?.let { ExtensionImage(extensionId, it) }
    ).sanitizedFor(displayName)
}

internal fun providerArtistSongOrderFromJson(item: JSONObject): ArtistSongOrderInfo? {
    val value = item.optJSONObject("artistSongOrder") ?: return null
    val title = value.optString("title")
        .trim()
        .take(MaxProviderArtistOrderTextLength)
        .takeIf(String::isNotEmpty)
        ?: return null
    return ArtistSongOrderInfo(
        id = value.optString("id")
            .trim()
            .take(MaxProviderArtistOrderIdLength)
            .takeIf(String::isNotEmpty),
        title = title
    )
}

private fun JSONObject.optNonNegativeInt(name: String): Int? {
    val value = opt(name) as? Number ?: return null
    val number = value.toDouble()
    return number
        .takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it == it.toInt().toDouble() }
        ?.toInt()
}

private const val MaxProviderArtistAliases = 8
private const val MaxProviderArtistTextLength = 120
private const val MaxProviderArtistBiographyLength = 4_000
private const val MaxProviderArtistOrderIdLength = 80
private const val MaxProviderArtistOrderTextLength = 120
