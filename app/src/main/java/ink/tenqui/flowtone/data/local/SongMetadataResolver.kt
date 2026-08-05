package ink.tenqui.flowtone.data.local

import ink.tenqui.flowtone.core.text.normalizeMetadataText

internal data class ResolvedSongMetadata(
    val title: String,
    val artist: String,
    val durationMs: Long
)

internal fun resolveSongMetadata(
    fileMetadata: SongFileMetadata?,
    mediaStoreTitle: String?,
    mediaStoreArtist: String?,
    mediaStoreDurationMs: Long
): ResolvedSongMetadata {
    return ResolvedSongMetadata(
        title = preferredMetadataText(
            fileValue = fileMetadata?.title,
            mediaStoreValue = mediaStoreTitle,
            defaultValue = "未知歌曲"
        ),
        artist = preferredMetadataText(
            fileValue = fileMetadata?.artist,
            mediaStoreValue = mediaStoreArtist,
            defaultValue = "未知艺术家"
        ),
        durationMs = mediaStoreDurationMs.coerceAtLeast(0L)
    )
}

private fun preferredMetadataText(
    fileValue: String?,
    mediaStoreValue: String?,
    defaultValue: String
): String {
    return sequenceOf(fileValue, mediaStoreValue)
        .mapNotNull(::usableMetadataText)
        .firstOrNull()
        ?: defaultValue
}

internal fun usableMetadataText(value: String?): String? {
    val normalized = normalizeMetadataText(value.orEmpty()).trim()
    return normalized.takeIf {
        it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true)
    }
}
