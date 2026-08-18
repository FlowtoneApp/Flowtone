package ink.tenqui.flowtone.data.local

import android.content.Context
import ink.tenqui.flowtone.core.model.Playlist
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.core.model.PlaylistCardStyle
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.playlistAppearanceColorKeyForStableId
import org.json.JSONArray
import org.json.JSONObject

class PlaylistStorage(context: Context) {
    private val prefs = context.getSharedPreferences(
        "flowtone_library_playlists",
        Context.MODE_PRIVATE
    )

    fun loadPlaylists(): List<Playlist> {
        val rawValue = prefs.getString(PLAYLISTS_KEY, null) ?: return emptyList()
        val loadedAt = System.currentTimeMillis()

        return runCatching {
            val jsonArray = JSONArray(rawValue)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val title = item.optString(TITLE_KEY).trim()
                    if (title.isEmpty()) {
                        continue
                    }

                    val createdAt = item.optLong(CREATED_AT_KEY, loadedAt)
                    val id = item.optString(ID_KEY).ifBlank {
                        "playlist_${index}_${title.hashCode()}"
                    }
                    add(
                        Playlist(
                            id = id,
                            title = title,
                            subtitle = item.optString(SUBTITLE_KEY, DEFAULT_SUBTITLE)
                                .ifBlank { DEFAULT_SUBTITLE },
                            cardStyle = item.optPlaylistCardStyle(),
                            appearanceColorKey = PlaylistAppearanceColorKey.fromStorageValue(
                                item.optString(APPEARANCE_COLOR_KEY)
                            ) ?: playlistAppearanceColorKeyForStableId(id),
                            order = item.optInt(ORDER_KEY, index),
                            createdAt = createdAt,
                            updatedAt = item.optLong(UPDATED_AT_KEY, createdAt),
                            customArtworkUri = item.optString(CUSTOM_ARTWORK_URI_KEY)
                                .trim()
                                .ifBlank { null }
                        )
                    )
                }
            }
                .sortedBy { playlist -> playlist.order }
                .distinctByNormalizedTitle()
                .mapIndexed { index, playlist -> playlist.copy(order = index) }
        }.getOrDefault(emptyList())
    }

    fun savePlaylists(playlists: List<Playlist>): Boolean {
        val jsonArray = JSONArray()
        playlists
            .sortedBy { playlist -> playlist.order }
            .mapIndexed { index, playlist -> playlist.copy(order = index) }
            .forEach { playlist ->
                jsonArray.put(
                    JSONObject()
                        .put(ID_KEY, playlist.id)
                        .put(TITLE_KEY, playlist.title)
                        .put(SUBTITLE_KEY, playlist.subtitle)
                        .put(CARD_STYLE_KEY, playlist.cardStyle.name)
                        .put(APPEARANCE_COLOR_KEY, playlist.appearanceColorKey.name)
                        .put(ORDER_KEY, playlist.order)
                        .put(CREATED_AT_KEY, playlist.createdAt)
                        .put(UPDATED_AT_KEY, playlist.updatedAt)
                        .put(CUSTOM_ARTWORK_URI_KEY, playlist.customArtworkUri)
                )
            }

        return prefs.edit()
            .putString(PLAYLISTS_KEY, jsonArray.toString())
            .commit()
    }

    fun loadPlaylistSongEntries(): List<PlaylistSongEntry> {
        val rawValue = prefs.getString(PLAYLIST_SONG_ENTRIES_KEY, null) ?: return emptyList()
        return decodePlaylistSongEntries(rawValue)
    }

    fun savePlaylistSongEntries(entries: List<PlaylistSongEntry>): Boolean {
        return prefs.edit()
            .putString(PLAYLIST_SONG_ENTRIES_KEY, encodePlaylistSongEntries(entries))
            .commit()
    }

    fun saveSnapshot(
        playlists: List<Playlist>,
        entries: List<PlaylistSongEntry>
    ): Boolean {
        return prefs.edit()
            .putString(PLAYLISTS_KEY, playlists.toPlaylistJsonArray().toString())
            .putString(
                PLAYLIST_SONG_ENTRIES_KEY,
                encodePlaylistSongEntries(entries)
            )
            .commit()
    }

    private companion object {
        const val PLAYLISTS_KEY = "playlist_cards"
        const val PLAYLIST_SONG_ENTRIES_KEY = "playlist_song_entries"
        const val ID_KEY = "id"
        const val TITLE_KEY = "title"
        const val SUBTITLE_KEY = "subtitle"
        const val CARD_STYLE_KEY = "cardStyle"
        const val APPEARANCE_COLOR_KEY = "appearanceColorKey"
        const val LEGACY_SIZE_KEY = "size"
        const val ORDER_KEY = "order"
        const val CREATED_AT_KEY = "createdAt"
        const val UPDATED_AT_KEY = "updatedAt"
        const val CUSTOM_ARTWORK_URI_KEY = "customArtworkUri"
        const val DEFAULT_SUBTITLE = "0 首歌曲"
        const val ENTRY_ID_KEY = "id"
        const val ENTRY_PLAYLIST_ID_KEY = "playlistId"
        const val ENTRY_TRACK_KEY = "track"
        const val ENTRY_ADDED_AT_KEY = "addedAt"
    }
}

private fun List<Playlist>.toPlaylistJsonArray(): JSONArray {
    val jsonArray = JSONArray()
    sortedBy { playlist -> playlist.order }
        .mapIndexed { index, playlist -> playlist.copy(order = index) }
        .forEach { playlist ->
            jsonArray.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("title", playlist.title)
                    .put("subtitle", playlist.subtitle)
                    .put("cardStyle", playlist.cardStyle.name)
                    .put("appearanceColorKey", playlist.appearanceColorKey.name)
                    .put("order", playlist.order)
                    .put("createdAt", playlist.createdAt)
                    .put("updatedAt", playlist.updatedAt)
                    .put("customArtworkUri", playlist.customArtworkUri)
            )
        }
    return jsonArray
}

private fun List<PlaylistSongEntry>.toPlaylistSongEntryJsonArray(): JSONArray {
    val jsonArray = JSONArray()
    forEach { entry ->
            jsonArray.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("playlistId", entry.playlistId)
                .put("track", PersistentTrackJsonCodec.encode(entry.track))
                .put("addedAt", entry.addedAt)
            )
        }
    return jsonArray
}

internal fun encodePlaylistSongEntries(entries: List<PlaylistSongEntry>): String =
    JSONObject()
        .put("formatVersion", 2)
        .put("entries", entries.toPlaylistSongEntryJsonArray())
        .toString()

internal fun decodePlaylistSongEntries(raw: String): List<PlaylistSongEntry> = runCatching {
    val jsonArray = if (raw.trimStart().startsWith("[")) {
        JSONArray(raw)
    } else {
        JSONObject(raw).optJSONArray("entries") ?: JSONArray()
    }
    buildList {
        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index) ?: continue
            val playlistId = item.optString("playlistId").trim()
            val track = item.optPersistentTrack()
            if (playlistId.isEmpty() || track == null) continue
            add(
                PlaylistSongEntry(
                    id = item.optString("id").ifBlank {
                        "entry_${playlistId}_${track.identityKey.hashCode()}"
                    },
                    playlistId = playlistId,
                    track = track,
                    addedAt = item.optLong("addedAt", 0L)
                )
            )
        }
    }.distinctBy { entry -> "${entry.playlistId}:${entry.track.identityKey}" }
}.getOrDefault(emptyList())

private fun JSONObject.optSongId(): String {
    val storedSongId = optString("songId").trim()
    if (storedSongId.isNotEmpty()) {
        return storedSongId
    }

    val legacySongId = optLong("songId", -1L)
    return if (legacySongId >= 0L) legacySongId.toString() else ""
}

private fun JSONObject.optPersistentTrack(): PersistentTrack? {
    optJSONObject("track")?.let(PersistentTrackJsonCodec::decode)?.let { return it }
    val item = optJSONObject("onlineSong")
    if (item == null) {
        return optSongId().takeIf(String::isNotEmpty)?.let(PersistentTrack::Local)
    }
    val sourceHost = item.optString("sourceHost").trim()
    val persistentId = item.optString("persistentId").trim()
    val title = item.optString("title").trim()
    val artist = item.optString("artist").trim()
    if (sourceHost.isEmpty() || persistentId.isEmpty() || title.isEmpty() || artist.isEmpty()) return null
    return PersistentTrack.Online(
        sourceHost = sourceHost,
        persistentId = persistentId,
        cachedTitle = title,
        cachedArtist = artist,
        cachedDurationMs = item.optLong("durationMs", -1L).takeIf { it >= 0L }
    )
}

private fun JSONObject.optPlaylistCardStyle(): PlaylistCardStyle {
    val storedStyle = optString("cardStyle").trim()
    PlaylistCardStyle.values()
        .firstOrNull { style -> style.name.equals(storedStyle, ignoreCase = true) }
        ?.let { style -> return style }

    val legacySize = optString("size").trim()
    PlaylistCardStyle.values()
        .firstOrNull { style -> style.name.equals(legacySize, ignoreCase = true) }
        ?.let { style -> return style }

    return when {
        legacySize.equals("Large", ignoreCase = true) -> PlaylistCardStyle.LARGE
        legacySize.equals("Wide", ignoreCase = true) -> PlaylistCardStyle.WIDE
        optDouble("widthDp", 0.0) >= 260.0 && optDouble("heightDp", 0.0) >= 180.0 ->
            PlaylistCardStyle.LARGE
        optDouble("widthDp", 0.0) >= 260.0 -> PlaylistCardStyle.WIDE
        else -> PlaylistCardStyle.SQUARE
    }
}

private fun List<Playlist>.distinctByNormalizedTitle(): List<Playlist> {
    val seenTitles = mutableSetOf<String>()
    return filter { playlist ->
        seenTitles.add(playlist.title.trim().lowercase())
    }
}
