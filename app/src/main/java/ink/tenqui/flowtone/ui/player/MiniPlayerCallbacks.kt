package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.core.model.Song

internal data class MiniPlayerCallbacks(
    val onTogglePlayPause: () -> Unit,
    val onPlayPrevious: () -> Unit,
    val onPlayNext: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onTogglePlaybackOrderMode: () -> Unit,
    val onPlayQueueSong: (Song) -> Unit,
    val onPlayArtistSongQueue: (List<Song>, Int) -> Unit,
    val onToggleSongLiked: (Song) -> Unit,
    val onOpenArtistRootPage: (String) -> Unit
)
