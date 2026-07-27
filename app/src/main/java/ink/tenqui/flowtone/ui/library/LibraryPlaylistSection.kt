package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.PlaylistCardContentColors
import ink.tenqui.flowtone.ui.components.PlaylistCardSurface
import ink.tenqui.flowtone.ui.components.PlaylistCardVisualType
import ink.tenqui.flowtone.ui.components.LocalLibraryDarkBackground
import ink.tenqui.flowtone.ui.components.LocalLibraryLightBackground
import ink.tenqui.flowtone.ui.components.playlistCardVisualTypeFor

@Composable
internal fun LibraryCollectionMenu(
    songCount: Int,
    likedPlaylist: LibraryPlaylistCard,
    playlists: List<LibraryPlaylistCard>,
    playlistItemHeight: Dp,
    libraryCardsProgress: Float,
    playlistItemOffsetYPx: Float,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    editingPlaylistId: String?,
    newlyCreatedPlaylistId: String?,
    onCreateAnimationFinished: (LibraryPlaylistCard) -> Unit,
    onOpenLocalLibrary: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    onStartPlaylistEditing: (LibraryPlaylistCard) -> Unit,
    onEditingPlaylistBoundsChanged: (String, Rect) -> Unit,
    onEditingPlaylistBoundsRemoved: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val parentColor = if (isDarkTheme) {
        LocalLibraryDarkBackground
    } else {
        LocalLibraryLightBackground
    }
    val outlineColor = MaterialTheme.colorScheme.outline
    val menuShape = RoundedCornerShape(LibraryMenuCornerRadius)
    val fillBrush = remember(parentColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to parentColor,
                0.28f to parentColor.copy(alpha = 0.82f),
                0.68f to parentColor.copy(alpha = 0.22f),
                1f to parentColor.copy(alpha = 0.025f)
            )
        )
    }
    val outlineBrush = remember(outlineColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.48f to Color.Transparent,
                0.76f to outlineColor.copy(alpha = 0.16f),
                1f to outlineColor.copy(alpha = 0.64f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(menuShape)
            .background(fillBrush)
            .border(
                border = BorderStroke(LibraryMenuOutlineWidth, outlineBrush),
                shape = menuShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = LibraryMenuBottomPadding),
            verticalArrangement = Arrangement.spacedBy(LibraryMenuChildSpacing)
        ) {
            LibraryHomeEntryCards(
                songCount = songCount,
                onOpenLocalLibrary = onOpenLocalLibrary,
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LibraryMenuChildHorizontalPadding)
            ) {
                LibraryPlaylistRowMotion(
                    progress = libraryPlaylistItemProgress(libraryCardsProgress, 8),
                    itemOffsetYPx = playlistItemOffsetYPx
                ) {
                    LibraryPlaylistListItem(
                        playlist = likedPlaylist,
                        itemHeight = playlistItemHeight,
                        editable = false,
                        isEditingTarget = false,
                        playCreateAnimation = false,
                        flowCloudSpeed = flowCloudSpeed,
                        isFlowCloudPlaying = isFlowCloudPlaying,
                        onCreateAnimationFinished = {},
                        onClick = { onOpenPlaylist(likedPlaylist) },
                        onLongClick = {},
                        onEditingBoundsChanged = {},
                        onEditingBoundsRemoved = {}
                    )
                }
            }
            playlists.forEachIndexed { index, playlist ->
                key(playlist.id) {
                    val editable = !playlist.isSystem
                    val isEditingTarget = editingPlaylistId == playlist.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LibraryMenuChildHorizontalPadding)
                    ) {
                        LibraryPlaylistRowMotion(
                            progress = libraryPlaylistItemProgress(
                                libraryCardsProgress,
                                12 + index * 2
                            ),
                            itemOffsetYPx = playlistItemOffsetYPx
                        ) {
                            LibraryPlaylistListItem(
                                playlist = playlist,
                                itemHeight = playlistItemHeight,
                                editable = editable,
                                isEditingTarget = editable && isEditingTarget,
                                playCreateAnimation = newlyCreatedPlaylistId == playlist.id,
                                flowCloudSpeed = flowCloudSpeed,
                                isFlowCloudPlaying = isFlowCloudPlaying,
                                onCreateAnimationFinished = {
                                    onCreateAnimationFinished(playlist)
                                },
                                onClick = {
                                    if (!isEditingTarget) onOpenPlaylist(playlist)
                                },
                                onLongClick = {
                                    if (editable) onStartPlaylistEditing(playlist)
                                },
                                onEditingBoundsChanged = {
                                    onEditingPlaylistBoundsChanged(playlist.id, it)
                                },
                                onEditingBoundsRemoved = {
                                    onEditingPlaylistBoundsRemoved(playlist.id)
                                }
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LibraryMenuChildHorizontalPadding)
            ) {
                LibraryPlaylistRowMotion(
                    progress = libraryPlaylistItemProgress(
                        libraryCardsProgress,
                        12 + playlists.size * 2
                    ),
                    itemOffsetYPx = playlistItemOffsetYPx
                ) {
                    LibraryCreatePlaylistListItem(
                        itemHeight = playlistItemHeight,
                        flowCloudSpeed = flowCloudSpeed,
                        isFlowCloudPlaying = isFlowCloudPlaying,
                        onClick = onCreatePlaylist
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryPlaylistRowMotion(
    progress: Float,
    itemOffsetYPx: Float,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.graphicsLayer {
            val easedProgress = FlowtoneMotion.Easing.transform(progress)
            alpha = easedProgress
            translationY = itemOffsetYPx * (1f - easedProgress)
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPlaylistListItem(
    playlist: LibraryPlaylistCard,
    itemHeight: Dp,
    editable: Boolean,
    isEditingTarget: Boolean,
    playCreateAnimation: Boolean,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    onCreateAnimationFinished: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditingBoundsChanged: (Rect) -> Unit,
    onEditingBoundsRemoved: () -> Unit
) {
    val createProgress = remember(playlist.id) { Animatable(if (playCreateAnimation) 0f else 1f) }
    LaunchedEffect(playCreateAnimation, playlist.id) {
        if (playCreateAnimation) {
            createProgress.snapTo(0f)
            createProgress.animateTo(1f, tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing))
            onCreateAnimationFinished()
        } else if (createProgress.value < 1f) {
            createProgress.animateTo(1f, tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing))
        }
    }
    val latestBounds = remember(playlist.id) { arrayOfNulls<Rect>(1) }
    if (editable) DisposableEffect(playlist.id) { onDispose(onEditingBoundsRemoved) }

    val clickModifier = if (editable) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = {
            onLongClick()
            latestBounds[0]?.let(onEditingBoundsChanged)
        })
    } else Modifier.clickable(onClick = onClick)
    val positionedModifier = if (editable) {
        Modifier.onGloballyPositioned { coordinates ->
            val topLeft = coordinates.positionInRoot()
            Rect(topLeft.x, topLeft.y, topLeft.x + coordinates.size.width, topLeft.y + coordinates.size.height)
                .also { bounds -> latestBounds[0] = bounds; onEditingBoundsChanged(bounds) }
        }
    } else Modifier

    if (isEditingTarget) {
        Box(
            modifier = positionedModifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )
    } else {
        LibraryPlaylistListVisual(
            playlist = playlist,
            itemHeight = itemHeight,
            appearProgress = createProgress.value,
            clickModifier = clickModifier,
            flowCloudSpeed = flowCloudSpeed,
            isFlowCloudPlaying = isFlowCloudPlaying,
            modifier = positionedModifier
        )
    }
}

@Composable
internal fun LibraryPlaylistListVisual(
    playlist: LibraryPlaylistCard,
    itemHeight: Dp,
    appearProgress: Float,
    clickModifier: Modifier,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    PlaylistListItemSurface(
        visualType = playlistCardVisualTypeFor(playlist),
        appearanceColorKey = playlist.appearanceColorKey,
        itemHeight = itemHeight,
        appearProgress = appearProgress,
        clickModifier = clickModifier,
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying,
        modifier = modifier
    ) { contentColors ->
        PlaylistListText(playlist.title, playlist.subtitle, contentColors)
    }
}

@Composable
private fun LibraryCreatePlaylistListItem(
    itemHeight: Dp,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    onClick: () -> Unit
) {
    PlaylistListItemSurface(
        visualType = PlaylistCardVisualType.CreatePlaylist,
        itemHeight = itemHeight,
        appearProgress = 1f,
        clickModifier = Modifier.clickable(onClick = onClick),
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying
    ) { contentColors ->
        PlaylistListText("创建歌单", "添加新的歌单", contentColors)
    }
}

@Composable
private fun PlaylistListItemSurface(
    visualType: PlaylistCardVisualType,
    appearanceColorKey: PlaylistAppearanceColorKey? = null,
    itemHeight: Dp,
    appearProgress: Float,
    clickModifier: Modifier,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(PlaylistCardContentColors) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clip(MaterialTheme.shapes.medium)
            .then(clickModifier)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .graphicsLayer {
                val eased = FlowtoneMotion.Easing.transform(appearProgress.coerceIn(0f, 1f))
                alpha = eased
                translationY = 12.dp.toPx() * (1f - eased)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCardSurface(
            visualType = visualType,
            appearanceColorKey = appearanceColorKey,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(0.dp),
            clickModifier = Modifier,
            flowCloudSpeed = flowCloudSpeed,
            isFlowCloudPlaying = isFlowCloudPlaying,
            modifier = Modifier.size(56.dp)
        ) { contentColors ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                contentColors.let { colors ->
                    when (visualType) {
                        PlaylistCardVisualType.LikedMusic -> Icon(Icons.Outlined.FavoriteBorder, null, tint = colors.iconColor)
                        PlaylistCardVisualType.CreatePlaylist -> Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "创建歌单", tint = colors.iconColor)
                        else -> Icon(Icons.Rounded.QueueMusic, null, tint = colors.iconColor)
                    }
                }
            }
        }
        content(PlaylistListContentColors())
    }
}

@Composable
private fun PlaylistListContentColors(): PlaylistCardContentColors {
    // Text remains readable on the page surface; artwork retains each playlist's visual style.
    return PlaylistCardContentColors(
        titleColor = MaterialTheme.colorScheme.onSurface,
        subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        actionColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RowScope.PlaylistListText(
    title: String,
    subtitle: String?,
    contentColors: PlaylistCardContentColors
) {
    Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal, color = contentColors.titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = contentColors.subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
    }
}

private fun libraryPlaylistItemProgress(globalProgress: Float, itemIndex: Int): Float {
    val delayMillis = FlowtoneMotion.staggerDelayMillis(itemIndex).toFloat()
    val durationMillis = FlowtoneMotion.staggerDurationMillis(itemIndex).coerceAtLeast(1).toFloat()
    return ((globalProgress.coerceIn(0f, 1f) * FlowtoneMotion.DurationMillis - delayMillis) / durationMillis).coerceIn(0f, 1f)
}
