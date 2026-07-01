package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier
import ink.tenqui.flowtone.ui.library.LocalLibraryScreen
import ink.tenqui.flowtone.ui.screens.AboutScreen
import ink.tenqui.flowtone.ui.screens.OpenSourceScreen
import ink.tenqui.flowtone.ui.screens.SettingsScreen
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.viewmodel.MusicUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SecondaryPageHost(
    secondaryPage: SecondaryPage?,
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    uiState: MusicUiState,
    currentSong: Song?,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    onCloseSecondaryPage: () -> Unit,
    onSettingsBackActionChange: ((() -> Unit)?) -> Unit,
    onSettingsPathSegmentsChange: (List<String>) -> Unit,
    onOpenSource: () -> Unit,
    onOpenSourceBack: () -> Unit,
    onOpenSourceBackActionChange: ((() -> Unit)?) -> Unit,
    onOpenSourcePathSegmentsChange: (List<String>) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    modifier: Modifier = Modifier
) {
    val previousPageHolder = remember {
        PreviousSecondaryPageHolder(secondaryPage)
    }
    val previousPage = previousPageHolder.value
    val movingBetweenAboutAndOtherSecondary =
        (previousPage == SecondaryPage.About &&
            secondaryPage != null &&
            secondaryPage != SecondaryPage.About) ||
            (previousPage != null &&
                previousPage != SecondaryPage.About &&
                secondaryPage == SecondaryPage.About)
    var keepAboutCardElementMotion by remember {
        mutableStateOf(false)
    }
    val aboutCardElementMotion =
        movingBetweenAboutAndOtherSecondary || keepAboutCardElementMotion

    SideEffect {
        previousPageHolder.value = secondaryPage
    }

    LaunchedEffect(secondaryPage) {
        if (movingBetweenAboutAndOtherSecondary) {
            keepAboutCardElementMotion = true
            delay(FlowtoneMotion.DurationMillis.toLong())
            keepAboutCardElementMotion = false
        } else {
            keepAboutCardElementMotion = false
        }
    }

    AnimatedContent(
        targetState = secondaryPage,
        transitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        label = "SecondaryContentTransition",
        modifier = modifier
    ) { page ->
        fun elementModifier(index: Int): Modifier {
            return staggeredPageElementModifier(index)
        }
        fun fadingContainerModifier(): Modifier = Modifier.animateEnterExit(
            enter = fadeIn(
                tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtonePageEasing
                )
            ),
            exit = fadeOut(
                tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtonePageEasing
                )
            )
        )
        fun songItemModifier(index: Int): Modifier {
            val delayMillis = FlowtoneMotion.staggerDelayMillis(index)
            val durationMillis = FlowtoneMotion.staggerDurationMillis(index)
            return Modifier.animateEnterExit(
                enter = fadeIn(
                    tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) { it / 6 },
                exit = fadeOut(
                    tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = FlowtonePageEasing
                    )
                ) { -it / 6 }
            )
        }
        when (page) {
            SecondaryPage.Settings -> SettingsScreen(
                appPreferences = appPreferences,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onBack = onCloseSecondaryPage,
                onBackActionChange = onSettingsBackActionChange,
                onPathSegmentsChange = onSettingsPathSegmentsChange,
                hideSecondaryBackButton = hideSecondaryBackButton,
                onHideSecondaryBackButtonChange = onHideSecondaryBackButtonChange,
                resumePlaybackAfterCall = resumePlaybackAfterCall,
                onResumePlaybackAfterCallChange = onResumePlaybackAfterCallChange,
                allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                onAllowFullscreenFromCollapsedChange = onAllowFullscreenFromCollapsedChange,
                preloadSongMetadataCount = preloadSongMetadataCount,
                onPreloadSongMetadataCountChange = onPreloadSongMetadataCountChange,
                elementModifier = ::elementModifier,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.About -> AboutScreen(
                onOpenSource = onOpenSource,
                onBack = onCloseSecondaryPage,
                elementModifier = ::elementModifier,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this,
                aboutCardElementMotion = aboutCardElementMotion,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.OpenSource -> OpenSourceScreen(
                onBack = onOpenSourceBack,
                onBackActionChange = onOpenSourceBackActionChange,
                onPathSegmentsChange = onOpenSourcePathSegmentsChange,
                elementModifier = ::elementModifier,
                modifier = Modifier.fillMaxSize()
            )

            SecondaryPage.LocalLibrary -> LocalLibraryScreen(
                uiState = uiState,
                currentSong = currentSong,
                permissionDenied = permissionDenied,
                onRequestPermission = onRequestPermission,
                onSongClick = onSongClick,
                itemModifier = ::songItemModifier,
                modifier = fadingContainerModifier()
                    .fillMaxSize()
                    .rightSwipeBackGesture(onCloseSecondaryPage)
            )

            null -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}

private class PreviousSecondaryPageHolder(
    var value: SecondaryPage?
)
