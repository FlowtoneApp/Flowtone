package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType

private val ArtistSeparatorRegex = Regex("[/／&＆]")
private val UnknownArtistValues = setOf(
    "未知艺术家",
    "unknown artist",
    "<unknown>"
)

internal fun parseArtistCandidates(rawArtist: String): List<String> {
    val trimmedRawArtist = rawArtist.trim()
    if (trimmedRawArtist.isBlank()) {
        return emptyList()
    }

    val candidates = ArtistSeparatorRegex
        .split(trimmedRawArtist)
        .map { candidate -> candidate.trim() }
        .filter { candidate -> candidate.isNotEmpty() }
        .distinct()

    return candidates.ifEmpty { listOf(trimmedRawArtist) }
}

internal fun isSelectableArtist(rawArtist: String): Boolean {
    val normalizedArtist = rawArtist.trim()
    if (normalizedArtist.isBlank()) {
        return false
    }

    return normalizedArtist.lowercase() !in UnknownArtistValues
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
