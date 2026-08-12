package ink.tenqui.flowtone.ui.player.lyrics

import android.os.Build
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import ink.tenqui.flowtone.BuildConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import ink.tenqui.flowtone.lyrics.LyricLine
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.ui.debug.performanceSample
import ink.tenqui.flowtone.ui.debug.performanceSampleOperation
import ink.tenqui.flowtone.ui.debug.WindowJankSampler
import ink.tenqui.flowtone.ui.player.PlayerSongSwitchDurationMillis
import ink.tenqui.flowtone.ui.player.TrackSwitchProgressEasing
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class LyricsTrackSwitchPhase {
    Static,
    WaitingToEnter,
    Entering,
    Exiting
}

@Composable
internal fun LyricsContent(
    lyricsSessionKey: Long,
    state: LyricsState,
    confirmedPlaybackPositionMs: Long?,
    activeLineTargetY: Dp,
    visibilityProgress: Float,
    contentVisible: Boolean = true,
    trackSwitchPhase: LyricsTrackSwitchPhase = LyricsTrackSwitchPhase.Static,
    trackSwitchDirection: Int = 1,
    onTrackEnterReady: () -> Unit = {},
    onTrackEnterFinished: () -> Unit = {},
    onLyricPress: () -> Unit,
    onLyricClick: (Long) -> Unit,
    onChooseLyricsDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleModifier = modifier
        .performanceSample("Lyrics") {
            "visible=$contentVisible phase=$trackSwitchPhase"
        }
        .graphicsLayer {
            alpha = visibilityProgress.coerceIn(0f, 1f)
            translationY = 10.dp.toPx() * (1f - visibilityProgress)
        }
        .then(
            if (trackSwitchPhase == LyricsTrackSwitchPhase.Exiting) {
                Modifier.clearAndSetSemantics { }
            } else {
                Modifier
            }
        )
        .fillMaxSize()

    when (state) {
        LyricsState.Idle -> Unit
        LyricsState.Loading -> LyricsMessage("正在读取歌词", visibleModifier)
        LyricsState.DirectoryNotSelected -> LyricsMessage(
            primary = "暂无歌词",
            secondary = "声流需要外部文件夹读取权限才能读取歌词",
            actionLabel = "选择歌词目录",
            onAction = onChooseLyricsDirectory,
            interactionEnabled = contentVisible,
            modifier = visibleModifier
        )
        LyricsState.DirectoryPermissionLost -> LyricsMessage(
            primary = "歌词目录授权已失效",
            secondary = "请重新选择歌词目录以恢复读取权限",
            actionLabel = "重新选择歌词目录",
            onAction = onChooseLyricsDirectory,
            interactionEnabled = contentVisible,
            modifier = visibleModifier
        )
        LyricsState.OutsideSelectedDirectory -> LyricsMessage(
            primary = "暂无歌词",
            secondary = "当前歌曲不在已授权的歌词目录中",
            actionLabel = "重新选择歌词目录",
            onAction = onChooseLyricsDirectory,
            interactionEnabled = contentVisible,
            modifier = visibleModifier
        )
        LyricsState.NotFound -> LyricsMessage(
            primary = "暂无歌词",
            secondary = "未找到与歌曲文件同名的 .lrc 文件",
            modifier = visibleModifier
        )
        is LyricsState.Error -> LyricsMessage("歌词读取失败", visibleModifier)
        is LyricsState.Available -> {
            when {
                state.lines.isEmpty() -> {
                    LyricsMessage("未识别到有效歌词", visibleModifier)
                }

                isPureMusicNotice(state.lines) -> {
                    PureMusicNotice(modifier = visibleModifier)
                }

                else -> {
                    BoxWithConstraints(modifier = visibleModifier) {
                        key(lyricsSessionKey) {
                            LyricsList(
                                lyricsSessionKey = lyricsSessionKey,
                                lines = state.lines,
                                containerWidthPx = constraints.maxWidth,
                                confirmedPlaybackPositionMs = confirmedPlaybackPositionMs,
                                activeLineTargetY = activeLineTargetY,
                                contentVisible = contentVisible,
                                trackSwitchPhase = trackSwitchPhase,
                                trackSwitchDirection = trackSwitchDirection,
                                onTrackEnterReady = onTrackEnterReady,
                                onTrackEnterFinished = onTrackEnterFinished,
                                onLyricPress = onLyricPress,
                                onLyricClick = onLyricClick,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun isPureMusicNotice(lines: List<LyricLine>): Boolean {
    val contentLines = lines
        .asSequence()
        .map { line -> line.text.trim() }
        .filter(String::isNotEmpty)
        .toList()
    return contentLines.size == 1 && contentLines.single() == PureMusicNoticeText
}

@Composable
private fun PureMusicNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = PureMusicNoticeText,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White
        )
    }
}

@Composable
private fun LyricsList(
    lyricsSessionKey: Long,
    lines: List<LyricLine>,
    containerWidthPx: Int,
    confirmedPlaybackPositionMs: Long?,
    activeLineTargetY: Dp,
    contentVisible: Boolean,
    trackSwitchPhase: LyricsTrackSwitchPhase,
    trackSwitchDirection: Int,
    onTrackEnterReady: () -> Unit,
    onTrackEnterFinished: () -> Unit,
    onLyricPress: () -> Unit,
    onLyricClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val gestureCoroutineScope = rememberCoroutineScope()
    val lastConfirmedPlaybackPosition = remember(lines) {
        PlaybackPositionHolder(confirmedPlaybackPositionMs)
    }
    var pendingLyricSeek by remember(lines) {
        mutableStateOf<PendingLyricSeek?>(null)
    }
    SideEffect {
        if (
            trackSwitchPhase != LyricsTrackSwitchPhase.Exiting &&
            confirmedPlaybackPositionMs != null
        ) {
            lastConfirmedPlaybackPosition.value = confirmedPlaybackPositionMs
            pendingLyricSeek?.let { pendingSeek ->
                val reachedPendingTarget = if (pendingSeek.direction >= 0) {
                    confirmedPlaybackPositionMs >= pendingSeek.timestampMs
                } else {
                    confirmedPlaybackPositionMs <= pendingSeek.timestampMs
                }
                if (reachedPendingTarget) {
                    pendingLyricSeek = null
                }
            }
        }
    }
    val effectivePlaybackPositionMs = if (
        trackSwitchPhase == LyricsTrackSwitchPhase.Exiting ||
        confirmedPlaybackPositionMs == null
    ) {
        lastConfirmedPlaybackPosition.value
    } else {
        confirmedPlaybackPositionMs
    }
    val displayedPlaybackPositionMs =
        pendingLyricSeek?.timestampMs ?: effectivePlaybackPositionMs
    val activeTimestampMs = remember(lines, displayedPlaybackPositionMs) {
        displayedPlaybackPositionMs?.let { positionMs ->
            activeLyricTimestampMs(
                lines = lines,
                playbackPositionMs = positionMs
            )
        }
    }
    val activeLineIndex = remember(lines, displayedPlaybackPositionMs) {
        displayedPlaybackPositionMs?.let { positionMs ->
            activeLyricAnchorIndex(
                lines = lines,
                playbackPositionMs = positionMs
            )
        } ?: lines.indices.firstOrNull()
    }
    var isFollowingCurrentLine by remember(lines) { mutableStateOf(true) }
    var isPointerDown by remember { mutableStateOf(false) }
    var userInteractionVersion by remember { mutableIntStateOf(0) }
    var lyricSeekVersion by remember(lines) { mutableIntStateOf(0) }
    var trackingSubpixelOffsetY by remember(lines) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val activeLineTargetYPx = with(density) {
        activeLineTargetY.roundToPx()
    }
    val lyricTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 32.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.SemiBold
    )
    val lyricTranslationTextStyle = lyricTextStyle.copy(
        fontSize = 21.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Medium
    )
    val blankLyricLineHeightPx = with(density) {
        BlankLyricLineHeight.roundToPx()
    }
    val lyricItemVerticalPaddingPx = with(density) {
        LyricItemOuterVerticalPadding.roundToPx() * 2 +
            LyricClickBackgroundVerticalPadding.roundToPx() * 2
    }
    val lyricsTranslationTopSpacingPx = with(density) {
        LyricsTranslationTopSpacing.roundToPx()
    }
    val trackSwitchDistancePx = with(density) {
        LyricsTrackSwitchDistance.toPx()
    }
    val textMeasurer = rememberTextMeasurer()
    val initialLyricTextWidthPx = (
        containerWidthPx - with(density) {
            (
                LyricsStartPadding +
                    LyricsEndPadding +
                    LyricClickBackgroundHorizontalPadding * 2f
                ).roundToPx()
        }
        ).coerceAtLeast(0)
    val layoutMetricsCacheKey = remember(
        lyricsSessionKey,
        lines,
        initialLyricTextWidthPx,
        lyricTextStyle,
        lyricTranslationTextStyle,
        lyricItemVerticalPaddingPx,
        lyricsTranslationTopSpacingPx,
        blankLyricLineHeightPx
    ) {
        LyricsLayoutMetricsCacheKey(
            sessionKey = lyricsSessionKey,
            lines = lines,
            textWidthPx = initialLyricTextWidthPx,
            lyricTextStyle = lyricTextStyle,
            translationTextStyle = lyricTranslationTextStyle,
            itemVerticalPaddingPx = lyricItemVerticalPaddingPx,
            translationTopSpacingPx = lyricsTranslationTopSpacingPx,
            blankLineHeightPx = blankLyricLineHeightPx
        )
    }
    val cachedLayoutMetrics = remember(layoutMetricsCacheKey) {
        LyricsLayoutMetricsCache.get(layoutMetricsCacheKey)
    }
    val firstLineLayoutMetrics = remember(
        layoutMetricsCacheKey,
        cachedLayoutMetrics
    ) {
        cachedLayoutMetrics?.firstOrNull() ?: lines.firstOrNull()?.let { firstLine ->
            performanceSampleOperation(
                section = "Lyrics",
                operation = "measureFirstLine width=$initialLyricTextWidthPx"
            ) {
                measureLyricLineLayoutMetrics(
                    line = firstLine,
                    textMeasurer = textMeasurer,
                    lyricTextStyle = lyricTextStyle,
                    translationTextStyle = lyricTranslationTextStyle,
                    textWidthPx = initialLyricTextWidthPx,
                    translationTopSpacingPx = lyricsTranslationTopSpacingPx,
                    itemVerticalPaddingPx = lyricItemVerticalPaddingPx,
                    blankLineHeightPx = blankLyricLineHeightPx
                )
            }
        } ?: LyricLineLayoutMetrics(blankLyricLineHeightPx, 0)
    }
    val listState = remember {
        LazyListState(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = firstLineLayoutMetrics.itemHeightPx / 2
        )
    }
    LaunchedEffect(Unit) {
        lyricsPositionLog(
            sessionKey = lyricsSessionKey,
            event = "LIST_CREATED",
            details = "lines=${lines.size} firstTimestamp=${lines.firstOrNull()?.timestampMs} " +
                "firstHeight=${firstLineLayoutMetrics.itemHeightPx} " +
                "initialIndex=${listState.firstVisibleItemIndex} " +
                "initialScroll=${listState.firstVisibleItemScrollOffset}"
        )
    }
    LaunchedEffect(listState, activeLineTargetYPx) {
        val firstLineItem = snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == 0 }
        }.first { item -> item != null } ?: return@LaunchedEffect
        val firstLineCenter = lazyListItemCenterInViewport(
            itemOffset = firstLineItem.offset,
            itemSize = firstLineItem.size,
            viewportStartOffset = listState.layoutInfo.viewportStartOffset
        )
        lyricsPositionLog(
            sessionKey = lyricsSessionKey,
            event = "FIRST_LAYOUT",
            details = "phase=$trackSwitchPhase firstOffset=${firstLineItem.offset} " +
                "firstSize=${firstLineItem.size} center=$firstLineCenter " +
                "target=$activeLineTargetYPx delta=${firstLineCenter - activeLineTargetYPx} " +
                "firstVisible=${listState.firstVisibleItemIndex}:" +
                listState.firstVisibleItemScrollOffset +
                " viewportStart=${listState.layoutInfo.viewportStartOffset}"
        )
    }
    val trackSwitchEntranceTimeline = remember(lines) {
        Animatable(
            if (
                trackSwitchPhase == LyricsTrackSwitchPhase.WaitingToEnter ||
                trackSwitchPhase == LyricsTrackSwitchPhase.Entering
            ) {
                0f
            } else {
                1f
            }
        )
    }
    val pageEntranceTimeline = remember(lines) {
        Animatable(if (contentVisible) 1f else 0f)
    }
    val trackSwitchExitTimeline = remember(lines) { Animatable(0f) }
    val trackSwitchEntranceDirection = remember(lines) { trackSwitchDirection }
    val trackSwitchStaggerAnchorIndex = remember(lines, trackSwitchPhase) {
        listState.firstVisibleItemIndex
    }
    var pageEntranceStaggerAnchorIndex by remember(lines) {
        mutableIntStateOf(listState.firstVisibleItemIndex)
    }
    LaunchedEffect(trackSwitchPhase) {
        when (trackSwitchPhase) {
            LyricsTrackSwitchPhase.Static -> {
                trackSwitchEntranceTimeline.snapTo(1f)
                trackSwitchExitTimeline.snapTo(0f)
            }
            LyricsTrackSwitchPhase.WaitingToEnter -> {
                trackSwitchEntranceTimeline.snapTo(0f)
                snapshotFlow {
                    val layoutInfo = listState.layoutInfo
                    layoutInfo.viewportEndOffset > layoutInfo.viewportStartOffset &&
                        layoutInfo.visibleItemsInfo.isNotEmpty()
                }.first { it }
                onTrackEnterReady()
            }
            LyricsTrackSwitchPhase.Entering -> {
                coroutineScope {
                    launch {
                        val remainingDurationMillis = (
                            LyricsTrackSwitchEntranceTotalDurationMillis *
                                (1f - trackSwitchEntranceTimeline.value)
                            ).roundToInt().coerceAtLeast(1)
                        trackSwitchEntranceTimeline.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = remainingDurationMillis,
                                easing = LinearEasing
                            )
                        )
                        onTrackEnterFinished()
                    }
                    launch {
                        val remainingDurationMillis = (
                            LyricsTrackSwitchExitTotalDurationMillis *
                                trackSwitchExitTimeline.value
                            ).roundToInt().coerceAtLeast(1)
                        trackSwitchExitTimeline.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = remainingDurationMillis,
                                easing = LinearEasing
                            )
                        )
                    }
                }
            }
            LyricsTrackSwitchPhase.Exiting -> {
                trackSwitchExitTimeline.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = LyricsTrackSwitchExitTotalDurationMillis,
                        easing = LinearEasing
                    )
                )
            }
        }
    }
    LaunchedEffect(contentVisible) {
        if (contentVisible) {
            pageEntranceStaggerAnchorIndex = listState.firstVisibleItemIndex
            val remainingDurationMillis = (
                LyricsTrackSwitchEntranceTotalDurationMillis *
                    (1f - pageEntranceTimeline.value)
                ).roundToInt().coerceAtLeast(1)
            pageEntranceTimeline.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = remainingDurationMillis,
                    easing = LinearEasing
                )
            )
        } else {
            pageEntranceTimeline.snapTo(0f)
        }
    }
    fun markUserInteraction() {
        isFollowingCurrentLine = false
        userInteractionVersion += 1
    }

    val userScrollInProgress = listState.isScrollInProgress
    LaunchedEffect(
        userInteractionVersion,
        isPointerDown,
        userScrollInProgress,
        isFollowingCurrentLine
    ) {
        if (
            !isFollowingCurrentLine &&
            !isPointerDown &&
            !userScrollInProgress
        ) {
            delay(LyricsReturnToCurrentLineDelayMs)
            isFollowingCurrentLine = true
        }
    }
    LaunchedEffect(lyricSeekVersion) {
        if (lyricSeekVersion > 0) {
            // 先让 seek 引发的进度与 BUFFERING 状态更新完成，避免它们和首批滚动帧
            // 同时占用主线程。高亮与点击反馈仍会在点击时立即更新。
            delay(LyricsSeekTrackingSettleDelayMs)
            isFollowingCurrentLine = true
        }
    }
    LaunchedEffect(contentVisible) {
        if (!contentVisible) {
            isPointerDown = false
            isFollowingCurrentLine = true
        }
    }
    BoxWithConstraints(modifier = modifier) {
        val targetY = activeLineTargetY.coerceIn(0.dp, maxHeight)
        val bottomPadding = (maxHeight - targetY).coerceAtLeast(0.dp)
        val horizontalPaddingPx = with(density) {
            (
                LyricsStartPadding +
                    LyricsEndPadding +
                    LyricClickBackgroundHorizontalPadding * 2f
                ).roundToPx()
        }
        val lyricTextWidthPx = (constraints.maxWidth - horizontalPaddingPx)
            .coerceAtLeast(0)
        val lyricRowWidth = (
            maxWidth - LyricsStartPadding - LyricsEndPadding
            ).coerceAtLeast(0.dp)
        val estimatedLyricTextHeightPx = with(density) { 42.sp.roundToPx() }
        val estimatedTranslationTextHeightPx = with(density) { 29.sp.roundToPx() }
        var measuredLineLayoutMetrics by remember(
            layoutMetricsCacheKey,
            lyricTextWidthPx,
        ) {
            mutableStateOf(
                cachedLayoutMetrics?.takeIf {
                    layoutMetricsCacheKey.textWidthPx == lyricTextWidthPx
                }
            )
        }
        val isUsingEstimatedLayoutMetrics = measuredLineLayoutMetrics == null
        val lineLayoutMetrics = measuredLineLayoutMetrics ?: remember(
            lines,
            estimatedLyricTextHeightPx,
            estimatedTranslationTextHeightPx,
            lyricsTranslationTopSpacingPx,
            lyricItemVerticalPaddingPx,
            blankLyricLineHeightPx
        ) {
            lines.map { line ->
                estimatedLyricLineLayoutMetrics(
                    line = line,
                    lyricTextHeightPx = estimatedLyricTextHeightPx,
                    translationTextHeightPx = estimatedTranslationTextHeightPx,
                    translationTopSpacingPx = lyricsTranslationTopSpacingPx,
                    itemVerticalPaddingPx = lyricItemVerticalPaddingPx,
                    blankLineHeightPx = blankLyricLineHeightPx
                )
            }
        }
        LaunchedEffect(
            layoutMetricsCacheKey,
            lyricTextWidthPx,
            trackSwitchPhase
        ) {
            if (trackSwitchPhase != LyricsTrackSwitchPhase.Static) {
                return@LaunchedEffect
            }
            if (measuredLineLayoutMetrics != null) {
                lyricsPerformanceLog(
                    sessionKey = lyricsSessionKey,
                    details = "cache=hit lines=${lines.size} width=$lyricTextWidthPx"
                )
                return@LaunchedEffect
            }
            val measuredMetrics = ArrayList<LyricLineLayoutMetrics>(lines.size)
            lines.firstOrNull()?.let { measuredMetrics += firstLineLayoutMetrics }
            val lineIndexBatches = lines.indices.drop(1).chunked(LyricsMeasurementLinesPerFrame)
            val batchCount = lineIndexBatches.size
            val measurementStartedAtNanos = System.nanoTime()
            var measurementWorkNanos = 0L
            var longestBatchNanos = 0L
            WindowJankSampler.updateState(
                key = "LyricsMeasure",
                value = "session=$lyricsSessionKey batches=$batchCount"
            )
            try {
                lineIndexBatches.forEach { lineIndexes ->
                val batchStartedAtNanos = System.nanoTime()
                performanceSampleOperation(
                    section = "Lyrics",
                    operation = "measureText lines=${lineIndexes.size} width=$lyricTextWidthPx"
                ) {
                    lineIndexes.forEach { index ->
                        measuredMetrics += measureLyricLineLayoutMetrics(
                            line = lines[index],
                            textMeasurer = textMeasurer,
                            lyricTextStyle = lyricTextStyle,
                            translationTextStyle = lyricTranslationTextStyle,
                            textWidthPx = lyricTextWidthPx,
                            translationTopSpacingPx = lyricsTranslationTopSpacingPx,
                            itemVerticalPaddingPx = lyricItemVerticalPaddingPx,
                            blankLineHeightPx = blankLyricLineHeightPx
                        )
                    }
                }
                val batchElapsedNanos = System.nanoTime() - batchStartedAtNanos
                measurementWorkNanos += batchElapsedNanos
                longestBatchNanos = maxOf(longestBatchNanos, batchElapsedNanos)
                    withFrameNanos { }
                }
            } finally {
                WindowJankSampler.updateState("LyricsMeasure", "idle")
            }
            LyricsLayoutMetricsCache.put(layoutMetricsCacheKey, measuredMetrics)
            measuredLineLayoutMetrics = measuredMetrics
            lyricsPerformanceLog(
                sessionKey = lyricsSessionKey,
                details = "cache=miss lines=${lines.size} " +
                    "textChars=${lines.sumOf { it.text.length }} " +
                    "translationChars=${lines.sumOf { it.translation?.length ?: 0 }} " +
                    "width=$lyricTextWidthPx batches=$batchCount " +
                    "work=${formatMillis(measurementWorkNanos)}ms " +
                    "wall=${formatMillis(System.nanoTime() - measurementStartedAtNanos)}ms " +
                    "worstBatch=${formatMillis(longestBatchNanos)}ms"
            )
        }
        val lyricItemHeightsPx = IntArray(lineLayoutMetrics.size) { index ->
            lineLayoutMetrics[index].itemHeightPx
        }
        val lyricItemStartOffsetsPx = run {
            var nextOffsetPx = 0
            IntArray(lyricItemHeightsPx.size) { index ->
                nextOffsetPx.also {
                    nextOffsetPx += lyricItemHeightsPx[index]
                }
            }
        }
        // 切歌预载与入场阶段始终从新歌词第一行开始，避免先跳到旧进度对应行。
        // 高亮仍使用 activeLineIndex，入场完成后才恢复自动跟随当前播放行。
        // Keep incoming lyrics anchored to their first line during the switch animation.
        val switchEntranceActive =
            trackSwitchPhase == LyricsTrackSwitchPhase.WaitingToEnter ||
                trackSwitchPhase == LyricsTrackSwitchPhase.Entering
        val scrollTargetLineIndex = if (switchEntranceActive) {
            lines.indices.firstOrNull()
        } else {
            activeLineIndex
        }
        val shouldSnapToFirstLine = switchEntranceActive
        val activeLineSizePx = scrollTargetLineIndex
            ?.let(lineLayoutMetrics::getOrNull)
            ?.itemHeightPx
            ?: blankLyricLineHeightPx
        val activeLineTransitionDurationMs = remember(lines, activeLineIndex) {
            activeLineIndex?.let { lineIndex ->
                lyricTrackingTransitionDurationMs(
                    lines = lines,
                    lineIndex = lineIndex
                )
            } ?: LyricsLineTransitionDurationMs
        }

        LaunchedEffect(
            scrollTargetLineIndex,
            activeLineTargetYPx,
            activeLineSizePx,
            activeLineTransitionDurationMs,
            contentVisible,
            isFollowingCurrentLine,
            trackSwitchPhase
        ) {
            if (
                trackSwitchPhase != LyricsTrackSwitchPhase.Exiting &&
                isFollowingCurrentLine &&
                scrollTargetLineIndex != null
            ) {
                lyricsPositionLog(
                    sessionKey = lyricsSessionKey,
                    event = "TRACK_REQUEST",
                    details = "phase=$trackSwitchPhase targetIndex=$scrollTargetLineIndex " +
                        "targetY=$activeLineTargetYPx itemHeight=$activeLineSizePx " +
                        "duration=${if (contentVisible && !shouldSnapToFirstLine) {
                            activeLineTransitionDurationMs
                        } else {
                            0
                        }} firstVisible=${listState.firstVisibleItemIndex}:" +
                        listState.firstVisibleItemScrollOffset
                )
                listState.animateScrollToItemAtY(
                    index = scrollTargetLineIndex,
                    targetYPx = activeLineTargetYPx,
                    targetItemSizePx = activeLineSizePx,
                    itemHeightsPx = lyricItemHeightsPx,
                    itemStartOffsetsPx = lyricItemStartOffsetsPx,
                    initialSubpixelOffsetPx = trackingSubpixelOffsetY,
                    onSubpixelOffsetChanged = { offsetPx ->
                        trackingSubpixelOffsetY = offsetPx
                    },
                    transitionDurationMs = if (contentVisible && !shouldSnapToFirstLine) {
                        activeLineTransitionDurationMs
                    } else {
                        0
                    }
                )
                if (contentVisible) {
                    val finalItem = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == scrollTargetLineIndex }
                    val finalVisibleY = finalItem?.let { item ->
                        lazyListItemCenterInViewport(
                            itemOffset = item.offset,
                            itemSize = item.size,
                            viewportStartOffset = listState.layoutInfo.viewportStartOffset
                        )
                    }
                    Log.d(
                        "LyricsAnchor",
                        "index=$scrollTargetLineIndex targetY=$activeLineTargetYPx " +
                            "measuredHeight=$activeLineSizePx finalY=$finalVisibleY " +
                            "viewportStart=${listState.layoutInfo.viewportStartOffset} " +
                        "viewport=${listState.layoutInfo.viewportSize.height}"
                    )
                }
                lyricsPositionLog(
                    sessionKey = lyricsSessionKey,
                    event = "TRACK_FINISHED",
                    details = "targetIndex=$scrollTargetLineIndex firstVisible=" +
                        "${listState.firstVisibleItemIndex}:" +
                        listState.firstVisibleItemScrollOffset
                )
            }
        }

        val switchVisualsActive = trackSwitchPhase != LyricsTrackSwitchPhase.Static
        LazyColumn(
            state = listState,
            userScrollEnabled = contentVisible,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = trackingSubpixelOffsetY
                }
                .padding(
                    start = LyricsStartPadding,
                    end = LyricsEndPadding
                )
                .then(
                    if (contentVisible) {
                        Modifier.pointerInput(lines) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                isPointerDown = true
                                markUserInteraction()
                                // 高亮切换时歌词列表仍可能处于自动滚动中。先停住列表，
                                // 避免歌词在按下与抬起之间移动，导致点击被判定为取消。
                                gestureCoroutineScope.launch {
                                    listState.stopScroll()
                                }
                                waitForUpOrCancellation()
                                isPointerDown = false
                                markUserInteraction()
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(
                top = targetY,
                bottom = bottomPadding
            )
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.timestampMs}:$index" }
            ) { index, line ->
                if (line.text.isBlank() && line.translation.isNullOrBlank()) {
                    // 保留一个不可见锚点，空行播放时中心会落在上下两句的间隙。
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(BlankLyricLineHeight)
                    )
                } else {
                    val isActive = line.timestampMs == activeTimestampMs
                    val textBlurRadius = when {
                        !switchVisualsActive && isActive -> ActiveLyricTextBlurRadius
                        !switchVisualsActive -> InactiveLyricTextBlurRadius
                        isActive -> SwitchingActiveLyricTextBlurRadius
                        else -> SwitchingInactiveLyricTextBlurRadius
                    }
                    val textBlurAlpha = when {
                        !switchVisualsActive && isActive -> ActiveLyricTextBlurAlpha
                        !switchVisualsActive -> InactiveLyricTextBlurAlpha
                        isActive -> SwitchingActiveLyricTextBlurAlpha
                        else -> SwitchingInactiveLyricTextBlurAlpha
                    }
                    val trackSwitchStaggerOrder = (
                        index - trackSwitchStaggerAnchorIndex
                        ).coerceIn(0, LyricsTrackSwitchMaxStaggeredLines - 1)
                    val pageEntranceStaggerOrder = (
                        index - pageEntranceStaggerAnchorIndex
                        ).coerceIn(0, LyricsTrackSwitchMaxStaggeredLines - 1)
                    val lyricInteractionSource = remember(line.timestampMs, index) {
                        MutableInteractionSource()
                    }
                    val lineLayout = lineLayoutMetrics[index]
                    // 分帧测量尚未完成时 contentWidthPx 为 0。此时必须给文字完整行宽，
                    // 否则文字会在约 10dp 的点击背景内逐字换行，把首个列表项撑得异常高。
                    val lyricBackgroundWidth = if (isUsingEstimatedLayoutMetrics) {
                        lyricRowWidth
                    } else {
                        (
                            with(density) {
                                lineLayout.contentWidthPx.toDp()
                            } +
                                LyricClickBackgroundHorizontalPadding * 2f
                            ).coerceAtMost(lyricRowWidth)
                    }
                    var clickFeedbackVersion by remember(line.timestampMs, index) {
                        mutableIntStateOf(0)
                    }
                    val clickFeedbackAlpha = remember(line.timestampMs, index) {
                        Animatable(0f)
                    }
                    LaunchedEffect(clickFeedbackVersion) {
                        if (clickFeedbackVersion > 0) {
                            clickFeedbackAlpha.snapTo(0f)
                            clickFeedbackAlpha.animateTo(
                                targetValue = LyricClickBackgroundMaxAlpha,
                                animationSpec = tween(
                                    durationMillis = LyricClickBackgroundFadeInMillis,
                                    easing = LyricsLineTransitionEasing
                                )
                            )
                            delay(LyricClickBackgroundHoldMillis)
                            clickFeedbackAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = LyricClickBackgroundFadeOutMillis,
                                    easing = LyricsLineTransitionEasing
                                )
                            )
                        }
                    }
                    val targetLyricAlpha = if (isActive) {
                        1f
                    } else {
                        inactiveLyricAlpha(
                            lineIndex = index,
                            activeLineIndex = activeLineIndex
                        )
                    }
                    val lyricTransitionDurationMs = lyricVisualTransitionDurationMs(
                        lines = lines,
                        lineIndex = index
                    )
                    val lyricAlpha by animateFloatAsState(
                        targetValue = targetLyricAlpha,
                        animationSpec = if (contentVisible) {
                            tween(
                                durationMillis = lyricTransitionDurationMs,
                                easing = LyricsLineTransitionEasing
                            )
                        } else {
                            snap()
                        },
                        label = "LyricHighlightAlpha"
                    )
                    val lyricScale by animateFloatAsState(
                        targetValue = if (isActive) {
                            ActiveLyricScale
                        } else {
                            InactiveLyricScale
                        },
                        animationSpec = if (contentVisible) {
                            tween(
                                durationMillis = lyricTransitionDurationMs,
                                easing = LyricsLineTransitionEasing
                            )
                        } else {
                            snap()
                        },
                        label = "LyricHighlightScale"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                val entranceElapsedMillis =
                                    trackSwitchEntranceTimeline.value *
                                        LyricsTrackSwitchEntranceTotalDurationMillis
                                val entranceLineDelayMillis =
                                    trackSwitchStaggerOrder * LyricsTrackSwitchStaggerMillis
                                val linearEntranceProgress = (
                                    (entranceElapsedMillis - entranceLineDelayMillis) /
                                        LyricsTrackSwitchLineDurationMillis
                                    ).coerceIn(0f, 1f)
                                val entranceVisibility = if (
                                    trackSwitchPhase == LyricsTrackSwitchPhase.Static
                                ) {
                                    1f
                                } else {
                                    TrackSwitchProgressEasing.transform(
                                        linearEntranceProgress
                                    )
                                }
                                val pageEntranceElapsedMillis =
                                    pageEntranceTimeline.value *
                                        LyricsTrackSwitchEntranceTotalDurationMillis
                                val pageEntranceLineDelayMillis =
                                    pageEntranceStaggerOrder * LyricsTrackSwitchStaggerMillis
                                val linearPageEntranceProgress = (
                                    (pageEntranceElapsedMillis - pageEntranceLineDelayMillis) /
                                        LyricsTrackSwitchLineDurationMillis
                                    ).coerceIn(0f, 1f)
                                val pageEntranceVisibility =
                                    TrackSwitchProgressEasing.transform(
                                        linearPageEntranceProgress
                                    )
                                val exitElapsedMillis =
                                    trackSwitchExitTimeline.value *
                                        LyricsTrackSwitchExitTotalDurationMillis
                                val exitLineDelayMillis =
                                    trackSwitchStaggerOrder * LyricsTrackSwitchExitStaggerMillis
                                val linearExitProgress = (
                                    (exitElapsedMillis - exitLineDelayMillis) /
                                        LyricsTrackSwitchExitLineDurationMillis
                                    ).coerceIn(0f, 1f)
                                val exitProgress = TrackSwitchProgressEasing.transform(
                                    linearExitProgress
                                )
                                alpha = entranceVisibility *
                                    pageEntranceVisibility *
                                    (1f - exitProgress)
                                translationX =
                                    trackSwitchDistancePx *
                                        trackSwitchEntranceDirection *
                                        (1f - entranceVisibility) -
                                        trackSwitchDistancePx *
                                        trackSwitchDirection *
                                        exitProgress
                                translationY =
                                    trackSwitchDistancePx *
                                        (1f - pageEntranceVisibility)
                            }
                            .zIndex(if (isActive) 1f else 0f)
                            .padding(vertical = LyricItemOuterVerticalPadding)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(lyricBackgroundWidth)
                                // 缩放层包住手势节点，让高亮动画中的视觉范围与点击范围一致。
                                .graphicsLayer {
                                    scaleX = lyricScale
                                    scaleY = lyricScale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .then(
                                    if (contentVisible) {
                                        Modifier.pointerInput(line.timestampMs, index) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                onLyricPress()
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable(
                                    enabled = contentVisible,
                                    interactionSource = lyricInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = "跳转到此歌词"
                                ) {
                                    clickFeedbackVersion += 1
                                    pendingLyricSeek = PendingLyricSeek(
                                        timestampMs = line.timestampMs,
                                        direction = if (
                                            line.timestampMs >=
                                                (effectivePlaybackPositionMs ?: line.timestampMs)
                                        ) {
                                            1
                                        } else {
                                            -1
                                        }
                                    )
                                    onLyricClick(line.timestampMs)
                                    lyricSeekVersion += 1
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .graphicsLayer {
                                        // 点击反馈每帧只更新图层透明度，不重组歌词内容。
                                        alpha = clickFeedbackAlpha.value
                                    }
                                    .background(
                                        color = Color(0xFF8A8A8A),
                                        shape = RoundedCornerShape(
                                            LyricClickBackgroundCornerRadius
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .padding(
                                        horizontal = LyricClickBackgroundHorizontalPadding,
                                        vertical = LyricClickBackgroundVerticalPadding
                                    )
                                    .graphicsLayer {
                                        compositingStrategy =
                                            CompositingStrategy.ModulateAlpha
                                        alpha = lyricAlpha
                                    }
                            ) {
                                SoftGlowText(
                                    text = line.text,
                                    style = lyricTextStyle,
                                    color = Color.White,
                                    glowColor = Color.White.copy(
                                        alpha = LyricGlowAlphaMultiplier
                                    ),
                                    glowEnabled = isActive,
                                    glowRadius = if (switchVisualsActive) {
                                        SwitchingLyricGlowRadius
                                    } else {
                                        LyricGlowRadius
                                    },
                                    textBlurRadius = textBlurRadius,
                                    textBlurAlpha = textBlurAlpha,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                line.translation
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { translation ->
                                        BlurredLyricText(
                                            text = translation,
                                            style = lyricTranslationTextStyle,
                                            color = Color.White.copy(
                                                alpha = LyricsTranslationAlphaMultiplier
                                            ),
                                            blurRadius = textBlurRadius,
                                            blurAlpha = textBlurAlpha,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = LyricsTranslationTopSpacing)
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoftGlowText(
    text: String,
    style: TextStyle,
    color: Color,
    glowColor: Color,
    glowEnabled: Boolean = true,
    glowRadius: Dp,
    textBlurRadius: Dp,
    textBlurAlpha: Float,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(modifier = modifier) {
            if (glowEnabled) {
                Text(
                text = text,
                style = style,
                color = glowColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .blur(
                        radius = glowRadius,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
                    // 模糊层负责效果，内层离屏层只缓存稳定的文字源。
                    // 列表滚动、外层 alpha 与缩放变化时无需重新栅格化整行文字。
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                )
            }
            BlurredLyricText(
                text = text,
                style = style,
                color = color,
                blurRadius = textBlurRadius,
                blurAlpha = textBlurAlpha,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        BlurredLyricText(
            text = text,
            style = style,
            color = color,
            blurRadius = textBlurRadius,
            blurAlpha = textBlurAlpha,
            modifier = modifier
        )
    }
}

@Composable
private fun BlurredLyricText(
    text: String,
    style: TextStyle,
    color: Color,
    blurRadius: Dp,
    blurAlpha: Float,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(modifier = modifier) {
            Text(
                text = text,
                style = style,
                color = color,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = text,
                style = style,
                color = color.copy(alpha = blurAlpha),
                modifier = Modifier
                    .fillMaxWidth()
                    .blur(
                        radius = blurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
        }
    } else {
        val blurRadiusPx = with(LocalDensity.current) { blurRadius.toPx() }
        Text(
            text = text,
            style = style.copy(
                shadow = Shadow(
                    color = color.copy(alpha = blurAlpha),
                    offset = Offset.Zero,
                    blurRadius = blurRadiusPx
                )
            ),
            color = color,
            modifier = modifier
        )
    }
}

private val LyricsStartPadding = 16.dp
private val LyricsEndPadding = 48.dp
private val LyricsLineSpacing = 38.dp
private val BlankLyricLineHeight = 12.dp
internal val LyricsEdgeFadeHeight = 64.dp
private val LyricsTrackSwitchDistance = 16.dp
private const val LyricsTrackSwitchStaggerMillis = 8
private const val LyricsTrackSwitchExitStaggerMillis =
    LyricsTrackSwitchStaggerMillis
private const val LyricsTrackSwitchMaxStaggeredLines = 9
private const val LyricsTrackSwitchLineDurationMillis =
    PlayerSongSwitchDurationMillis -
        LyricsTrackSwitchStaggerMillis * (LyricsTrackSwitchMaxStaggeredLines - 1)
private const val LyricsTrackSwitchExitLineDurationMillis =
    LyricsTrackSwitchLineDurationMillis
private const val LyricsTrackSwitchEntranceTotalDurationMillis =
    PlayerSongSwitchDurationMillis
private const val LyricsTrackSwitchExitTotalDurationMillis =
    PlayerSongSwitchDurationMillis

private class PlaybackPositionHolder(var value: Long?)
private data class PendingLyricSeek(
    val timestampMs: Long,
    val direction: Int
)
private data class LyricLineLayoutMetrics(
    val itemHeightPx: Int,
    val contentWidthPx: Int
)

private data class LyricsLayoutMetricsCacheKey(
    val sessionKey: Long,
    // 使用结构相等性：同一歌词重新读取为新的 List 时也能复用测量结果，
    // 但文字、时间戳或翻译真正变化时仍会自动失效。
    val lines: List<LyricLine>,
    val textWidthPx: Int,
    val lyricTextStyle: TextStyle,
    val translationTextStyle: TextStyle,
    val itemVerticalPaddingPx: Int,
    val translationTopSpacingPx: Int,
    val blankLineHeightPx: Int
)

private object LyricsLayoutMetricsCache {
    // 歌词和指标都很小，保留近期歌曲可覆盖常见的前后切歌和歌单浏览。
    private const val MaxEntries = 12
    private val entries = object : LinkedHashMap<
        LyricsLayoutMetricsCacheKey,
        List<LyricLineLayoutMetrics>
        >(MaxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<LyricsLayoutMetricsCacheKey, List<LyricLineLayoutMetrics>>?
        ): Boolean = size > MaxEntries
    }

    fun get(key: LyricsLayoutMetricsCacheKey): List<LyricLineLayoutMetrics>? = entries[key]

    fun put(key: LyricsLayoutMetricsCacheKey, metrics: List<LyricLineLayoutMetrics>) {
        entries[key] = metrics
    }
}

private fun estimatedLyricLineLayoutMetrics(
    line: LyricLine,
    lyricTextHeightPx: Int,
    translationTextHeightPx: Int,
    translationTopSpacingPx: Int,
    itemVerticalPaddingPx: Int,
    blankLineHeightPx: Int
): LyricLineLayoutMetrics {
    if (line.text.isBlank() && line.translation.isNullOrBlank()) {
        return LyricLineLayoutMetrics(blankLineHeightPx, 0)
    }
    val hasTranslation = !line.translation.isNullOrBlank()
    return LyricLineLayoutMetrics(
        itemHeightPx = lyricTextHeightPx +
            (if (hasTranslation) translationTextHeightPx + translationTopSpacingPx else 0) +
            itemVerticalPaddingPx,
        contentWidthPx = 0
    )
}

private fun measureLyricLineLayoutMetrics(
    line: LyricLine,
    textMeasurer: TextMeasurer,
    lyricTextStyle: TextStyle,
    translationTextStyle: TextStyle,
    textWidthPx: Int,
    translationTopSpacingPx: Int,
    itemVerticalPaddingPx: Int,
    blankLineHeightPx: Int
): LyricLineLayoutMetrics {
    if (line.text.isBlank() && line.translation.isNullOrBlank()) {
        return LyricLineLayoutMetrics(blankLineHeightPx, 0)
    }
    val lyricLayout = textMeasurer.measure(
        text = line.text,
        style = lyricTextStyle,
        constraints = Constraints(maxWidth = textWidthPx)
    )
    val translationLayout = line.translation
        ?.takeIf(String::isNotBlank)
        ?.let { translation ->
            textMeasurer.measure(
                text = translation,
                style = translationTextStyle,
                constraints = Constraints(maxWidth = textWidthPx)
            )
        }
    return LyricLineLayoutMetrics(
        itemHeightPx = lyricLayout.size.height +
            (translationLayout?.size?.height ?: 0) +
            (if (translationLayout != null) translationTopSpacingPx else 0) +
            itemVerticalPaddingPx,
        contentWidthPx = max(
            lyricLayout.maxVisibleLineWidthPx(),
            translationLayout?.maxVisibleLineWidthPx() ?: 0
        )
    )
}

private fun TextLayoutResult.maxVisibleLineWidthPx(): Int {
    var maxLineWidthPx = 0f
    repeat(lineCount) { lineIndex ->
        val lineWidthPx = kotlin.math.abs(
            getLineRight(lineIndex) - getLineLeft(lineIndex)
        )
        maxLineWidthPx = max(maxLineWidthPx, lineWidthPx)
    }
    return ceil(maxLineWidthPx).toInt()
}
private const val ActiveLyricScale = 1.04f
private const val InactiveLyricScale = 0.98f
private const val LyricGlowAlphaMultiplier = 0.18f
private val LyricGlowRadius = 18.dp
private val SwitchingLyricGlowRadius = 8.dp
private val ActiveLyricTextBlurRadius = 2.dp
private val InactiveLyricTextBlurRadius = 7.dp
private val SwitchingActiveLyricTextBlurRadius = 1.dp
private val SwitchingInactiveLyricTextBlurRadius = 3.dp
private const val ActiveLyricTextBlurAlpha = 0.14f
private const val InactiveLyricTextBlurAlpha = 0.34f
private const val SwitchingActiveLyricTextBlurAlpha = 0.08f
private const val SwitchingInactiveLyricTextBlurAlpha = 0.18f
private const val NearestInactiveLyricAlpha = 0.56f
private const val InactiveLyricAlphaStep = 0.10f
private const val FarthestInactiveLyricAlpha = 0.16f
private const val DefaultInactiveLyricAlpha = 0.42f
private const val LyricClickBackgroundMaxAlpha = 0.20f
private const val LyricClickBackgroundFadeInMillis = 110
private const val LyricClickBackgroundHoldMillis = 70L
private const val LyricClickBackgroundFadeOutMillis = 420
private const val LyricsSeekTrackingSettleDelayMs = 10L
private val LyricClickBackgroundHorizontalPadding = 5.dp
private val LyricClickBackgroundVerticalPadding = 2.dp
private val LyricItemOuterVerticalPadding =
    (LyricsLineSpacing - LyricClickBackgroundVerticalPadding * 2f) / 2f
private val LyricClickBackgroundCornerRadius = 9.dp
private val LyricsTranslationTopSpacing = 2.dp
private const val LyricsTranslationAlphaMultiplier = 0.78f

internal fun Modifier.verticalFadingEdges(fadeHeight: Dp): Modifier =
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithCache {
        val fadeFraction = if (size.height > 0f) {
            (fadeHeight.toPx() / size.height).coerceIn(0f, 0.5f)
        } else {
            0f
        }
        val maskStops = buildList {
            for (index in 0..LyricsEdgeFadeSteps) {
                val progress = index.toFloat() / LyricsEdgeFadeSteps
                val smoothAlpha = progress * progress * (3f - 2f * progress)
                add(fadeFraction * progress to Color.Black.copy(alpha = smoothAlpha))
            }
            for (index in 0..LyricsEdgeFadeSteps) {
                val progress = index.toFloat() / LyricsEdgeFadeSteps
                val inverseProgress = 1f - progress
                val smoothAlpha =
                    inverseProgress * inverseProgress * (3f - 2f * inverseProgress)
                add(
                    1f - fadeFraction + fadeFraction * progress to
                        Color.Black.copy(alpha = smoothAlpha)
                )
            }
        }
        val mask = Brush.verticalGradient(*maskStops.toTypedArray())
        onDrawWithContent {
            drawContent()
            drawRect(
                brush = mask,
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * 以极小且几乎透明的离屏内容预热边缘渐隐使用的渐变/DstIn 着色器。
 * 由宿主在歌词页稳定后仅插入两帧，避免首次切歌时触发驱动即时编译。
 */
@Composable
internal fun LyricsEdgeFadeShaderWarmup() {
    Canvas(
        modifier = Modifier
            .size(1.dp)
            .graphicsLayer {
                alpha = 0.01f
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val mask = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Black
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(
                        brush = mask,
                        blendMode = BlendMode.DstIn
                    )
                }
            }
    ) {
        drawRect(Color.White)
    }
}

private const val LyricsEdgeFadeSteps = 10
private const val LyricsMeasurementLinesPerFrame = 2
private const val PureMusicNoticeText = "纯音乐，请欣赏"

private fun lyricsPositionLog(
    sessionKey: Long,
    event: String,
    details: String
) {
    if (BuildConfig.PERFORMANCE_SAMPLING_ENABLED) {
        Log.d(LyricsPositionLogTag, "session=$sessionKey event=$event $details")
    }
}

private const val LyricsPositionLogTag = "FlowtoneLyricsPosition"
private const val LyricsPerformanceLogTag = "FlowtoneLyricsPerf"

private fun lyricsPerformanceLog(sessionKey: Long, details: String) {
    if (BuildConfig.PERFORMANCE_SAMPLING_ENABLED) {
        Log.d(LyricsPerformanceLogTag, "session=$sessionKey event=TEXT_MEASURE $details")
    }
}

private fun formatMillis(nanos: Long): String = "%.2f".format(nanos / 1_000_000.0)

private fun inactiveLyricAlpha(
    lineIndex: Int,
    activeLineIndex: Int?
): Float {
    if (activeLineIndex == null) {
        return DefaultInactiveLyricAlpha
    }
    val distance = kotlin.math.abs(lineIndex - activeLineIndex)
    val fadeSteps = (distance - 1).coerceAtLeast(0)
    return (NearestInactiveLyricAlpha - fadeSteps * InactiveLyricAlphaStep)
        .coerceAtLeast(FarthestInactiveLyricAlpha)
}

@Composable
private fun LyricsMessage(
    primary: String,
    modifier: Modifier,
    secondary: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    interactionEnabled: Boolean = true
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        Text(text = primary, style = MaterialTheme.typography.titleMedium, color = Color.White)
        secondary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.70f)
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                enabled = interactionEnabled,
                shape = RoundedCornerShape(50),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}
