package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem

@Composable
internal fun ArtistPlaceholderOverlay(
    artists: List<String>,
    artistSongs: List<Song>,
    currentSong: Song?,
    progress: Float,
    backGestureThresholdPx: Float,
    backGestureEnabled: Boolean,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        return
    }
    val localSongListState = rememberLazyListState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .fullscreenContentBackGesture(
                enabled = backGestureEnabled,
                thresholdPx = backGestureThresholdPx,
                canStartPullDown = {
                    artists.size != 1 || localSongListState.isScrolledToTop()
                },
                onBack = onBack
            )
    ) {
        val overlayProgress = progress.coerceIn(0f, 1f)
        val singleArtist = artists.size == 1
        val contentHeight =
            ArtistPlaceholderAvatarSize + ArtistPlaceholderAvatarGap + ArtistPlaceholderNameHeight
        val multiArtistContentHeight =
            ArtistPlaceholderHintHeight + ArtistPlaceholderHintBottomGap + contentHeight
        val contentTop = if (!singleArtist) {
            (maxHeight - multiArtistContentHeight) / 2f
        } else {
            val nameCenterY = maxHeight * ArtistPlaceholderNameYFraction
            val nameTop = (nameCenterY - ArtistPlaceholderNameHeight / 2f)
                .coerceAtLeast(0.dp)
            (nameTop - ArtistPlaceholderAvatarGap - ArtistPlaceholderAvatarSize)
                .coerceAtLeast(0.dp)
        }

        if (singleArtist) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = contentTop)
                    .fillMaxWidth()
                    .height(contentHeight)
                    .graphicsLayer {
                        alpha = overlayProgress
                        translationY = 18.dp.toPx() * (1f - overlayProgress)
                    },
                contentPadding = PaddingValues(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 20.dp,
                    alignment = Alignment.CenterHorizontally
                )
            ) {
                itemsIndexed(
                    items = artists,
                    key = { index, artist -> "$index:$artist" }
                ) { _, artist ->
                    ArtistPlaceholderItem(
                        artist = artist,
                        onArtistClick = onArtistClick
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = contentTop)
                    .fillMaxWidth()
                    .height(multiArtistContentHeight)
                    .graphicsLayer {
                        alpha = overlayProgress
                        translationY = 18.dp.toPx() * (1f - overlayProgress)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择一位艺人以查看详情",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ArtistPlaceholderHintHeight)
                        .padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(ArtistPlaceholderHintBottomGap))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentHeight),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 20.dp,
                        alignment = Alignment.CenterHorizontally
                    )
                ) {
                    itemsIndexed(
                        items = artists,
                        key = { index, artist -> "$index:$artist" }
                    ) { _, artist ->
                        ArtistPlaceholderItem(
                            artist = artist,
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }

        if (singleArtist) {
            val listTop = contentTop + contentHeight + ArtistPlaceholderListTopGap
            val listHeight = (maxHeight - listTop - 28.dp).coerceAtLeast(156.dp)
            ArtistPlaceholderLocalSongList(
                artistName = artists.first(),
                songs = artistSongs,
                currentSong = currentSong,
                listState = localSongListState,
                onSongClick = onSongClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = listTop)
                    .fillMaxWidth()
                    .height(listHeight)
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        alpha = overlayProgress
                        translationY = 18.dp.toPx() * (1f - overlayProgress)
                    }
            )
        }
    }
}

@Composable
private fun ArtistPlaceholderLocalSongList(
    artistName: String,
    songs: List<Song>,
    currentSong: Song?,
    listState: LazyListState,
    onSongClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 12.dp)
    ) {
        Text(
            text = "本地音乐中的 $artistName",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有找到该艺术家的本地歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {
                itemsIndexed(
                    items = songs,
                    key = { index, song -> "${song.id}-${song.uri}-$index" }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                        onClick = {
                            onSongClick(songs, index)
                        },
                        titleColor = Color.White,
                        artistColor = Color.White.copy(alpha = 0.76f),
                        durationColor = Color.White.copy(alpha = 0.72f),
                        currentSongBackgroundColor = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun LazyListState.isScrolledToTop(): Boolean {
    return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

@Composable
private fun ArtistPlaceholderItem(
    artist: String,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.width(ArtistPlaceholderItemWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(ArtistPlaceholderAvatarSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFFB8B8B8).copy(alpha = 0.72f))
                .clickable(
                    interactionSource = noRippleInteractionSource,
                    indication = null,
                    onClick = { onArtistClick(artist) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(ArtistPlaceholderAvatarSize * 0.54f)
            )
        }
        Text(
            text = artist,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(ArtistPlaceholderNameHeight)
                .clickable(
                    interactionSource = noRippleInteractionSource,
                    indication = null,
                    onClick = { onArtistClick(artist) }
                )
                .padding(top = 8.dp)
        )
    }
}
