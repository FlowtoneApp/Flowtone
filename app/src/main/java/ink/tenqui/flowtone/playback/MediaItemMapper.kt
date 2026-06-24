package ink.tenqui.flowtone.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType

object MediaItemMapper {
    private const val EXTRA_SONG_ID = "song_id"
    private const val EXTRA_SONG_URI = "song_uri"
    private const val EXTRA_ARTWORK_URI = "artwork_uri"
    private const val EXTRA_DURATION_MS = "duration_ms"

    fun toMediaItem(song: Song): MediaItem {
        val mediaId = song.id.takeIf { it > 0L }?.toString()
            ?: song.uri.toString()
        val extras = Bundle().apply {
            putLong(EXTRA_SONG_ID, song.id)
            putString(EXTRA_SONG_URI, song.uri.toString())
            song.artworkUri?.let { putString(EXTRA_ARTWORK_URI, it.toString()) }
            putLong(EXTRA_DURATION_MS, song.durationMs)
        }
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(song.artworkUri)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(song.uri)
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    fun toSongOrNull(mediaItem: MediaItem, scannedSongs: List<Song>): Song? {
        val mediaIdAsLong = mediaItem.mediaId.toLongOrNull()
        val extras = mediaItem.mediaMetadata.extras
        val songId = mediaIdAsLong
            ?: extras?.getLong(EXTRA_SONG_ID)?.takeIf { it > 0L }

        if (songId != null) {
            scannedSongs.firstOrNull { it.id == songId }?.let { return it }
        }

        val uri = mediaItem.localConfiguration?.uri
            ?: extras?.getString(EXTRA_SONG_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return null
        val artworkUri = mediaItem.mediaMetadata.artworkUri
            ?: extras?.getString(EXTRA_ARTWORK_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "\u672a\u77e5\u6b4c\u66f2" }
        val artist = mediaItem.mediaMetadata.artist?.toString().orEmpty().ifBlank { "\u672a\u77e5\u827a\u672f\u5bb6" }
        val durationMs = extras?.getLong(EXTRA_DURATION_MS)?.takeIf { it > 0L } ?: 0L

        return Song(
            id = songId ?: uri.toString().hashCode().toLong(),
            sourceType = SourceType.Local,
            title = title,
            artist = artist,
            durationMs = durationMs,
            uri = uri,
            artworkUri = artworkUri
        )
    }
}
