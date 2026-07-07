package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.player.QueueDisplayOrder
import ink.tenqui.flowtone.ui.theme.AppThemeMode

internal data class FlowtoneAppCallbacks(
    val onThemeModeChange: (AppThemeMode) -> Unit,
    val onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    val onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    val onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    val onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    val onPreloadSongMetadataCountChange: (Int) -> Unit,
    val onSongRecordThresholdSecondsChange: (Int) -> Unit,
    val onOpenSongRecordThresholdDialog: () -> Unit,
    val onCloseSongRecordThresholdDialog: () -> Unit,
    val onSongRecordThresholdDialogClosed: () -> Unit,
    val onPlaybackQueueDisplayOrderChange: (QueueDisplayOrder) -> Unit,
    val settingsBackActionChange: ((() -> Unit)?) -> Unit,
    val onSettingsPathSegmentsChange: (List<String>) -> Unit,
    val openSourceBackActionChange: ((() -> Unit)?) -> Unit,
    val onOpenSourcePathSegmentsChange: (List<String>) -> Unit,
    val onNavigateBack: () -> Unit,
    val onCloseSecondaryPage: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenLocalLibrary: () -> Unit,
    val onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    val onOpenArtistRootPage: (String) -> Unit,
    val onCloseArtistRootPage: () -> Unit,
    val onOpenSource: () -> Unit,
    val onOpenSourceBack: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onSongClick: (Song) -> Unit,
    val onDismissExpandedPlayer: () -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
    val onFullscreenChange: (Boolean) -> Unit,
    val onMinimizedChange: (Boolean) -> Unit,
    val onTogglePlayPause: () -> Unit,
    val onPlayPrevious: () -> Unit,
    val onPlayNext: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onTogglePlaybackOrderMode: () -> Unit,
    val onPlayQueueSong: (Song) -> Unit,
    val onPlaylistSongClick: (List<Song>, Int) -> Unit,
    val onSetSongLiked: (Song, Boolean) -> Unit,
    val onToggleSongLiked: (Song) -> Unit
)

internal fun flowtoneAppCallbacks(
    appState: FlowtoneAppState,
    appPreferences: AppPreferences,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    onCloseArtistRootPage: () -> Unit,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaylistSongClick: (List<Song>, Int) -> Unit,
    onExitMiniPlayerFullscreen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    onPlayQueueSong: (Song) -> Unit,
    onSetSongLiked: (Song, Boolean) -> Unit,
    onToggleSongLiked: (Song) -> Unit
): FlowtoneAppCallbacks {
    return FlowtoneAppCallbacks(
        onThemeModeChange = onThemeModeChange,
        onDisablePausedArtworkTiltChange = { disable ->
            appState.disablePausedArtworkTilt = disable
            appPreferences.setDisablePausedArtworkTilt(disable)
        },
        onHideSecondaryBackButtonChange = { hide ->
            appState.hideSecondaryBackButton = hide
            appPreferences.setHideSecondaryBackButton(hide)
        },
        onResumePlaybackAfterCallChange = { resume ->
            appState.resumePlaybackAfterCall = resume
            appPreferences.setResumePlaybackAfterCall(resume)
        },
        onAllowFullscreenFromCollapsedChange = { allow ->
            appState.allowFullscreenFromCollapsed = allow
            appPreferences.setAllowFullscreenFromCollapsed(allow)
        },
        onPreloadSongMetadataCountChange = { count ->
            appState.preloadSongMetadataCount = count
            appPreferences.setSongMetadataPreloadCount(count)
        },
        onSongRecordThresholdSecondsChange = { seconds ->
            appState.songRecordThresholdSeconds = seconds
            appPreferences.setSongRecordThresholdSeconds(seconds)
        },
        onOpenSongRecordThresholdDialog = {
            if (appState.songRecordThresholdDialogState == SongRecordThresholdDialogState.Idle) {
                appState.songRecordThresholdDialogState = SongRecordThresholdDialogState.Editing
            }
        },
        onCloseSongRecordThresholdDialog = {
            if (appState.songRecordThresholdDialogState == SongRecordThresholdDialogState.Editing) {
                appState.songRecordThresholdDialogState = SongRecordThresholdDialogState.Closing
            }
        },
        onSongRecordThresholdDialogClosed = {
            if (appState.songRecordThresholdDialogState == SongRecordThresholdDialogState.Closing) {
                appState.songRecordThresholdDialogState = SongRecordThresholdDialogState.Idle
            }
        },
        onPlaybackQueueDisplayOrderChange = { order ->
            appState.playbackQueueDisplayOrder = order
            appPreferences.setPlaybackQueueDisplayOrder(order)
        },
        settingsBackActionChange = { action ->
            appState.settingsBackAction = action
        },
        onSettingsPathSegmentsChange = { segments ->
            if (appState.secondaryPage == SecondaryPage.Settings) {
                appState.secondaryPathSegments = segments
            }
        },
        openSourceBackActionChange = { action ->
            appState.openSourceBackAction = action
        },
        onOpenSourcePathSegmentsChange = { segments ->
            if (appState.secondaryPage == SecondaryPage.OpenSource) {
                appState.secondaryPathSegments = segments
            }
        },
        onNavigateBack = onNavigateBack,
        onCloseSecondaryPage = {
            appState.secondaryPage = null
            appState.secondaryPathSegments = emptyList()
            appState.selectedPlaylistId = null
            appState.selectedPlaylistTitle = null
            appState.selectedArtistName = null
        },
        onOpenSettings = {
            appState.secondaryPathSegments = emptyList()
            appState.selectedArtistName = null
            appState.secondaryPage = SecondaryPage.Settings
        },
        onOpenAbout = {
            appState.secondaryPathSegments = emptyList()
            appState.selectedArtistName = null
            appState.secondaryPage = SecondaryPage.About
        },
        onOpenLocalLibrary = {
            appState.secondaryPathSegments = emptyList()
            appState.selectedPlaylistId = null
            appState.selectedPlaylistTitle = null
            appState.selectedArtistName = null
            appState.secondaryPage = SecondaryPage.LocalLibrary
        },
        onOpenPlaylist = { playlist ->
            appState.selectedPlaylistId = playlist.id
            appState.selectedPlaylistTitle = playlist.title
            appState.selectedArtistName = null
            appState.secondaryPathSegments = listOf(playlist.title)
            appState.secondaryPage = SecondaryPage.Playlist
        },
        onOpenArtistRootPage = { artistName ->
            appState.artistRootReturnInProgress = false
            appState.artistRootPageArtistName = artistName
            appState.miniPlayerFullscreen = false
            appState.miniPlayerExpanded = false
            appState.miniPlayerFullscreenEnteredFromCollapsed = false
            appState.miniPlayerMinimized = false
        },
        onCloseArtistRootPage = onCloseArtistRootPage,
        onOpenSource = {
            appState.secondaryPathSegments = emptyList()
            appState.selectedArtistName = null
            appState.secondaryPage = SecondaryPage.OpenSource
        },
        onOpenSourceBack = {
            appState.secondaryPathSegments = emptyList()
            appState.secondaryPage = SecondaryPage.About
        },
        onRequestPermission = onRequestPermission,
        onSongClick = onSongClick,
        onDismissExpandedPlayer = {
            if (appState.miniPlayerFullscreen) {
                onExitMiniPlayerFullscreen()
            } else {
                appState.miniPlayerExpanded = false
            }
        },
        onExpandedChange = { expanded ->
            if (!expanded && appState.miniPlayerFullscreen) {
                onExitMiniPlayerFullscreen()
            } else {
                if (expanded) {
                    appState.miniPlayerMinimized = false
                }
                appState.miniPlayerExpanded = expanded
            }
        },
        onFullscreenChange = { fullscreen ->
            if (fullscreen) {
                appState.miniPlayerFullscreenEnteredFromCollapsed =
                    !appState.miniPlayerExpanded
                appState.miniPlayerExpanded = true
                appState.miniPlayerMinimized = false
                appState.miniPlayerFullscreen = true
            } else {
                onExitMiniPlayerFullscreen()
            }
        },
        onMinimizedChange = { minimized ->
            if (minimized) {
                appState.miniPlayerFullscreen = false
                appState.miniPlayerExpanded = false
                appState.miniPlayerFullscreenEnteredFromCollapsed = false
            }
            appState.miniPlayerMinimized = minimized
        },
        onTogglePlayPause = onTogglePlayPause,
        onPlayPrevious = onPlayPrevious,
        onPlayNext = onPlayNext,
        onSeekTo = onSeekTo,
        onTogglePlaybackOrderMode = onTogglePlaybackOrderMode,
        onPlayQueueSong = onPlayQueueSong,
        onPlaylistSongClick = onPlaylistSongClick,
        onSetSongLiked = onSetSongLiked,
        onToggleSongLiked = onToggleSongLiked
    )
}
