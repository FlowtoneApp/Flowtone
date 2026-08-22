package ink.tenqui.flowtone.ui.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.listening.ListeningSourceStats
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.playback.PlaybackSourceType
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderPlaceholder
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork
import ink.tenqui.flowtone.ui.components.FlowtoneContentSectionTitle
import ink.tenqui.flowtone.ui.components.FlowtoneSongArtworkCard
import ink.tenqui.flowtone.ui.components.PageTransitionElement
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.playlistCardVisualTypeFor
import ink.tenqui.flowtone.ui.components.topLevelPageBackground
import ink.tenqui.flowtone.ui.player.DefaultFlowCloudSpeed
import ink.tenqui.flowtone.ui.theme.LocalMainPagesCloudPalette
import ink.tenqui.flowtone.ui.theme.monochromeFlowtoneCloudPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun HomeScreen(
    pagerState: PagerState,
    pagerFlingBehavior: FlingBehavior,
    songs: List<Song> = emptyList(),
    listeningStats: ListeningStatsSnapshot = ListeningStatsSnapshot(),
    playlists: List<LibraryPlaylistCard> = emptyList(),
    playlistSongEntries: List<PlaylistSongEntry> = emptyList(),
    onSongClick: (Song) -> Unit = {},
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit = {},
    pageScope: PageTransitionScope,
    flowCloudSpeed: Float = DefaultFlowCloudSpeed,
    isFlowCloudPlaying: Boolean = true,
    drawBackground: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    val backgroundModifier = if (drawBackground) {
        Modifier.topLevelPageBackground(
            cloudPalette = monochromeFlowtoneCloudPalette(
                LocalMainPagesCloudPalette.current.homeAccent
            )
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        HomeContent(
            pagerState = pagerState,
            pagerFlingBehavior = pagerFlingBehavior,
            songs = songs,
            listeningStats = listeningStats,
            playlists = playlists,
            playlistSongEntries = playlistSongEntries,
            onSongClick = onSongClick,
            onOpenPlaylist = onOpenPlaylist,
            pageScope = pageScope,
            flowCloudSpeed = flowCloudSpeed,
            isFlowCloudPlaying = isFlowCloudPlaying,
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp)
        )
    }
}

@Composable
private fun Modifier.controlledHorizontalListGesture(
    scrollState: ScrollState,
    pagerState: PagerState,
    pagerFlingBehavior: FlingBehavior
): Modifier {
    val listFlingBehavior = ScrollableDefaults.flingBehavior()
    var nextGestureId by remember { mutableLongStateOf(0L) }
    return pointerInput(scrollState, pagerState, pagerFlingBehavior) {
        coroutineScope {
            awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Main
            )
            val gestureId = ++nextGestureId
            val atLeftAtDown = !scrollState.canScrollBackward
            val atRightAtDown = !scrollState.canScrollForward
            logHomeHorizontalGesture(
                "DOWN id=$gestureId startInList=true owner=PENDING " +
                    "left=$atLeftAtDown right=$atRightAtDown " +
                    "page=${pagerState.currentPage} offset=${pagerState.currentPageOffsetFraction} " +
                    "pagerScrolling=${pagerState.isScrollInProgress}"
            )

            var multiTouch = false
            var owner: HomeHorizontalGestureOwner? = null
            var firstOwnerChange: androidx.compose.ui.input.pointer.PointerInputChange? = null
            while (owner == null) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { pointer -> pointer.id == down.id }
                    ?: event.changes.firstOrNull()
                    ?: break
                if (!change.pressed) {
                    logHomeHorizontalGesture("UP id=$gestureId owner=PENDING")
                    break
                }
                if (event.changes.count { pointer -> pointer.pressed } > 1) {
                    multiTouch = true
                }
                val totalX = change.position.x - down.position.x
                val totalY = change.position.y - down.position.y
                val movedEnough = abs(totalX) > viewConfiguration.touchSlop ||
                    abs(totalY) > viewConfiguration.touchSlop
                if (!movedEnough) continue
                if (abs(totalX) <= abs(totalY)) {
                    logHomeHorizontalGesture(
                        "SLOP id=$gestureId direction=VERTICAL owner=NONE"
                    )
                    break
                }
                val movingLeft = totalX < 0f
                val listCanScrollInDirection = if (movingLeft) {
                    scrollState.canScrollForward
                } else {
                    scrollState.canScrollBackward
                }
                owner = when {
                    listCanScrollInDirection -> HomeHorizontalGestureOwner.List
                    movingLeft && !scrollState.canScrollForward &&
                        pagerState.currentPage < pagerState.pageCount - 1 -> {
                        HomeHorizontalGestureOwner.PagerNext
                    }
                    !movingLeft && !scrollState.canScrollBackward &&
                        pagerState.currentPage > 0 -> {
                        HomeHorizontalGestureOwner.PagerPrevious
                    }
                    else -> HomeHorizontalGestureOwner.List
                }
                val reason = when (owner) {
                    HomeHorizontalGestureOwner.List -> if (listCanScrollInDirection) {
                        "LIST_CAN_SCROLL"
                    } else {
                        "LIST_EDGE_WITHOUT_PAGER_TARGET"
                    }
                    HomeHorizontalGestureOwner.PagerNext -> "RIGHT_EDGE_LEFT_DRAG"
                    HomeHorizontalGestureOwner.PagerPrevious -> "LEFT_EDGE_RIGHT_DRAG"
                }
                logHomeHorizontalGesture(
                    "SLOP id=$gestureId direction=${if (movingLeft) "LEFT" else "RIGHT"} " +
                        "owner=$owner reason=$reason"
                )
                firstOwnerChange = change
            }

            val finalOwner = owner ?: return@awaitEachGesture
            val initialChange = firstOwnerChange ?: return@awaitEachGesture
            var lastEventTime = initialChange.uptimeMillis
            var endVelocityX = 0f

            fun updateVelocity(change: androidx.compose.ui.input.pointer.PointerInputChange) {
                val deltaX = change.position.x - change.previousPosition.x
                val elapsedMillis = change.uptimeMillis - lastEventTime
                if (elapsedMillis > 0L && deltaX != 0f) {
                    endVelocityX = deltaX / elapsedMillis * 1_000f
                }
                lastEventTime = change.uptimeMillis
            }

            val frames = kotlinx.coroutines.channels.Channel<HomeHorizontalGestureFrame>(
                capacity = kotlinx.coroutines.channels.Channel.UNLIMITED
            )
            this@coroutineScope.launch {
                try {
                    for (frame in frames) {
                        when (frame) {
                            is HomeHorizontalGestureFrame.Drag -> {
                                if (finalOwner == HomeHorizontalGestureOwner.List) {
                                    var rawConsumption = 0f
                                    scrollState.scroll(MutatePriority.UserInput) {
                                        rawConsumption = scrollBy(-frame.deltaX)
                                    }
                                    val consumption = -rawConsumption
                                    logHomeHorizontalGesture(
                                        "DRAG id=$gestureId owner=$finalOwner delta=${frame.deltaX} " +
                                            "listConsumed=$consumption " +
                                            "remaining=${frame.deltaX - consumption} " +
                                            "page=${pagerState.currentPage} " +
                                            "offset=${pagerState.currentPageOffsetFraction} " +
                                            "pagerScrolling=${pagerState.isScrollInProgress}"
                                    )
                                } else {
                                    var rawConsumption = 0f
                                    pagerState.scroll(MutatePriority.UserInput) {
                                        rawConsumption = scrollBy(-frame.deltaX)
                                    }
                                    val consumption = -rawConsumption
                                    logHomeHorizontalGesture(
                                        "DRAG id=$gestureId owner=$finalOwner delta=${frame.deltaX} " +
                                            "pagerConsumed=$consumption " +
                                            "remaining=${frame.deltaX - consumption} " +
                                            "page=${pagerState.currentPage} " +
                                            "offset=${pagerState.currentPageOffsetFraction} " +
                                            "pagerScrolling=${pagerState.isScrollInProgress}"
                                    )
                                }
                            }

                            is HomeHorizontalGestureFrame.End -> {
                                val flingVelocity = if (frame.cancelled || frame.multiTouch) {
                                    0f
                                } else {
                                    -frame.velocityX
                                }
                                if (finalOwner == HomeHorizontalGestureOwner.List) {
                                    scrollState.scroll(MutatePriority.UserInput) {
                                        performConfiguredFling(listFlingBehavior, flingVelocity)
                                    }
                                } else {
                                    logHomeHorizontalGesture(
                                        "${if (frame.cancelled) "CANCEL" else "UP"} id=$gestureId " +
                                            "owner=$finalOwner velocity=$flingVelocity " +
                                            "target=${pagerState.targetPage}"
                                    )
                                    pagerState.scroll(MutatePriority.UserInput) {
                                        performConfiguredFling(pagerFlingBehavior, flingVelocity)
                                    }
                                    logHomeHorizontalGesture(
                                        "SETTLED id=$gestureId page=${pagerState.settledPage} " +
                                            "offset=${pagerState.currentPageOffsetFraction} " +
                                            "pagerScrolling=${pagerState.isScrollInProgress}"
                                    )
                                }
                                break
                            }
                        }
                    }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    logHomeHorizontalGesture("SCROLL_CANCEL id=$gestureId owner=$finalOwner")
                    throw error
                } finally {
                    frames.close()
                }
            }

            fun enqueueDrag(change: androidx.compose.ui.input.pointer.PointerInputChange) {
                val deltaX = change.position.x - change.previousPosition.x
                if (deltaX == 0f) return
                updateVelocity(change)
                // The custom owner consumes raw movement before HorizontalPager can
                // observe it. The actor above is the only code that scrolls state.
                change.consume()
                frames.trySend(HomeHorizontalGestureFrame.Drag(deltaX))
            }

            enqueueDrag(initialChange)
            var endedNormally = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    if (!change.pressed) {
                        endedNormally = true
                        frames.trySend(
                            HomeHorizontalGestureFrame.End(
                                velocityX = endVelocityX,
                                multiTouch = multiTouch,
                                cancelled = false
                            )
                        )
                        break
                    }
                    if (event.changes.count { it.pressed } > 1) multiTouch = true
                    enqueueDrag(change)
                }
            } finally {
                if (!endedNormally) {
                    frames.trySend(
                        HomeHorizontalGestureFrame.End(
                            velocityX = 0f,
                            multiTouch = true,
                            cancelled = true
                        )
                    )
                }
            }
            }
        }
    }
}

private enum class HomeHorizontalGestureOwner {
    List,
    PagerNext,
    PagerPrevious
}

private sealed interface HomeHorizontalGestureFrame {
    data class Drag(val deltaX: Float) : HomeHorizontalGestureFrame

    data class End(
        val velocityX: Float,
        val multiTouch: Boolean,
        val cancelled: Boolean
    ) : HomeHorizontalGestureFrame
}

private suspend fun ScrollScope.performConfiguredFling(
    flingBehavior: FlingBehavior,
    velocity: Float
): Float {
    return with(flingBehavior) { performFling(velocity) }
}

private fun logHomeHorizontalGesture(message: String) {
    Log.d("FlowtoneGesture", message)
}

@Composable
private fun HomeContent(
    pagerState: PagerState,
    pagerFlingBehavior: FlingBehavior,
    songs: List<Song>,
    listeningStats: ListeningStatsSnapshot,
    playlists: List<LibraryPlaylistCard>,
    playlistSongEntries: List<PlaylistSongEntry>,
    onSongClick: (Song) -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    pageScope: PageTransitionScope,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val recommendedSongs = remember(songs) { songs.shuffled().take(HomeRecommendationCount) }
    val recommendationScrollState = rememberScrollState()
    val recentlyAddedSongs = remember(songs) {
        songs.sortedWith(
            compareByDescending<Song> { song -> song.dateAddedSeconds }
                .thenByDescending { song -> song.id }
        ).take(HomeRecentlyAddedCount)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState, enabled = false)
            .padding(bottom = 32.dp)
    ) {
        PageTransitionElement(
            scope = pageScope,
            order = 0,
            orderCount = 3
        ) {
            FlowtonePageHeaderPlaceholder()
        }
        Spacer(modifier = Modifier.height(HomeHeaderToContentSpacing))
        PageTransitionElement(
            scope = pageScope,
            order = 1,
            orderCount = 3
        ) {
            HomeRecommendationSection(
                title = "随便听听",
                songs = recommendedSongs,
                scrollState = recommendationScrollState,
                pagerState = pagerState,
                pagerFlingBehavior = pagerFlingBehavior,
                onSongClick = onSongClick
            )
        }
        Spacer(modifier = Modifier.height(HomeSectionSpacing))
        PageTransitionElement(
            scope = pageScope,
            order = 2,
            orderCount = 3
        ) {
            RecentlyAddedSection(
                title = "最近新增",
                songs = recentlyAddedSongs,
                onSongClick = onSongClick
            )
        }
    }
}

@Composable
private fun HomeRecommendationSection(
    title: String,
    songs: List<Song>,
    scrollState: ScrollState,
    pagerState: PagerState,
    pagerFlingBehavior: FlingBehavior,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlowtoneContentSectionTitle(
            title = title,
            modifier = Modifier.padding(start = HomeHorizontalListStartPadding)
        )
        if (songs.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HomeSectionTitleToContentSpacing)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val fadeWidth = HomeRecommendationEdgeFadeWidth.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White,
                                    Color.Transparent
                                ),
                                startX = size.width - fadeWidth,
                                endX = size.width
                            ),
                            topLeft = Offset(size.width - fadeWidth, 0f),
                            size = Size(fadeWidth, size.height),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                val cardWidth = (
                    maxWidth - HomeHorizontalListStartPadding -
                        HomeHorizontalListSpacing * 2 - HomeRecommendationNextCardPeek
                ) / 3
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .controlledHorizontalListGesture(
                            scrollState = scrollState,
                            pagerState = pagerState,
                            pagerFlingBehavior = pagerFlingBehavior
                        )
                        .horizontalScroll(scrollState, enabled = false)
                ) {
                    Spacer(modifier = Modifier.width(HomeHorizontalListStartPadding))
                    songs.forEachIndexed { index, song ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.width(HomeHorizontalListSpacing))
                        }
                        FlowtoneSongArtworkCard(
                            song = song,
                            onClick = { onSongClick(song) },
                            modifier = Modifier.width(cardWidth)
                        )
                    }
                    Spacer(modifier = Modifier.width(HomeHorizontalListEndPadding))
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedSection(
    title: String,
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlowtoneContentSectionTitle(
            title = title,
            modifier = Modifier.padding(start = HomeHorizontalListStartPadding)
        )
        if (songs.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(HomeSongListItemSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HomeHorizontalListStartPadding,
                        top = HomeSectionTitleToContentSpacing,
                        end = HomeHorizontalListEndPadding
                    )
            ) {
                songs.forEach { song ->
                    RecentlyAddedSongItem(song = song, onClick = { onSongClick(song) })
                }
            }
        }
    }
}

@Composable
private fun RecentlyAddedSongItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(72.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowtoneArtwork(
            song = song,
            modifier = Modifier.size(56.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private const val HomeRecommendationCount = 8
private const val HomeRecentlyAddedCount = 3
private val HomeHorizontalListStartPadding = 20.dp
private val HomeHorizontalListEndPadding = 20.dp
private val HomeHorizontalListSpacing = 12.dp
private val HomeRecommendationNextCardPeek = 20.dp
private val HomeRecommendationEdgeFadeWidth = 64.dp
private val HomeSectionTitleToContentSpacing = 12.dp
private val HomeSectionSpacing = 24.dp
private val HomeHeaderToContentSpacing = 20.dp
private val HomeSongListItemSpacing = 4.dp
