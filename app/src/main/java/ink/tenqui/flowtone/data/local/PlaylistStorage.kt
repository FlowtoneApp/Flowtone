package ink.tenqui.flowtone.data.local

import android.content.Context
import ink.tenqui.flowtone.core.model.Playlist
import ink.tenqui.flowtone.core.model.PlaylistCardStyle
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
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
                    add(
                        Playlist(
                            id = item.optString(ID_KEY).ifBlank {
                                "playlist_${index}_${title.hashCode()}"
                            },
                            title = title,
                            subtitle = item.optString(SUBTITLE_KEY, DEFAULT_SUBTITLE)
                                .ifBlank { DEFAULT_SUBTITLE },
                            cardStyle = item.optPlaylistCardStyle(),
                            order = item.optInt(ORDER_KEY, index),
                            createdAt = createdAt,
                            updatedAt = item.optLong(UPDATED_AT_KEY, createdAt)
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
                        .put(ORDER_KEY, playlist.order)
                        .put(CREATED_AT_KEY, playlist.createdAt)
                        .put(UPDATED_AT_KEY, playlist.updatedAt)
                )
            }

        return prefs.edit()
            .putString(PLAYLISTS_KEY, jsonArray.toString())
            .commit()
    }

    fun loadPlaylistSongEntries(): List<PlaylistSongEntry> {
        val rawValue = prefs.getString(PLAYLIST_SONG_ENTRIES_KEY, null) ?: return emptyList()

        return runCatching {
            val jsonArray = JSONArray(rawValue)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val playlistId = item.optString(ENTRY_PLAYLIST_ID_KEY).trim()
                    val songId = item.optLong(ENTRY_SONG_ID_KEY, -1L)
                    if (playlistId.isEmpty() || songId < 0L) {
                        continue
                    }

                    add(
                        PlaylistSongEntry(
                            playlistId = playlistId,
                            songId = songId,
                            addedAt = item.optLong(ENTRY_ADDED_AT_KEY, 0L),
                            order = item.optInt(ENTRY_ORDER_KEY, index),
                            titleSnapshot = item.optString(
                                ENTRY_TITLE_SNAPSHOT_KEY,
                                DEFAULT_SONG_TITLE
                            ).ifBlank { DEFAULT_SONG_TITLE },
                            artistSnapshot = item.optString(
                                ENTRY_ARTIST_SNAPSHOT_KEY,
                                DEFAULT_SONG_ARTIST
                            ).ifBlank { DEFAULT_SONG_ARTIST },
                            artworkUriSnapshot = item.optString(
                                ENTRY_ARTWORK_URI_SNAPSHOT_KEY,
                                ""
                            ).ifBlank { null }
                        )
                    )
                }
            }
                .sortedWith(compareBy<PlaylistSongEntry> { entry -> entry.playlistId }
                    .thenBy { entry -> entry.addedAt }
                    .thenBy { entry -> entry.order })
                .distinctBy { entry -> "${entry.playlistId}:${entry.songId}" }
        }.getOrDefault(emptyList())
    }

    fun savePlaylistSongEntries(entries: List<PlaylistSongEntry>): Boolean {
        val jsonArray = JSONArray()
        entries
            .sortedWith(compareBy<PlaylistSongEntry> { entry -> entry.playlistId }
                .thenBy { entry -> entry.addedAt }
                .thenBy { entry -> entry.order })
            .forEach { entry ->
                jsonArray.put(
                    JSONObject()
                        .put(ENTRY_PLAYLIST_ID_KEY, entry.playlistId)
                        .put(ENTRY_SONG_ID_KEY, entry.songId)
                        .put(ENTRY_ADDED_AT_KEY, entry.addedAt)
                        .put(ENTRY_ORDER_KEY, entry.order)
                        .put(ENTRY_TITLE_SNAPSHOT_KEY, entry.titleSnapshot)
                        .put(ENTRY_ARTIST_SNAPSHOT_KEY, entry.artistSnapshot)
                        .put(ENTRY_ARTWORK_URI_SNAPSHOT_KEY, entry.artworkUriSnapshot.orEmpty())
                )
            }

        return prefs.edit()
            .putString(PLAYLIST_SONG_ENTRIES_KEY, jsonArray.toString())
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
                entries.toPlaylistSongEntryJsonArray().toString()
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
        const val LEGACY_SIZE_KEY = "size"
        const val ORDER_KEY = "order"
        const val CREATED_AT_KEY = "createdAt"
        const val UPDATED_AT_KEY = "updatedAt"
        const val DEFAULT_SUBTITLE = "0 首歌曲"
        const val ENTRY_PLAYLIST_ID_KEY = "playlistId"
        const val ENTRY_SONG_ID_KEY = "songId"
        const val ENTRY_ADDED_AT_KEY = "addedAt"
        const val ENTRY_ORDER_KEY = "order"
        const val ENTRY_TITLE_SNAPSHOT_KEY = "titleSnapshot"
        const val ENTRY_ARTIST_SNAPSHOT_KEY = "artistSnapshot"
        const val ENTRY_ARTWORK_URI_SNAPSHOT_KEY = "artworkUriSnapshot"
        const val DEFAULT_SONG_TITLE = "未知歌曲"
        const val DEFAULT_SONG_ARTIST = "未知艺术家"
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
                    .put("order", playlist.order)
                    .put("createdAt", playlist.createdAt)
                    .put("updatedAt", playlist.updatedAt)
            )
        }
    return jsonArray
}

private fun List<PlaylistSongEntry>.toPlaylistSongEntryJsonArray(): JSONArray {
    val jsonArray = JSONArray()
    sortedWith(compareBy<PlaylistSongEntry> { entry -> entry.playlistId }
        .thenBy { entry -> entry.addedAt }
        .thenBy { entry -> entry.order })
        .forEach { entry ->
            jsonArray.put(
                JSONObject()
                    .put("playlistId", entry.playlistId)
                    .put("songId", entry.songId)
                    .put("addedAt", entry.addedAt)
                    .put("order", entry.order)
                    .put("titleSnapshot", entry.titleSnapshot)
                    .put("artistSnapshot", entry.artistSnapshot)
                    .put("artworkUriSnapshot", entry.artworkUriSnapshot.orEmpty())
            )
        }
    return jsonArray
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
