package ink.tenqui.flowtone.app

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.BuildConfig
import ink.tenqui.flowtone.ui.components.FlowtoneCollectionArtworkCard
import ink.tenqui.flowtone.ui.components.FlowtoneContentSectionTitle
import ink.tenqui.flowtone.ui.components.FlowtoneContentCardShape
import ink.tenqui.flowtone.ui.components.FlowtonePageHeader
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderBodyGap
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedTopPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedEndPadding
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.flowtoneRoundedClickable
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.library.FastInteractionTrace
import ink.tenqui.flowtone.ui.screens.LegacyHomeScreen

// A/B 共用同一包装器，A 原样报告状态，B 仅隐藏 isInProgress。
internal class HomeOverscrollExperiment(
    private val original: OverscrollEffect,
    private val hideProgress: Boolean = true,
    private val diagnostics: HomeInputDiagnostics? = null
) : OverscrollEffect by original {
    override val isInProgress: Boolean
        get() = if (hideProgress) false else original.isInProgress

    override fun applyToScroll(
        delta: Offset,
        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset {
        val effectWasInProgress = original.isInProgress
        var performInput = Offset.Zero
        var performConsumed = Offset.Zero
        val result = original.applyToScroll(delta, source) { available ->
            performInput += available
            val consumed = performScroll(available)
            performConsumed += consumed
            consumed
        }
        diagnostics?.recordOverscrollScroll(
            input = delta,
            performInput = performInput,
            performConsumed = performConsumed,
            effectConsumed = result,
            wasInProgress = effectWasInProgress,
            isInProgress = original.isInProgress
        )
        return result
    }
}

/** 按当前会话种子从本地歌曲池构造不重复的首页推荐。 */
@Composable
internal fun HomeGalleryContent(
    selection: HomeGallerySelection?,
    scrollState: ScrollState,
    pagerState: PagerState,
    pagerFlingBehavior: FlingBehavior,
    pagerScrollEnabled: Boolean,
    pageScope: PageTransitionScope,
    songs: List<Song>,
    minimalSongs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val homeIsolationMode = if (BuildConfig.BUILD_TYPE == "fast") {
        BuildConfig.HOME_ISOLATION_MODE
    } else {
        "normal"
    }
    val minimalMode = homeIsolationMode == "baseline"
    val showHero = homeIsolationMode == "normal" || homeIsolationMode == "hero"
    val showDiscovery = homeIsolationMode == "normal" || homeIsolationMode == "discovery"
    val showSongs = homeIsolationMode == "normal" || homeIsolationMode == "songs"
    val discoveryListState = if (showDiscovery) rememberLazyListState() else null
    // 显式持有默认回弹实例，仅用于读取诊断状态，仍由原滚动容器绘制和处理。
    val homeOverscroll = rememberOverscrollEffect()
    val discoveryOverscroll = if (showDiscovery) rememberOverscrollEffect() else null
    val experimentEnabled = BuildConfig.HOME_OVERSCROLL_EXPERIMENT_ENABLED
    // fast 关闭了 debuggable，临时诊断仍需覆盖当前实际复现的构建变体。
    val diagnostics = if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "fast") {
        remember(scrollState, discoveryListState, pagerState, homeOverscroll, discoveryOverscroll) {
            HomeInputDiagnostics(
                scrollState, discoveryListState, pagerState, homeOverscroll, discoveryOverscroll,
                experimentEnabled,
                homeMode = if (minimalMode) "minimal" else "normal",
                homeIsolation = homeIsolationMode
            )
        }
    } else {
        null
    }
    val effectiveHomeOverscroll = remember(homeOverscroll, experimentEnabled, diagnostics) {
        if (homeOverscroll != null && diagnostics != null) {
            HomeOverscrollExperiment(homeOverscroll, hideProgress = experimentEnabled, diagnostics)
        } else if (experimentEnabled && homeOverscroll != null) {
            HomeOverscrollExperiment(homeOverscroll, hideProgress = true)
        } else {
            homeOverscroll
        }
    }
    if (diagnostics != null) {
        LaunchedEffect(diagnostics) {
            Log.d(
                HomeInputDiagnosticTag,
                "ENABLED buildType=${BuildConfig.BUILD_TYPE} " +
                    "homeMode=${if (minimalMode) "minimal" else "normal"} " +
                    "homeIsolation=$homeIsolationMode " +
                    "overscrollMode=${if (experimentEnabled) "B" else "A"}"
            )
        }
    }
    val onGallerySongClick: (Song) -> Unit = { song ->
        if (diagnostics == null) {
            onSongClick(song)
        } else {
            diagnostics.traceClick(song) { onSongClick(song) }
        }
    }
    val onSongPressInteraction: ((Song) -> Unit)? = if (BuildConfig.BUILD_TYPE == "fast") {
        { pressedSong ->
            FastInteractionTrace.record(
                stage = "PRESS_INTERACTION_COLLECTED",
                page = "home-gallery",
                song = pressedSong,
                details = "source=MutableInteractionSource collector; visual frame not measured"
            )
        }
    } else {
        null
    }

    if (homeIsolationMode == "original-hero") {
        OriginalHeroHomeContent(
            selection = checkNotNull(selection),
            scrollState = scrollState,
            onSongClick = onSongClick,
            modifier = modifier
        )
    } else if (homeIsolationMode == "legacy") {
        LegacyHomeScreen(
            pagerState = pagerState,
            pagerFlingBehavior = pagerFlingBehavior,
            pagerScrollEnabled = pagerScrollEnabled,
            songs = songs,
            onSongClick = onGallerySongClick,
            pageScope = pageScope,
            drawBackground = false,
            scrollState = scrollState,
            modifier = modifier.observeHomeInput("outsideScroll", diagnostics)
        )
    } else {
        Column(
            modifier = modifier
                .observeHomeInput("outsideScroll", diagnostics)
                .verticalScroll(scrollState, overscrollEffect = effectiveHomeOverscroll)
                .observeHomeInput("insideScroll", diagnostics)
                .padding(bottom = 32.dp)
        ) {
        // 与覆盖层使用相同文本和约束，让字体缩放下的占位也覆盖完整标题。
        FlowtonePageHeader(
            title = stringResource(R.string.top_level_home_title),
            subtitle = stringResource(R.string.top_level_home_subtitle),
            modifier = Modifier.fillMaxWidth()
                .padding(
                    start = FlowtonePageHeaderExpandedStartPadding,
                    top = FlowtonePageHeaderExpandedTopPadding,
                    end = FlowtonePageHeaderExpandedEndPadding
                )
                .alpha(0f)
                .clearAndSetSemantics { }
        )
        Spacer(Modifier.height(FlowtonePageHeaderBodyGap))
        if (minimalMode) {
            HomeInteractionBaselineSongs(
                songs = minimalSongs,
                currentSong = currentSong,
                onSongClick = onGallerySongClick,
                onSongPressInteraction = onSongPressInteraction
            )
        } else {
        val gallerySelection = checkNotNull(selection)
        val discoverySongs = gallerySelection.discoverySongs
        val suggestedSongs = gallerySelection.suggestedSongs
        val featuredSong = gallerySelection.featuredSong
        if (showHero && featuredSong != null) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                val contentWidth = maxWidth - 24.dp
                val artworkSize = minOf(132.dp, contentWidth * 0.4f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .flowtoneRoundedClickable { onGallerySongClick(featuredSong) }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(artworkSize)
                                .clip(FlowtoneContentCardShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            AsyncImage(
                                model = featuredSong.artworkUri,
                                contentDescription = "封面",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "本地音乐 · 随机发现",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                featuredSong.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                featuredSong.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "点击播放",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        } else if (showHero) {
            Text("曲库中暂无音乐", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (showDiscovery && discoverySongs.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            FlowtoneContentSectionTitle("发现一些声音", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            LazyRow(
                state = checkNotNull(discoveryListState),
                overscrollEffect = discoveryOverscroll,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(discoverySongs, key = { "${it.sourceType.name}:${it.id}" }) { song ->
                    FlowtoneCollectionArtworkCard(
                        title = song.title, subtitle = song.artist, artworkUri = song.artworkUri,
                        onClick = { onGallerySongClick(song) },
                        modifier = Modifier.width(140.dp).observeHomeInput("discoveryItem", diagnostics))
                }
            }
        }
        if (showSongs && suggestedSongs.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            FlowtoneContentSectionTitle("听听这些", modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestedSongs.forEach { song ->
                    androidx.compose.runtime.key(song.sourceType, song.id) {
                        SongListItem(
                            song = song,
                            isCurrentSong = false,
                            onClick = onGallerySongClick,
                            compact = true,
                            onPressInteraction = onSongPressInteraction
                        )
                    }
                }
            }
        }
        if (homeIsolationMode != "normal") {
            Spacer(Modifier.height(32.dp))
            HomeInteractionBaselineSongs(
                songs = minimalSongs,
                currentSong = currentSong,
                onSongClick = onGallerySongClick,
                onSongPressInteraction = onSongPressInteraction
            )
        }
        }
        }
    }
}

@Composable
private fun HomeInteractionBaselineSongs(
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onSongPressInteraction: ((Song) -> Unit)?
) {
    FlowtoneContentSectionTitle(
        title = "交互性能测试",
        modifier = Modifier.padding(horizontal = 20.dp)
    )
    if (songs.isEmpty()) {
        Text(
            text = "当前本地曲库没有可用于测试的歌曲",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    } else {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            songs.forEach { song ->
                androidx.compose.runtime.key(song.sourceType, song.id) {
                    SongListItem(
                        song = song,
                        isCurrentSong = currentSong?.let { current ->
                            current.sourceType == song.sourceType && current.id == song.id
                        } ?: false,
                        onClick = onSongClick,
                        compact = true,
                        onPressInteraction = onSongPressInteraction
                    )
                }
            }
        }
    }
}

// 临时诊断：只观察事件，不调用 consume，也不使用 Compose 状态，避免日志触发重组。
private fun Modifier.observeHomeInput(
    stage: String,
    diagnostics: HomeInputDiagnostics?
): Modifier = if (diagnostics == null) {
    this
} else {
    pointerInput(stage, diagnostics) {
        diagnostics.observerLifecycle(stage, "START")
        try {
            awaitPointerEventScope {
                while (true) {
                    val initial = awaitPointerEvent(PointerEventPass.Initial)
                    diagnostics.recordPointerEvent(stage, "Initial", initial)

                    val final = awaitPointerEvent(PointerEventPass.Final)
                    diagnostics.recordPointerEvent(stage, "Final", final)
                }
            }
        } finally {
            diagnostics.observerLifecycle(stage, "STOP")
        }
    }
}

internal class HomeInputDiagnostics(
    private val scrollState: ScrollState,
    private val discoveryListState: LazyListState?,
    private val pagerState: PagerState,
    private val homeOverscroll: OverscrollEffect?,
    private val discoveryOverscroll: OverscrollEffect?,
    private val experimentEnabled: Boolean,
    private val homeMode: String,
    private val homeIsolation: String
) {
    private var gesture = 0L
    private var lastUpEventTime = 0L
    private var startScrollValue = 0
    private var inputY = 0f
    private var inputAbsY = 0f
    private var applyInputY = 0f
    private var applyInputAbsY = 0f
    private var performInputY = 0f
    private var performInputAbsY = 0f
    private var performConsumedY = 0f
    private var performConsumedAbsY = 0f
    private var effectConsumedY = 0f
    private var effectConsumedAbsY = 0f
    private var realOverscrollDuringGesture = false
    private var downConsumed = false
    private var upConsumed = false
    private var consumedMoveSeen = false

    fun recordPointerEvent(stage: String, pass: String, event: PointerEvent) {
        for (change in event.changes) {
            if (stage != "outsideScroll") continue
            realOverscrollDuringGesture = realOverscrollDuringGesture || homeOverscroll?.isInProgress == true
            if (pass == "Initial") {
                if (change.pressed && !change.previousPressed) {
                    gesture++
                    startScrollValue = scrollState.value
                    inputY = 0f
                    inputAbsY = 0f
                    applyInputY = 0f
                    applyInputAbsY = 0f
                    performInputY = 0f
                    performInputAbsY = 0f
                    performConsumedY = 0f
                    performConsumedAbsY = 0f
                    effectConsumedY = 0f
                    effectConsumedAbsY = 0f
                    realOverscrollDuringGesture = homeOverscroll?.isInProgress == true
                    downConsumed = change.isConsumed
                    upConsumed = false
                    consumedMoveSeen = false
                } else if (change.pressed && change.previousPressed && change.position != change.previousPosition) {
                    val deltaY = change.position.y - change.previousPosition.y
                    inputY += deltaY
                    inputAbsY += kotlin.math.abs(deltaY)
                    if (change.isConsumed) consumedMoveSeen = true
                }
            } else if (change.pressed && !change.previousPressed) {
                FastInteractionTrace.record(
                    stage = "POINTER_DOWN",
                    page = "home-gallery",
                    details = "gesture=$gesture consumed=${change.isConsumed} " +
                        "eventUptimeMs=${change.uptimeMillis}"
                )
            } else if (!change.pressed && change.previousPressed) {
                upConsumed = change.isConsumed
                lastUpEventTime = change.uptimeMillis
                FastInteractionTrace.record(
                    stage = "POINTER_UP",
                    page = "home-gallery",
                    details = "gesture=$gesture consumed=${change.isConsumed} " +
                        "eventUptimeMs=${change.uptimeMillis}"
                )
                logGestureSummary()
            } else if (change.isConsumed && change.position != change.previousPosition) {
                consumedMoveSeen = true
            }
        }
    }

    fun recordOverscrollScroll(
        input: Offset,
        performInput: Offset,
        performConsumed: Offset,
        effectConsumed: Offset,
        wasInProgress: Boolean,
        isInProgress: Boolean
    ) {
        applyInputY += input.y
        applyInputAbsY += kotlin.math.abs(input.y)
        performInputY += performInput.y
        performInputAbsY += kotlin.math.abs(performInput.y)
        performConsumedY += performConsumed.y
        performConsumedAbsY += kotlin.math.abs(performConsumed.y)
        effectConsumedY += effectConsumed.y
        effectConsumedAbsY += kotlin.math.abs(effectConsumed.y)
        realOverscrollDuringGesture = realOverscrollDuringGesture || wasInProgress || isInProgress
    }

    fun traceClick(song: Song, action: () -> Unit) {
        val started = SystemClock.uptimeMillis()
        val sinceUp = if (lastUpEventTime == 0L) -1L else started - lastUpEventTime
        FastInteractionTrace.record(
            stage = "ON_CLICK_BEGIN",
            page = "home-gallery",
            song = song,
            details = "gesture=$gesture sinceUpMs=$sinceUp"
        )
        Log.d(HomeInputDiagnosticTag, "CLICK_BEGIN gesture=$gesture song=${song.sourceType}:${song.id} sinceUpMs=$sinceUp ${snapshot()}")
        try {
            action()
        } finally {
            Log.d(
                HomeInputDiagnosticTag,
                "CLICK_END gesture=$gesture durationMs=${SystemClock.uptimeMillis() - started}"
            )
        }
    }

    fun observerLifecycle(stage: String, event: String) {
        if (stage == "outsideScroll") {
            Log.d(HomeInputDiagnosticTag, "OBSERVER_$event stage=$stage ${snapshot()}")
        }
    }

    private fun logGestureSummary() {
        Log.d(
            HomeInputDiagnosticTag,
            "GESTURE gesture=$gesture mode=${if (experimentEnabled) "B" else "A"} " +
                "scroll=$startScrollValue->${scrollState.value} inputY=$inputY inputAbsY=$inputAbsY " +
                "applyY=$applyInputY applyAbsY=$applyInputAbsY performInputY=$performInputY " +
                "performInputAbsY=$performInputAbsY performConsumedY=$performConsumedY " +
                "performConsumedAbsY=$performConsumedAbsY effectConsumedY=$effectConsumedY " +
                "effectConsumedAbsY=$effectConsumedAbsY downConsumed=$downConsumed upConsumed=$upConsumed " +
                "consumedMoveSeen=$consumedMoveSeen realOverscrollDuringGesture=$realOverscrollDuringGesture ${snapshot()}"
        )
    }

    private fun snapshot(): String =
        "homeMode=$homeMode homeIsolation=$homeIsolation " +
            "overscrollMode=${if (experimentEnabled) "B" else "A"} " +
            "home=${scrollState.value}/${scrollState.maxValue} homeScrolling=${scrollState.isScrollInProgress} " +
            "homeOverscroll=${homeOverscroll?.isInProgress == true} " +
            "reportedHomeOverscroll=${if (experimentEnabled) false else homeOverscroll?.isInProgress == true} " +
            "row=${discoveryListState?.let { "${it.firstVisibleItemIndex}:${it.firstVisibleItemScrollOffset}" } ?: "absent"} " +
            "rowScrolling=${discoveryListState?.isScrollInProgress == true} " +
            "rowOverscroll=${discoveryOverscroll?.isInProgress == true} " +
            "pager=${pagerState.currentPage}:${pagerState.currentPageOffsetFraction} " +
            "pagerScrolling=${pagerState.isScrollInProgress}"
}

private const val HomeInputDiagnosticTag = "FlowtoneHomeInput"
