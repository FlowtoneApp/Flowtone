package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import kotlin.math.abs

@Composable
internal fun FullscreenSongInfoOverlay(
    song: Song?,
    progress: Float,
    topPadding: Dp,
    backGestureThresholdPx: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overlayProgress = progress.coerceIn(0f, 1f)
    if (song == null && overlayProgress <= 0.001f) {
        return
    }

    val rows = remember(song) {
        song?.toSongInfoRows().orEmpty()
    }
    val contentColor = Color.White
    val secondaryColor = Color.White.copy(alpha = 0.72f)
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayProgress
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.04f),
                        Color.Black.copy(alpha = 0.36f),
                        Color.Black.copy(alpha = 0.68f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 44.dp * (1f - overlayProgress))
                .padding(start = 24.dp, top = topPadding, end = 24.dp, bottom = 36.dp)
                .songInfoBackGesture(
                    enabled = overlayProgress > 0.5f,
                    scrollState = scrollState,
                    thresholdPx = backGestureThresholdPx,
                    onBack = onBack
                )
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "歌曲信息",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            rows.forEachIndexed { index, row ->
                SongInfoRow(
                    label = row.label,
                    value = row.value,
                    labelColor = secondaryColor,
                    valueColor = contentColor
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                }
            }
        }
    }
}

@Composable
private fun SongInfoRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            maxLines = if (value.length > 48) 4 else 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Song.toSongInfoRows(): List<SongInfoRowData> {
    return listOf(
        SongInfoRowData("标题", title.ifBlank { "未知" }),
        SongInfoRowData("艺术家", artist.ifBlank { "未知" }),
        SongInfoRowData("专辑", "未知"),
        SongInfoRowData("时长", formatSongInfoDuration(durationMs)),
        SongInfoRowData("来源", sourceType.toDisplayText()),
        SongInfoRowData("Uri", uri.toString().ifBlank { "未知" }),
        SongInfoRowData("Song ID", id.toString()),
        SongInfoRowData("Album ID", albumId?.toString() ?: "未知"),
        SongInfoRowData("Artwork Uri", artworkUri?.toString() ?: "未知")
    )
}

private fun SourceType.toDisplayText(): String {
    return when (this) {
        SourceType.Local -> "本地音乐"
        SourceType.Online -> "在线音乐"
    }
}

private fun formatSongInfoDuration(durationMs: Long): String {
    if (durationMs <= 0L) {
        return "未知"
    }

    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private data class SongInfoRowData(
    val label: String,
    val value: String
)

private fun Modifier.songInfoBackGesture(
    enabled: Boolean,
    scrollState: ScrollState,
    thresholdPx: Float,
    onBack: () -> Unit
): Modifier {
    if (!enabled) {
        return this
    }

    return pointerInput(scrollState, thresholdPx) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )

            var dragX = 0f
            var dragY = 0f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break

                if (!event.changes.any { pointer -> pointer.pressed }) {
                    break
                }
                if (scrollState.value != 0) {
                    break
                }

                val delta = change.position - change.previousPosition
                dragX += delta.x
                dragY += delta.y

                val absDragX = abs(dragX)
                val absDragY = abs(dragY)
                val isClearRightSwipe =
                    dragX > thresholdPx && absDragX > absDragY * 1.5f
                val isClearPullDown =
                    dragY > thresholdPx && absDragY > absDragX * 1.25f

                if (isClearRightSwipe || isClearPullDown) {
                    change.consume()
                    onBack()
                    break
                }

                val upwardPassedThreshold = dragY < -thresholdPx
                val horizontalGestureDominates =
                    absDragX > thresholdPx && absDragX >= absDragY
                if (upwardPassedThreshold || horizontalGestureDominates) {
                    break
                }
            }
        }
    }
}
