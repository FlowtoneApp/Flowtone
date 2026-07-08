package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.data.local.isSelectableLocalArtist
import ink.tenqui.flowtone.data.local.parseLocalArtistCandidates

internal fun parseArtistCandidates(rawArtist: String): List<String> {
    return parseLocalArtistCandidates(rawArtist)
}

internal fun isSelectableArtist(rawArtist: String): Boolean {
    return isSelectableLocalArtist(rawArtist)
}

internal fun localSongsForArtist(
    allSongs: List<Song>,
    artistName: String
): List<Song> {
    val displayArtist = artistName.trim()
    if (displayArtist.isBlank()) {
        return emptyList()
    }

    return allSongs.filter { song ->
        song.sourceType == SourceType.Local &&
            parseArtistCandidates(song.artist).any { candidate ->
                candidate.equals(displayArtist, ignoreCase = true)
            }
    }
}
