package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.FlowtonePageEasing
import kotlin.math.roundToInt

@Composable
internal fun PreloadStrengthRow(
    selectedCount: Int,
    onSelectedCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) = PreloadCountRow(
    title = "预载歌曲元信息强度",
    description = "按上一曲方向 20%、下一曲方向 80% 提前准备封面；歌曲文本元信息会随曲库或在线来源解析一并准备。",
    animationLabel = "PreloadStrengthExpandIconRotation",
    selectedCount = selectedCount,
    onSelectedCountChange = onSelectedCountChange,
    options = listOf(1, 3, 5, 7, 10),
    valueSuffix = "首",
    modifier = modifier
)

@Composable
internal fun LyricsPreloadStrengthRow(
    selectedCount: Int,
    onSelectedCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) = PreloadCountRow(
    title = "预载歌词强度",
    description = "按上一曲方向 20%、下一曲方向 80% 提前读取并解析本地歌词，减少前后切歌后的等待。",
    animationLabel = "LyricsPreloadStrengthExpandIconRotation",
    selectedCount = selectedCount,
    onSelectedCountChange = onSelectedCountChange,
    options = listOf(1, 3, 5, 7, 10),
    valueSuffix = "首",
    modifier = modifier
)

@Composable
internal fun OnlinePlaybackPreloadCountRow(
    selectedCount: Int,
    onSelectedCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) = PreloadCountRow(
    title = "在线播放预载曲数",
    description = "提前解析播放队列中后续在线歌曲的播放信息。",
    animationLabel = "OnlinePlaybackPreloadCountExpandIconRotation",
    selectedCount = selectedCount,
    onSelectedCountChange = onSelectedCountChange,
    options = listOf(1, 2, 3, 5),
    valueSuffix = "首",
    modifier = modifier
)

@Composable
internal fun OnlinePlaybackPreloadPercentageRow(
    selectedPercentage: Int,
    onSelectedPercentageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) = PreloadCountRow(
    title = "在线播放内容预载比例",
    description = "按资源字节比例预载支持分段请求的普通在线音频开头；流媒体与未知长度资源仅预解析。",
    animationLabel = "OnlinePlaybackPreloadPercentageExpandIconRotation",
    selectedCount = selectedPercentage,
    onSelectedCountChange = onSelectedPercentageChange,
    options = listOf(0, 10, 20, 30, 50),
    valueSuffix = "%",
    modifier = modifier
)

@Composable
private fun PreloadCountRow(
    title: String,
    description: String,
    animationLabel: String,
    selectedCount: Int,
    onSelectedCountChange: (Int) -> Unit,
    options: List<Int>,
    valueSuffix: String,
    modifier: Modifier = Modifier
) {
    val selectedIndex = options.indexOf(selectedCount).takeIf { it != -1 } ?: 2
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    val expandIconRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(280, easing = FlowtonePageEasing),
        label = animationLabel
    )

    SettingsExpandableOptionRow(
        title = title,
        subtitle = "当前：$selectedCount$valueSuffix",
        expanded = expanded,
        expandIconRotation = expandIconRotation,
        onExpandedChange = { nextExpanded ->
            expanded = nextExpanded
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { value ->
                    val index = value
                        .roundToInt()
                        .coerceIn(options.indices)
                    onSelectedCountChange(options[index])
                },
                valueRange = 0f..(options.size - 1).toFloat(),
                steps = options.size - 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                options.forEach { count ->
                    Text(
                        text = "$count$valueSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (count == selectedCount) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "低",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "高",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
