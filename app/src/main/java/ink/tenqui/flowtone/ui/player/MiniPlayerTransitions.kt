package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.ui.player.lyrics.FullscreenPlaybackContentMode

internal class MiniPlayerTransitions(
    private val state: MiniPlayerState
) {
    fun enterLyricsMode() {
        if (
            state.fullscreenContentMode != FullscreenContentMode.Playback ||
            !state.isFullscreenPlayer
        ) {
            return
        }
        state.expandedMoreMenu = false
        state.fullscreenPlaybackContentMode = FullscreenPlaybackContentMode.LyricsPlaceholder
    }

    fun exitLyricsMode() {
        state.fullscreenPlaybackContentMode = FullscreenPlaybackContentMode.Artwork
    }

    fun resetFullscreenPlaybackContentMode() {
        state.fullscreenPlaybackContentMode = FullscreenPlaybackContentMode.Artwork
    }

    fun enterAddToPlaylistMode() {
        state.artistPlaceholderArtists = emptyList()
        state.expandedMoreMenu = false
        state.fullscreenContentMode = FullscreenContentMode.AddToPlaylist
    }

    fun enterSongInfoMode(
        fullscreenContentExitProgress: Float,
        artistPlaceholderProgress: Float
    ) {
        if (
            state.fullscreenContentMode != FullscreenContentMode.Playback ||
            !state.isFullscreenPlayer ||
            fullscreenContentExitProgress > 0.01f ||
            artistPlaceholderProgress > 0.01f
        ) {
            return
        }
        state.artistPlaceholderArtists = emptyList()
        state.expandedMoreMenu = false
        state.fullscreenContentMode = FullscreenContentMode.SongInfo
    }

    fun enterArtistPlaceholderMode(
        rawArtist: String,
        fullscreenContentExitProgress: Float,
        artistPlaceholderProgress: Float
    ) {
        if (
            state.fullscreenContentMode != FullscreenContentMode.Playback ||
            !state.isFullscreenPlayer ||
            fullscreenContentExitProgress > 0.01f ||
            artistPlaceholderProgress > 0.01f
        ) {
            return
        }
        val displayArtist = rawArtist.trim()
        if (!isSelectableArtist(displayArtist)) {
            return
        }
        val artists = parseArtistCandidates(displayArtist)
        if (artists.isEmpty()) {
            return
        }

        state.expandedMoreMenu = false
        state.artistPlaceholderArtists = artists
        state.fullscreenContentMode = FullscreenContentMode.ArtistPlaceholder
    }

    fun handleArtistClick(
        rawArtist: String,
        artistClickEnabled: Boolean,
        fullscreenContentExitProgress: Float,
        artistPlaceholderProgress: Float
    ) {
        if (!artistClickEnabled) {
            return
        }
        enterArtistPlaceholderMode(
            rawArtist = rawArtist,
            fullscreenContentExitProgress = fullscreenContentExitProgress,
            artistPlaceholderProgress = artistPlaceholderProgress
        )
    }

    fun openArtistDetailFromPlaceholder(
        artistName: String,
        artistPlaceholderActive: Boolean
    ): String? {
        val selectedArtistName = artistName.trim()
        if (!artistPlaceholderActive || selectedArtistName.isBlank()) {
            return null
        }
        resetFullscreenContentMode()
        return selectedArtistName
    }

    fun finishArtistPlaceholderProgress(finalValue: Float) {
        if (
            finalValue == 0f &&
            state.fullscreenContentMode != FullscreenContentMode.ArtistPlaceholder
        ) {
            state.artistPlaceholderArtists = emptyList()
        }
    }

    fun exitArtistPlaceholderWithAnimation() {
        state.expandedMoreMenu = false
        if (state.fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder) {
            state.fullscreenContentMode = FullscreenContentMode.Playback
        }
    }

    fun exitFullscreenContentMode() {
        when (state.fullscreenContentMode) {
            FullscreenContentMode.ArtistPlaceholder -> exitArtistPlaceholderWithAnimation()
            FullscreenContentMode.Playback -> {
                state.expandedMoreMenu = false
            }
            else -> {
                state.expandedMoreMenu = false
                state.artistPlaceholderArtists = emptyList()
                state.fullscreenContentMode = FullscreenContentMode.Playback
            }
        }
    }

    fun resetFullscreenContentMode() {
        state.expandedMoreMenu = false
        state.artistPlaceholderArtists = emptyList()
        if (state.fullscreenContentMode != FullscreenContentMode.Playback) {
            state.fullscreenContentMode = FullscreenContentMode.Playback
        }
    }

    fun exitFullscreenContentModeForSongChange(artistPlaceholderProgress: Float) {
        val artistPlaceholderExitInProgress = isArtistPlaceholderExitInProgress(
            fullscreenContentMode = state.fullscreenContentMode,
            artistPlaceholderArtists = state.artistPlaceholderArtists,
            artistPlaceholderProgress = artistPlaceholderProgress
        )
        if (state.fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder) {
            exitArtistPlaceholderWithAnimation()
        } else if (!artistPlaceholderExitInProgress) {
            resetFullscreenContentMode()
        }
    }

    fun exitAddToPlaylistMode() {
        exitFullscreenContentMode()
    }

    fun lockPlayPauseVisual(isPlayingToLock: Boolean) {
        state.lockedIsPlayingDuringScrub = isPlayingToLock
        state.keepPlayPauseVisualLockedAfterSeek = true
        state.playPauseVisualLockToken += 1
    }

    fun preparePlayPrevious() {
        state.collapsedMetadataSwitchDirection = -1
        lockPlayPauseVisual(true)
    }

    fun preparePlayNext() {
        state.collapsedMetadataSwitchDirection = 1
        lockPlayPauseVisual(true)
    }

    fun prepareTogglePlayPause(hasCurrentSong: Boolean): Boolean {
        state.expandedMoreMenu = false
        if (hasCurrentSong) {
            state.isProgressScrubbing = false
            state.keepPlayPauseVisualLockedAfterSeek = false
            return true
        }
        return false
    }

    fun setProgressScrubbing(scrubbing: Boolean) {
        state.isProgressScrubbing = scrubbing
    }

    fun setKeepPlayPauseVisualLockedAfterSeek(locked: Boolean) {
        state.keepPlayPauseVisualLockedAfterSeek = locked
    }

    fun closeExpandedMoreMenu() {
        state.expandedMoreMenu = false
    }

    fun setExpandedMoreMenu(expanded: Boolean) {
        state.expandedMoreMenu = expanded
    }

    fun openQueueSheet() {
        state.queueSheetBackgroundBlurred = true
        state.showQueueSheet = true
    }

    fun startQueueSheetDismiss() {
        state.queueSheetBackgroundBlurred = false
    }

    fun finishQueueSheetDismiss() {
        state.showQueueSheet = false
    }
}
