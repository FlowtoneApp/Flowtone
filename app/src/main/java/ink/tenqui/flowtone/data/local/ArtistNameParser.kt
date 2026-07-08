package ink.tenqui.flowtone.data.local

import java.util.Locale

private val ArtistSeparatorRegex = Regex("[/\\uFF0F&\\uFF06]")
private val UnknownArtistValues = setOf(
    "\u672a\u77e5\u827a\u672f\u5bb6",
    "unknown artist",
    "<unknown>"
)

fun parseLocalArtistCandidates(rawArtist: String): List<String> {
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

fun isSelectableLocalArtist(rawArtist: String): Boolean {
    val normalizedArtist = rawArtist.trim()
    if (normalizedArtist.isBlank()) {
        return false
    }

    return normalizedArtist.lowercase(Locale.ROOT) !in UnknownArtistValues
}

fun localArtistStableId(artistName: String): String {
    return artistName.trim().lowercase(Locale.ROOT)
}
