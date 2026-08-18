package ink.tenqui.flowtone.data.local

import android.content.Context
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.toPersistentTrack

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

    fun loadLikedTracks(localSongs: List<Song>): List<PersistentTrack> {
        val stored = prefs.getString(LIKED_TRACKS, null)
            ?.let(PersistentTrackListJsonCodec::decode)
            .orEmpty()
        return mergeStoredAndLegacyLikedTracks(
            stored = stored,
            legacyKeys = loadLikedSongKeys(),
            localSongs = localSongs
        )
    }

    fun saveLikedTracks(tracks: Collection<PersistentTrack>) {
        prefs.edit()
            .putString(LIKED_TRACKS, PersistentTrackListJsonCodec.encode(tracks))
            .apply()
    }

    private companion object {
        const val LIKED_SONG_KEYS = "liked_song_keys"
        const val LIKED_TRACKS = "liked_tracks_v2"
    }
}

internal fun mergeStoredAndLegacyLikedTracks(
    stored: List<PersistentTrack>,
    legacyKeys: Collection<String>,
    localSongs: List<Song>
): List<PersistentTrack> {
    val existingKeys = stored.mapTo(mutableSetOf(), PersistentTrack::identityKey)
    val legacyKeySet = legacyKeys.toSet()
    val migratedLocal = localSongs.filter { song ->
        likedSongStorageKeys(song).any(legacyKeySet::contains) &&
            song.toPersistentTrack().identityKey !in existingKeys
    }.map(Song::toPersistentTrack)
    return (stored + migratedLocal).distinctBy(PersistentTrack::identityKey)
}

fun isTrackLiked(track: PersistentTrack?, likedTracks: Collection<PersistentTrack>): Boolean =
    track != null && likedTracks.any { it.identityKey == track.identityKey }

fun likedSongStorageKeys(song: Song): List<String> {
    return listOf(
        song.toPersistentTrack().identityKey,
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
