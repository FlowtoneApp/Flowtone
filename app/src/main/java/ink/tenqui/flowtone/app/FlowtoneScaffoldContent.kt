package ink.tenqui.flowtone.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.playlistAppearanceColorKeyForStableId
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.PageTransitionHost
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PlaylistCardVisualType
import ink.tenqui.flowtone.ui.components.playlistCardVisualTypeFor
import ink.tenqui.flowtone.ui.components.playlistDetailCloudPaletteFor
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController
import ink.tenqui.flowtone.ui.library.PlaylistBatchActions
import ink.tenqui.flowtone.ui.library.PlaylistSongSort
import ink.tenqui.flowtone.ui.screens.topLevelPageBackground
import ink.tenqui.flowtone.ui.theme.FlowtoneCloudPalette
import ink.tenqui.flowtone.ui.theme.LocalMainPagesCloudPalette
import ink.tenqui.flowtone.ui.theme.accentAt
import ink.tenqui.flowtone.ui.theme.monochromeFlowtoneCloudPalette

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun FlowtoneScaffoldContent(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    homeScrollState: ScrollState,
    topLevelPageCollapseProgress: TopLevelPageCollapseProgress,
    libraryPlaylistController: LibraryPlaylistController,
    playlistSongEntries: List<PlaylistSongEntry>,
    playlistBatchActions: PlaylistBatchActions,
    likedSongCount: Int,
    onDetailHeaderCollapseProgressStateChange: (State<Float>?) -> Unit,
    playlistSongSort: PlaylistSongSort,
    playlistSortPanelOpen: Boolean,
    onClosePlaylistSortPanel: () -> Unit,
    innerPadding: PaddingValues,
    topBarBackgroundHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val detailUsesSharedCloud = state.secondaryPage == SecondaryPage.Playlist ||
        state.secondaryPage == SecondaryPage.LocalLibrary
    val pagePosition = topLevelContinuousPagePosition(
        currentPage = state.pagerState.currentPage,
        currentPageOffsetFraction = state.pagerState.currentPageOffsetFraction
    )
    val mainPagesCloudPalette = LocalMainPagesCloudPalette.current
    val mainPageCloudAccent = mainPagesCloudPalette.accentAt(pagePosition)
    val cloudPlacement = topLevelCloudPlacementForPagePosition(pagePosition)
    val selectedPlaylistCard = remember(
        state.selectedPlaylistId,
        libraryPlaylistController.playlists
    ) {
        libraryPlaylistController.playlists.firstOrNull { playlist ->
            playlist.id == state.selectedPlaylistId
        }
    }
    val selectedPlaylistVisualType = when {
        state.selectedPlaylistId == LikedSongsPlaylistId -> PlaylistCardVisualType.LikedMusic
        selectedPlaylistCard != null -> playlistCardVisualTypeFor(selectedPlaylistCard)
        state.selectedPlaylistId != null -> PlaylistCardVisualType.UserPlaylist
        else -> PlaylistCardVisualType.Default
    }
    val selectedPlaylistAppearanceColorKey = selectedPlaylistCard?.appearanceColorKey
        ?: state.selectedPlaylistId
            ?.takeUnless { playlistId -> playlistId == LikedSongsPlaylistId }
            ?.let(::playlistAppearanceColorKeyForStableId)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val targetCloudPalette = remember(
        state.secondaryPage,
        state.selectedPlaylistId,
        selectedPlaylistVisualType,
        selectedPlaylistAppearanceColorKey,
        isDarkTheme,
        mainPageCloudAccent
    ) {
        when (state.secondaryPage) {
            SecondaryPage.LocalLibrary -> playlistDetailCloudPaletteFor(
                visualType = PlaylistCardVisualType.LocalLibrary,
                appearanceColorKey = null,
                isDarkTheme = isDarkTheme,
                fallbackAccent = mainPageCloudAccent
            )

            SecondaryPage.Playlist -> playlistDetailCloudPaletteFor(
                visualType = selectedPlaylistVisualType,
                appearanceColorKey = selectedPlaylistAppearanceColorKey,
                isDarkTheme = isDarkTheme,
                fallbackAccent = mainPageCloudAccent
            )

            else -> FlowtoneCloudPalette(
                primary = mainPageCloudAccent,
                secondary = mainPageCloudAccent,
                tertiary = mainPageCloudAccent
            )
        }
    }
    val detailCloudTransition = updateTransition(
        targetState = detailUsesSharedCloud,
        label = "SharedDetailCloudTransition"
    )
    val animatedPrimaryCloudColor by detailCloudTransition.animateColor(
        transitionSpec = {
            tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing)
        },
        label = "SharedCloudPrimaryColor"
    ) { usesDetailPalette ->
        if (usesDetailPalette) targetCloudPalette.primary else mainPageCloudAccent
    }
    val animatedSecondaryCloudColor by detailCloudTransition.animateColor(
        transitionSpec = {
            tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing)
        },
        label = "SharedCloudSecondaryColor"
    ) { usesDetailPalette ->
        if (usesDetailPalette) targetCloudPalette.secondary else mainPageCloudAccent
    }
    val animatedTertiaryCloudColor by detailCloudTransition.animateColor(
        transitionSpec = {
            tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing)
        },
        label = "SharedCloudTertiaryColor"
    ) { usesDetailPalette ->
        if (usesDetailPalette) targetCloudPalette.tertiary else mainPageCloudAccent
    }
    val animatedCloudPalette = if (
        detailCloudTransition.currentState || detailCloudTransition.targetState
    ) {
        FlowtoneCloudPalette(
            primary = animatedPrimaryCloudColor,
            secondary = animatedSecondaryCloudColor,
            tertiary = animatedTertiaryCloudColor
        )
    } else {
        monochromeFlowtoneCloudPalette(mainPageCloudAccent)
    }

    Box(modifier = modifier.fillMaxSize()) {
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(state.topBarScrollConnection)
        ) {
            PageTransitionHost(
                targetState = if (state.secondaryPage == null) {
                    FlowtoneScaffoldPage.MainTabs
                } else {
                    FlowtoneScaffoldPage.Secondary(state.secondaryPage)
                },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val pageScope = this
                val pageUsesSharedCloud = when (page) {
                    FlowtoneScaffoldPage.MainTabs -> true
                    is FlowtoneScaffoldPage.Secondary ->
                        page.page == SecondaryPage.Playlist ||
                            page.page == SecondaryPage.LocalLibrary
                }
                val pageCloudAlpha = if (pageUsesSharedCloud) 1f else 0f
                val pageSecondaryBackgroundAlpha = if (pageUsesSharedCloud) 0f else 1f
                val pageTopBarSurfaceAlpha = when {
                    // FlowtoneAppEffects resets the shared scroll offset after navigation.
                    // An incoming slot must not briefly inherit the outgoing page's alpha
                    // while that reset animates; every destination starts with its normal
                    // transparent scroll surface and PageTransitionHost reveals it as one layer.
                    pageScope.phase == PageTransitionPhase.Incoming -> 0f

                    page is FlowtoneScaffoldPage.MainTabs && state.searchActive -> 0f
                    else -> state.topBarBackgroundAlpha
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.rootPage == FlowtoneRootPage.MainTabs) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(pageScope.backgroundModifier())
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                        .topLevelPageBackground(
                                        cloudPalette = animatedCloudPalette,
                                        cloudAlpha = pageCloudAlpha,
                                        cloudPlacement = cloudPlacement
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.background.copy(
                                            alpha = pageSecondaryBackgroundAlpha
                                            )
                                        )
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(bottom = state.miniPlayerContentBottomPadding)
                    ) {
                    when (page) {
                        FlowtoneScaffoldPage.MainTabs -> Box(modifier = Modifier.fillMaxSize()) {
                        TopLevelPagerContent(
                            pagerState = state.pagerState,
                            uiState = state.uiState,
                            homeScrollState = homeScrollState,
                            libraryPlaylistController = libraryPlaylistController,
                            playlistSongEntries = playlistSongEntries,
                            permissionDenied = state.permissionDenied,
                            showSwipeHint = state.showSwipeHint,
                            pageScope = pageScope,
                            userScrollEnabled = !state.searchActive &&
                                libraryPlaylistController.editingPlaylistId == null,
                            onRequestPermission = callbacks.onRequestPermission,
                            onSongClick = callbacks.onSongClick,
                            onOpenSettings = callbacks.onOpenSettings,
                            onOpenAbout = callbacks.onOpenAbout,
                            onOpenLocalLibrary = callbacks.onOpenLocalLibrary,
                            onOpenPlaylist = callbacks.onOpenPlaylist,
                            onOpenListeningRecords = callbacks.onOpenListeningRecords,
                            likedSongCount = likedSongCount,
                            flowCloudSpeed = state.flowCloudSpeed,
                            modifier = Modifier.fillMaxSize()
                        )
                        TopLevelSharedPageHeader(
                            pagerState = state.pagerState,
                            collapseProgress = topLevelPageCollapseProgress,
                            pageScope = pageScope,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 21.dp, top = 48.dp, end = 20.dp)
                        )
                        }

                        is FlowtoneScaffoldPage.Secondary -> SecondaryPageHost(
                        secondaryPage = page.page,
                        pageScope = pageScope,
                        appPreferences = state.appPreferences,
                    themeMode = state.themeMode,
                    onThemeModeChange = callbacks.onThemeModeChange,
                    disablePausedArtworkTilt = state.disablePausedArtworkTilt,
                    onDisablePausedArtworkTiltChange = callbacks.onDisablePausedArtworkTiltChange,
                    strictProgressBar = state.strictProgressBar,
                    onStrictProgressBarChange = callbacks.onStrictProgressBarChange,
                    allowScreenOffOnLyricsPage = state.allowScreenOffOnLyricsPage,
                    onAllowScreenOffOnLyricsPageChange =
                        callbacks.onAllowScreenOffOnLyricsPageChange,
                    hideSecondaryBackButton = state.hideSecondaryBackButton,
                    onHideSecondaryBackButtonChange = callbacks.onHideSecondaryBackButtonChange,
                    resumePlaybackAfterCall = state.resumePlaybackAfterCall,
                    onResumePlaybackAfterCallChange = callbacks.onResumePlaybackAfterCallChange,
                    allowFullscreenFromCollapsed = state.allowFullscreenFromCollapsed,
                    onAllowFullscreenFromCollapsedChange =
                        callbacks.onAllowFullscreenFromCollapsedChange,
                    openExpandedMiniPlayerOnMediaClick =
                        state.openExpandedMiniPlayerOnMediaClick,
                    onOpenExpandedMiniPlayerOnMediaClickChange =
                        callbacks.onOpenExpandedMiniPlayerOnMediaClickChange,
                    preloadSongMetadataCount = state.preloadSongMetadataCount,
                    onPreloadSongMetadataCountChange =
                        callbacks.onPreloadSongMetadataCountChange,
                    preloadLyricsCount = state.preloadLyricsCount,
                    onPreloadLyricsCountChange = callbacks.onPreloadLyricsCountChange,
                    songRecordThresholdSeconds = state.songRecordThresholdSeconds,
                    onOpenSongRecordThresholdDialog = callbacks.onOpenSongRecordThresholdDialog,
                    flowCloudSpeed = state.flowCloudSpeed,
                    onOpenFlowCloudSpeedDialog = callbacks.onOpenFlowCloudSpeedDialog,
                    darkFlowCloudOverlayEnabled = state.darkFlowCloudOverlayEnabled,
                    onDarkFlowCloudOverlayChange = callbacks.onDarkFlowCloudOverlayChange,
                    lyricsBackgroundStyle = state.lyricsBackgroundStyle,
                    onLyricsBackgroundStyleChange = callbacks.onLyricsBackgroundStyleChange,
                    uiState = state.uiState,
                    currentSong = state.playerUiState.currentSong,
                    selectedPlaylistId = state.selectedPlaylistId,
                    selectedPlaylistTitle = state.selectedPlaylistTitle,
                    selectedArtistName = state.selectedArtistName,
                    listeningRecordInitialTab = state.listeningRecordInitialTab,
                    likedSongKeys = state.likedSongKeys,
                    playlistSongEntries = playlistSongEntries,
                    playlistBatchActions = playlistBatchActions,
                    onDetailHeaderCollapseProgressStateChange =
                        onDetailHeaderCollapseProgressStateChange,
                    playlistSongSort = playlistSongSort,
                    playlistSortPanelOpen = playlistSortPanelOpen,
                    onClosePlaylistSortPanel = onClosePlaylistSortPanel,
                    permissionDenied = state.permissionDenied,
                    onRequestPermission = callbacks.onRequestPermission,
                    onSongClick = callbacks.onSongClick,
                    onPlaylistSongClick = callbacks.onPlaylistSongClick,
                    onPersistentTrackQueueClick = callbacks.onPersistentTrackQueueClick,
                    onCloseSecondaryPage = callbacks.onCloseSecondaryPage,
                    onSettingsBackActionChange = callbacks.settingsBackActionChange,
                    onSettingsPathSegmentsChange = callbacks.onSettingsPathSegmentsChange,
                    onOpenSource = callbacks.onOpenSource,
                    onOpenSourceBack = callbacks.onOpenSourceBack,
                    onOpenSourceBackActionChange = callbacks.openSourceBackActionChange,
                    onOpenSourcePathSegmentsChange = callbacks.onOpenSourcePathSegmentsChange,
                    modifier = Modifier.fillMaxSize()
                        )
                        }
                    }
                    }

                    // The top-bar color is part of this page visual layer. The
                    // controls remain in ScaffoldTopLayer, but no second page
                    // clock is needed to synchronize their background.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topBarBackgroundHeight)
                            .then(pageScope.backgroundModifier())
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer.copy(
                                    alpha = pageTopBarSurfaceAlpha
                                )
                            )
                    )
                }
            }
    }
}

private sealed interface FlowtoneScaffoldPage {
    data object MainTabs : FlowtoneScaffoldPage
    data class Secondary(val page: SecondaryPage) : FlowtoneScaffoldPage
}

private fun topLevelContinuousPagePosition(
    currentPage: Int,
    currentPageOffsetFraction: Float
): Float {
    return (currentPage + currentPageOffsetFraction).coerceIn(
        minimumValue = 0f,
        maximumValue = TopLevelPage.entries.lastIndex.toFloat()
    )
}
