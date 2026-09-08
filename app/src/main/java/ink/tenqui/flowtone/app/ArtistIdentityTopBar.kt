package ink.tenqui.flowtone.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarNavigationTitleShift
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.library.ArtistHeroBackgroundKind
import ink.tenqui.flowtone.ui.library.ArtistIdentityTopBarRow

internal val ArtistTopBarIdentityMotionDistance = 16.dp
private const val ArtistTopBarPathStaggerFraction = 0.18f

internal enum class ArtistTopBarSurfaceTreatment { Transparent }

internal fun artistTopBarSurfaceTreatment(
    backgroundKind: ArtistHeroBackgroundKind
): ArtistTopBarSurfaceTreatment = ArtistTopBarSurfaceTreatment.Transparent

internal data class ArtistTopBarPathElementProgress(
    val separator: Float,
    val title: Float
)

internal data class ArtistTopBarIdentityPresentation(
    val alpha: Float,
    val translationYFraction: Float
)

internal enum class ArtistTopBarPresentedElement { Back, Avatar, Breadcrumb }

internal val ArtistTopBarUnifiedPresentedElements = ArtistTopBarPresentedElement.entries.toSet()

internal fun artistTopBarIdentityPresentation(
    progress: Float
): ArtistTopBarIdentityPresentation {
    val safeProgress = progress.coerceIn(0f, 1f)
    return ArtistTopBarIdentityPresentation(
        alpha = safeProgress,
        translationYFraction = -(1f - safeProgress)
    )
}

internal fun artistTopBarPathElementProgress(
    pathProgress: Float,
    entering: Boolean
): ArtistTopBarPathElementProgress {
    val progress = pathProgress.coerceIn(0f, 1f)
    return ArtistTopBarPathElementProgress(
        separator = (progress / (1f - ArtistTopBarPathStaggerFraction)).coerceIn(0f, 1f),
        title = if (entering) {
            ((progress - ArtistTopBarPathStaggerFraction) /
                (1f - ArtistTopBarPathStaggerFraction)).coerceIn(0f, 1f)
        } else {
            progress
        }
    )
}

@Composable
internal fun ArtistIdentityTopBar(
    artistName: String,
    avatarImage: ExtensionImage?,
    backgroundKind: ArtistHeroBackgroundKind,
    identityVisible: Boolean,
    pathEntryKey: String?,
    pathSegments: List<String>,
    onBack: () -> Unit,
    onFullTitleRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var retainedPathEntryKey by remember { mutableStateOf(pathEntryKey) }
    var retainedPathSegments by remember { mutableStateOf(pathSegments) }
    val pathProgress = remember { Animatable(if (pathSegments.isEmpty()) 0f else 1f) }
    LaunchedEffect(pathEntryKey, pathSegments) {
        if (pathEntryKey != null && pathSegments.isNotEmpty()) {
            retainedPathEntryKey = pathEntryKey
            retainedPathSegments = pathSegments
            withFrameNanos { }
            pathProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.ShortDurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
        } else {
            pathProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.ShortDurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
            retainedPathEntryKey = null
            retainedPathSegments = emptyList()
        }
    }
    val enteringPath = pathSegments.isNotEmpty()
    val pathElementProgress = artistTopBarPathElementProgress(
        pathProgress = pathProgress.value,
        entering = enteringPath
    )
    val identityTransition = updateTransition(
        targetState = identityVisible,
        label = "ArtistTopBarIdentity"
    )
    val identityProgress by identityTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = FlowtoneMotion.ShortDurationMillis,
                easing = FlowtoneMotion.Easing
            )
        },
        label = "ArtistTopBarIdentityProgress"
    ) { shown -> if (shown) 1f else 0f }
    val identityPresentation = artistTopBarIdentityPresentation(identityProgress)
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val identityMotionDistancePx = with(density) { ArtistTopBarIdentityMotionDistance.toPx() }
    val contentColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarTop + FlowtoneTopBarContentHeight)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(FlowtoneTopBarContentHeight)
                .graphicsLayer {
                    alpha = identityPresentation.alpha
                    translationY = identityMotionDistancePx *
                        identityPresentation.translationYFraction
                },
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = onBack,
                enabled = identityVisible,
                modifier = Modifier
                    .offset(x = (-8).dp)
                    .padding(start = 12.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = contentColor
                )
            }
            ArtistIdentityTopBarRow(
                artistName = artistName,
                avatarImage = avatarImage,
                pathSegments = retainedPathSegments,
                pathPresentationKey = retainedPathEntryKey,
                pathProgress = pathProgress.value,
                pathSeparatorProgress = pathElementProgress.separator,
                pathTitleProgress = pathElementProgress.title,
                contentColor = contentColor,
                onFullTitleRequest = onFullTitleRequest,
                interactionEnabled = identityVisible,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FlowtoneTopBarTitleStartPadding +
                            FlowtoneTopBarNavigationTitleShift,
                        end = 24.dp
                    )
            )
        }
    }
}
