package ink.tenqui.flowtone.ui.player.lyrics

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import ink.tenqui.flowtone.lyrics.LyricLine
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.ui.player.PlayerSongSwitchDurationMillis
import ink.tenqui.flowtone.ui.player.TrackSwitchProgressEasing
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
    state: LyricsState,
    confirmedPlaybackPositionMs: Long?,
    activeLineTargetY: Dp,
    visibilityProgress: Float,
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
        .graphicsLayer {
            alpha = visibilityProgress.coerceIn(0f, 1f)
            translationY = 10.dp.toPx() * (1f - visibilityProgress)
        }
        .fillMaxSize()

    when (state) {
        LyricsState.Idle -> Unit
        LyricsState.Loading -> LyricsMessage("正在读取歌词", visibleModifier)
        LyricsState.DirectoryNotSelected -> LyricsMessage(
            primary = "暂无歌词",
            secondary = "声流需要外部文件夹读取权限才能读取歌词",
            actionLabel = "选择歌词目录",
            onAction = onChooseLyricsDirectory,
            modifier = visibleModifier
        )
        LyricsState.DirectoryPermissionLost -> LyricsMessage(
            primary = "歌词目录授权已失效",
            secondary = "请重新选择歌词目录以恢复读取权限",
            actionLabel = "重新选择歌词目录",
            onAction = onChooseLyricsDirectory,
            modifier = visibleModifier
        )
        LyricsState.OutsideSelectedDirectory -> LyricsMessage(
            primary = "暂无歌词",
            secondary = "当前歌曲不在已授权的歌词目录中",
            actionLabel = "重新选择歌词目录",
            onAction = onChooseLyricsDirectory,
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
                    LyricsList(
                        lines = state.lines,
                        confirmedPlaybackPositionMs = confirmedPlaybackPositionMs,
                        activeLineTargetY = activeLineTargetY,
                        trackSwitchPhase = trackSwitchPhase,
                        trackSwitchDirection = trackSwitchDirection,
                        onTrackEnterReady = onTrackEnterReady,
                        onTrackEnterFinished = onTrackEnterFinished,
                        onLyricPress = onLyricPress,
                        onLyricClick = onLyricClick,
                        modifier = visibleModifier
                    )
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
    val glowBlurRadiusPx = with(LocalDensity.current) {
        LyricGlowRadius.toPx()
    }
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = PureMusicNoticeText,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = Shadow(
                    color = Color.White.copy(alpha = LyricGlowAlphaMultiplier),
                    offset = Offset.Zero,
                    blurRadius = glowBlurRadiusPx
                )
            ),
            color = Color.White
        )
    }
}

@Composable
private fun LyricsList(
    lines: List<LyricLine>,
    confirmedPlaybackPositionMs: Long?,
    activeLineTargetY: Dp,
    trackSwitchPhase: LyricsTrackSwitchPhase,
    trackSwitchDirection: Int,
    onTrackEnterReady: () -> Unit,
    onTrackEnterFinished: () -> Unit,
    onLyricPress: () -> Unit,
    onLyricClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val gestureCoroutineScope = rememberCoroutineScope()
    val lastConfirmedPlaybackPosition = remember(lines) {
        PlaybackPositionHolder(confirmedPlaybackPositionMs)
    }
    SideEffect {
        if (
            trackSwitchPhase != LyricsTrackSwitchPhase.Exiting &&
            confirmedPlaybackPositionMs != null
        ) {
            lastConfirmedPlaybackPosition.value = confirmedPlaybackPositionMs
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
    val activeTimestampMs = remember(lines, effectivePlaybackPositionMs) {
        effectivePlaybackPositionMs?.let { positionMs ->
            activeLyricTimestampMs(
                lines = lines,
                playbackPositionMs = positionMs
            )
        }
    }
    val activeLineIndex = remember(lines, effectivePlaybackPositionMs) {
        effectivePlaybackPositionMs?.let { positionMs ->
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
    val activeLyricTextStyle = lyricTextStyle
    val lyricGlowBlurRadiusPx = with(density) {
        LyricGlowRadius.toPx()
    }
    val lyricLineSpacingPx = with(density) {
        LyricsLineSpacing.roundToPx()
    }
    val blankLyricLineHeightPx = with(density) {
        BlankLyricLineHeight.roundToPx()
    }
    val trackSwitchDistancePx = with(density) {
        LyricsTrackSwitchDistance.toPx()
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
    val trackSwitchExitTimeline = remember(lines) { Animatable(0f) }
    val trackSwitchEntranceDirection = remember(lines) { trackSwitchDirection }
    val trackSwitchStaggerAnchorIndex = remember(lines, trackSwitchPhase) {
        listState.firstVisibleItemIndex
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
                withFrameNanos { }
                withFrameNanos { }
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
                val remainingDurationMillis = (
                    LyricsTrackSwitchExitTotalDurationMillis *
                        (1f - trackSwitchExitTimeline.value)
                    ).roundToInt().coerceAtLeast(1)
                trackSwitchExitTimeline.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainingDurationMillis,
                        easing = LinearEasing
                    )
                )
            }
        }
    }
    val textMeasurer = rememberTextMeasurer()

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
        val activeLineSizePx = remember(
            activeLineIndex,
            lines,
            lyricTextWidthPx,
            activeLyricTextStyle,
            lyricTranslationTextStyle,
            lyricLineSpacingPx,
            blankLyricLineHeightPx
        ) {
            val activeLine = activeLineIndex?.let(lines::getOrNull)
            if (
                activeLine == null ||
                    (activeLine.text.isBlank() && activeLine.translation.isNullOrBlank())
            ) {
                blankLyricLineHeightPx
            } else {
                val originalHeightPx = textMeasurer.measure(
                    text = activeLine.text,
                    style = activeLyricTextStyle,
                    constraints = Constraints(maxWidth = lyricTextWidthPx)
                ).size.height
                val translationHeightPx = activeLine.translation
                    ?.takeIf(String::isNotBlank)
                    ?.let { translation ->
                        with(density) { LyricsTranslationTopSpacing.roundToPx() } +
                            textMeasurer.measure(
                                text = translation,
                                style = lyricTranslationTextStyle,
                                constraints = Constraints(maxWidth = lyricTextWidthPx)
                            ).size.height
                    }
                    ?: 0
                originalHeightPx + translationHeightPx + lyricLineSpacingPx
            }
        }
        val activeLineTransitionDurationMs = remember(lines, activeLineIndex) {
            activeLineIndex?.let { lineIndex ->
                lyricTrackingTransitionDurationMs(
                    lines = lines,
                    lineIndex = lineIndex
                )
            } ?: LyricsLineTransitionDurationMs
        }

        LaunchedEffect(
            activeLineIndex,
            activeLineTargetYPx,
            activeLineSizePx,
            activeLineTransitionDurationMs,
            isFollowingCurrentLine,
            trackSwitchPhase
        ) {
            if (
                trackSwitchPhase != LyricsTrackSwitchPhase.Exiting &&
                isFollowingCurrentLine &&
                activeLineIndex != null
            ) {
                listState.animateScrollToItemAtY(
                    index = activeLineIndex,
                    targetYPx = activeLineTargetYPx,
                    targetItemSizePx = activeLineSizePx,
                    transitionDurationMs = activeLineTransitionDurationMs
                )
                val finalItem = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == activeLineIndex }
                val finalVisibleY = finalItem?.let { item ->
                    lazyListItemCenterInViewport(
                        itemOffset = item.offset,
                        itemSize = item.size,
                        viewportStartOffset = listState.layoutInfo.viewportStartOffset
                    )
                }
                Log.d(
                    "LyricsAnchor",
                    "index=$activeLineIndex targetY=$activeLineTargetYPx " +
                        "measuredHeight=$activeLineSizePx finalY=$finalVisibleY " +
                        "viewportStart=${listState.layoutInfo.viewportStartOffset} " +
                        "viewport=${listState.layoutInfo.viewportSize.height}"
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalFadingEdges(LyricsEdgeFadeHeight)
                .padding(
                    start = LyricsStartPadding,
                    end = LyricsEndPadding
                )
                .pointerInput(lines) {
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
                },
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
                    val staggerOrder = (
                        index - trackSwitchStaggerAnchorIndex
                        ).coerceIn(0, LyricsTrackSwitchMaxStaggeredLines - 1)
                    val lyricInteractionSource = remember(line.timestampMs, index) {
                        MutableInteractionSource()
                    }
                    val lyricTextLayout = remember(
                        line.text,
                        lyricTextWidthPx,
                        lyricTextStyle
                    ) {
                        textMeasurer.measure(
                            text = line.text,
                            style = lyricTextStyle,
                            constraints = Constraints(maxWidth = lyricTextWidthPx)
                        )
                    }
                    val translationTextLayout = line.translation
                        ?.takeIf(String::isNotBlank)
                        ?.let { translation ->
                            remember(
                                translation,
                                lyricTextWidthPx,
                                lyricTranslationTextStyle
                            ) {
                                textMeasurer.measure(
                                    text = translation,
                                    style = lyricTranslationTextStyle,
                                    constraints = Constraints(maxWidth = lyricTextWidthPx)
                                )
                            }
                        }
                    val lyricBackgroundWidth = if (
                        lyricTextLayout.lineCount > 1 ||
                            (translationTextLayout?.lineCount ?: 0) > 1
                    ) {
                        lyricRowWidth
                    } else {
                        (
                            with(density) {
                                max(
                                    lyricTextLayout.size.width,
                                    translationTextLayout?.size?.width ?: 0
                                ).toDp()
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
                    val lyricColor by animateColorAsState(
                        targetValue = Color.White.copy(alpha = targetLyricAlpha),
                        animationSpec = tween(
                            durationMillis = lyricTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricHighlightColor"
                    )
                    val lyricScale by animateFloatAsState(
                        targetValue = if (isActive) {
                            ActiveLyricScale
                        } else {
                            InactiveLyricScale
                        },
                        animationSpec = tween(
                            durationMillis = lyricTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricHighlightScale"
                    )
                    val lyricGlowAlpha by animateFloatAsState(
                        targetValue = targetLyricAlpha * LyricGlowAlphaMultiplier,
                        animationSpec = tween(
                            durationMillis = lyricTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricGlowAlpha"
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
                                    staggerOrder * LyricsTrackSwitchStaggerMillis
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
                                val exitElapsedMillis =
                                    trackSwitchExitTimeline.value *
                                        LyricsTrackSwitchExitTotalDurationMillis
                                val exitLineDelayMillis =
                                    staggerOrder * LyricsTrackSwitchExitStaggerMillis
                                val linearExitProgress = (
                                    (exitElapsedMillis - exitLineDelayMillis) /
                                        LyricsTrackSwitchExitLineDurationMillis
                                    ).coerceIn(0f, 1f)
                                val exitProgress = TrackSwitchProgressEasing.transform(
                                    linearExitProgress
                                )
                                alpha = entranceVisibility * (1f - exitProgress)
                                translationX =
                                    trackSwitchDistancePx *
                                        trackSwitchEntranceDirection *
                                        (1f - entranceVisibility) -
                                        trackSwitchDistancePx *
                                        trackSwitchDirection *
                                        exitProgress
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
                                .pointerInput(line.timestampMs, index) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        onLyricPress()
                                    }
                                }
                                .clickable(
                                    interactionSource = lyricInteractionSource,
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = "跳转到此歌词"
                                ) {
                                    clickFeedbackVersion += 1
                                    onLyricClick(line.timestampMs)
                                    lyricSeekVersion += 1
                                }
                                .background(
                                    color = Color(0xFF8A8A8A).copy(
                                        alpha = clickFeedbackAlpha.value
                                    ),
                                    shape = RoundedCornerShape(LyricClickBackgroundCornerRadius)
                                )
                                .padding(
                                    horizontal = LyricClickBackgroundHorizontalPadding,
                                    vertical = LyricClickBackgroundVerticalPadding
                                )
                        ) {
                            Column {
                                Text(
                                    text = line.text,
                                    style = lyricTextStyle.copy(
                                        shadow = Shadow(
                                            color = Color.White.copy(alpha = lyricGlowAlpha),
                                            offset = Offset.Zero,
                                            blurRadius = lyricGlowBlurRadiusPx
                                        )
                                    ),
                                    color = lyricColor,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                line.translation
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { translation ->
                                        Text(
                                            text = translation,
                                            style = lyricTranslationTextStyle.copy(
                                                shadow = Shadow(
                                                    color = Color.White.copy(
                                                        alpha =
                                                            lyricGlowAlpha *
                                                                LyricsTranslationAlphaMultiplier
                                                    ),
                                                    offset = Offset.Zero,
                                                    blurRadius = lyricGlowBlurRadiusPx
                                                )
                                            ),
                                            color = lyricColor.copy(
                                                alpha =
                                                    lyricColor.alpha *
                                                        LyricsTranslationAlphaMultiplier
                                            ),
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

private val LyricsStartPadding = 16.dp
private val LyricsEndPadding = 48.dp
private val LyricsLineSpacing = 38.dp
private val BlankLyricLineHeight = 12.dp
private val LyricsEdgeFadeHeight = 64.dp
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
private const val ActiveLyricScale = 1.04f
private const val InactiveLyricScale = 0.98f
private const val LyricGlowAlphaMultiplier = 0.18f
private val LyricGlowRadius = 18.dp
private const val NearestInactiveLyricAlpha = 0.56f
private const val InactiveLyricAlphaStep = 0.10f
private const val FarthestInactiveLyricAlpha = 0.16f
private const val DefaultInactiveLyricAlpha = 0.42f
private const val LyricClickBackgroundMaxAlpha = 0.20f
private const val LyricClickBackgroundFadeInMillis = 110
private const val LyricClickBackgroundHoldMillis = 70L
private const val LyricClickBackgroundFadeOutMillis = 420
private val LyricClickBackgroundHorizontalPadding = 5.dp
private val LyricClickBackgroundVerticalPadding = 2.dp
private val LyricItemOuterVerticalPadding =
    (LyricsLineSpacing - LyricClickBackgroundVerticalPadding * 2f) / 2f
private val LyricClickBackgroundCornerRadius = 9.dp
private val LyricsTranslationTopSpacing = 2.dp
private const val LyricsTranslationAlphaMultiplier = 0.78f

private fun Modifier.verticalFadingEdges(fadeHeight: Dp): Modifier =
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

private const val LyricsEdgeFadeSteps = 10
private const val PureMusicNoticeText = "纯音乐，请欣赏"

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
    onAction: (() -> Unit)? = null
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
                shape = RoundedCornerShape(50),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}
