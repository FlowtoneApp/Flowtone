package ink.tenqui.flowtone.app

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.library.ArtistCloudBackdrop
import ink.tenqui.flowtone.ui.library.ArtistHeroBackgroundKind
import ink.tenqui.flowtone.ui.library.ArtistHeroStateStore
import ink.tenqui.flowtone.ui.library.ArtistScrollStateStore
import ink.tenqui.flowtone.ui.library.ArtistTopBarStateOwner
import ink.tenqui.flowtone.ui.library.ArtistTopBarStateStore
import ink.tenqui.flowtone.ui.library.PlaylistSelectionTopBarState

@Composable
internal fun FlowtoneScaffoldTopLayer(
    state: FlowtoneAppScaffoldState,
    callbacks: FlowtoneAppCallbacks,
    detailHeaderCollapseProgressState: State<Float>?,
    songSelectionState: PlaylistSelectionTopBarState?,
    onCloseSongSelection: () -> Unit,
    playlistBackAction: (() -> Unit)?,
    playlistSortProgress: Float,
    descriptionBlurRadius: Dp,
    onFullTitleRequest: (String) -> Unit,
    artistHeroStateStore: ArtistHeroStateStore,
    artistScrollStateStore: ArtistScrollStateStore,
    artistTopBarStateStore: ArtistTopBarStateStore
) {
    val artistRoute = artistTopBarRoute(state.secondaryEntries)
    val retainedArtistPresentation = remember {
        mutableStateOf<ArtistTopBarRetainedPresentation?>(null)
    }
    val liveArtistPresentation = artistRoute?.let { route ->
        val topBarStateOwner = artistTopBarStateStore.ownerFor(
            entryKey = route.artistEntryKey,
            initialScrollPosition = artistScrollStateStore.ownerFor(
                route.artistEntryKey
            ).position
        )
        val heroOwner = artistHeroStateStore.ownerFor(
            entryKey = route.artistEntryKey,
            initialAvatar = route.artist.identity.avatar,
            initialBackgroundKind = if (
                route.artist.identity.profileMetadata?.banner != null
            ) {
                ArtistHeroBackgroundKind.Banner
            } else {
                ArtistHeroBackgroundKind.Cloud
            }
        )
        ArtistTopBarRetainedPresentation(
            route = route,
            avatarImage = heroOwner.resolvedAvatar ?: route.artist.identity.avatar,
            backgroundKind = heroOwner.backgroundKind,
            cloudColor = heroOwner.resolvedCloudColor
                ?: MaterialTheme.colorScheme.primaryContainer,
            stateOwner = topBarStateOwner
        )
    }
    if (liveArtistPresentation != null) {
        SideEffect {
            retainedArtistPresentation.value = liveArtistPresentation
        }
    }
    val liveIdentityVisible = liveArtistPresentation?.let { presentation ->
        artistTopBarIdentityVisible(
            route = presentation.route,
            scrollIdentityVisible = presentation.stateOwner.visible
        )
    } ?: false
    val artistPresentationTarget = artistTopBarPresentationTarget(
        liveRoute = artistRoute,
        retainedRoute = retainedArtistPresentation.value?.route,
        liveIdentityVisible = liveIdentityVisible
    )

    if (artistRoute == null) {
        val titleVisible = state.secondaryPage != null
        val standardPathSegments = secondaryDestinationBreadcrumbs(
            current = state.secondaryDestination,
            previous = state.previousSecondaryDestination,
            nestedSegments = state.secondaryPathSegments
        )

        FlowtoneTopBar(
            selectedTopLevelPage = state.selectedTopLevelPage,
            pagerState = state.pagerState,
            secondaryPage = state.secondaryPage,
            additionalPathSegments = standardPathSegments,
            titleVisible = titleVisible,
            songSelectionState = songSelectionState,
            hideBackButton = state.hideSecondaryBackButton,
            searchActive = state.searchActive,
            searchQuery = state.searchUiState.queryText,
            searchColors = state.searchColors,
            searchFocusRequest = state.searchFocusRequest,
            searchKeyboardDismissRequest = state.searchKeyboardDismissRequest,
            onBack = if (songSelectionState != null) {
                onCloseSongSelection
            } else if (state.secondaryPage == SecondaryPage.Playlist) {
                playlistBackAction ?: callbacks.onCloseSecondaryPage
            } else {
                callbacks.onNavigateBack
            },
            onSearchClick = callbacks.onOpenSearch,
            onSearchQueryChange = callbacks.onSearchQueryChange,
            onExitSearch = callbacks.onExitSearch,
            onClearSearch = callbacks.onClearSearch,
            onSearchFocusRequestConsumed = callbacks.onSearchFocusRequestConsumed,
            onSearchKeyboardDismissRequestConsumed =
                callbacks.onSearchKeyboardDismissRequestConsumed,
            onSearchInputFocusChange = callbacks.onSearchInputFocusChange,
            onSearchImeAction = callbacks.onSearchImeAction,
            onFullTitleRequest = onFullTitleRequest,
            playlistSortProgress = playlistSortProgress,
            modifier = Modifier.blur(descriptionBlurRadius)
        )
    }

    artistPresentationTarget?.let { target ->
        val presentation = liveArtistPresentation ?: retainedArtistPresentation.value
            ?: return@let
        LaunchedEffect(
            presentation.stateOwner,
            target.targetVisible,
            target.exiting
        ) {
            presentation.stateOwner.presentationProgress.animateTo(
                targetValue = if (target.targetVisible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.ShortDurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
            if (target.exiting && artistTopBarExitSnapshotShouldRelease(
                    presentationProgress = presentation.stateOwner.presentationProgress.value
                )
            ) {
                retainedArtistPresentation.value = null
            }
        }
        key(presentation.route.artistEntryKey) {
            if (target.exiting) {
                ArtistTopBarExitBackdrop(
                    cloudColor = presentation.cloudColor,
                    presentationProgress = presentation.stateOwner.presentationProgress.value
                )
            }
            ArtistIdentityTopBar(
                artistName = presentation.route.artist.name,
                avatarImage = presentation.avatarImage,
                backgroundKind = presentation.backgroundKind,
                identityVisible = target.targetVisible,
                identityProgress = presentation.stateOwner.presentationProgress.value,
                pathSegments = presentation.route.pathSegments,
                onBack = if (target.targetVisible) {
                    {
                        val heroOwner = artistHeroStateStore.ownerFor(
                            entryKey = presentation.route.artistEntryKey,
                            initialAvatar = presentation.route.artist.identity.avatar,
                            initialBackgroundKind = presentation.backgroundKind
                        )
                        if (heroOwner.focusRequested) {
                            heroOwner.focusRequested = false
                        } else {
                            callbacks.onCloseSecondaryPage()
                        }
                    }
                } else {
                    {}
                },
                onFullTitleRequest = onFullTitleRequest
            )
        }
    }
}

internal data class ArtistTopBarRetainedPresentation(
    val route: ArtistTopBarRoute,
    val avatarImage: ExtensionImage?,
    val backgroundKind: ArtistHeroBackgroundKind,
    val cloudColor: Color,
    val stateOwner: ArtistTopBarStateOwner
)

internal data class ArtistTopBarPresentationTarget(
    val route: ArtistTopBarRoute,
    val targetVisible: Boolean,
    val exiting: Boolean
)

internal fun artistTopBarPresentationTarget(
    liveRoute: ArtistTopBarRoute?,
    retainedRoute: ArtistTopBarRoute?,
    liveIdentityVisible: Boolean
): ArtistTopBarPresentationTarget? {
    val route = liveRoute ?: retainedRoute ?: return null
    return ArtistTopBarPresentationTarget(
        route = route,
        targetVisible = liveRoute != null && liveIdentityVisible,
        exiting = liveRoute == null
    )
}

internal fun artistTopBarExitSnapshotShouldRelease(
    presentationProgress: Float
): Boolean = presentationProgress <= 0f

internal fun artistTopBarBackdropAlpha(
    presentationProgress: Float
): Float = presentationProgress.coerceIn(0f, 1f)

@Composable
private fun ArtistTopBarExitBackdrop(
    cloudColor: Color,
    presentationProgress: Float
) {
    val density = LocalDensity.current
    val topBarHeightPx = with(density) {
        WindowInsets.statusBars.getTop(this).toFloat() + FlowtoneTopBarContentHeight.toPx()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = artistTopBarBackdropAlpha(presentationProgress)
            }
            .drawWithContent {
                clipRect(bottom = topBarHeightPx) {
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        ArtistCloudBackdrop(accentColor = cloudColor)
    }
}
