package ink.tenqui.flowtone.playback

import androidx.media3.common.MediaItem
import ink.tenqui.flowtone.core.model.Song

fun Song.toMediaItem(): MediaItem {
    return MediaItemMapper.toMediaItem(this)
}

fun Song.toMediaItem(source: PlaybackSource): MediaItem {
    return MediaItemMapper.toMediaItem(this, source)
}

fun MediaItem.toSongOrNull(scannedSongs: List<Song>): Song? {
    return MediaItemMapper.toSongOrNull(this, scannedSongs)
}

fun MediaItem?.toPlaybackSource(): PlaybackSource {
    return MediaItemMapper.toPlaybackSource(this)
}
