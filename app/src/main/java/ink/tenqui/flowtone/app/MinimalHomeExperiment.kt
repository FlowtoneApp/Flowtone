package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType

/** A bounded, repeatable local sample keeps the vertical-scroll test finite and inexpensive. */
internal fun selectMinimalHomeSongs(songs: List<Song>): List<Song> = songs
    .asSequence()
    .filter { song -> song.sourceType == SourceType.Local }
    .sortedWith(
        compareBy<Song> { it.id }
            .thenBy { it.uri.toString() }
            .thenBy { it.title }
            .thenBy { it.artist }
    )
    .distinctBy { song -> song.sourceType to song.id }
    .take(MinimalHomeSongLimit)
    .toList()

internal const val MinimalHomeSongLimit = 32
