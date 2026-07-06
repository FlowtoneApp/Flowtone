package ink.tenqui.flowtone.data.local

import android.content.Context
import ink.tenqui.flowtone.core.model.Song

class LikedSongsStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "flowtone_liked_songs",
        Context.MODE_PRIVATE
    )

    fun loadLikedSongKeys(): List<String> {
        return prefs.getStringSet(LIKED_SONG_KEYS, emptySet())
            .orEmpty()
            .filter { key -> key.isNotBlank() }
            .distinct()
    }

    fun saveLikedSongKeys(keys: Collection<String>) {
        prefs.edit()
            .putStringSet(
                LIKED_SONG_KEYS,
                keys.filter { key -> key.isNotBlank() }.toSet()
            )
            .apply()
    }

    private companion object {
        const val LIKED_SONG_KEYS = "liked_song_keys"
    }
}

fun likedSongStorageKeys(song: Song): List<String> {
    return listOf(
        song.id.toString(),
        song.uri.toString()
    )
        .filter { key -> key.isNotBlank() }
        .distinct()
}

fun isSongLiked(song: Song, likedSongKeys: Collection<String>): Boolean {
    val likedKeys = likedSongKeys.toSet()
    return likedSongStorageKeys(song).any { key -> key in likedKeys }
}
