package ink.tenqui.flowtone.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.LocalPlaylistCreatorName
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.playlistAppearanceColorKeyForStableId
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.PageTransitionHost
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.PlaylistCardVisualType
import ink.tenqui.flowtone.ui.components.playlistCardVisualTypeFor
import ink.tenqui.flowtone.ui.components.playlistDetailCloudPaletteFor
import ink.tenqui.flowtone.ui.components.rememberAlbumArtworkCloudPalette
import ink.tenqui.flowtone.ui.library.LibraryPlaylistController
import ink.tenqui.flowtone.ui.library.PlaylistBatchActions
import ink.tenqui.flowtone.ui.library.PlaylistDetailMetadata
import ink.tenqui.flowtone.ui.library.PlaylistSongSort
import ink.tenqui.flowtone.ui.search.GlobalSearchContent
import ink.tenqui.flowtone.ui.components.topLevelPageBackground
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
    descriptionBlurRadius: Dp,
    onUpdatePlaylistDescription: (String, String?) -> Unit,
    onPlaylistBackActionChange: ((() -> Unit)?) -> Unit,
    onDetailHeaderCollapseProgressStateChange: (State<Float>?) -> Unit,
    playlistSongSort: PlaylistSongSort,
    playlistSortPanelOpen: Boolean,
    onClosePlaylistSortPanel: () -> Unit,
    innerPadding: PaddingValues,
    topBarBackgroundHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val secondaryPageStateHolder = rememberSaveableStateHolder()
    val detailUsesSharedCloud = state.secondaryPage == SecondaryPage.Playlist ||
        state.secondaryPage == SecondaryPage.Album ||
        state.secondaryPage == SecondaryPage.LocalLibrary
    val pagePosition = topLevelContinuousPagePosition(
        currentPage = state.pagerState.currentPage,
        currentPageOffsetFraction = state.pagerState.currentPageOffsetFraction
    )
    val mainPagesCloudPalette = LocalMainPagesCloudPalette.current
    val mainPageCloudAccent = mainPagesCloudPalette.accentAt(pagePosition)
    val cloudPlacement = topLevelCloudPlacementForPagePosition(pagePosition)
    val selectedPlaylistDestination = state.secondaryDestination as? SecondaryDestination.Playlist
    val selectedAlbumDestination = state.secondaryDestination as? SecondaryDestination.Album
    val selectedPlaylistCard = remember(
        selectedPlaylistDestination?.playlistId,
        libraryPlaylistController.playlists
    ) {
        libraryPlaylistController.playlists.firstOrNull { playlist ->
            playlist.id == selectedPlaylistDestination?.playlistId
        }
    }
    val selectedPlaylistVisualType = when {
        selectedPlaylistDestination?.playlistId == LikedSongsPlaylistId -> PlaylistCardVisualType.LikedMusic
        selectedPlaylistCard != null -> playlistCardVisualTypeFor(selectedPlaylistCard)
        selectedPlaylistDestination != null -> PlaylistCardVisualType.UserPlaylist
        else -> PlaylistCardVisualType.Default
    }
    val selectedPlaylistAppearanceColorKey = selectedPlaylistCard?.appearanceColorKey
        ?: selectedPlaylistDestination?.playlistId
            ?.takeUnless { playlistId -> playlistId == LikedSongsPlaylistId }
            ?.let(::playlistAppearanceColorKeyForStableId)
    val selectedAlbum = remember(selectedAlbumDestination?.albumId, state.uiState.albums) {
        state.uiState.albums.firstOrNull { album -> album.id == selectedAlbumDestination?.albumId }
    }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val defaultAlbumCloudPalette = remember(isDarkTheme, mainPageCloudAccent) {
        playlistDetailCloudPaletteFor(
            visualType = PlaylistCardVisualType.Default,
            appearanceColorKey = null,
            isDarkTheme = isDarkTheme,
            fallbackAccent = mainPageCloudAccent
        )
    }
    val albumArtworkCloudPalette = rememberAlbumArtworkCloudPalette(
        artworkUri = selectedAlbum?.artworkUri,
        fallbackPalette = defaultAlbumCloudPalette,
        isDarkTheme = isDarkTheme
    )
    val targetCloudPalette = remember(
        state.secondaryPage,
        selectedPlaylistDestination,
        selectedPlaylistVisualType,
        selectedPlaylistAppearanceColorKey,
        albumArtworkCloudPalette,
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

            SecondaryPage.Album -> albumArtworkCloudPalette

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
    val playlistDetailDestination = selectedPlaylistDestination?.let { selectedDestination ->
        val playlistId = selectedDestination.playlistId
        val metadata = if (playlistId == LikedSongsPlaylistId) {
            PlaylistDetailMetadata(
                title = selectedDestination.title,
                creatorName = LocalPlaylistCreatorName
            )
        } else {
            PlaylistDetailMetadata(
                title = selectedDestination.title.ifBlank {
                    selectedPlaylistCard?.title ?: SecondaryPage.Playlist.title
                },
                creatorName = selectedPlaylistCard?.creatorName ?: LocalPlaylistCreatorName,
                description = selectedPlaylistCard?.description,
                customArtworkUri = selectedPlaylistCard?.customArtworkUri,
                isDescriptionEditable = false
            )
        }
        remember(playlistId) {
            PlaylistDetailDestination(
                playlistId = playlistId,
                initialMetadata = metadata
            )
        }.also { destination ->
            SideEffect {
                destination.updateMetadata(metadata)
            }
        }
    }
    val albumDetailDestination = selectedAlbumDestination?.let { selectedDestination ->
        val albumId = selectedDestination.albumId
        remember(albumId) {
            AlbumDetailDestination(
                albumId = albumId,
                initialAlbum = selectedAlbum
            )
        }.also { destination ->
            SideEffect {
                destination.updateAlbum(selectedAlbum)
            }
        }
    }
    val targetPage = state.secondaryDestination?.let { destination ->
        FlowtoneScaffoldPage.Secondary(
            destination = destination,
            playlistDetailDestination = playlistDetailDestination,
            albumDetailDestination = albumDetailDestination
        )
    } ?: FlowtoneScaffoldPage.MainTabs

    Box(modifier = modifier.fillMaxSize()) {
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(state.topBarScrollConnection)
        ) {
            PageTransitionHost(
                targetState = targetPage,
                modifier = Modifier.fillMaxSize(),
                reversibleTransitionKey = ::flowtoneReversibleTransitionKey,
                isReversibleTransition = ::isFlowtoneCollectionDetailTransition
            ) { page ->
                val pageScope = this
                val pageUsesSharedCloud = when (page) {
                    FlowtoneScaffoldPage.MainTabs -> true
                    is FlowtoneScaffoldPage.Secondary ->
                        page.destination.page == SecondaryPage.Playlist ||
                            page.destination.page == SecondaryPage.Album ||
                            page.destination.page == SecondaryPage.LocalLibrary
                }
                val pageCloudAlpha = if (pageUsesSharedCloud) 1f else 0f
                val pageSecondaryBackgroundAlpha = if (pageUsesSharedCloud) 0f else 1f
                val pageTopBarSurfaceAlpha = when {
                    // FlowtoneAppEffects resets the shared scroll offset after navigation.
                    // An incoming slot must not briefly inherit the outgoing page's alpha
                    // while that reset animates; every destination starts with its normal
                    // transparent scroll surface and PageTransitionHost reveals it as one layer.
                    pageScope.phase == PageTransitionPhase.Incoming -> 0f

                    pageUsesSharedCloud -> 0f

                    page is FlowtoneScaffoldPage.MainTabs && state.searchActive -> 0f
                    else -> state.topBarBackgroundAlpha
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(pageScope.backgroundModifier())
                                .blur(descriptionBlurRadius)
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
                    when (page) {
                        FlowtoneScaffoldPage.MainTabs -> PageTransitionHost(
                            targetState = mainTabsContentMode(state.searchActive),
                            parentScope = pageScope,
                            modifier = Modifier.fillMaxSize()
                        ) { mode ->
                            val mainModeScope = this
                            when (mode) {
                                MainTabsContentMode.Normal -> Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .then(mainModeScope.backgroundModifier())
                                                .topLevelPageBackground(
                                                    cloudPalette = animatedCloudPalette,
                                                    cloudPlacement = cloudPlacement
                                                )
                                        )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                            .padding(
                                                bottom = state.miniPlayerContentBottomPadding
                                            )
                                    ) {
                                        TopLevelPagerContent(
                                            pagerState = state.pagerState,
                                            uiState = state.uiState,
                                            homeScrollState = homeScrollState,
                                            libraryPlaylistController =
                                                libraryPlaylistController,
                                            playlistSongEntries = playlistSongEntries,
                                            permissionDenied = state.permissionDenied,
                                            showSwipeHint = state.showSwipeHint,
                                            pageScope = mainModeScope,
                                            userScrollEnabled =
                                                libraryPlaylistController.editingPlaylistId == null,
                                            onRequestPermission = callbacks.onRequestPermission,
                                            onSongClick = callbacks.onSongClick,
                                            onOpenSettings = callbacks.onOpenSettings,
                                            onOpenAbout = callbacks.onOpenAbout,
                                            onOpenLocalLibrary = callbacks.onOpenLocalLibrary,
                                            onOpenPlaylist = callbacks.onOpenPlaylist,
                                            onOpenListeningRecords =
                                                callbacks.onOpenListeningRecords,
                                            likedSongCount = likedSongCount,
                                            flowCloudSpeed = state.flowCloudSpeed,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        TopLevelSharedPageHeader(
                                            pagerState = state.pagerState,
                                            collapseProgress =
                                                topLevelPageCollapseProgress,
                                            pageScope = mainModeScope,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = 21.dp,
                                                    top = 48.dp,
                                                    end = 20.dp
                                                )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(topBarBackgroundHeight)
                                            .then(mainModeScope.backgroundModifier())
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainer.copy(
                                                    alpha = pageTopBarSurfaceAlpha
                                                )
                                            )
                                            .blur(descriptionBlurRadius)
                                    )
                                }

                                MainTabsContentMode.Search -> FlowtoneMainTabsSearchContent(
                                    state = state,
                                    callbacks = callbacks,
                                    pageTransition = mainModeScope,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        is FlowtoneScaffoldPage.Secondary -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(bottom = state.miniPlayerContentBottomPadding)
                        ) {
                            secondaryPageStateHolder.SaveableStateProvider(
                                key = page.destination.uiStateKey()
                            ) {
                                SecondaryPageHost(
                        destination = page.destination,
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
                    isPlaying = state.playerUiState.isPlaying,
                    playlistDetailDestination = page.playlistDetailDestination,
                    albumDetailDestination = page.albumDetailDestination,
                    onUpdatePlaylistDescription = onUpdatePlaylistDescription,
                    onPlaylistBackActionChange = onPlaylistBackActionChange,
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
                    onOpenAlbum = callbacks.onOpenAlbum,
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
                    if (page is FlowtoneScaffoldPage.Secondary) {
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
                                .blur(descriptionBlurRadius)
                        )
                    }
                }
            }
    }
}
@Composable
private fun FlowtoneMainTabsSearchContent(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    pageTransition: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val miniPlayerSpaceProgress by animateFloatAsState(
        targetValue = if (
            state.searchActive &&
            state.searchKeyboardVisible &&
            state.playerUiState.hasCurrentSong
        ) {
            0f
        } else {
            1f
        },
        animationSpec = tween(
            durationMillis = 360,
            easing = FastOutSlowInEasing
        ),
        label = "SearchMiniPlayerBottomSpaceProgress"
    )
    GlobalSearchContent(
        searchUiState = state.searchUiState,
        currentSong = state.playerUiState.currentSong,
        listState = state.searchListState,
        onSongClick = { songs, index ->
            callbacks.onPlaylistSongClick(songs, index, PlaybackSource.Search)
        },
        onOnlineSongClick = callbacks.onOnlineSongClick,
        pendingTrackIdentityKey = state.uiState.pendingPlayback?.track?.identityKey,
        onArtistClick = { artist ->
            callbacks.onOpenArtist(artist.name)
        },
        onAlbumClick = callbacks.onOpenAlbum,
        onExitSearch = callbacks.onExitSearch,
        onQueryChange = callbacks.onSearchQueryChange,
        onScopeChange = callbacks.onSearchScopeChange,
        onCategoryChange = callbacks.onSearchCategoryChange,
        onLoadMore = callbacks.onLoadMoreSearchResults,
        bottomContentPadding =
            state.miniPlayerContentBottomPadding * miniPlayerSpaceProgress,
        interactionsEnabled = true,
        reentryProgress = 1f,
        pageTransition = pageTransition,
        modifier = modifier
    )
}

internal enum class MainTabsContentMode {
    Normal,
    Search
}

internal fun mainTabsContentMode(searchActive: Boolean): MainTabsContentMode {
    return if (searchActive) MainTabsContentMode.Search else MainTabsContentMode.Normal
}

private sealed interface FlowtoneScaffoldPage {
    data object MainTabs : FlowtoneScaffoldPage
    data class Secondary(
        val destination: SecondaryDestination,
        val playlistDetailDestination: PlaylistDetailDestination? = null,
        val albumDetailDestination: AlbumDetailDestination? = null
    ) : FlowtoneScaffoldPage
}

private sealed interface FlowtoneReversibleTransitionKey {
    data object MainTabs : FlowtoneReversibleTransitionKey
    data class Playlist(val playlistId: String) : FlowtoneReversibleTransitionKey
    data class Album(val albumId: Long) : FlowtoneReversibleTransitionKey
}

private fun flowtoneReversibleTransitionKey(
    page: FlowtoneScaffoldPage
): FlowtoneReversibleTransitionKey? {
    return when (page) {
        FlowtoneScaffoldPage.MainTabs -> FlowtoneReversibleTransitionKey.MainTabs
        is FlowtoneScaffoldPage.Secondary -> {
            if (page.destination.page == SecondaryPage.Playlist) {
                page.playlistDetailDestination?.playlistId?.let { playlistId ->
                    FlowtoneReversibleTransitionKey.Playlist(playlistId)
                }
            } else if (page.destination.page == SecondaryPage.Album) {
                page.albumDetailDestination?.albumId?.let { albumId ->
                    FlowtoneReversibleTransitionKey.Album(albumId)
                }
            } else {
                null
            }
        }
    }
}

private fun isFlowtoneCollectionDetailTransition(
    first: FlowtoneScaffoldPage,
    second: FlowtoneScaffoldPage
): Boolean {
    return (first == FlowtoneScaffoldPage.MainTabs && second.isCollectionDetailPage()) ||
        (second == FlowtoneScaffoldPage.MainTabs && first.isCollectionDetailPage())
}

private fun FlowtoneScaffoldPage.isCollectionDetailPage(): Boolean {
    return this is FlowtoneScaffoldPage.Secondary &&
        when (destination.page) {
            SecondaryPage.Playlist -> playlistDetailDestination?.playlistId != null
            SecondaryPage.Album -> albumDetailDestination != null
            else -> false
        }
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
