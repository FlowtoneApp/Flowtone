package ink.tenqui.flowtone.playback

import ink.tenqui.flowtone.core.model.Song

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackOrderMode: PlaybackOrderMode = PlaybackOrderMode.Sequence,
    val errorMessage: String? = null
)
