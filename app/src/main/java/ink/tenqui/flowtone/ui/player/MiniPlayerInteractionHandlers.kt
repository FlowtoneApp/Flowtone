package ink.tenqui.flowtone.ui.player

import android.util.Log
import androidx.compose.ui.input.pointer.PointerInputChange
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Song

private const val FLOWTONE_FAVORITE_BUTTON_TAG = "FlowtoneFavoriteButton"

internal data class MiniPlayerFullscreenInteractionHandlers(
    val onArtistClick: (String) -> Unit,
    val onDismissAddToPlaylistAtTop: () -> Unit,
    val onPlaylistClick: (LibraryPlaylistCard) -> Unit,
    val onLockPlayPauseVisual: (Boolean) -> Unit,
    val onScrubbingChange: (Boolean) -> Unit,
    val onPlayPrevious: () -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onPlayNext: () -> Unit,
    val onMoreMenuExpandedChange: (Boolean) -> Unit,
    val onToggleLiked: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    val onOpenSongInfo: () -> Unit,
    val onOpenQueue: () -> Unit,
    val onArtistHostBack: () -> Unit,
    val onArtistHostArtistClick: (String) -> Unit,
    val onCollapseClick: () -> Unit
)

internal fun miniPlayerFullscreenInteractionHandlers(
    currentSong: Song?,
    expanded: Boolean,
    fullscreen: Boolean,
    minimized: Boolean,
    hasCurrentSong: Boolean,
    artistClickEnabled: Boolean,
    artistPlaceholderActive: Boolean,
    fullscreenContentMode: FullscreenContentMode,
    fullscreenContentExitProgress: Float,
    artistPlaceholderProgress: Float,
    allowFullscreenFromCollapsed: Boolean,
    transitions: MiniPlayerTransitions,
    callbacks: MiniPlayerCallbacks,
    onAddSongToPlaylist: (LibraryPlaylistCard, () -> Unit) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit
): MiniPlayerFullscreenInteractionHandlers =
    MiniPlayerFullscreenInteractionHandlers(
        onArtistClick = { rawArtist ->
            handleMiniPlayerArtistClick(
                rawArtist = rawArtist,
                artistClickEnabled = artistClickEnabled,
                fullscreenContentExitProgress = fullscreenContentExitProgress,
                artistPlaceholderProgress = artistPlaceholderProgress,
                transitions = transitions
            )
        },
        onDismissAddToPlaylistAtTop = transitions::exitAddToPlaylistMode,
        onPlaylistClick = { playlist ->
            handleMiniPlayerPlaylistClick(
                playlist = playlist,
                transitions = transitions,
                onAddSongToPlaylist = onAddSongToPlaylist
            )
        },
        onLockPlayPauseVisual = transitions::lockPlayPauseVisual,
        onScrubbingChange = transitions::setProgressScrubbing,
        onPlayPrevious = {
            handleMiniPlayerPreviousClick(
                hasCurrentSong = hasCurrentSong,
                transitions = transitions,
                callbacks = callbacks
            )
        },
        onTogglePlayPause = {
            handleMiniPlayerTogglePlayPause(
                hasCurrentSong = hasCurrentSong,
                transitions = transitions,
                callbacks = callbacks
            )
        },
        onPlayNext = {
            handleMiniPlayerNextClick(
                hasCurrentSong = hasCurrentSong,
                transitions = transitions,
                callbacks = callbacks
            )
        },
        onMoreMenuExpandedChange = transitions::setExpandedMoreMenu,
        onToggleLiked = {
            handleToggleCurrentSongLiked(
                currentSong = currentSong,
                expanded = expanded,
                fullscreen = fullscreen,
                minimized = minimized,
                callbacks = callbacks
            )
        },
        onAddToPlaylist = transitions::enterAddToPlaylistMode,
        onOpenSongInfo = {
            handleMiniPlayerOpenSongInfo(
                fullscreenContentExitProgress = fullscreenContentExitProgress,
                artistPlaceholderProgress = artistPlaceholderProgress,
                transitions = transitions
            )
        },
        onOpenQueue = transitions::openQueueSheet,
        onArtistHostBack = transitions::exitFullscreenContentMode,
        onArtistHostArtistClick = { artistName ->
            handleMiniPlayerArtistHostArtistClick(
                artistName = artistName,
                artistPlaceholderActive = artistPlaceholderActive,
                transitions = transitions,
                callbacks = callbacks
            )
        },
        onCollapseClick = {
            handleMiniPlayerCollapseClick(
                fullscreenContentMode = fullscreenContentMode,
                transitions = transitions,
                allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                onFullscreenChange = onFullscreenChange,
                onExpandedChange = onExpandedChange
            )
        }
    )

internal fun handleMiniPlayerActivate(
    minimized: Boolean,
    onMinimizedChange: (Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    openExpandedOnMediaClick: Boolean
) {
    if (!openExpandedOnMediaClick) {
        onFullscreenChange(true)
    } else if (minimized) {
        onMinimizedChange(false)
    } else {
        onExpandedChange(true)
    }
}

internal fun handleMiniPlayerVerticalDragEnd(
    state: MiniPlayerState,
    transitions: MiniPlayerTransitions,
    hasCurrentSong: Boolean,
    fullscreenContentBackGesturesEnabled: Boolean,
    swipeThresholdPx: Float,
    fullscreenSwipeThresholdPx: Float,
    minimized: Boolean,
    expanded: Boolean,
    fullscreenInteractionActive: Boolean,
    allowFullscreenFromCollapsed: Boolean,
    allowFullscreenFromExpanded: Boolean,
    onMinimizedChange: (Boolean) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit
) {
    if (!hasCurrentSong) {
        return
    }
    if (fullscreenContentBackGesturesEnabled) {
        if (state.accumulatedDragY >= fullscreenSwipeThresholdPx) {
            transitions.exitFullscreenContentMode()
        }
        return
    }
    when {
        state.accumulatedDragY <= -swipeThresholdPx && minimized -> {
            onMinimizedChange(false)
        }
        state.accumulatedDragY <= -fullscreenSwipeThresholdPx &&
            !expanded &&
            !fullscreenInteractionActive &&
            allowFullscreenFromCollapsed -> {
            onMinimizedChange(false)
            onFullscreenChange(true)
            onExpandedChange(true)
        }
        state.accumulatedDragY <= -fullscreenSwipeThresholdPx &&
            expanded &&
            !fullscreenInteractionActive &&
            allowFullscreenFromExpanded -> {
            onFullscreenChange(true)
        }
        state.accumulatedDragY <= -swipeThresholdPx && !expanded -> {
            onExpandedChange(true)
        }
        state.accumulatedDragY >= fullscreenSwipeThresholdPx &&
            fullscreenInteractionActive -> {
            onFullscreenChange(false)
        }
        state.accumulatedDragY >= swipeThresholdPx &&
            expanded &&
            !fullscreenInteractionActive -> {
            onExpandedChange(false)
        }
        state.accumulatedDragY >= swipeThresholdPx &&
            !expanded &&
            !fullscreenInteractionActive &&
            !minimized -> {
            onMinimizedChange(true)
        }
    }
}

internal fun handleExpandedMoreMenuPointerUp(
    up: PointerInputChange?,
    transitions: MiniPlayerTransitions
) {
    if (up != null) {
        transitions.closeExpandedMoreMenu()
    }
}

internal fun handleToggleCurrentSongLiked(
    currentSong: Song?,
    expanded: Boolean,
    fullscreen: Boolean,
    minimized: Boolean,
    callbacks: MiniPlayerCallbacks
) {
    currentSong?.let { song ->
        Log.d(
            FLOWTONE_FAVORITE_BUTTON_TAG,
            "favorite click songId=${song.id}, expanded=$expanded, fullscreen=$fullscreen, " +
                "minimized=$minimized"
        )
        callbacks.onToggleSongLiked(song)
    }
    Unit
}

internal fun handleMiniPlayerPlayPrevious(
    hasCurrentSong: Boolean,
    transitions: MiniPlayerTransitions,
    callbacks: MiniPlayerCallbacks
) {
    if (hasCurrentSong) {
        transitions.preparePlayPrevious()
        callbacks.onPlayPrevious()
    }
}

internal fun handleMiniPlayerPlayNext(
    hasCurrentSong: Boolean,
    transitions: MiniPlayerTransitions,
    callbacks: MiniPlayerCallbacks
) {
    if (hasCurrentSong) {
        transitions.preparePlayNext()
        callbacks.onPlayNext()
    }
}

internal fun handleMiniPlayerPreviousClick(
    hasCurrentSong: Boolean,
    transitions: MiniPlayerTransitions,
    callbacks: MiniPlayerCallbacks
) {
    transitions.closeExpandedMoreMenu()
    handleMiniPlayerPlayPrevious(
        hasCurrentSong = hasCurrentSong,
        transitions = transitions,
        callbacks = callbacks
    )
}

internal fun handleMiniPlayerNextClick(
    hasCurrentSong: Boolean,
    transitions: MiniPlayerTransitions,
    callbacks: MiniPlayerCallbacks
) {
    transitions.closeExpandedMoreMenu()
    handleMiniPlayerPlayNext(
        hasCurrentSong = hasCurrentSong,
        transitions = transitions,
        callbacks = callbacks
    )
}

internal fun handleMiniPlayerTogglePlayPause(
    hasCurrentSong: Boolean,
    transitions: MiniPlayerTransitions,
    callbacks: MiniPlayerCallbacks
) {
    if (transitions.prepareTogglePlayPause(hasCurrentSong)) {
        callbacks.onTogglePlayPause()
    }
}

internal fun handleMiniPlayerArtistClick(
    rawArtist: String,
    artistClickEnabled: Boolean,
    fullscreenContentExitProgress: Float,
    artistPlaceholderProgress: Float,
    transitions: MiniPlayerTransitions
) {
    transitions.handleArtistClick(
        rawArtist = rawArtist,
        artistClickEnabled = artistClickEnabled,
        fullscreenContentExitProgress = fullscreenContentExitProgress,
        artistPlaceholderProgress = artistPlaceholderProgress
    )
}

internal fun handleMiniPlayerArtistHostArtistClick(
    artistName: String,
    artistPlaceholderActive: Boolean,
    transitions: MiniPlayerTransitions,
    callbacks: MiniPlayerCallbacks
) {
    transitions.openArtistDetailFromPlaceholder(
        artistName = artistName,
        artistPlaceholderActive = artistPlaceholderActive
    )?.let(callbacks.onOpenArtistRootPage)
}

internal fun handleMiniPlayerOpenSongInfo(
    fullscreenContentExitProgress: Float,
    artistPlaceholderProgress: Float,
    transitions: MiniPlayerTransitions
) {
    transitions.enterSongInfoMode(
        fullscreenContentExitProgress = fullscreenContentExitProgress,
        artistPlaceholderProgress = artistPlaceholderProgress
    )
}

internal fun handleMiniPlayerPlaylistClick(
    playlist: LibraryPlaylistCard,
    transitions: MiniPlayerTransitions,
    onAddSongToPlaylist: (LibraryPlaylistCard, () -> Unit) -> Unit
) {
    onAddSongToPlaylist(playlist, transitions::exitAddToPlaylistMode)
}

internal fun handleMiniPlayerCollapseClick(
    fullscreenContentMode: FullscreenContentMode,
    transitions: MiniPlayerTransitions,
    allowFullscreenFromCollapsed: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit
) {
    if (fullscreenContentMode != FullscreenContentMode.Playback) {
        transitions.exitFullscreenContentMode()
    } else if (allowFullscreenFromCollapsed) {
        onFullscreenChange(false)
        onExpandedChange(false)
    } else {
        onFullscreenChange(false)
    }
}
