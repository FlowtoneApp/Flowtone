package ink.tenqui.flowtone.app

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.library.ArtistPage
import ink.tenqui.flowtone.ui.library.AlbumDetailScreen
import ink.tenqui.flowtone.ui.library.LikedSongsPlaylistScreen
import ink.tenqui.flowtone.ui.library.LocalLibraryScreen
import ink.tenqui.flowtone.ui.library.PlaylistDetailScreen
import ink.tenqui.flowtone.ui.library.PlaylistDetailMetadata
import ink.tenqui.flowtone.ui.library.PlaylistBatchActions
import ink.tenqui.flowtone.ui.screens.AboutScreen
import ink.tenqui.flowtone.ui.screens.ListeningRecordTab
import ink.tenqui.flowtone.ui.screens.ListeningRecordsScreen
import ink.tenqui.flowtone.ui.screens.OpenSourceScreen
import ink.tenqui.flowtone.ui.screens.SettingsScreen
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.ui.player.lyrics.LyricsBackgroundStyle
import ink.tenqui.flowtone.viewmodel.MusicUiState
import ink.tenqui.flowtone.ui.library.PlaylistSongSort

internal class PlaylistDetailDestination(
    val playlistId: String?,
    initialMetadata: PlaylistDetailMetadata
) {
    var metadata by mutableStateOf(initialMetadata)
        private set

    fun updateMetadata(updatedMetadata: PlaylistDetailMetadata) {
        if (metadata != updatedMetadata) {
            metadata = updatedMetadata
        }
    }

    fun updateDescription(description: String?): Boolean {
        if (metadata.description == description) return false
        updateMetadata(metadata.copy(description = description))
        return true
    }

    override fun equals(other: Any?): Boolean {
        return other is PlaylistDetailDestination && playlistId == other.playlistId
    }

    override fun hashCode(): Int = playlistId?.hashCode() ?: 0
}

internal class AlbumDetailDestination(
    val albumId: Long,
    initialAlbum: LocalAlbum?
) {
    var album by mutableStateOf(initialAlbum)
        private set

    fun updateAlbum(updatedAlbum: LocalAlbum?) {
        if (updatedAlbum != null && album != updatedAlbum) {
            album = updatedAlbum
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is AlbumDetailDestination && albumId == other.albumId
    }

    override fun hashCode(): Int = albumId.hashCode()
}

@Composable
internal fun SecondaryPageHost(
    destination: SecondaryDestination,
    pageScope: PageTransitionScope,
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    disablePausedArtworkTilt: Boolean,
    onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    strictProgressBar: Boolean,
    onStrictProgressBarChange: (Boolean) -> Unit,
    allowScreenOffOnLyricsPage: Boolean,
    onAllowScreenOffOnLyricsPageChange: (Boolean) -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    openExpandedMiniPlayerOnMediaClick: Boolean,
    onOpenExpandedMiniPlayerOnMediaClickChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    preloadLyricsCount: Int,
    onPreloadLyricsCountChange: (Int) -> Unit,
    songRecordThresholdSeconds: Int,
    onOpenSongRecordThresholdDialog: () -> Unit,
    flowCloudSpeed: Float,
    onOpenFlowCloudSpeedDialog: () -> Unit,
    darkFlowCloudOverlayEnabled: Boolean,
    onDarkFlowCloudOverlayChange: (Boolean) -> Unit,
    lyricsBackgroundStyle: LyricsBackgroundStyle,
    onLyricsBackgroundStyleChange: (LyricsBackgroundStyle) -> Unit,
    uiState: MusicUiState,
    currentSong: Song?,
    isPlaying: Boolean,
    playlistDetailDestination: PlaylistDetailDestination?,
    albumDetailDestination: AlbumDetailDestination?,
    listeningRecordInitialTab: ListeningRecordTab,
    likedSongKeys: List<String>,
    playlistSongEntries: List<PlaylistSongEntry>,
    playlistBatchActions: PlaylistBatchActions,
    onUpdatePlaylistDescription: (String, String?) -> Unit,
    onPlaylistBackActionChange: ((() -> Unit)?) -> Unit,
    onDetailHeaderCollapseProgressStateChange: (State<Float>?) -> Unit,
    playlistSongSort: PlaylistSongSort,
    playlistSortPanelOpen: Boolean,
    onClosePlaylistSortPanel: () -> Unit,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlaylistSongClick: (List<Song>, Int, PlaybackSource) -> Unit,
    onPersistentTrackQueueClick: (List<PersistentTrack>, Int, PlaybackSource) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onCloseSecondaryPage: () -> Unit,
    onSettingsBackActionChange: ((() -> Unit)?) -> Unit,
    onSettingsPathSegmentsChange: (List<String>) -> Unit,
    onOpenSource: () -> Unit,
    onOpenSourceBack: () -> Unit,
    onOpenSourceBackActionChange: ((() -> Unit)?) -> Unit,
    onOpenSourcePathSegmentsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var songSelectionActive by remember { mutableStateOf(false) }
    val activeBatchActions = playlistBatchActions.copy(
        onSelectionModeChange = { active ->
            songSelectionActive = active
            playlistBatchActions.onSelectionModeChange(active)
        }
    )
    fun closeSelectionOrPage() {
        if (playlistSortPanelOpen) {
            onClosePlaylistSortPanel()
        } else if (songSelectionActive) {
            playlistBatchActions.onRequestClearSelection()
        } else {
            onCloseSecondaryPage()
        }
    }
    val secondaryPage = destination.page

    fun elementModifier(index: Int): Modifier {
        return pageScope.elementModifier(index)
    }

    fun playlistItemModifier(
        pageProgress: Float,
        order: Int,
        orderCount: Int
    ): Modifier {
        return pageScope.elementModifierAt(pageProgress, order, orderCount)
    }

    fun viewportItemModifier(order: Int, orderCount: Int): Modifier {
        return pageScope.elementModifier(order, orderCount)
    }
    Box(modifier = modifier) {
        when (secondaryPage) {
            SecondaryPage.Settings -> SettingsScreen(
                appPreferences = appPreferences,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                disablePausedArtworkTilt = disablePausedArtworkTilt,
                onDisablePausedArtworkTiltChange = onDisablePausedArtworkTiltChange,
                strictProgressBar = strictProgressBar,
                onStrictProgressBarChange = onStrictProgressBarChange,
                allowScreenOffOnLyricsPage = allowScreenOffOnLyricsPage,
                onAllowScreenOffOnLyricsPageChange =
                    onAllowScreenOffOnLyricsPageChange,
                onBack = onCloseSecondaryPage,
                onBackActionChange = onSettingsBackActionChange,
                onPathSegmentsChange = onSettingsPathSegmentsChange,
                hideSecondaryBackButton = hideSecondaryBackButton,
                onHideSecondaryBackButtonChange = onHideSecondaryBackButtonChange,
                resumePlaybackAfterCall = resumePlaybackAfterCall,
                onResumePlaybackAfterCallChange = onResumePlaybackAfterCallChange,
                allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                onAllowFullscreenFromCollapsedChange = onAllowFullscreenFromCollapsedChange,
                pageScope = pageScope,
                openExpandedMiniPlayerOnMediaClick = openExpandedMiniPlayerOnMediaClick,
                onOpenExpandedMiniPlayerOnMediaClickChange =
                    onOpenExpandedMiniPlayerOnMediaClickChange,
                preloadSongMetadataCount = preloadSongMetadataCount,
                onPreloadSongMetadataCountChange = onPreloadSongMetadataCountChange,
                preloadLyricsCount = preloadLyricsCount,
                onPreloadLyricsCountChange = onPreloadLyricsCountChange,
                songRecordThresholdSeconds = songRecordThresholdSeconds,
                onOpenSongRecordThresholdDialog = onOpenSongRecordThresholdDialog,
                flowCloudSpeed = flowCloudSpeed,
                onOpenFlowCloudSpeedDialog = onOpenFlowCloudSpeedDialog,
                darkFlowCloudOverlayEnabled = darkFlowCloudOverlayEnabled,
                onDarkFlowCloudOverlayChange = onDarkFlowCloudOverlayChange,
                lyricsBackgroundStyle = lyricsBackgroundStyle,
                onLyricsBackgroundStyleChange = onLyricsBackgroundStyleChange,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.About -> AboutScreen(
                onOpenSource = onOpenSource,
                onBack = onCloseSecondaryPage,
                pageScope = pageScope,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.OpenSource -> OpenSourceScreen(
                onBack = onOpenSourceBack,
                onBackActionChange = onOpenSourceBackActionChange,
                onPathSegmentsChange = onOpenSourcePathSegmentsChange,
                pageScope = pageScope,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.LocalLibrary -> LocalLibraryScreen(
                title = SecondaryPage.LocalLibrary.title,
                uiState = uiState,
                currentSong = currentSong,
                songSort = playlistSongSort,
                permissionDenied = permissionDenied,
                onRequestPermission = onRequestPermission,
                onSongClick = onSongClick,
                batchActions = activeBatchActions,
                showContentHeader = false,
                pageTransition = pageScope,
                itemModifier = ::playlistItemModifier,
                onCollapseProgressStateChange =
                    onDetailHeaderCollapseProgressStateChange,
                headerModifier = elementModifier(0),
                modifier = Modifier
                    .fillMaxSize()
                    .rightSwipeBackGesture(::closeSelectionOrPage)
            )

            SecondaryPage.Playlist -> {
                val destination = playlistDetailDestination ?: return@Box
                if (destination.playlistId == LikedSongsPlaylistId) {
                    LikedSongsPlaylistScreen(
                        metadata = destination.metadata,
                        allSongs = uiState.songs,
                        likedTracks = uiState.likedTracks,
                        currentSong = currentSong,
                        pendingTrackIdentityKey = uiState.pendingPlayback?.track?.identityKey,
                        songSort = playlistSongSort,
                        onSongClick = { tracks, index ->
                            onPersistentTrackQueueClick(
                                tracks,
                                index,
                                PlaybackSource.LikedSongs
                            )
                        },
                        playbackErrorMessage = uiState.trackPlaybackErrorMessage,
                        playbackErrorEventId = uiState.trackPlaybackErrorEventId,
                        batchActions = activeBatchActions,
                        pageTransition = pageScope,
                        itemModifier = ::playlistItemModifier,
                        onCollapseProgressStateChange =
                            onDetailHeaderCollapseProgressStateChange,
                        headerModifier = elementModifier(0),
                        modifier = Modifier
                            .fillMaxSize()
                            .rightSwipeBackGesture(::closeSelectionOrPage)
                    )
                } else {
                    PlaylistDetailScreen(
                        playlistId = destination.playlistId,
                        metadata = destination.metadata,
                        allSongs = uiState.songs,
                        playlistSongEntries = playlistSongEntries,
                        currentSong = currentSong,
                        pendingTrackIdentityKey = uiState.pendingPlayback?.track?.identityKey,
                        songSort = playlistSongSort,
                        onSongClick = { tracks, index ->
                            onPersistentTrackQueueClick(
                                tracks,
                                index,
                                PlaybackSource.userPlaylist(
                                    playlistId = destination.playlistId.orEmpty(),
                                    displayName = destination.metadata.title
                                )
                            )
                        },
                        playbackErrorMessage = uiState.trackPlaybackErrorMessage,
                        playbackErrorEventId = uiState.trackPlaybackErrorEventId,
                        batchActions = activeBatchActions,
                        pageTransition = pageScope,
                        itemModifier = ::playlistItemModifier,
                        onCollapseProgressStateChange =
                            onDetailHeaderCollapseProgressStateChange,
                        headerModifier = elementModifier(0),
                        suppressEmptyState = secondaryPage != SecondaryPage.Playlist,
                        modifier = Modifier
                            .fillMaxSize()
                            .rightSwipeBackGesture(::closeSelectionOrPage)
                    )
                }
            }

            SecondaryPage.Album -> {
                val destination = albumDetailDestination ?: return@Box
                AlbumDetailScreen(
                    albumId = destination.albumId,
                    album = destination.album,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    pendingTrackIdentityKey = uiState.pendingPlayback?.track?.identityKey,
                    songSort = playlistSongSort,
                    onSongClick = { songs, index ->
                        onPlaylistSongClick(
                            songs,
                            index,
                            PlaybackSource.album(
                                albumId = destination.albumId,
                                displayName = destination.album?.title.orEmpty()
                            )
                        )
                    },
                    playbackErrorMessage = uiState.trackPlaybackErrorMessage,
                    playbackErrorEventId = uiState.trackPlaybackErrorEventId,
                    batchActions = activeBatchActions,
                    pageTransition = pageScope,
                    itemModifier = ::playlistItemModifier,
                    onCollapseProgressStateChange =
                        onDetailHeaderCollapseProgressStateChange,
                    headerModifier = elementModifier(0),
                    modifier = Modifier
                        .fillMaxSize()
                        .rightSwipeBackGesture(::closeSelectionOrPage)
                )
            }

            SecondaryPage.Artist -> {
                val artist = destination as? SecondaryDestination.Artist ?: return@Box
                  ArtistPage(
                    artistName = artist.name,
                    allSongs = uiState.songs,
                    albums = uiState.albums,
                    currentSong = currentSong,
                    onBack = onCloseSecondaryPage,
                    onSongClick = { songs, index ->
                        onPlaylistSongClick(songs, index, PlaybackSource.artist(artist.name))
                    },
                    onOpenAlbum = onOpenAlbum,
                    itemModifier = { index -> pageScope.elementModifier(index, orderCount = 24) },
                    headerCardModifier = { index -> pageScope.elementModifier(index, orderCount = 4) },
                    modifier = Modifier
                        .fillMaxSize()
                        .rightSwipeBackGesture(onCloseSecondaryPage)
                )
            }

            SecondaryPage.ListeningRecords -> ListeningRecordsScreen(
                listeningStats = uiState.listeningStats,
                initialTab = listeningRecordInitialTab,
                onBack = onCloseSecondaryPage,
                itemModifier = ::elementModifier,
                modifier = Modifier
                    .fillMaxSize()
            )

        }
    }
}
