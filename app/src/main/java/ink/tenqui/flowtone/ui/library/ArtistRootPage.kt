package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.player.localSongsForArtist

private const val ArtistHeaderHeightFraction = 0.312f
private val ArtistRootToolbarHeight = 64.dp
private val ArtistRootAvatarSize = 92.dp
private val ArtistRootSmallAvatarSize = 32.dp
private val ArtistRootHeaderCornerRadius = 24.dp
private val ArtistRootBackButtonStartPadding = 4.dp
private val ArtistRootTitleGap = 10.dp
private val ArtistRootExpandedNameTopGap = 14.dp
private val ArtistRootToolbarAnimationDistance = 14.dp
private const val ArtistRootAvatarDelayMillis = 64
private const val ArtistRootTitleDelayMillis = 128
private const val ArtistRootHeaderCardAnimationIndex = 0
private const val ArtistRootHeaderAvatarAnimationIndex = 1
private const val ArtistRootHeaderNameAnimationIndex = 2
private const val ArtistRootFirstSongAnimationIndex = 3
private const val ArtistRootVisibleSongStaggerCount = 8

@Composable
fun ArtistRootPage(
    artistName: String,
    allSongs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
    modifier: Modifier = Modifier
) {
    val displayArtist = artistName.trim()
    val listState = rememberLazyListState()
    val artistSongs = remember(displayArtist, allSongs) {
        localSongsForArtist(allSongs, displayArtist)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val density = LocalDensity.current
        val statusBarTop = with(density) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
        val toolbarHeight = ArtistRootToolbarHeight + statusBarTop
        val headerHeight = (maxHeight * ArtistHeaderHeightFraction + statusBarTop)
            .coerceAtLeast(toolbarHeight)
        val showToolbarContentThresholdPx = with(density) {
            (headerHeight - toolbarHeight)
                .roundToPx()
                .coerceAtLeast(0)
        }
        val hideToolbarContentThresholdPx = with(density) {
            (headerHeight - toolbarHeight - 24.dp)
                .roundToPx()
                .coerceAtLeast(0)
        }
        var toolbarContentVisible by remember {
            mutableStateOf(false)
        }
        LaunchedEffect(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            showToolbarContentThresholdPx,
            hideToolbarContentThresholdPx
        ) {
            val hasPassedHeaderRange =
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset >= showToolbarContentThresholdPx
            val hasReturnedToHeaderRange =
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset <= hideToolbarContentThresholdPx

            toolbarContentVisible = when {
                !toolbarContentVisible && hasPassedHeaderRange -> true
                toolbarContentVisible && hasReturnedToHeaderRange -> false
                else -> toolbarContentVisible
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "artist-header") {
                ArtistHeaderCard(
                    artistName = displayArtist,
                    height = headerHeight,
                    topPadding = statusBarTop,
                    itemModifier = itemModifier,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "artist-local-title") {
                Text(
                    text = "来自此艺术家的本地音乐",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp)
                )
            }
            if (artistSongs.isEmpty()) {
                item(key = "artist-empty") {
                    Text(
                        text = "没有找到该艺术家的本地歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = itemModifier(ArtistRootFirstSongAnimationIndex)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = artistSongs,
                    key = { index, song -> "${song.id}-${song.uri}-$index" }
                ) { index, song ->
                    val animationIndex = artistRootSongAnimationIndex(
                        songIndex = index,
                        firstVisibleItemIndex = listState.firstVisibleItemIndex
                    )
                    SongListItem(
                        song = song,
                        isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                        onClick = {
                            onSongClick(artistSongs, index)
                        },
                        modifier = itemModifier(animationIndex)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }

        ArtistRootToolbar(
            artistName = displayArtist,
            showArtist = toolbarContentVisible,
            height = toolbarHeight,
            topPadding = statusBarTop,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .zIndex(2f)
        )
    }
}

@Composable
private fun ArtistHeaderCard(
    artistName: String,
    height: Dp,
    topPadding: Dp,
    itemModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = itemModifier(ArtistRootHeaderCardAnimationIndex)
                .matchParentSize()
                .clip(
                    RoundedCornerShape(
                        bottomStart = ArtistRootHeaderCornerRadius,
                        bottomEnd = ArtistRootHeaderCornerRadius
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = topPadding)
        ) {
            ArtistAvatar(
                size = ArtistRootAvatarSize,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = itemModifier(ArtistRootHeaderAvatarAnimationIndex)
            )
            Text(
                text = artistName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = itemModifier(ArtistRootHeaderNameAnimationIndex)
                    .padding(
                        start = 24.dp,
                        top = ArtistRootExpandedNameTopGap,
                        end = 24.dp
                    )
            )
        }
    }
}

private fun artistRootSongAnimationIndex(
    songIndex: Int,
    firstVisibleItemIndex: Int
): Int {
    val visibleSongOffset = (songIndex - firstVisibleItemIndex).coerceAtLeast(0)
    return ArtistRootFirstSongAnimationIndex +
        visibleSongOffset.coerceAtMost(ArtistRootVisibleSongStaggerCount)
}

@Composable
private fun ArtistRootToolbar(
    artistName: String,
    showArtist: Boolean,
    height: Dp,
    topPadding: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toolbarAnimationDistancePx = with(LocalDensity.current) {
        ArtistRootToolbarAnimationDistance.roundToPx()
    }
    Box(
        modifier = modifier
            .height(height)
            .background(Color.Transparent)
    ) {
        AnimatedVisibility(
            visible = showArtist,
            enter = fadeIn(
                animationSpec = tween(durationMillis = 120)
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 180),
                initialOffsetY = { heightPx -> -heightPx - toolbarAnimationDistancePx }
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 90,
                    delayMillis = ArtistRootTitleDelayMillis
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = 160,
                    delayMillis = ArtistRootTitleDelayMillis
                ),
                targetOffsetY = { heightPx -> -heightPx - toolbarAnimationDistancePx }
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = ArtistRootBackButtonStartPadding,
                    top = topPadding,
                    end = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            AnimatedVisibility(
                visible = showArtist,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 120,
                        delayMillis = ArtistRootAvatarDelayMillis
                    )
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = 180,
                        delayMillis = ArtistRootAvatarDelayMillis
                    ),
                    initialOffsetY = { heightPx -> -heightPx - toolbarAnimationDistancePx }
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 90,
                        delayMillis = ArtistRootAvatarDelayMillis
                    )
                ) + slideOutVertically(
                    animationSpec = tween(
                        durationMillis = 160,
                        delayMillis = ArtistRootAvatarDelayMillis
                    ),
                    targetOffsetY = { heightPx -> -heightPx - toolbarAnimationDistancePx }
                )
            ) {
                ArtistAvatar(
                    size = ArtistRootSmallAvatarSize,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            AnimatedVisibility(
                visible = showArtist,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 120,
                        delayMillis = ArtistRootTitleDelayMillis
                    )
                ) + slideInVertically(
                    animationSpec = tween(
                        durationMillis = 180,
                        delayMillis = ArtistRootTitleDelayMillis
                    ),
                    initialOffsetY = { heightPx -> -heightPx - toolbarAnimationDistancePx }
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 90)
                ) + slideOutVertically(
                    animationSpec = tween(durationMillis = 160),
                    targetOffsetY = { heightPx -> -heightPx - toolbarAnimationDistancePx }
                )
            ) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ArtistRootTitleGap)
                )
            }
        }
    }
}

@Composable
private fun ArtistAvatar(
    size: Dp,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 50))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(size * 0.54f)
        )
    }
}
