package ink.tenqui.flowtone.data.local

import android.content.Context
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide liked-track owner shared by the UI and MediaSession service. */
class LikedTracksRepository private constructor(context: Context) {
    private val store = LikedSongsStore(context.applicationContext)
    private val _tracks = MutableStateFlow(store.loadLikedTracks(emptyList()))

    val tracks: StateFlow<List<PersistentTrack>> = _tracks.asStateFlow()

    @Synchronized
    fun refreshLocalSongs(localSongs: List<Song>) {
        val restored = store.loadLikedTracks(localSongs)
        val merged = (_tracks.value + restored).distinctBy(PersistentTrack::identityKey)
        if (merged != _tracks.value) {
            _tracks.value = merged
            store.saveLikedTracks(merged)
        }
    }

    @Synchronized
    fun setLiked(track: PersistentTrack, liked: Boolean) {
        val next = if (liked) {
            (_tracks.value + track).distinctBy(PersistentTrack::identityKey)
        } else {
            _tracks.value.filterNot { it.identityKey == track.identityKey }
        }
        if (next == _tracks.value) return
        _tracks.value = next
        store.saveLikedTracks(next)
    }

    @Synchronized
    fun toggle(track: PersistentTrack): Boolean {
        val nextLiked = _tracks.value.none { it.identityKey == track.identityKey }
        setLiked(track, nextLiked)
        return nextLiked
    }

    fun isLiked(track: PersistentTrack?): Boolean =
        track != null && tracks.value.any { it.identityKey == track.identityKey }

    companion object {
        @Volatile private var instance: LikedTracksRepository? = null

        fun get(context: Context): LikedTracksRepository = instance ?: synchronized(this) {
            instance ?: LikedTracksRepository(context).also { instance = it }
        }
    }
}
