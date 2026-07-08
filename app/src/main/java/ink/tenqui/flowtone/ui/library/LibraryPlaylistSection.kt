package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

internal fun LazyListScope.libraryPlaylistRows(
    likedPlaylist: LibraryPlaylistCard,
    playlistRows: List<List<LibraryPlaylistCard>>,
    playlistCardHeight: Dp,
    libraryCardsProgress: Float,
    playlistRowItemOffsetYPx: Float,
    activePlaylistActionId: String?,
    newlyCreatedPlaylistId: String?,
    onCreateAnimationFinished: (LibraryPlaylistCard) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    onShowPlaylistActions: (String) -> Unit,
    onRenamePlaylist: (LibraryPlaylistCard) -> Unit,
    onDeletePlaylist: (LibraryPlaylistCard) -> Unit
) {
    item(key = "library-pinned-playlist-row") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .libraryPlaylistRowMotion(
                    globalProgress = libraryCardsProgress,
                    rowIndex = 1,
                    rowAppearProgress = 1f,
                    itemOffsetYPx = playlistRowItemOffsetYPx
                ),
            horizontalArrangement = Arrangement.spacedBy(LibraryActionCardSpacing)
        ) {
            LibraryPlaylistTileCardView(
                playlist = likedPlaylist,
                cardHeight = playlistCardHeight,
                editable = false,
                showActions = false,
                playCreateAnimation = false,
                onCreateAnimationFinished = {},
                onClick = {
                    onOpenPlaylist(likedPlaylist)
                },
                onLongClick = {},
                onEdit = {},
                onDelete = {},
                modifier = Modifier.weight(1f)
            )
            LibraryCreatePlaylistTileCardView(
                cardHeight = playlistCardHeight,
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
                    rowIndex = rowIndex + 2,
                    rowAppearProgress = 1f,
                    itemOffsetYPx = playlistRowItemOffsetYPx
                ),
            horizontalArrangement = Arrangement.spacedBy(LibraryActionCardSpacing)
        ) {
            rowPlaylists.forEach { playlist ->
                val showActions = activePlaylistActionId == playlist.id
                val editable = !playlist.isSystem
                LibraryPlaylistTileCardView(
                    playlist = playlist,
                    cardHeight = playlistCardHeight,
                    editable = editable,
                    showActions = editable && showActions,
                    playCreateAnimation =
                        newlyCreatedPlaylistId == playlist.id,
                    onCreateAnimationFinished = {
                        onCreateAnimationFinished(playlist)
                    },
                    onClick = {
                        onOpenPlaylist(playlist)
                    },
                    onLongClick = {
                        if (editable) {
                            onShowPlaylistActions(playlist.id)
                        }
                    },
                    onEdit = {
                        onRenamePlaylist(playlist)
                    },
                    onDelete = {
                        onDeletePlaylist(playlist)
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
    showActions: Boolean,
    playCreateAnimation: Boolean,
    onCreateAnimationFinished: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionProgress by animateFloatAsState(
        targetValue = if (showActions) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis / 2,
            easing = FlowtoneMotion.Easing
        ),
        label = "LibraryPlaylistActionButtons"
    )
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
    val actionButtonColor = MaterialTheme.colorScheme.onSurface
    val editActionIconSize = 24.dp
    val editActionTouchSize = 36.dp
    val isLikedPlaylist = playlist.isLikedSongsPlaylist()
    val cardClickModifier = if (editable) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    LibraryPlaylistTileSurface(
        cardHeight = cardHeight,
        appearProgress = createProgress.value,
        clickModifier = cardClickModifier,
        modifier = modifier
    ) {
        LibraryPlaylistTileTextContent(
            title = playlist.title,
            subtitle = playlist.subtitle,
            reserveActionWidth = editable,
            icon = if (isLikedPlaylist) {
                {
                    LikedSongsPlaylistTileIcon()
                }
            } else {
                null
            }
        )

        if (showActions || actionProgress > 0.001f) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        val eased = FlowtoneMotion.Easing.transform(
                            actionProgress.coerceIn(0f, 1f)
                        )
                        alpha = eased
                        translationX = 18.dp.toPx() * (1f - eased)
                        scaleX = 0.96f + 0.04f * eased
                        scaleY = 0.96f + 0.04f * eased
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(editActionTouchSize)
                        .clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "\u7f16\u8f91\u6b4c\u5355",
                        tint = actionButtonColor,
                        modifier = Modifier.size(editActionIconSize)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(editActionTouchSize)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "\u5220\u9664\u6b4c\u5355",
                        tint = actionButtonColor,
                        modifier = Modifier.size(editActionIconSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCreatePlaylistTileCardView(
    cardHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LibraryPlaylistTileSurface(
        cardHeight = cardHeight,
        appearProgress = 1f,
        clickModifier = Modifier.clickable(onClick = onClick),
        modifier = modifier
    ) {
        LibraryPlaylistTileTextContent(
            title = "\u521b\u5efa\u6b4c\u5355",
            subtitle = "\u6dfb\u52a0\u65b0\u7684\u6b4c\u5355",
            reserveActionWidth = false,
            icon = {
                CreatePlaylistTileIcon()
            }
        )
    }
}

@Composable
private fun LibraryPlaylistTileSurface(
    cardHeight: Dp,
    appearProgress: Float,
    clickModifier: Modifier,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.height(cardHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .graphicsLayer {
                    val eased = FlowtoneMotion.Easing.transform(
                        appearProgress.coerceIn(0f, 1f)
                    )
                    alpha = eased
                    translationY = 18.dp.toPx() * (1f - eased)
                }
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .then(clickModifier)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            content = content
        )
    }
}

@Composable
private fun BoxScope.LibraryPlaylistTileTextContent(
    title: String,
    subtitle: String?,
    reserveActionWidth: Boolean,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(end = if (reserveActionWidth) 44.dp else 0.dp),
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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun LikedSongsPlaylistTileIcon() {
    LibraryPlaylistTileIconContainer(
        backgroundColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CreatePlaylistTileIcon() {
    LibraryPlaylistTileIconContainer(
        backgroundColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = "\u521b\u5efa\u6b4c\u5355",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
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
