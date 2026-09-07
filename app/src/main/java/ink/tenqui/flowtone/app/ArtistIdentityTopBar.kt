package ink.tenqui.flowtone.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarNavigationTitleShift
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.library.ArtistHeaderVariant
import ink.tenqui.flowtone.ui.library.ArtistIdentityTopBarRow

private val ArtistTopBarIdentityMotionDistance = 8.dp
private const val ArtistTopBarIdentityStaggerMillis = 40
private const val ArtistTopBarIdentityElementDurationMillis =
    FlowtoneMotion.ShortDurationMillis - ArtistTopBarIdentityStaggerMillis
private const val ArtistTopBarPathStaggerFraction = 0.18f

internal enum class ArtistTopBarSurfaceTreatment { Transparent, ArtistColor }

internal fun artistTopBarSurfaceTreatment(
    variant: ArtistHeaderVariant
): ArtistTopBarSurfaceTreatment = when (variant) {
    ArtistHeaderVariant.Banner -> ArtistTopBarSurfaceTreatment.ArtistColor
    ArtistHeaderVariant.Cloud -> ArtistTopBarSurfaceTreatment.Transparent
}

internal data class ArtistTopBarPathElementProgress(
    val separator: Float,
    val title: Float
)

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
    variant: ArtistHeaderVariant,
    artistColor: Color,
    identityVisible: Boolean,
    albumEntryKey: String?,
    albumTitle: String?,
    onBack: () -> Unit,
    onFullTitleRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var retainedAlbumEntryKey by remember { mutableStateOf(albumEntryKey) }
    var retainedAlbumTitle by remember { mutableStateOf(albumTitle) }
    val pathProgress = remember { Animatable(if (albumTitle == null) 0f else 1f) }
    LaunchedEffect(albumEntryKey, albumTitle) {
        if (albumEntryKey != null && albumTitle != null) {
            retainedAlbumEntryKey = albumEntryKey
            retainedAlbumTitle = albumTitle
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
            retainedAlbumEntryKey = null
            retainedAlbumTitle = null
        }
    }
    val enteringPath = albumTitle != null
    val pathElementProgress = artistTopBarPathElementProgress(
        pathProgress = pathProgress.value,
        entering = enteringPath
    )
    val identityTransition = updateTransition(
        targetState = identityVisible,
        label = "ArtistTopBarIdentity"
    )
    val surfaceProgress by identityTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = FlowtoneMotion.ShortDurationMillis,
                easing = FlowtoneMotion.Easing
            )
        },
        label = "ArtistTopBarSurfaceProgress"
    ) { shown -> if (shown) 1f else 0f }
    val avatarProgress by identityTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = ArtistTopBarIdentityElementDurationMillis,
                delayMillis = if (targetState) 0 else ArtistTopBarIdentityStaggerMillis,
                easing = FlowtoneMotion.Easing
            )
        },
        label = "ArtistTopBarAvatarProgress"
    ) { shown -> if (shown) 1f else 0f }
    val artistTitleProgress by identityTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = ArtistTopBarIdentityElementDurationMillis,
                delayMillis = if (targetState) ArtistTopBarIdentityStaggerMillis else 0,
                easing = FlowtoneMotion.Easing
            )
        },
        label = "ArtistTopBarTitleProgress"
    ) { shown -> if (shown) 1f else 0f }
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val identityMotionDistancePx = with(density) { ArtistTopBarIdentityMotionDistance.toPx() }
    val contentColor = if (variant == ArtistHeaderVariant.Banner) {
        Color.White.copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarTop + FlowtoneTopBarContentHeight)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        if (artistTopBarSurfaceTreatment(variant) == ArtistTopBarSurfaceTreatment.ArtistColor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = surfaceProgress }
                    .background(artistColor)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(FlowtoneTopBarContentHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = onBack,
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
                albumTitle = retainedAlbumTitle,
                albumPresentationKey = retainedAlbumEntryKey,
                albumBreadcrumbProgress = pathProgress.value,
                albumSeparatorProgress = pathElementProgress.separator,
                albumTitleProgress = pathElementProgress.title,
                avatarProgress = avatarProgress,
                artistTitleProgress = artistTitleProgress,
                identityMotionDistancePx = identityMotionDistancePx,
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
