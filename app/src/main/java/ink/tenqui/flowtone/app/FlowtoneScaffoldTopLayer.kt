package ink.tenqui.flowtone.app

import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.library.ArtistHeroBackgroundKind
import ink.tenqui.flowtone.ui.library.ArtistHeroStateStore
import ink.tenqui.flowtone.ui.library.ArtistScrollStateStore
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
    if (artistRoute != null) {
        val topBarStateOwner = artistTopBarStateStore.ownerFor(
            entryKey = artistRoute.artistEntryKey,
            initialScrollPosition = artistScrollStateStore.ownerFor(
                artistRoute.artistEntryKey
            ).position
        )
        val heroOwner = artistHeroStateStore.ownerFor(
            entryKey = artistRoute.artistEntryKey,
            initialAvatar = artistRoute.artist.identity.avatar,
            initialBackgroundKind = if (
                artistRoute.artist.identity.profileMetadata?.banner != null
            ) {
                ArtistHeroBackgroundKind.Banner
            } else {
                ArtistHeroBackgroundKind.Cloud
            }
        )
        key(artistRoute.artistEntryKey) {
            val identityVisible = artistTopBarIdentityVisible(
                route = artistRoute,
                scrollIdentityVisible = topBarStateOwner.visible
            )
            LaunchedEffect(topBarStateOwner, identityVisible) {
                topBarStateOwner.presentationProgress.animateTo(
                    targetValue = if (identityVisible) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.ShortDurationMillis,
                        easing = FlowtoneMotion.Easing
                    )
                )
            }
            val identityProgress = topBarStateOwner.presentationProgress.value
            ArtistIdentityTopBar(
                artistName = artistRoute.artist.name,
                avatarImage = heroOwner.resolvedAvatar
                    ?: artistRoute.artist.identity.avatar,
                backgroundKind = heroOwner.backgroundKind,
                identityVisible = identityVisible,
                identityProgress = identityProgress,
                pathSegments = artistRoute.pathSegments,
                onBack = {
                    if (heroOwner.focusRequested) {
                        heroOwner.focusRequested = false
                    } else {
                        callbacks.onCloseSecondaryPage()
                    }
                },
                onFullTitleRequest = onFullTitleRequest
            )
        }
    } else {
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
}
