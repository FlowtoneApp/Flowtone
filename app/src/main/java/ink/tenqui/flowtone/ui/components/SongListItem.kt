package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.image.ExtensionImageKeyer

internal data class SongListItemLayoutSpec(
    val rowMinHeight: Dp,
    val outerMinHeight: Dp,
    val artworkSize: Dp,
    val verticalPadding: Dp,
    val horizontalPadding: Dp,
    val titleToArtistSpacing: Dp,
    val artworkToTextSpacing: Dp,
    val textToTrailingSpacing: Dp,
    val trailingWidth: Dp
)

internal val StandardSongListItemSpacing = 4.dp

internal enum class SongArtworkLoadState {
    Loading,
    Success,
    Failure
}

/**
 * The cached image itself remains owned by Coil. This only decides whether the
 * fallback glyph is visible while Coil resolves the current artwork identity.
 */
internal fun shouldShowSongArtworkPlaceholder(
    hasArtworkSource: Boolean,
    loadState: SongArtworkLoadState,
    hasKnownCachedArtwork: Boolean
): Boolean = when {
    !hasArtworkSource -> true
    loadState == SongArtworkLoadState.Failure -> true
    loadState == SongArtworkLoadState.Success -> false
    else -> !hasKnownCachedArtwork
}

internal fun songListItemLayoutSpec(
    compact: Boolean,
    selectionSlotPadding: Dp = 0.dp
): SongListItemLayoutSpec {
    val rowMinHeight = if (compact) 64.dp else 72.dp
    return SongListItemLayoutSpec(
        rowMinHeight = rowMinHeight,
        outerMinHeight = rowMinHeight + selectionSlotPadding * 2,
        artworkSize = if (compact) 48.dp else 56.dp,
        verticalPadding = if (compact) 6.dp else 8.dp,
        horizontalPadding = 12.dp,
        titleToArtistSpacing = if (compact) 0.dp else 2.dp,
        artworkToTextSpacing = 12.dp,
        textToTrailingSpacing = 12.dp,
        trailingWidth = 96.dp
    )
}

@Composable
fun SongListItem(
    song: Song,
    isCurrentSong: Boolean,
    onClick: (Song) -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    artistColor: Color? = null,
    durationColor: Color? = null,
    currentSongBackgroundColor: Color? = null,
    compact: Boolean = false,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    isPreviousSelected: Boolean = false,
    isNextSelected: Boolean = false,
    selectionSlotPadding: Dp = 0.dp,
    onLongClick: ((Song) -> Unit)? = null,
    isPendingPlayback: Boolean = false,
    extensionArtwork: ExtensionImage? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val itemIndication = if (selectionMode) null else LocalIndication.current
    val itemClickModifier = if (!selectionMode && onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = itemIndication,
            onClick = { onClick(song) },
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick(song)
            }
        )
    } else {
        // Playlist drag-selection owns long presses at the list level. Keeping the row
        // on a plain click recognizer prevents both recognizers from tracking the tap.
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = itemIndication,
            onClick = { onClick(song) }
        )
    }
    val layoutSpec = songListItemLayoutSpec(compact, selectionSlotPadding)
    val contentColor = if (isCurrentSong) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val rowShape = MaterialTheme.shapes.medium
    val selectionPosition = selectionGroupPosition(
        isSelected = isSelected,
        isPreviousSelected = isPreviousSelected,
        isNextSelected = isNextSelected
    )
    val selectionTransition = updateTransition(
        targetState = SongSelectionVisualState(
            selectionMode = selectionMode,
            position = selectionPosition
        ),
        label = "SongSelection"
    )
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val selectionAlpha = if (isDarkTheme) {
        0.16f
    } else {
        0.12f
    }
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = selectionAlpha)
    val selectionBorderColor = MaterialTheme.colorScheme.primary.copy(
        alpha = if (isDarkTheme) 0.34f else 0.24f
    )
    val selectionBackground by selectionTransition.animateColor(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionBackground"
    ) { state ->
        if (state.position == SelectionGroupPosition.None) Color.Transparent else selectionColor
    }
    val topConnectionProgress by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionTopConnection"
    ) { state ->
        if (state.position.connectsTop) 1f else 0f
    }
    val bottomConnectionProgress by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionBottomConnection"
    ) { state ->
        if (state.position.connectsBottom) 1f else 0f
    }
    val topCornerRadius by selectionTransition.animateDp(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionTopCorner"
    ) { state ->
        if (state.position.connectsTop) 0.dp else SelectionCornerRadius
    }
    val bottomCornerRadius by selectionTransition.animateDp(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionBottomCorner"
    ) { state ->
        if (state.position.connectsBottom) 0.dp else SelectionCornerRadius
    }
    val selectionBorderAlpha by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionBorderAlpha"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) 1f else 0f
    }
    val topBorderEdgeAlpha by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionTopBorderEdge"
    ) { state ->
        if (
            state.position != SelectionGroupPosition.None &&
            !state.position.connectsTop
        ) {
            1f
        } else {
            0f
        }
    }
    val bottomBorderEdgeAlpha by selectionTransition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionShapeDurationMillis) },
        label = "SongSelectionBottomBorderEdge"
    ) { state ->
        if (
            state.position != SelectionGroupPosition.None &&
            !state.position.connectsBottom
        ) {
            1f
        } else {
            0f
        }
    }
    val currentSongBackground = if (isCurrentSong && !isSelected) {
        currentSongBackgroundColor ?: MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = layoutSpec.outerMinHeight)
    ) {
        val selectionTopInset =
            selectionSlotPadding * (1f - topConnectionProgress)
        val selectionBottomInset =
            selectionSlotPadding * (1f - bottomConnectionProgress)
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(
                    top = selectionTopInset,
                    bottom = selectionBottomInset
                )
                .background(
                    color = selectionBackground,
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    )
                )
                .selectionGroupBorder(
                    color = selectionBorderColor,
                    alpha = selectionBorderAlpha,
                    topEdgeAlpha = topBorderEdgeAlpha,
                    bottomEdgeAlpha = bottomBorderEdgeAlpha,
                    topConnectionProgress = topConnectionProgress,
                    bottomConnectionProgress = bottomConnectionProgress,
                    topCornerRadius = topCornerRadius,
                    bottomCornerRadius = bottomCornerRadius
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = selectionSlotPadding)
                .clip(rowShape)
                .background(currentSongBackground)
                .then(itemClickModifier)
                .semantics {
                    if (selectionMode) {
                        selected = isSelected
                        onClick(
                            label = if (isSelected) "点按取消选择" else "点按选择"
                        ) {
                            onClick(song)
                            true
                        }
                    }
                }
                .heightIn(min = layoutSpec.rowMinHeight)
                .padding(
                    horizontal = layoutSpec.horizontalPadding,
                    vertical = layoutSpec.verticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(layoutSpec.artworkSize)) {
                AlbumArtwork(
                    song = song,
                    isCurrentSong = isCurrentSong,
                    extensionArtwork = extensionArtwork,
                    modifier = Modifier.matchParentSize()
                )
                SongSelectionIndicator(
                    transition = selectionTransition,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = layoutSpec.artworkToTextSpacing,
                        end = layoutSpec.textToTrailingSpacing
                    )
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor ?: contentColor,
                    fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = artistColor ?: if (isCurrentSong) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = layoutSpec.titleToArtistSpacing)
                )
            }
            Box(
                modifier = Modifier.width(layoutSpec.trailingWidth)
            ) {
                if (isPendingPlayback) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else if (isCurrentSong) {
                    Text(
                        text = "\u64ad\u653e\u4e2d",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = formatDuration(song.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = durationColor ?: if (isCurrentSong) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

/**
 * A standalone loading visual. It owns no real-song identity, state, placement or geometry;
 * the caller supplies only the LoadingContent presentation modifier.
 */
@Composable
internal fun SongListItemSkeleton(
    modifier: Modifier = Modifier
) {
    val breathingTransition = rememberInfiniteTransition(label = "SongListItemSkeleton")
    val breathingProgress by breathingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SongListItemSkeletonBreathing"
    )
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = 0.18f + 0.08f * breathingProgress
    )
    val placeholderShape = RoundedCornerShape(percent = 50)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(placeholderColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .heightIn(min = 16.dp)
                    .clip(placeholderShape)
                    .background(placeholderColor)
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.52f)
                    .heightIn(min = 12.dp)
                    .clip(placeholderShape)
                    .background(placeholderColor.copy(alpha = placeholderColor.alpha * 0.82f))
            )
        }
        Box(
            modifier = Modifier.width(96.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .heightIn(min = 12.dp)
                    .clip(placeholderShape)
                    .background(placeholderColor.copy(alpha = placeholderColor.alpha * 0.82f))
            )
        }
    }
}

private fun Modifier.selectionGroupBorder(
    color: Color,
    alpha: Float,
    topEdgeAlpha: Float,
    bottomEdgeAlpha: Float,
    topConnectionProgress: Float,
    bottomConnectionProgress: Float,
    topCornerRadius: Dp,
    bottomCornerRadius: Dp
): Modifier = drawBehind {
    if (alpha <= 0f) return@drawBehind

    val strokeWidth = 1.dp.toPx()
    val halfStroke = strokeWidth / 2f
    val left = halfStroke
    val right = size.width - halfStroke
    val top = halfStroke
    val bottom = size.height - halfStroke
    val topRadius = topCornerRadius.toPx().coerceAtMost((right - left) / 2f)
    val bottomRadius = bottomCornerRadius.toPx().coerceAtMost((right - left) / 2f)
    val sideTop = (top + topRadius) * (1f - topConnectionProgress)
    val sideBottom = (bottom - bottomRadius) * (1f - bottomConnectionProgress) +
        size.height * bottomConnectionProgress
    val stroke = Stroke(width = strokeWidth)
    val sideColor = color.copy(alpha = color.alpha * alpha)

    drawLine(
        color = sideColor,
        start = androidx.compose.ui.geometry.Offset(left, sideTop),
        end = androidx.compose.ui.geometry.Offset(left, sideBottom),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = sideColor,
        start = androidx.compose.ui.geometry.Offset(right, sideTop),
        end = androidx.compose.ui.geometry.Offset(right, sideBottom),
        strokeWidth = strokeWidth
    )

    if (topEdgeAlpha > 0f) {
        val topPath = Path().apply {
            moveTo(left, top + topRadius)
            quadraticTo(left, top, left + topRadius, top)
            lineTo(right - topRadius, top)
            quadraticTo(right, top, right, top + topRadius)
        }
        drawPath(
            path = topPath,
            color = color.copy(alpha = color.alpha * alpha * topEdgeAlpha),
            style = stroke
        )
    }

    if (bottomEdgeAlpha > 0f) {
        val bottomPath = Path().apply {
            moveTo(left, bottom - bottomRadius)
            quadraticTo(left, bottom, left + bottomRadius, bottom)
            lineTo(right - bottomRadius, bottom)
            quadraticTo(right, bottom, right, bottom - bottomRadius)
        }
        drawPath(
            path = bottomPath,
            color = color.copy(alpha = color.alpha * alpha * bottomEdgeAlpha),
            style = stroke
        )
    }
}

@Composable
private fun SongSelectionIndicator(
    transition: Transition<SongSelectionVisualState>,
    modifier: Modifier = Modifier
) {
    val pageBackground = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val indicatorOutlineColor =
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val indicatorAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorAlpha"
    ) { state ->
        if (state.selectionMode) 1f else 0f
    }
    val indicatorFill by transition.animateColor(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorFill"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) {
            primaryColor
        } else {
            Color.Transparent
        }
    }
    val indicatorBorder by transition.animateColor(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorBorder"
    ) { state ->
        if (state.position == SelectionGroupPosition.None) {
            indicatorOutlineColor
        } else {
            Color.Transparent
        }
    }
    val indicatorScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionIndicatorScale"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) 1f else 0.9f
    }
    val checkAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SelectionColorDurationMillis) },
        label = "SongSelectionCheckAlpha"
    ) { state ->
        if (state.position != SelectionGroupPosition.None) 1f else 0f
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .graphicsLayer {
                alpha = indicatorAlpha
                scaleX = indicatorScale
                scaleY = indicatorScale
            }
            // Outer page-colored ring keeps the control legible over any album art.
            .background(pageBackground, CircleShape)
            .padding(2.dp)
            .background(indicatorFill, CircleShape)
            .border(
                width = 1.5.dp,
                color = indicatorBorder,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    alpha = checkAlpha
                    scaleX = 0.72f + 0.28f * checkAlpha
                    scaleY = 0.72f + 0.28f * checkAlpha
                }
        )
    }
}

private data class SongSelectionVisualState(
    val selectionMode: Boolean,
    val position: SelectionGroupPosition
)

private const val SelectionColorDurationMillis = 180
private const val SelectionShapeDurationMillis = 220
private val SelectionCornerRadius = 20.dp

@Composable
private fun AlbumArtwork(
    song: Song,
    isCurrentSong: Boolean,
    extensionArtwork: ExtensionImage?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extensionArtworkCacheKey = remember(extensionArtwork) {
        extensionArtwork?.let(ExtensionImageKeyer::cacheKey)
    }
    val hasKnownCachedArtwork = remember(context, extensionArtworkCacheKey) {
        extensionArtworkCacheKey?.let { cacheKey ->
            ExtensionManager.get(context).extensionImageLoader.memoryCache
                ?.get(MemoryCache.Key(cacheKey)) != null
        } ?: false
    }
    val extensionImageRequest = remember(context, extensionArtwork, extensionArtworkCacheKey) {
        extensionArtwork?.let { artwork ->
            ImageRequest.Builder(context)
                .data(artwork)
                .memoryCacheKey(requireNotNull(extensionArtworkCacheKey))
                .placeholderMemoryCacheKey(requireNotNull(extensionArtworkCacheKey))
                .build()
        }
    }
    val imageRequest: ImageRequest? = remember(song.artworkUri, context) {
        song.artworkUri?.let { artworkUri ->
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(96, 96)
                .placeholderMemoryCacheKey(artworkUri.toString())
                .build()
        }
    }
    val artworkIdentity = extensionArtworkCacheKey ?: song.artworkUri?.toString()
    var artworkLoadState by remember(artworkIdentity) {
        mutableStateOf(
            if (hasKnownCachedArtwork) SongArtworkLoadState.Success else SongArtworkLoadState.Loading
        )
    }
    val showPlaceholder = shouldShowSongArtworkPlaceholder(
        hasArtworkSource = artworkIdentity != null,
        loadState = artworkLoadState,
        hasKnownCachedArtwork = hasKnownCachedArtwork
    )
    val shape = MaterialTheme.shapes.medium
    val isSystemDark = isSystemInDarkTheme()
    val placeholderColor = if (isSystemDark) {
        Color.Black
    } else {
        Color.White
    }
    val iconColor = if (isSystemDark) {
        Color.White.copy(alpha = 0.78f)
    } else {
        Color.Black.copy(alpha = 0.72f)
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(shape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        if (showPlaceholder) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = iconColor
            )
        }
        extensionImageRequest?.let { request ->
            AsyncImage(
                model = request,
                imageLoader = ExtensionManager.get(context).extensionImageLoader,
                contentDescription = "专辑封面",
                contentScale = ContentScale.Crop,
                onSuccess = { artworkLoadState = SongArtworkLoadState.Success },
                onError = { artworkLoadState = SongArtworkLoadState.Failure },
                modifier = Modifier.matchParentSize()
            )
        } ?: imageRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = "\u4e13\u8f91\u5c01\u9762",
                contentScale = ContentScale.Crop,
                onSuccess = { artworkLoadState = SongArtworkLoadState.Success },
                onError = { artworkLoadState = SongArtworkLoadState.Failure },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
