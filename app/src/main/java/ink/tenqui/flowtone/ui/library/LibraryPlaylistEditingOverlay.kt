package ink.tenqui.flowtone.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun LibraryPlaylistEditingOverlay(
    playlist: LibraryPlaylistCard?,
    cardBounds: Rect?,
    viewportBounds: Rect?,
    progress: Float,
    bottomContentPadding: Dp,
    flowCloudSpeed: Float,
    dialogVisible: Boolean,
    onDismissRequest: () -> Unit,
    onLongPressOtherPlaylist: (Offset) -> Boolean,
    onDeletePlaylist: (LibraryPlaylistCard) -> Unit,
    onRenamePlaylist: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var retainedPlaylist by remember { mutableStateOf<LibraryPlaylistCard?>(null) }
    var retainedCardBounds by remember { mutableStateOf<Rect?>(null) }
    var retainedViewportBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(playlist, cardBounds, viewportBounds) {
        if (playlist != null && cardBounds != null) {
            retainedPlaylist = playlist
            retainedCardBounds = cardBounds
            retainedViewportBounds = viewportBounds
        }
    }
    LaunchedEffect(playlist, progress) {
        if (playlist == null && progress <= 0.001f) {
            retainedPlaylist = null
            retainedCardBounds = null
            retainedViewportBounds = null
        }
    }

    val activeTargetReady = playlist != null && cardBounds != null
    val displayedPlaylist = if (activeTargetReady) playlist else if (playlist == null) {
        retainedPlaylist
    } else {
        null
    }
    val displayedCardBounds = if (activeTargetReady) cardBounds else if (playlist == null) {
        retainedCardBounds
    } else {
        null
    }
    val displayedViewportBounds = if (activeTargetReady) {
        viewportBounds
    } else {
        retainedViewportBounds
    }
    val safeProgress = progress.coerceIn(0f, 1f)

    BackHandler(
        enabled = playlist != null && !dialogVisible,
        onBack = onDismissRequest
    )

    if (
        displayedPlaylist == null ||
        displayedCardBounds == null ||
        playlist == null && safeProgress <= 0.001f
    ) {
        return
    }

    var overlayRootTopLeft by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayRootTopLeft = coordinates.positionInRoot()
            }
    ) {
        val rootWidthPx = constraints.maxWidth.toFloat()
        val rootHeightPx = constraints.maxHeight.toFloat()
        val sideMarginPx = with(density) { PlaylistEditOverlaySafeMargin.toPx() }
        val windowInsets = WindowInsets.safeDrawing
        val safeLeftPx = windowInsets.getLeft(density, layoutDirection) + sideMarginPx
        val safeRightPx = rootWidthPx -
            windowInsets.getRight(density, layoutDirection) - sideMarginPx
        val safeTopPx = windowInsets.getTop(density) + sideMarginPx
        val safeBottomPx = rootHeightPx - max(
            windowInsets.getBottom(density).toFloat(),
            with(density) { bottomContentPadding.toPx() }
        ) - sideMarginPx
        val localCardBounds = displayedCardBounds.translate(-overlayRootTopLeft)
        val localViewportBounds = displayedViewportBounds
            ?.translate(-overlayRootTopLeft)
            ?: Rect(0f, 0f, rootWidthPx, rootHeightPx)
        val visibleCardBounds = localCardBounds.intersection(localViewportBounds)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = PlaylistEditOverlayScrimAlpha * safeProgress
                    )
                )
                .pointerInput(playlist, overlayRootTopLeft) {
                    detectTapGestures(
                        onTap = {
                            if (playlist != null) {
                                onDismissRequest()
                            }
                        },
                        onLongPress = { position ->
                            if (
                                playlist != null &&
                                onLongPressOtherPlaylist(position + overlayRootTopLeft)
                            ) {
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                            }
                        }
                    )
                }
        )

        if (visibleCardBounds.width > 0f && visibleCardBounds.height > 0f) {
            val visibleWidth = with(density) { visibleCardBounds.width.toDp() }
            val visibleHeight = with(density) { visibleCardBounds.height.toDp() }
            val cardWidth = with(density) { localCardBounds.width.toDp() }
            val cardHeight = with(density) { localCardBounds.height.toDp() }

            Box(
                modifier = Modifier
                    .offsetInParent(visibleCardBounds.left, visibleCardBounds.top)
                    .requiredSize(visibleWidth, visibleHeight)
                    .clipToBounds()
            ) {
                LibraryPlaylistTileVisual(
                    playlist = displayedPlaylist,
                    cardHeight = cardHeight,
                    appearProgress = 1f,
                    clickModifier = Modifier.clickable(onClick = {}),
                    flowCloudSpeed = flowCloudSpeed,
                    isFlowCloudPlaying = false,
                    modifier = Modifier
                        .offsetInParent(
                            localCardBounds.left - visibleCardBounds.left,
                            localCardBounds.top - visibleCardBounds.top
                        )
                        .width(cardWidth)
                        .border(
                            width = PlaylistEditCardBorderWidth,
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = safeProgress
                            ),
                            shape = PlaylistEditCardShape
                        )
                )
            }
        }

        val placement = calculatePlaylistEditActionsPlacement(
            cardBounds = visibleCardBounds,
            safeBounds = Rect(safeLeftPx, safeTopPx, safeRightPx, safeBottomPx),
            buttonSizePx = with(density) { PlaylistEditActionButtonSize.toPx() },
            buttonGapPx = with(density) { PlaylistEditActionButtonGap.toPx() },
            cardGapPx = with(density) { PlaylistEditActionCardGap.toPx() }
        )
        Row(
            modifier = Modifier.offsetInParent(placement.left, placement.top),
            horizontalArrangement = Arrangement.spacedBy(PlaylistEditActionButtonGap)
        ) {
            PlaylistEditActionButton(
                progress = staggeredPlaylistEditActionProgress(safeProgress, 0),
                onClick = {
                    playlist?.let(onDeletePlaylist)
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "\u5220\u9664\u6b4c\u5355"
                )
            }
            PlaylistEditActionButton(
                progress = staggeredPlaylistEditActionProgress(safeProgress, 1),
                onClick = {
                    playlist?.let(onRenamePlaylist)
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "\u4fee\u6539\u6b4c\u5355\u540d\u79f0"
                )
            }
            PlaylistEditActionButton(
                progress = staggeredPlaylistEditActionProgress(safeProgress, 2),
                onClick = {
                    // TODO: 后续在这里接入歌单外观自定义流程。
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = "\u81ea\u5b9a\u4e49\u6b4c\u5355\u5916\u89c2"
                )
            }
        }
    }
}

@Composable
private fun PlaylistEditActionButton(
    progress: Float,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val translation = with(density) { PlaylistEditActionEnterDistance.toPx() }
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .size(PlaylistEditActionButtonSize)
            .graphicsLayer {
                alpha = progress
                translationY = translation * (1f - progress)
            },
        content = content
    )
}

internal data class PlaylistEditActionsPlacement(
    val left: Float,
    val top: Float
)

internal fun calculatePlaylistEditActionsPlacement(
    cardBounds: Rect,
    safeBounds: Rect,
    buttonSizePx: Float,
    buttonGapPx: Float,
    cardGapPx: Float
): PlaylistEditActionsPlacement {
    val rowWidth = buttonSizePx * PlaylistEditActionCount +
        buttonGapPx * (PlaylistEditActionCount - 1)
    val desiredLeft = cardBounds.center.x - rowWidth / 2f
    val maxLeft = max(safeBounds.left, safeBounds.right - rowWidth)
    val left = desiredLeft.coerceIn(safeBounds.left, maxLeft)
    val desiredTop = cardBounds.top - cardGapPx - buttonSizePx
    val maxTop = max(safeBounds.top, safeBounds.bottom - buttonSizePx)
    val top = desiredTop.coerceIn(safeBounds.top, maxTop)
    return PlaylistEditActionsPlacement(
        left = left,
        top = top
    )
}

private fun Rect.translate(delta: Offset): Rect {
    return Rect(
        left = left + delta.x,
        top = top + delta.y,
        right = right + delta.x,
        bottom = bottom + delta.y
    )
}

private fun Rect.intersection(other: Rect): Rect {
    val intersectionLeft = max(left, other.left)
    val intersectionTop = max(top, other.top)
    val intersectionRight = min(right, other.right)
    val intersectionBottom = min(bottom, other.bottom)
    return if (
        intersectionRight > intersectionLeft && intersectionBottom > intersectionTop
    ) {
        Rect(intersectionLeft, intersectionTop, intersectionRight, intersectionBottom)
    } else {
        Rect.Zero
    }
}

private fun Modifier.offsetInParent(xPx: Float, yPx: Float): Modifier {
    return this.then(
        Modifier.graphicsLayer {
            translationX = xPx
            translationY = yPx
        }
    )
}

private fun staggeredPlaylistEditActionProgress(
    overallProgress: Float,
    index: Int
): Float {
    val delayFraction = index.coerceIn(0, PlaylistEditActionCount - 1) * 0.08f
    return ((overallProgress - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f)
}

private val PlaylistEditCardShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
private val PlaylistEditOverlaySafeMargin = 12.dp
private val PlaylistEditCardBorderWidth = 1.5.dp
private val PlaylistEditActionButtonSize = 52.dp
private val PlaylistEditActionButtonGap = 10.dp
private val PlaylistEditActionCardGap = 12.dp
private val PlaylistEditActionEnterDistance = 8.dp
private const val PlaylistEditActionCount = 3
private const val PlaylistEditOverlayScrimAlpha = 0.08f
