package ink.tenqui.flowtone.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.listening.ListeningSourceStats
import ink.tenqui.flowtone.data.listening.ListeningStatsSnapshot
import ink.tenqui.flowtone.playback.PlaybackSourceType
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderPlaceholder
import ink.tenqui.flowtone.ui.components.PlaylistCardSurface
import ink.tenqui.flowtone.ui.components.StaggeredPageElement
import ink.tenqui.flowtone.ui.components.playlistCardVisualTypeFor
import ink.tenqui.flowtone.ui.player.DefaultFlowCloudSpeed
import ink.tenqui.flowtone.ui.theme.LocalMainPagesCloudPalette
import ink.tenqui.flowtone.ui.theme.FlowtoneCloudPalette
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
    onSongClick: (Song) -> Unit = {},
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit = {},
    visible: Boolean = true,
    flowCloudSpeed: Float = DefaultFlowCloudSpeed,
    isFlowCloudPlaying: Boolean = true,
    drawBackground: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    val backgroundModifier = if (drawBackground) {
        Modifier.homeScreenBackground()
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
            onSongClick = onSongClick,
            onOpenPlaylist = onOpenPlaylist,
            visible = visible,
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
internal fun Modifier.homeScreenBackground(): Modifier {
    return topLevelPageBackground(LocalMainPagesCloudPalette.current.homeAccent)
}

@Composable
internal fun Modifier.topLevelPageBackground(
    accentColor: Color,
    cloudAlpha: Float = 1f,
    cloudPlacement: TopLevelBackgroundCloudPlacement = HomeBackgroundCloudPlacement
): Modifier = topLevelPageBackground(
    cloudPalette = monochromeFlowtoneCloudPalette(accentColor),
    cloudAlpha = cloudAlpha,
    cloudPlacement = cloudPlacement
)

@Composable
internal fun Modifier.topLevelPageBackground(
    cloudPalette: FlowtoneCloudPalette,
    cloudAlpha: Float = 1f,
    cloudPlacement: TopLevelBackgroundCloudPlacement = HomeBackgroundCloudPlacement
): Modifier {
    val backgroundColor = MaterialTheme.colorScheme.background
    val safeCloudAlpha = cloudAlpha.coerceIn(0f, 1f)
    return drawBehind {
        drawRect(color = backgroundColor)
        drawTopPageColorCloud(
            cloudPalette = cloudPalette,
            backgroundColor = backgroundColor,
            cloudAlpha = safeCloudAlpha,
            cloudPlacement = cloudPlacement
        )
    }
}

internal data class TopLevelBackgroundCloudPlacement(
    val cloudCenterWidthFraction: Float,
    val cloudCenterRadiusOffsetXFactor: Float,
    val cloudCenterRadiusOffsetYFactor: Float,
    val clearCenterWidthFraction: Float,
    val clearCenterHeightFraction: Float
)

internal val HomeBackgroundCloudPlacement = TopLevelBackgroundCloudPlacement(
    cloudCenterWidthFraction = 0f,
    cloudCenterRadiusOffsetXFactor = -0.08f,
    cloudCenterRadiusOffsetYFactor = 0.08f,
    clearCenterWidthFraction = 1f,
    clearCenterHeightFraction = 1f
)

internal val LibraryBackgroundCloudPlacement = HomeBackgroundCloudPlacement.copy(
    cloudCenterWidthFraction = 0.5f,
    cloudCenterRadiusOffsetXFactor = 0f,
    cloudCenterRadiusOffsetYFactor = -0.12f
)

internal val MineBackgroundCloudPlacement = HomeBackgroundCloudPlacement.copy(
    cloudCenterWidthFraction = 1f,
    cloudCenterRadiusOffsetXFactor = 0.08f,
    clearCenterWidthFraction = 0f
)

private fun DrawScope.drawTopPageColorCloud(
    cloudPalette: FlowtoneCloudPalette,
    backgroundColor: Color,
    cloudAlpha: Float,
    cloudPlacement: TopLevelBackgroundCloudPlacement
) {
    if (cloudAlpha <= 0f) return
    val cloudDiameter = size.height * TopCloudVisibleHeightFraction * 2f /
        (1f + HomeBackgroundCloudPlacement.cloudCenterRadiusOffsetYFactor)
    if (cloudDiameter <= 0f) return

    val cloudRadius = cloudDiameter / 2f
    val cloudCenter = Offset(
        x = size.width * cloudPlacement.cloudCenterWidthFraction +
            cloudRadius * cloudPlacement.cloudCenterRadiusOffsetXFactor,
        y = cloudRadius * cloudPlacement.cloudCenterRadiusOffsetYFactor
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cloudPalette.primary.copy(alpha = 0.34f * cloudAlpha),
                cloudPalette.secondary.copy(alpha = 0.22f * cloudAlpha),
                cloudPalette.tertiary.copy(alpha = 0.08f * cloudAlpha),
                Color.Transparent
            ),
            center = cloudCenter,
            radius = cloudRadius
        ),
        radius = cloudRadius,
        center = cloudCenter
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                backgroundColor.copy(alpha = 0.98f * cloudAlpha),
                backgroundColor.copy(alpha = 0.72f * cloudAlpha),
                Color.Transparent
            ),
            center = Offset(
                x = size.width * cloudPlacement.clearCenterWidthFraction,
                y = size.height * cloudPlacement.clearCenterHeightFraction
            ),
            radius = size.minDimension * BottomRightClearRadiusFraction
        )
    )
}

private const val TopCloudVisibleHeightFraction = 1.30f
private const val BottomRightClearRadiusFraction = 0.82f

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
    onSongClick: (Song) -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    visible: Boolean,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val recommendedSongs = remember(songs) { songs.shuffled().take(HomeRecommendationCount) }
    val frequentPlaylists = remember(listeningStats.totalSources, playlists) {
        buildFrequentPlaylistCards(
            sources = listeningStats.totalSources,
            playlists = playlists
        )
    }
    val recommendationScrollState = rememberScrollState()
    val frequentPlaylistScrollState = rememberScrollState()
    val recentlyAddedScrollState = rememberScrollState()
    val recentlyAddedSongs = remember(songs) {
        songs.sortedWith(
            compareByDescending<Song> { song -> song.dateAddedSeconds }
                .thenByDescending { song -> song.id }
        ).take(HomeRecentlyAddedCount)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp)
    ) {
        StaggeredPageElement(
            visible = visible,
            animationIndex = 0
        ) {
            FlowtonePageHeaderPlaceholder()
        }
        Spacer(modifier = Modifier.height(30.dp))
        StaggeredPageElement(
            visible = visible,
            animationIndex = 4
        ) {
            HomeRecommendationSection(
                title = "随便听听",
                songs = recommendedSongs,
                scrollState = recommendationScrollState,
                onSongClick = onSongClick
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        StaggeredPageElement(
            visible = visible,
            animationIndex = 8
        ) {
            FrequentPlaylistSection(
                playlists = frequentPlaylists,
                scrollState = frequentPlaylistScrollState,
                flowCloudSpeed = flowCloudSpeed,
                isFlowCloudPlaying = isFlowCloudPlaying,
                onOpenPlaylist = onOpenPlaylist
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        StaggeredPageElement(
            visible = visible,
            animationIndex = 12
        ) {
            RecentlyAddedSection(
                title = "最近新增",
                songs = recentlyAddedSongs,
                scrollState = recentlyAddedScrollState,
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
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HomeHorizontalListStartPadding)
        )
        if (songs.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HomeHorizontalListStartPadding,
                        top = 12.dp,
                        end = HomeHorizontalListEndPadding
                    )
            ) {
                val cardWidth = (maxWidth - HomeHorizontalListSpacing * 2) / 3
                Row(horizontalArrangement = Arrangement.spacedBy(HomeHorizontalListSpacing)) {
                    songs.forEach { song ->
                        RecommendationSongCard(
                            song = song,
                            onClick = { onSongClick(song) },
                            modifier = Modifier.width(cardWidth)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequentPlaylistSection(
    playlists: List<FrequentPlaylistCard>,
    scrollState: ScrollState,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "常听歌单",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HomeHorizontalListStartPadding)
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = HomeHorizontalListStartPadding,
                    top = 12.dp,
                    end = HomeHorizontalListEndPadding
                )
        ) {
            val cardWidth = (maxWidth - HomeFrequentPlaylistCardSpacing * 2) / 3
            Row(horizontalArrangement = Arrangement.spacedBy(HomeFrequentPlaylistCardSpacing)) {
                if (playlists.isEmpty()) {
                    FrequentPlaylistPlaceholderCard(modifier = Modifier.width(cardWidth))
                } else {
                    playlists.forEach { playlist ->
                        FrequentPlaylistCardItem(
                            playlist = playlist,
                            flowCloudSpeed = flowCloudSpeed,
                            isFlowCloudPlaying = isFlowCloudPlaying,
                            onClick = { onOpenPlaylist(playlist.card) },
                            modifier = Modifier.width(cardWidth)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequentPlaylistPlaceholderCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(HomeFrequentPlaylistCardHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无数据",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FrequentPlaylistCardItem(
    playlist: FrequentPlaylistCard,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaylistCardSurface(
        visualType = playlistCardVisualTypeFor(playlist.card),
        appearanceColorKey = playlist.card.appearanceColorKey,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(12.dp),
        clickModifier = Modifier.clickable(onClick = onClick),
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying,
        modifier = modifier
            .height(HomeFrequentPlaylistCardHeight)
    ) { contentColors ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = playlist.card.title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColors.titleColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColors.subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun RecentlyAddedSection(
    title: String,
    songs: List<Song>,
    scrollState: ScrollState,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HomeHorizontalListStartPadding)
        )
        if (songs.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HomeHorizontalListStartPadding,
                        top = 12.dp,
                        end = HomeHorizontalListEndPadding
                    )
            ) {
                val columnWidth = (maxWidth - HomeHorizontalListSpacing) / 2
                Row(horizontalArrangement = Arrangement.spacedBy(HomeHorizontalListSpacing)) {
                    songs.chunked(HomeRecentlyAddedRows).forEach { columnSongs ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.width(columnWidth)
                        ) {
                            columnSongs.forEach { song ->
                                RecentlyAddedSongItem(song = song, onClick = { onSongClick(song) })
                            }
                            if (columnSongs.size == 1) Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
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
        RecommendationArtwork(
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

@Composable
private fun RecommendationSongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        RecommendationArtwork(song = song)
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
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

@Composable
private fun RecommendationArtwork(
    song: Song,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest: ImageRequest? = remember(song.artworkUri, context) {
        song.artworkUri?.let { artworkUri ->
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(264, 264)
                .build()
        }
    }
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
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = iconColor
        )
        imageRequest?.let { request ->
            AsyncImage(
                model = request,
                contentDescription = "专辑封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

private const val HomeRecommendationCount = 3
private const val HomeRecentlyAddedCount = 4
private const val HomeRecentlyAddedRows = 2
private val HomeHorizontalListStartPadding = 20.dp
private val HomeHorizontalListEndPadding = 20.dp
private val HomeHorizontalListSpacing = 12.dp
private val HomeFrequentPlaylistCardHeight = 92.dp
private val HomeFrequentPlaylistCardSpacing = 12.dp

private data class FrequentPlaylistCard(
    val card: LibraryPlaylistCard,
    val subtitle: String
)

private fun buildFrequentPlaylistCards(
    sources: List<ListeningSourceStats>,
    playlists: List<LibraryPlaylistCard>
): List<FrequentPlaylistCard> {
    val playlistsById = playlists.associateBy { playlist -> playlist.id }
    val listenedPlaylists = sources
        .asSequence()
        .filter { source ->
            source.sourceType == PlaybackSourceType.UserPlaylist ||
                source.sourceType == PlaybackSourceType.LikedSongs
        }
        .mapNotNull { source ->
            val sourceId = source.sourceId ?: return@mapNotNull null
            val playlist = playlistsById[sourceId] ?: return@mapNotNull null
            FrequentPlaylistCard(
                card = playlist,
                subtitle = playlist.subtitle
            )
        }
        .distinctBy { playlist -> playlist.card.id }
        .toList()
    val likedSongsPlaylist = playlistsById[LikedSongsPlaylistId]?.let { playlist ->
        FrequentPlaylistCard(
            card = playlist,
            subtitle = playlist.subtitle
        )
    }

    return (listenedPlaylists + listOfNotNull(likedSongsPlaylist))
        .distinctBy { playlist -> playlist.card.id }
        .take(HomeFrequentPlaylistCount)
}

private const val HomeFrequentPlaylistCount = 3
