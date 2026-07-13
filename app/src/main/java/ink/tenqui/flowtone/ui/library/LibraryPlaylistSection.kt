package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.PlaylistCardContentColors
import ink.tenqui.flowtone.ui.components.PlaylistCardSurface
import ink.tenqui.flowtone.ui.components.PlaylistCardVisualType
import ink.tenqui.flowtone.ui.components.playlistCardVisualTypeFor

internal fun LazyListScope.libraryPlaylistRows(
    likedPlaylist: LibraryPlaylistCard,
    playlistRows: List<List<LibraryPlaylistCard>>,
    playlistCardHeight: Dp,
    libraryCardsProgress: Float,
    playlistRowItemOffsetYPx: Float,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    editingPlaylistId: String?,
    newlyCreatedPlaylistId: String?,
    onCreateAnimationFinished: (LibraryPlaylistCard) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    onStartPlaylistEditing: (LibraryPlaylistCard) -> Unit,
    onEditingPlaylistBoundsChanged: (String, Rect) -> Unit,
    onEditingPlaylistBoundsRemoved: (String) -> Unit
) {
    item(key = "library-pinned-playlist-row") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                    .libraryPlaylistRowMotion(
                        globalProgress = libraryCardsProgress,
                        rowIndex = 8,
                        rowAppearProgress = 1f,
                        itemOffsetYPx = playlistRowItemOffsetYPx
                ),
            horizontalArrangement = Arrangement.spacedBy(LibraryActionCardSpacing)
        ) {
            LibraryPlaylistTileCardView(
                playlist = likedPlaylist,
                cardHeight = playlistCardHeight,
                editable = false,
                isEditingTarget = false,
                playCreateAnimation = false,
                flowCloudSpeed = flowCloudSpeed,
                isFlowCloudPlaying = isFlowCloudPlaying,
                onCreateAnimationFinished = {},
                onClick = {
                    onOpenPlaylist(likedPlaylist)
                },
                onLongClick = {},
                onEditingBoundsChanged = {},
                onEditingBoundsRemoved = {},
                modifier = Modifier.weight(1f)
            )
            LibraryCreatePlaylistTileCardView(
                cardHeight = playlistCardHeight,
                flowCloudSpeed = flowCloudSpeed,
                isFlowCloudPlaying = isFlowCloudPlaying,
                onClick = onCreatePlaylist,
                modifier = Modifier.weight(1f)
            )
        }
    }

    itemsIndexed(
        items = playlistRows,
        key = { _, rowPlaylists ->
            rowPlaylists.toLibraryPlaylistRowKey()
        }
    ) { rowIndex, rowPlaylists ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                    .libraryPlaylistRowMotion(
                        globalProgress = libraryCardsProgress,
                        rowIndex = 12 + rowIndex * 4,
                        rowAppearProgress = 1f,
                        itemOffsetYPx = playlistRowItemOffsetYPx
                ),
            horizontalArrangement = Arrangement.spacedBy(LibraryActionCardSpacing)
        ) {
            rowPlaylists.forEach { playlist ->
                val editable = !playlist.isSystem
                val isEditingTarget = editingPlaylistId == playlist.id
                LibraryPlaylistTileCardView(
                    playlist = playlist,
                    cardHeight = playlistCardHeight,
                    editable = editable,
                    isEditingTarget = editable && isEditingTarget,
                    playCreateAnimation =
                        newlyCreatedPlaylistId == playlist.id,
                    flowCloudSpeed = flowCloudSpeed,
                    isFlowCloudPlaying = isFlowCloudPlaying,
                    onCreateAnimationFinished = {
                        onCreateAnimationFinished(playlist)
                    },
                    onClick = {
                        if (!isEditingTarget) {
                            onOpenPlaylist(playlist)
                        }
                    },
                    onLongClick = {
                        if (editable) {
                            onStartPlaylistEditing(playlist)
                        }
                    },
                    onEditingBoundsChanged = { bounds ->
                        onEditingPlaylistBoundsChanged(playlist.id, bounds)
                    },
                    onEditingBoundsRemoved = {
                        onEditingPlaylistBoundsRemoved(playlist.id)
                    },
                    modifier = Modifier
                        .weight(1f)
                )
            }

            if (rowPlaylists.size == 1) {
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .height(playlistCardHeight)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPlaylistTileCardView(
    playlist: LibraryPlaylistCard,
    cardHeight: Dp,
    editable: Boolean,
    isEditingTarget: Boolean,
    playCreateAnimation: Boolean,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    onCreateAnimationFinished: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditingBoundsChanged: (Rect) -> Unit,
    onEditingBoundsRemoved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val createProgress = remember(playlist.id) {
        Animatable(if (playCreateAnimation) 0f else 1f)
    }
    LaunchedEffect(playCreateAnimation, playlist.id) {
        if (playCreateAnimation) {
            createProgress.snapTo(0f)
            createProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
            onCreateAnimationFinished()
        } else if (createProgress.value < 1f) {
            createProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (
                        FlowtoneMotion.DurationMillis * (1f - createProgress.value)
                        ).toInt().coerceAtLeast(1),
                    easing = FlowtoneMotion.Easing
                )
            )
        }
    }
    val latestBounds = remember(playlist.id) {
        arrayOfNulls<Rect>(1)
    }
    if (editable) {
        DisposableEffect(playlist.id) {
            onDispose(onEditingBoundsRemoved)
        }
    }
    val cardClickModifier = if (editable) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                onLongClick()
                latestBounds[0]?.let(onEditingBoundsChanged)
            }
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    val positionedModifier = if (editable) {
        modifier.onGloballyPositioned { coordinates ->
            val topLeft = coordinates.positionInRoot()
            val bounds = Rect(
                left = topLeft.x,
                top = topLeft.y,
                right = topLeft.x + coordinates.size.width,
                bottom = topLeft.y + coordinates.size.height
            )
            latestBounds[0] = bounds
            onEditingBoundsChanged(bounds)
        }
    } else {
        modifier
    }

    if (isEditingTarget) {
        LibraryPlaylistEditingPlaceholder(
            cardHeight = cardHeight,
            modifier = positionedModifier
        )
        return
    }

    LibraryPlaylistTileVisual(
        playlist = playlist,
        cardHeight = cardHeight,
        appearProgress = createProgress.value,
        clickModifier = cardClickModifier,
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying,
        modifier = positionedModifier
    )
}

@Composable
internal fun LibraryPlaylistTileVisual(
    playlist: LibraryPlaylistCard,
    cardHeight: Dp,
    appearProgress: Float,
    clickModifier: Modifier,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val isLikedPlaylist = playlist.isLikedSongsPlaylist()
    LibraryPlaylistTileSurface(
        visualType = playlistCardVisualTypeFor(playlist),
        cardHeight = cardHeight,
        appearProgress = appearProgress,
        clickModifier = clickModifier,
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying,
        modifier = modifier
    ) { contentColors ->
        LibraryPlaylistTileTextContent(
            title = playlist.title,
            subtitle = playlist.subtitle,
            contentColors = contentColors,
            icon = if (isLikedPlaylist) {
                {
                    LikedSongsPlaylistTileIcon(contentColors)
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun LibraryPlaylistEditingPlaceholder(
    cardHeight: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(cardHeight)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
private fun LibraryCreatePlaylistTileCardView(
    cardHeight: Dp,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LibraryPlaylistTileSurface(
        visualType = PlaylistCardVisualType.CreatePlaylist,
        cardHeight = cardHeight,
        appearProgress = 1f,
        clickModifier = Modifier.clickable(onClick = onClick),
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying,
        modifier = modifier
    ) { contentColors ->
        LibraryPlaylistTileTextContent(
            title = "\u521b\u5efa\u6b4c\u5355",
            subtitle = "\u6dfb\u52a0\u65b0\u7684\u6b4c\u5355",
            contentColors = contentColors,
            icon = {
                CreatePlaylistTileIcon(contentColors)
            }
        )
    }
}

@Composable
private fun LibraryPlaylistTileSurface(
    visualType: PlaylistCardVisualType,
    cardHeight: Dp,
    appearProgress: Float,
    clickModifier: Modifier,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PlaylistCardContentColors) -> Unit
) {
    Box(
        modifier = modifier.height(cardHeight)
    ) {
        PlaylistCardSurface(
            visualType = visualType,
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            clickModifier = clickModifier,
            flowCloudSpeed = flowCloudSpeed,
            isFlowCloudPlaying = isFlowCloudPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .graphicsLayer {
                    val eased = FlowtoneMotion.Easing.transform(
                        appearProgress.coerceIn(0f, 1f)
                    )
                    alpha = eased
                    translationY = 18.dp.toPx() * (1f - eased)
                },
            content = content
        )
    }
}

@Composable
private fun BoxScope.LibraryPlaylistTileTextContent(
    title: String,
    subtitle: String?,
    contentColors: PlaylistCardContentColors,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Top
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(bottom = 14.dp)) {
                icon()
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColors.titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColors.subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun LikedSongsPlaylistTileIcon(
    contentColors: PlaylistCardContentColors
) {
    LibraryPlaylistTileIconContainer(
        backgroundColor = contentColors.iconContainerColor
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = contentColors.iconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CreatePlaylistTileIcon(
    contentColors: PlaylistCardContentColors
) {
    LibraryPlaylistTileIconContainer(
        backgroundColor = contentColors.iconContainerColor
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = "\u521b\u5efa\u6b4c\u5355",
            tint = contentColors.iconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun LibraryPlaylistTileIconContainer(
    backgroundColor: androidx.compose.ui.graphics.Color,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
        content = content
    )
}

private fun Modifier.libraryPlaylistRowMotion(
    globalProgress: Float,
    rowIndex: Int,
    rowAppearProgress: Float,
    itemOffsetYPx: Float
): Modifier {
    val rowProgress = libraryPlaylistRowProgress(
        globalProgress = globalProgress,
        rowIndex = rowIndex
    )
    val easedProgress = FlowtoneMotion.Easing.transform(rowProgress)
    val easedAppearProgress = FlowtoneMotion.Easing.transform(rowAppearProgress.coerceIn(0f, 1f))
    val itemProgress = easedProgress * easedAppearProgress
    return graphicsLayer {
        alpha = itemProgress
        translationY = itemOffsetYPx * (1f - itemProgress)
    }
}

private fun List<LibraryPlaylistCard>.toLibraryPlaylistRowKey(): String {
    return joinToString(separator = "_") { playlist -> playlist.id }
}

private fun libraryPlaylistRowProgress(
    globalProgress: Float,
    rowIndex: Int
): Float {
    val delayMillis = FlowtoneMotion.staggerDelayMillis(rowIndex).toFloat()
    val durationMillis = FlowtoneMotion.staggerDurationMillis(rowIndex)
        .coerceAtLeast(1)
        .toFloat()
    val elapsedMillis = globalProgress.coerceIn(0f, 1f) * FlowtoneMotion.DurationMillis
    return ((elapsedMillis - delayMillis) / durationMillis).coerceIn(0f, 1f)
}
