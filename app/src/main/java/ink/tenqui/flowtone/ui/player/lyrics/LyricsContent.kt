package ink.tenqui.flowtone.ui.player.lyrics

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import ink.tenqui.flowtone.lyrics.LyricLine
import ink.tenqui.flowtone.lyrics.LyricsState

@Composable
internal fun LyricsContent(
    state: LyricsState,
    confirmedPlaybackPositionMs: Long,
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
    confirmedPlaybackPositionMs: Long,
    activeLineTargetY: Dp,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeTimestampMs = remember(lines, confirmedPlaybackPositionMs) {
        activeLyricTimestampMs(
            lines = lines,
            playbackPositionMs = confirmedPlaybackPositionMs
        )
    }
    val activeLineIndex = remember(lines, confirmedPlaybackPositionMs) {
        activeLyricAnchorIndex(
            lines = lines,
            playbackPositionMs = confirmedPlaybackPositionMs
        )
    }
    var isFollowingCurrentLine by remember(lines) { mutableStateOf(true) }
    var isPointerDown by remember { mutableStateOf(false) }
    var userInteractionVersion by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val activeLineTargetYPx = with(density) {
        activeLineTargetY.roundToPx()
    }
    val activeLyricTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold
    )
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
        val horizontalPaddingPx = with(density) { 56.dp.roundToPx() }
        val lyricTextWidthPx = (constraints.maxWidth - horizontalPaddingPx)
            .coerceAtLeast(0)
        val activeLineSizePx = remember(
            activeLineIndex,
            lines,
            lyricTextWidthPx,
            activeLyricTextStyle
        ) {
            val activeLine = activeLineIndex?.let(lines::getOrNull)
            if (activeLine == null || activeLine.text.isBlank()) {
                with(density) { 1.dp.roundToPx() }
            } else {
                textMeasurer.measure(
                    text = activeLine.text,
                    style = activeLyricTextStyle,
                    constraints = Constraints(maxWidth = lyricTextWidthPx)
                ).size.height
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
                .padding(horizontal = 28.dp)
                .pointerInput(lines) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        isPointerDown = true
                        markUserInteraction()
                        waitForUpOrCancellation()
                        isPointerDown = false
                        markUserInteraction()
                    }
                },
            contentPadding = PaddingValues(
                top = targetY,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.timestampMs}:$index" }
            ) { _, line ->
                if (line.text.isBlank()) {
                    // 保留一个不可见锚点，空行播放时中心会落在上下两句的间隙。
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                    )
                } else {
                    val isActive = line.timestampMs == activeTimestampMs
                    val lyricColor by animateColorAsState(
                        targetValue = if (isActive) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.48f)
                        },
                        animationSpec = tween(
                            durationMillis = LyricsLineTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricHighlightColor"
                    )
                    val lyricWeight by animateIntAsState(
                        targetValue = if (isActive) {
                            FontWeight.SemiBold.weight
                        } else {
                            FontWeight.Normal.weight
                        },
                        animationSpec = tween(
                            durationMillis = LyricsLineTransitionDurationMs,
                            easing = LyricsLineTransitionEasing
                        ),
                        label = "LyricHighlightWeight"
                    )
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineSmall,
                        color = lyricColor,
                        fontWeight = FontWeight(lyricWeight),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
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
