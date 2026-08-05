package ink.tenqui.flowtone.ui.player.lyrics

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import ink.tenqui.flowtone.lyrics.LyricLine
import ink.tenqui.flowtone.lyrics.LyricsState

@Composable
internal fun LyricsContent(
    state: LyricsState,
    confirmedPlaybackPositionMs: Long?,
    activeLineTargetY: Dp,
    visibilityProgress: Float,
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
            if (state.lines.isEmpty()) {
                LyricsMessage("未识别到有效歌词", visibleModifier)
            } else {
                LyricsList(
                    lines = state.lines,
                    confirmedPlaybackPositionMs = confirmedPlaybackPositionMs,
                    activeLineTargetY = activeLineTargetY,
                    modifier = visibleModifier
                )
            }
        }
    }
}

@Composable
private fun LyricsList(
    lines: List<LyricLine>,
    confirmedPlaybackPositionMs: Long?,
    activeLineTargetY: Dp,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeTimestampMs = remember(lines, confirmedPlaybackPositionMs) {
        confirmedPlaybackPositionMs?.let { positionMs ->
            activeLyricTimestampMs(
                lines = lines,
                playbackPositionMs = positionMs
            )
        }
    }
    val activeLineIndex = remember(lines, confirmedPlaybackPositionMs) {
        confirmedPlaybackPositionMs?.let { positionMs ->
            activeLyricAnchorIndex(
                lines = lines,
                playbackPositionMs = positionMs
            )
        } ?: lines.indices.firstOrNull()
    }
    var isFollowingCurrentLine by remember(lines) { mutableStateOf(true) }
    var isPointerDown by remember { mutableStateOf(false) }
    var userInteractionVersion by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val activeLineTargetYPx = with(density) {
        activeLineTargetY.roundToPx()
    }
    val lyricTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold
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

    BoxWithConstraints(modifier = modifier) {
        val targetY = activeLineTargetY.coerceIn(0.dp, maxHeight)
        val bottomPadding = (maxHeight - targetY).coerceAtLeast(0.dp)
        val horizontalPaddingPx = with(density) {
            (LyricsHorizontalPadding * 2).roundToPx()
        }
        val lyricTextWidthPx = (constraints.maxWidth - horizontalPaddingPx)
            .coerceAtLeast(0)
        val activeLineSizePx = remember(
            activeLineIndex,
            lines,
            lyricTextWidthPx,
            activeLyricTextStyle,
            lyricLineSpacingPx,
            blankLyricLineHeightPx
        ) {
            val activeLine = activeLineIndex?.let(lines::getOrNull)
            if (activeLine == null || activeLine.text.isBlank()) {
                blankLyricLineHeightPx
            } else {
                textMeasurer.measure(
                    text = activeLine.text,
                    style = activeLyricTextStyle,
                    constraints = Constraints(maxWidth = lyricTextWidthPx)
                ).size.height + lyricLineSpacingPx
            }
        }

        LaunchedEffect(
            activeLineIndex,
            activeLineTargetYPx,
            activeLineSizePx,
            isFollowingCurrentLine
        ) {
            if (isFollowingCurrentLine && activeLineIndex != null) {
                listState.animateScrollToItemAtY(
                    index = activeLineIndex,
                    targetYPx = activeLineTargetYPx,
                    targetItemSizePx = activeLineSizePx
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
                .padding(horizontal = LyricsHorizontalPadding)
                .pointerInput(lines) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isPointerDown = true
                        markUserInteraction()
                        waitForUpOrCancellation()
                        isPointerDown = false
                        markUserInteraction()
                    }
                }
                .verticalFadingEdges(LyricsEdgeFadeHeight),
            contentPadding = PaddingValues(
                top = targetY,
                bottom = bottomPadding
            )
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.timestampMs}:$index" }
            ) { index, line ->
                if (line.text.isBlank()) {
                    // 保留一个不可见锚点，空行播放时中心会落在上下两句的间隙。
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(BlankLyricLineHeight)
                    )
                } else {
                    val isActive = line.timestampMs == activeTimestampMs
                    val targetLyricAlpha = if (isActive) {
                        1f
                    } else {
                        inactiveLyricAlpha(
                            lineIndex = index,
                            activeLineIndex = activeLineIndex
                        )
                    }
                    val lyricColor by animateColorAsState(
                        targetValue = Color.White.copy(alpha = targetLyricAlpha),
                        animationSpec = tween(
                            durationMillis = LyricsLineTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricHighlightColor"
                    )
                    val lyricScale by animateFloatAsState(
                        targetValue = if (isActive) ActiveLyricScale else 1f,
                        animationSpec = tween(
                            durationMillis = LyricsLineTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricHighlightScale"
                    )
                    val lyricGlowAlpha by animateFloatAsState(
                        targetValue = targetLyricAlpha * LyricGlowAlphaMultiplier,
                        animationSpec = tween(
                            durationMillis = LyricsLineTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricGlowAlpha"
                    )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isActive) 1f else 0f)
                            .padding(vertical = LyricsLineSpacing / 2f)
                            .graphicsLayer {
                                scaleX = lyricScale
                                scaleY = lyricScale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                    )
                }
            }
        }
    }
}

private val LyricsHorizontalPadding = 36.dp
private val LyricsLineSpacing = 38.dp
private val BlankLyricLineHeight = 12.dp
private val LyricsEdgeFadeHeight = 64.dp
private const val ActiveLyricScale = 1.12f
private const val LyricGlowAlphaMultiplier = 0.18f
private val LyricGlowRadius = 18.dp
private const val NearestInactiveLyricAlpha = 0.64f
private const val InactiveLyricAlphaStep = 0.11f
private const val FarthestInactiveLyricAlpha = 0.20f
private const val DefaultInactiveLyricAlpha = 0.48f

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
