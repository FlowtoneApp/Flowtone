package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.search.SearchScope
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.player.QueueDisplayOrder
import ink.tenqui.flowtone.ui.player.coerceFlowCloudSpeed
import ink.tenqui.flowtone.ui.player.lyrics.LyricsBackgroundStyle
import ink.tenqui.flowtone.ui.screens.ListeningRecordTab
import ink.tenqui.flowtone.ui.theme.AppThemeMode

internal data class FlowtoneAppCallbacks(
    val onThemeModeChange: (AppThemeMode) -> Unit,
    val onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    val onStrictProgressBarChange: (Boolean) -> Unit,
    val onAllowScreenOffOnLyricsPageChange: (Boolean) -> Unit,
    val onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    val onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    val onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    val onOpenExpandedMiniPlayerOnMediaClickChange: (Boolean) -> Unit,
    val onPreloadSongMetadataCountChange: (Int) -> Unit,
    val onPreloadLyricsCountChange: (Int) -> Unit,
    val onSongRecordThresholdSecondsChange: (Int) -> Unit,
    val onOpenSongRecordThresholdDialog: () -> Unit,
    val onCloseSongRecordThresholdDialog: () -> Unit,
    val onSongRecordThresholdDialogClosed: () -> Unit,
    val onFlowCloudSpeedChange: (Float) -> Unit,
    val onDarkFlowCloudOverlayChange: (Boolean) -> Unit,
    val onLyricsBackgroundStyleChange: (LyricsBackgroundStyle) -> Unit,
    val onOpenFlowCloudSpeedDialog: () -> Unit,
    val onCloseFlowCloudSpeedDialog: () -> Unit,
    val onFlowCloudSpeedDialogClosed: () -> Unit,
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
    val onOpenArtistRootPage: (String, ArtistRootNavigationMode) -> Unit,
    val onOpenListeningRecords: (ListeningRecordTab) -> Unit,
    val onCloseArtistRootPage: () -> Unit,
    val onOpenSource: () -> Unit,
    val onOpenSourceBack: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onSongClick: (Song) -> Unit,
    val onOnlineSongClick: (ProviderSong) -> Unit,
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
    val onPlaylistSongClick: (List<Song>, Int, PlaybackSource) -> Unit,
    val onPersistentTrackQueueClick: (List<PersistentTrack>, Int, PlaybackSource) -> Unit,
    val onAddSongsToNext: (List<Song>) -> Boolean,
    val onAppendSongsToQueue: (List<Song>) -> Boolean,
    val onSetSongLiked: (Song, Boolean) -> Unit,
    val onSetSongsLiked: (List<Song>, Boolean) -> Unit,
    val onSetTracksLiked: (List<PersistentTrack>, Boolean) -> Unit,
    val onDeleteSongs: (List<Song>, (Boolean) -> Unit) -> Unit,
    val onToggleSongLiked: (Song) -> Unit,
    val onOpenSearch: () -> Unit,
    val onExitSearch: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSearchScopeChange: (SearchScope) -> Unit,
    val onRefreshSearchSources: () -> Unit,
    val onClearSearch: () -> Unit,
    val onSearchFocusRequestConsumed: () -> Unit,
    val onSearchKeyboardDismissRequestConsumed: () -> Unit,
    val onSearchInputFocusChange: (Boolean) -> Unit,
    val onSearchImeAction: () -> Unit
)

internal fun flowtoneAppCallbacks(
    appState: FlowtoneAppState,
    appPreferences: AppPreferences,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    onCloseArtistRootPage: () -> Unit,
    onOpenArtistRootPage: (String, ArtistRootNavigationMode) -> Unit,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onOnlineSongClick: (ProviderSong) -> Unit,
    onPlaylistSongClick: (List<Song>, Int, PlaybackSource) -> Unit,
    onPersistentTrackQueueClick: (List<PersistentTrack>, Int, PlaybackSource) -> Unit,
    onExitMiniPlayerFullscreen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    onPlayQueueSong: (Song) -> Unit,
    onAddSongsToNext: (List<Song>) -> Boolean,
    onAppendSongsToQueue: (List<Song>) -> Boolean,
    onSetSongLiked: (Song, Boolean) -> Unit,
    onSetSongsLiked: (List<Song>, Boolean) -> Unit,
    onSetTracksLiked: (List<PersistentTrack>, Boolean) -> Unit,
    onDeleteSongs: (List<Song>, (Boolean) -> Unit) -> Unit,
    onToggleSongLiked: (Song) -> Unit,
    onOpenSearch: () -> Unit,
    onExitSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchScopeChange: (SearchScope) -> Unit,
    onRefreshSearchSources: () -> Unit,
    onClearSearch: () -> Unit
): FlowtoneAppCallbacks {
    return FlowtoneAppCallbacks(
        onThemeModeChange = onThemeModeChange,
        onDisablePausedArtworkTiltChange = { disable ->
            appState.disablePausedArtworkTilt = disable
            appPreferences.setDisablePausedArtworkTilt(disable)
        },
        onStrictProgressBarChange = { strict ->
            appState.strictProgressBar = strict
            appPreferences.setStrictProgressBar(strict)
        },
        onAllowScreenOffOnLyricsPageChange = { allow ->
            appState.allowScreenOffOnLyricsPage = allow
            appPreferences.setAllowScreenOffOnLyricsPage(allow)
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
        onOpenExpandedMiniPlayerOnMediaClickChange = { openExpanded ->
            appState.openExpandedMiniPlayerOnMediaClick = openExpanded
            appPreferences.setOpenExpandedMiniPlayerOnMediaClick(openExpanded)
        },
        onPreloadSongMetadataCountChange = { count ->
            appState.preloadSongMetadataCount = count
            appPreferences.setSongMetadataPreloadCount(count)
        },
        onPreloadLyricsCountChange = { count ->
            appState.preloadLyricsCount = count
            appPreferences.setLyricsPreloadCount(count)
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
        onFlowCloudSpeedChange = { speed ->
            val safeSpeed = speed.coerceFlowCloudSpeed()
            appState.flowCloudSpeed = safeSpeed
            appPreferences.setFlowCloudSpeed(safeSpeed)
        },
        onDarkFlowCloudOverlayChange = { enabled ->
            appState.darkFlowCloudOverlayEnabled = enabled
            appPreferences.setDarkFlowCloudOverlay(enabled)
        },
        onLyricsBackgroundStyleChange = { style ->
            appState.lyricsBackgroundStyle = style
            appPreferences.setLyricsBackgroundStyle(style)
        },
        onOpenFlowCloudSpeedDialog = {
            if (appState.flowCloudSpeedDialogState == FlowCloudSpeedDialogState.Idle) {
                appState.flowCloudSpeedDialogState = FlowCloudSpeedDialogState.Editing
            }
        },
        onCloseFlowCloudSpeedDialog = {
            if (appState.flowCloudSpeedDialogState == FlowCloudSpeedDialogState.Editing) {
                appState.flowCloudSpeedDialogState = FlowCloudSpeedDialogState.Closing
            }
        },
        onFlowCloudSpeedDialogClosed = {
            if (appState.flowCloudSpeedDialogState == FlowCloudSpeedDialogState.Closing) {
                appState.flowCloudSpeedDialogState = FlowCloudSpeedDialogState.Idle
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
        onOpenArtistRootPage = onOpenArtistRootPage,
        onOpenListeningRecords = { initialTab ->
            appState.listeningRecordInitialTab = initialTab
            appState.secondaryPathSegments = emptyList()
            appState.selectedPlaylistId = null
            appState.selectedPlaylistTitle = null
            appState.selectedArtistName = null
            appState.secondaryPage = SecondaryPage.ListeningRecords
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
        onOnlineSongClick = onOnlineSongClick,
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
        onPersistentTrackQueueClick = onPersistentTrackQueueClick,
        onAddSongsToNext = onAddSongsToNext,
        onAppendSongsToQueue = onAppendSongsToQueue,
        onSetSongLiked = onSetSongLiked,
        onSetSongsLiked = onSetSongsLiked,
        onSetTracksLiked = onSetTracksLiked,
        onDeleteSongs = onDeleteSongs,
        onToggleSongLiked = onToggleSongLiked,
        onOpenSearch = onOpenSearch,
        onExitSearch = onExitSearch,
        onSearchQueryChange = onSearchQueryChange,
        onSearchScopeChange = onSearchScopeChange,
        onRefreshSearchSources = onRefreshSearchSources,
        onClearSearch = onClearSearch,
        onSearchFocusRequestConsumed = {
            appState.searchFocusRequest = 0
        },
        onSearchKeyboardDismissRequestConsumed = {
            appState.searchKeyboardDismissRequest = 0
        },
        onSearchInputFocusChange = { focused ->
            appState.searchInputFocused = focused
        },
        onSearchImeAction = {
            appState.searchInputFocused = false
        }
    )
}
