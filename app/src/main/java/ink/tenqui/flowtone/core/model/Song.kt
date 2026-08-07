package ink.tenqui.flowtone.core.model

import android.net.Uri

data class Song(
    val id: Long,
    val sourceType: SourceType,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri,
    val albumId: Long? = null,
    val artworkUri: Uri? = null,
    val filePath: String? = null,
    val displayName: String? = null,
    val relativePath: String? = null,
    val volumeName: String? = null,
    val dateAddedSeconds: Long = 0L,
    val dateModifiedSeconds: Long = 0L
)
