package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import kotlin.random.Random

internal data class FastInteractionTestSelection(
    val playlistSongs: List<Song>
) {
    val comparisonSong: Song?
        get() = playlistSongs.firstOrNull()
}

internal fun selectFastInteractionTestSongs(
    songs: List<Song>,
    seed: Long
): FastInteractionTestSelection {
    val selected = songs.asSequence()
        .filter { song -> song.sourceType == SourceType.Local }
        // PersistentTrack.Local and the playback resolver use this source-scoped ID.
        .distinctBy { song -> song.sourceType to song.id }
        .toList()
        .shuffled(Random(seed))
        .take(FastInteractionTestPlaylistSize)

    return FastInteractionTestSelection(playlistSongs = selected)
}

internal const val FastInteractionTestPlaylistSize = 10
