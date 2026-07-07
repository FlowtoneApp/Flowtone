package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.isLikedSongsPlaylist
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.pullToDismissAtTop

private sealed class AddToPlaylistCardItem {
    data class Playlist(val playlist: LibraryPlaylistCard) : AddToPlaylistCardItem()
    object CreatePlaylist : AddToPlaylistCardItem()
}

@Composable
internal fun AddToPlaylistPlaylistGrid(
    playlists: List<LibraryPlaylistCard>,
    playlistIdsContainingCurrentSong: Set<String>,
    newlyCreatedPlaylistId: String?,
    onNewPlaylistCreateAnimationFinished: (String) -> Unit,
    listState: LazyListState,
    progress: Float,
    screenWidth: Dp,
    pullToDismissEnabled: Boolean,
    onDismissAtTop: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(playlists) {
        playlists
            .sortedBy { playlist -> playlist.order }
            .map<LibraryPlaylistCard, AddToPlaylistCardItem> { playlist ->
                AddToPlaylistCardItem.Playlist(playlist)
            } + AddToPlaylistCardItem.CreatePlaylist
    }
    val rows = remember(items) {
        items.chunked(2)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.pullToDismissAtTop(
            listState = listState,
            enabled = pullToDismissEnabled,
            threshold = 64.dp,
            onDismiss = onDismissAtTop
        ),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(AddToPlaylistCardSpacing)
    ) {
        itemsIndexed(
            items = rows,
            key = { _, rowItems -> rowItems.toAddToPlaylistRowKey() }
        ) { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AddToPlaylistCardSpacing)
            ) {
                rowItems.forEachIndexed { columnIndex, item ->
                    val itemIndex = rowIndex * 2 + columnIndex
                    AddToPlaylistCard(
                        item = item,
                        progress = progress,
                        screenWidth = screenWidth,
                        isLeftColumn = itemIndex % 2 == 0,
                        playCreateAnimation = when (item) {
                            is AddToPlaylistCardItem.Playlist ->
                                item.playlist.id == newlyCreatedPlaylistId
                            AddToPlaylistCardItem.CreatePlaylist -> false
                        },
                        alreadyContainsSong = when (item) {
                            is AddToPlaylistCardItem.Playlist ->
                                item.playlist.id in playlistIdsContainingCurrentSong
                            AddToPlaylistCardItem.CreatePlaylist -> false
                        },
                        onCreateAnimationFinished = onNewPlaylistCreateAnimationFinished,
                        onCreatePlaylistClick = onCreatePlaylistClick,
                        onPlaylistClick = onPlaylistClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(AddToPlaylistCardHeight)
                    )
                }

                if (rowItems.size == 1) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(AddToPlaylistCardHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistCard(
    item: AddToPlaylistCardItem,
    progress: Float,
    screenWidth: Dp,
    isLeftColumn: Boolean,
    playCreateAnimation: Boolean,
    alreadyContainsSong: Boolean,
    onCreateAnimationFinished: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardProgress = progress.coerceIn(0f, 1f)
    val itemKey = when (item) {
        is AddToPlaylistCardItem.Playlist -> item.playlist.id
        AddToPlaylistCardItem.CreatePlaylist -> "create_playlist"
    }
    val createProgress = remember(itemKey) {
        Animatable(if (playCreateAnimation) 0f else 1f)
    }
    LaunchedEffect(playCreateAnimation, itemKey) {
        if (playCreateAnimation) {
            createProgress.snapTo(0f)
            createProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
            if (item is AddToPlaylistCardItem.Playlist) {
                onCreateAnimationFinished(item.playlist.id)
            }
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
    val createEasedProgress = FlowtoneMotion.Easing.transform(
        createProgress.value.coerceIn(0f, 1f)
    )
    val startOffsetX = with(density) {
        if (isLeftColumn) {
            -screenWidth.toPx()
        } else {
            screenWidth.toPx()
        }
    }
    val cardClickEnabled =
        cardProgress > 0.99f && createEasedProgress > 0.99f && !alreadyContainsSong
    val disabledColor = Color.White.copy(alpha = 0.38f)
    val primaryContentColor = if (alreadyContainsSong) disabledColor else Color.White
    val secondaryContentColor = if (alreadyContainsSong) {
        disabledColor
    } else {
        Color.White.copy(alpha = 0.78f)
    }
    val onClick = when (item) {
        is AddToPlaylistCardItem.Playlist -> {
            { onPlaylistClick(item.playlist) }
        }
        AddToPlaylistCardItem.CreatePlaylist -> {
            onCreatePlaylistClick
        }
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = cardProgress * createEasedProgress
                translationX = lerpFloat(startOffsetX, 0f, cardProgress)
                translationY = 18.dp.toPx() * (1f - createEasedProgress)
            }
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = primaryContentColor,
                shape = RoundedCornerShape(24.dp)
            )
            .background(Color.Transparent)
            .clickable(
                enabled = cardClickEnabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        when (item) {
            is AddToPlaylistCardItem.Playlist -> {
                if (item.playlist.isLikedSongsPlaylist()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = primaryContentColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = primaryContentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text(
                    text = item.playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (item.playlist.isLikedSongsPlaylist()) {
                        Modifier.padding(top = 14.dp)
                    } else {
                        Modifier
                    }
                )
                Text(
                    text = if (alreadyContainsSong) {
                        "\u6b64\u6b4c\u66f2\u5df2\u5b58\u5728"
                    } else {
                        item.playlist.subtitle
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            AddToPlaylistCardItem.CreatePlaylist -> {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "创建歌单",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "创建歌单",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    text = "新歌单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun List<AddToPlaylistCardItem>.toAddToPlaylistRowKey(): String {
    return joinToString(separator = "_") { item ->
        when (item) {
            is AddToPlaylistCardItem.Playlist -> item.playlist.id
            AddToPlaylistCardItem.CreatePlaylist -> "create_playlist"
        }
    }
}
