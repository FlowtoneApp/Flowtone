package ink.tenqui.flowtone.data.local

import android.content.Context
import android.net.Uri
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.core.model.playlistAppearanceColorKeyForStableId
import org.json.JSONArray
import org.json.JSONObject

class LibraryPlaylistCardStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "flowtone_library_playlists",
        Context.MODE_PRIVATE
    )

    fun loadCards(): List<LibraryPlaylistCard> {
        val rawValue = prefs.getString(PLAYLIST_CARDS_KEY, null) ?: return emptyList()

        return runCatching {
            val jsonArray = JSONArray(rawValue)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val title = item.optString(TITLE_KEY).trim()
                    if (title.isEmpty()) {
                        continue
                    }

                    val id = item.optString(ID_KEY).ifBlank {
                        "playlist_${index}_${title.hashCode()}"
                    }
                    add(
                        LibraryPlaylistCard(
                            id = id,
                            title = title,
                            subtitle = item.optString(SUBTITLE_KEY, DEFAULT_SUBTITLE)
                                .ifBlank { DEFAULT_SUBTITLE },
                            order = item.optInt(ORDER_KEY, index),
                            widthDp = item.optDouble(WIDTH_DP_KEY, DEFAULT_WIDTH_DP.toDouble())
                                .toFloat(),
                            heightDp = item.optDouble(
                                HEIGHT_DP_KEY,
                                DEFAULT_HEIGHT_DP.toDouble()
                            ).toFloat(),
                            appearanceColorKey = PlaylistAppearanceColorKey.fromStorageValue(
                                item.optString(APPEARANCE_COLOR_KEY)
                            ) ?: playlistAppearanceColorKeyForStableId(id),
                            customArtworkUri = item.optString(CUSTOM_ARTWORK_URI_KEY)
                                .trim()
                                .ifBlank { null }
                                ?.let(Uri::parse),
                            creatorName = item.optString(CREATOR_NAME_KEY)
                                .trim()
                                .ifBlank { null },
                            description = item.optString(DESCRIPTION_KEY)
                                .trim()
                                .ifBlank { null }
                        )
                    )
                }
            }
                .sortedBy { card -> card.order }
                .distinctByNormalizedTitle()
        }.getOrDefault(emptyList())
    }

    fun saveCards(cards: List<LibraryPlaylistCard>): List<LibraryPlaylistCard> {
        val normalizedCards = cards
            .filterNot { card -> card.isSystem }
            .sortedBy { card -> card.order }
            .mapIndexed { index, card ->
                card.copy(
                    title = card.title.trim(),
                    subtitle = card.subtitle.ifBlank { DEFAULT_SUBTITLE },
                    widthDp = card.widthDp.takeIf { value -> value > 0f } ?: DEFAULT_WIDTH_DP,
                    heightDp = card.heightDp.takeIf { value -> value > 0f } ?: DEFAULT_HEIGHT_DP,
                    order = index
                )
            }
            .filter { card -> card.title.isNotEmpty() }
            .distinctByNormalizedTitle()
            .mapIndexed { index, card -> card.copy(order = index) }

        val existingItemsById = loadExistingItemsById()
        val jsonArray = JSONArray()
        normalizedCards.forEach { card ->
            jsonArray.put(
                (existingItemsById[card.id] ?: JSONObject())
                    .put(ID_KEY, card.id)
                    .put(TITLE_KEY, card.title)
                    .put(SUBTITLE_KEY, card.subtitle)
                    .put(WIDTH_DP_KEY, card.widthDp)
                    .put(HEIGHT_DP_KEY, card.heightDp)
                    .put(
                        APPEARANCE_COLOR_KEY,
                        card.appearanceColorKey
                            ?.name
                            ?: playlistAppearanceColorKeyForStableId(card.id).name
                    )
                    .put(ORDER_KEY, card.order)
                    .put(CUSTOM_ARTWORK_URI_KEY, card.customArtworkUri?.toString())
                    .put(CREATOR_NAME_KEY, card.creatorName)
                    .put(DESCRIPTION_KEY, card.description)
            )
        }

        prefs.edit()
            .putString(PLAYLIST_CARDS_KEY, jsonArray.toString())
            .apply()

        return normalizedCards
    }

    private fun loadExistingItemsById(): Map<String, JSONObject> {
        val rawValue = prefs.getString(PLAYLIST_CARDS_KEY, null) ?: return emptyMap()
        return runCatching {
            val jsonArray = JSONArray(rawValue)
            buildMap {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val id = item.optString(ID_KEY).trim()
                    if (id.isNotEmpty()) {
                        put(id, item)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val PLAYLIST_CARDS_KEY = "playlist_cards"
        const val ID_KEY = "id"
        const val TITLE_KEY = "title"
        const val SUBTITLE_KEY = "subtitle"
        const val WIDTH_DP_KEY = "widthDp"
        const val HEIGHT_DP_KEY = "heightDp"
        const val APPEARANCE_COLOR_KEY = "appearanceColorKey"
        const val ORDER_KEY = "order"
        const val CUSTOM_ARTWORK_URI_KEY = "customArtworkUri"
        const val CREATOR_NAME_KEY = "creatorName"
        const val DESCRIPTION_KEY = "description"
        const val DEFAULT_SUBTITLE = "0 \u9996\u6b4c\u66f2"
        const val DEFAULT_WIDTH_DP = 320f
        const val DEFAULT_HEIGHT_DP = 236f
    }
}

private fun List<LibraryPlaylistCard>.distinctByNormalizedTitle(): List<LibraryPlaylistCard> {
    val seenTitles = mutableSetOf<String>()
    return filter { card ->
        seenTitles.add(card.title.trim().lowercase())
    }
}
