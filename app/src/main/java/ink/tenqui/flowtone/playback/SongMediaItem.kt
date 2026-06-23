package ink.tenqui.flowtone.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import ink.tenqui.flowtone.core.model.Song

private const val EXTRA_SONG_ID = "song_id"
private const val EXTRA_SONG_URI = "song_uri"
private const val EXTRA_ARTWORK_URI = "artwork_uri"
private const val EXTRA_DURATION_MS = "duration_ms"

fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putLong(EXTRA_SONG_ID, id)
        putString(EXTRA_SONG_URI, uri.toString())
        artworkUri?.let { putString(EXTRA_ARTWORK_URI, it.toString()) }
        putLong(EXTRA_DURATION_MS, durationMs)
    }
    val mediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(artworkUri)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(mediaMetadata)
        .build()
}

fun MediaItem.toSongOrNull(scannedSongs: List<Song>): Song? {
    val mediaIdAsLong = mediaId.toLongOrNull()
    val extras = mediaMetadata.extras
    val songId = mediaIdAsLong
        ?: extras?.getLong(EXTRA_SONG_ID)?.takeIf { it > 0L }

    if (songId != null) {
        scannedSongs.firstOrNull { it.id == songId }?.let { return it }
    }

    val uri = localConfiguration?.uri
        ?: extras?.getString(EXTRA_SONG_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        ?: return null
    val artworkUri = mediaMetadata.artworkUri
        ?: extras?.getString(EXTRA_ARTWORK_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }
    val title = mediaMetadata.title?.toString().orEmpty().ifBlank { "未知歌曲" }
    val artist = mediaMetadata.artist?.toString().orEmpty().ifBlank { "未知艺术家" }
    val durationMs = extras?.getLong(EXTRA_DURATION_MS)?.takeIf { it > 0L } ?: 0L

    return Song(
        id = songId ?: uri.toString().hashCode().toLong(),
        title = title,
        artist = artist,
        durationMs = durationMs,
        uri = uri,
        artworkUri = artworkUri
    )
}
