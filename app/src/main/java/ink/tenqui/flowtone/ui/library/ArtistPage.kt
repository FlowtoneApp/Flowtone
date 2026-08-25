package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.player.localSongsForArtist

private val ArtistHeaderMinimumContentHeight = 248.dp
private val ArtistToolbarHeight = 64.dp
private val ArtistAvatarSize = 112.dp
private val ArtistCompactAvatarSize = 104.dp
private val ArtistSmallAvatarSize = 32.dp
private val ArtistHeaderCornerRadius = 24.dp
private val ArtistBackButtonStartPadding = 4.dp
private val ArtistTitleGap = 10.dp
private val ArtistHeaderContentTopGap = 16.dp
private val ArtistHeaderAvatarNameGap = 16.dp
private val ArtistHeaderStatisticsBottomPadding = 22.dp
private val ArtistHeaderStatisticsEndPadding = 20.dp
private val ArtistToolbarAnimationDistance = 14.dp
private val ArtistAlbumArtworkSize = 140.dp
private const val ArtistAvatarDelayMillis = 64
private const val ArtistTitleDelayMillis = 128
private const val ArtistHeaderCardAnimationIndex = 0
private const val ArtistHeaderAvatarAnimationIndex = 1
private const val ArtistHeaderNameAnimationIndex = 2
private const val ArtistHeaderAliasAnimationIndex = 3
private const val ArtistHeaderBiographyAnimationIndex = 4
private const val ArtistHeaderStatsAnimationIndex = 5
private const val ArtistSongsTitleAnimationIndex = 6
private const val ArtistFirstSongAnimationIndex = 7
private const val ArtistAlbumsTitleAnimationIndex = 11
private const val ArtistAlbumCardsAnimationIndex = 12
internal const val ArtistTransitionOrderCount = ArtistAlbumCardsAnimationIndex + 1
private const val ArtistFirstSongListItemIndex = 2

@Composable
internal fun ArtistPage(
    artistName: String,
    allSongs: List<Song>,
    albums: List<LocalAlbum>,
    currentSong: Song?,
    onBack: () -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    pageTransition: PageTransitionScope,
    itemModifier: (pageProgress: Float, order: Int, orderCount: Int) -> Modifier =
        { _, _, _ -> Modifier },
    modifier: Modifier = Modifier
) {
    val displayArtist = artistName.trim()
    val listState = rememberLazyListState()
    val artistSongs = remember(displayArtist, allSongs) {
        localSongsForArtist(allSongs, displayArtist)
    }
    val artistAlbums = remember(displayArtist, albums) {
        artistAlbumsFor(albums, displayArtist)
    }
    val artistSongKeys = remember(artistSongs) {
        artistSongs.mapIndexed(::artistSongItemKey)
    }
    val avatarLookupSongTitle = remember(artistSongs, currentSong) {
        val currentArtistSong = currentSong?.takeIf { playingSong ->
            artistSongs.any { artistSong ->
                artistSong.id == playingSong.id || artistSong.uri == playingSong.uri
            }
        }
        (currentArtistSong ?: artistSongs.firstOrNull())?.title.orEmpty()
    }
    val artistAvatarImage = rememberExperimentalArtistAvatarImage(
        songTitle = avatarLookupSongTitle,
        artistName = displayArtist
    )
    val artistMetadata = rememberArtistMetadata(displayArtist)

    val visibleSongKeys by remember(listState, artistSongKeys) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                artistSongKeys.getOrNull(item.index - ArtistFirstSongListItemIndex)
            }.distinct()
        }
    }
    var frozenTransitionId by remember(displayArtist) { mutableStateOf<Int?>(null) }
    var frozenViewportKeys by remember(displayArtist) {
        mutableStateOf<List<String>>(emptyList())
    }
    var capturedPageProgress by remember(displayArtist) { mutableStateOf(0f) }

    LaunchedEffect(
        pageTransition.transitionId,
        pageTransition.phase,
        visibleSongKeys
    ) {
        if (pageTransition.phase == PageTransitionPhase.Current) {
            frozenTransitionId = null
            frozenViewportKeys = emptyList()
            capturedPageProgress = 0f
        } else if (
            frozenTransitionId != pageTransition.transitionId &&
            visibleSongKeys.isNotEmpty()
        ) {
            frozenTransitionId = pageTransition.transitionId
            frozenViewportKeys = visibleSongKeys
            capturedPageProgress = pageTransition.progress.coerceIn(0f, 1f)
        }
    }

    val animationGroupKeys = if (pageTransition.phase == PageTransitionPhase.Current) {
        visibleSongKeys
    } else if (pageTransition.phase == PageTransitionPhase.Incoming) {
        frozenViewportKeys
    } else {
        frozenViewportKeys.ifEmpty { visibleSongKeys }
    }
    val listProgress = when {
        pageTransition.phase != PageTransitionPhase.Incoming -> pageTransition.progress
        frozenViewportKeys.isEmpty() -> 0f
        else -> {
            val remaining = (1f - capturedPageProgress).coerceAtLeast(0.0001f)
            ((pageTransition.progress - capturedPageProgress) / remaining).coerceIn(0f, 1f)
        }
    }
    val enterGroupReady = pageTransition.phase != PageTransitionPhase.Incoming ||
        frozenViewportKeys.isNotEmpty()
    val animationOrderByKey = remember(animationGroupKeys) {
        animationGroupKeys.withIndex().associate { (order, key) -> key to order }
    }

    fun fixedItemModifier(index: Int): Modifier {
        return pageTransition.elementModifier(index, ArtistTransitionOrderCount)
    }

    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val toolbarHeight = ArtistToolbarHeight + statusBarTop
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val defaultHeaderHeightPx = with(density) {
        (ArtistHeaderMinimumContentHeight + statusBarTop).roundToPx()
    }
    val measuredHeaderHeightPx = headerHeightPx.takeIf { it > 0 } ?: defaultHeaderHeightPx
    val toolbarHeightPx = with(density) { toolbarHeight.roundToPx() }
    val showToolbarContentThresholdPx = (measuredHeaderHeightPx - toolbarHeightPx).coerceAtLeast(0)
    val hideToolbarContentThresholdPx = (showToolbarContentThresholdPx - with(density) {
        24.dp.roundToPx()
    }).coerceAtLeast(0)
    var toolbarContentVisible by remember {
        mutableStateOf(
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset >= showToolbarContentThresholdPx
        )
    }

    LaunchedEffect(
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        showToolbarContentThresholdPx,
        hideToolbarContentThresholdPx
    ) {
        val hasPassedHeaderRange = listState.firstVisibleItemIndex > 0 ||
            listState.firstVisibleItemScrollOffset >= showToolbarContentThresholdPx
        val hasReturnedToHeaderRange = listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset <= hideToolbarContentThresholdPx
        toolbarContentVisible = when {
            !toolbarContentVisible && hasPassedHeaderRange -> true
            toolbarContentVisible && hasReturnedToHeaderRange -> false
            else -> toolbarContentVisible
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // The surface stays behind the Header while its large avatar is still in the viewport.
        // Once the Header leaves, the foreground layer preserves the same toolbar presentation.
        ArtistToolbarBackground(
            visible = toolbarContentVisible,
            height = toolbarHeight,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "artist-header") {
                ArtistHeaderCard(
                    artistName = displayArtist,
                    statistics = artistStatisticsText(artistSongs.size, artistAlbums.size),
                    avatarImage = artistAvatarImage,
                    topPadding = statusBarTop,
                    alias = artistMetadata?.aliases?.take(3)?.joinToString(" · "),
                    biography = artistMetadata?.biography,
                    itemModifier = ::fixedItemModifier,
                    onHeightChanged = { headerHeightPx = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "artist-songs-title") {
                ArtistSectionTitle(
                    title = "歌曲",
                    modifier = fixedItemModifier(ArtistSongsTitleAnimationIndex)
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp)
                )
            }
            if (artistSongs.isEmpty()) {
                item(key = "artist-empty") {
                    Text(
                        text = "没有找到该艺术家的歌曲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = fixedItemModifier(ArtistFirstSongAnimationIndex)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = artistSongs,
                    key = ::artistSongItemKey
                ) { index, song ->
                    val songKey = artistSongKeys[index]
                    val viewportOrder = animationOrderByKey[songKey] ?: 0
                    val viewportOrderCount = animationGroupKeys.size.coerceAtLeast(1)
                    SongListItem(
                        song = song,
                        isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                        onClick = { onSongClick(artistSongs, index) },
                        modifier = if (enterGroupReady) {
                            itemModifier(listProgress, viewportOrder, viewportOrderCount)
                        } else {
                            Modifier.graphicsLayer { alpha = 0f }
                        }.padding(horizontal = 8.dp)
                    )
                }
            }
            if (artistAlbums.isNotEmpty()) {
                item(key = "artist-albums") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ArtistSectionTitle(
                            title = "专辑",
                            modifier = fixedItemModifier(ArtistAlbumsTitleAnimationIndex)
                                .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 12.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(artistAlbums, key = LocalAlbum::id) { album ->
                                ArtistAlbumCard(
                                    album = album,
                                    onClick = { onOpenAlbum(album.id) },
                                    modifier = fixedItemModifier(ArtistAlbumCardsAnimationIndex)
                                )
                            }
                        }
                    }
                }
            }
        }

        val headerHasLeftViewport = listState.firstVisibleItemIndex > 0 ||
            listState.firstVisibleItemScrollOffset >= measuredHeaderHeightPx
        if (toolbarContentVisible && headerHasLeftViewport) {
            ArtistToolbarForegroundSurface(
                height = toolbarHeight,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .zIndex(1f)
            )
        }

        ArtistToolbarContent(
            artistName = displayArtist,
            avatarImage = artistAvatarImage,
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
    statistics: String,
    avatarImage: ExtensionImage?,
    topPadding: Dp,
    alias: String? = null,
    biography: String? = null,
    itemModifier: (Int) -> Modifier,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayAlias = alias?.trim()?.takeIf(String::isNotEmpty)
    val displayBiography = biography?.trim()?.takeIf(String::isNotEmpty)
    BoxWithConstraints(
        modifier = modifier
            .heightIn(min = ArtistHeaderMinimumContentHeight + topPadding)
            .onSizeChanged { size -> onHeightChanged(size.height) }
            .clip(
                RoundedCornerShape(
                    bottomStart = ArtistHeaderCornerRadius,
                    bottomEnd = ArtistHeaderCornerRadius
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(itemModifier(ArtistHeaderCardAnimationIndex))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        val avatarSize = if (maxWidth < 380.dp) ArtistCompactAvatarSize else ArtistAvatarSize
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    top = topPadding + ArtistToolbarHeight + ArtistHeaderContentTopGap,
                    end = 20.dp,
                    bottom = ArtistHeaderStatisticsBottomPadding + 26.dp
                )
        ) {
            ArtistAvatar(
                size = avatarSize,
                image = avatarImage,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = itemModifier(ArtistHeaderAvatarAnimationIndex)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ArtistHeaderAvatarNameGap)
            ) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = itemModifier(ArtistHeaderNameAnimationIndex)
                )
                if (displayAlias != null) {
                    Text(
                        text = displayAlias,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = itemModifier(ArtistHeaderAliasAnimationIndex)
                            .padding(top = 4.dp)
                    )
                }
                if (displayBiography != null) {
                    Text(
                        text = displayBiography,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = itemModifier(ArtistHeaderBiographyAnimationIndex)
                            .padding(top = if (displayAlias == null) 12.dp else 10.dp)
                    )
                }
            }
        }
        Text(
            text = statistics,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = itemModifier(ArtistHeaderStatsAnimationIndex)
                .align(Alignment.BottomEnd)
                .padding(
                    end = ArtistHeaderStatisticsEndPadding,
                    bottom = ArtistHeaderStatisticsBottomPadding
                )
        )
    }
}

@Composable
private fun ArtistSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
private fun ArtistAlbumCard(
    album: LocalAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(ArtistAlbumArtworkSize)
            .clickable(onClick = onClick)
    ) {
        FlowtoneArtwork(
            artworkUri = album.artworkUri,
            modifier = Modifier.size(ArtistAlbumArtworkSize)
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "${album.songs.size} 首歌曲",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun artistSongItemKey(index: Int, song: Song): String =
    "${song.id}-${song.uri}-$index"

@Composable
private fun ArtistToolbarBackground(
    visible: Boolean,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.height(height).background(Color.Transparent)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(120)) + slideInVertically(tween(180)) { -it },
            exit = fadeOut(tween(90, delayMillis = ArtistTitleDelayMillis)) +
                slideOutVertically(tween(160, delayMillis = ArtistTitleDelayMillis)) { -it },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

@Composable
private fun ArtistToolbarForegroundSurface(
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

@Composable
private fun ArtistToolbarContent(
    artistName: String,
    avatarImage: ExtensionImage?,
    showArtist: Boolean,
    height: Dp,
    topPadding: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toolbarAnimationDistancePx = with(LocalDensity.current) {
        ArtistToolbarAnimationDistance.roundToPx()
    }
    Box(modifier = modifier.height(height).background(Color.Transparent)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(
                start = ArtistBackButtonStartPadding,
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
                enter = fadeIn(tween(120, delayMillis = ArtistAvatarDelayMillis)) +
                    slideInVertically(tween(180, delayMillis = ArtistAvatarDelayMillis)) {
                        -it - toolbarAnimationDistancePx
                    },
                exit = fadeOut(tween(90, delayMillis = ArtistAvatarDelayMillis)) +
                    slideOutVertically(tween(160, delayMillis = ArtistAvatarDelayMillis)) {
                        -it - toolbarAnimationDistancePx
                    }
            ) {
                ArtistAvatar(
                    size = ArtistSmallAvatarSize,
                    image = avatarImage,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            AnimatedVisibility(
                visible = showArtist,
                enter = fadeIn(tween(120, delayMillis = ArtistTitleDelayMillis)) +
                    slideInVertically(tween(180, delayMillis = ArtistTitleDelayMillis)) {
                        -it - toolbarAnimationDistancePx
                    },
                exit = fadeOut(tween(90)) + slideOutVertically(tween(160)) {
                    -it - toolbarAnimationDistancePx
                }
            ) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = ArtistTitleGap)
                )
            }
        }
    }
}

@Composable
private fun ArtistAvatar(
    size: Dp,
    image: ExtensionImage?,
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
        ExperimentalArtistAvatarImage(image = image, modifier = Modifier.fillMaxSize())
    }
}
