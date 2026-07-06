package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.PagerState

@Composable
internal fun FlowtoneTopBar(
    selectedTopLevelPage: TopLevelPage,
    pagerState: PagerState,
    secondaryPage: SecondaryPage?,
    additionalPathSegments: List<String>,
    backgroundAlpha: Float,
    hideBackButton: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pathSegments = when (secondaryPage) {
        SecondaryPage.Settings -> listOf(SecondaryPage.Settings.title) + additionalPathSegments
        SecondaryPage.About -> listOf(SecondaryPage.About.title)
        SecondaryPage.LocalLibrary -> listOf(SecondaryPage.LocalLibrary.title)
        SecondaryPage.Playlist -> additionalPathSegments.ifEmpty {
            listOf(SecondaryPage.Playlist.title)
        }
        SecondaryPage.Artist -> listOf(SecondaryPage.Artist.title) + additionalPathSegments
        SecondaryPage.OpenSource -> listOf(
            SecondaryPage.About.title,
            SecondaryPage.OpenSource.title
        ) + additionalPathSegments
        null -> emptyList()
    }
    val showBackButton = secondaryPage != null && !hideBackButton
    val backButtonProgress by animateFloatAsState(
        targetValue = if (showBackButton) 1f else 0f,
        animationSpec = tween(280, easing = FlowtonePageEasing),
        label = "SecondaryBackButtonProgress"
    )
    val density = LocalDensity.current
    val navigationShiftPx = with(density) { 40.dp.toPx() } * backButtonProgress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = backgroundAlpha)
            )
            .statusBarsPadding()
            .height(56.dp)
            .clipToBounds()
            .padding(start = 20.dp, end = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = showBackButton,
            enter = fadeIn(tween(180, easing = FlowtonePageEasing)) +
                slideInHorizontally(tween(260, easing = FlowtonePageEasing)) { -it * 2 },
            exit = fadeOut(tween(140, easing = FlowtonePageEasing)) +
                slideOutHorizontally(tween(260, easing = FlowtonePageEasing)) { -it * 2 },
            modifier = Modifier.offset(x = (-8).dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "\u8fd4\u56de",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        FlowtonePathTitle(
            pagerState = pagerState,
            rootPage = selectedTopLevelPage,
            segments = pathSegments,
            navigationShiftPx = navigationShiftPx,
            modifier = Modifier.fillMaxSize()
        )
    }
}
