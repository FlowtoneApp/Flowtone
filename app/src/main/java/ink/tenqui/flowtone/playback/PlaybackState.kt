package ink.tenqui.flowtone.playback

import ink.tenqui.flowtone.model.Song

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)
