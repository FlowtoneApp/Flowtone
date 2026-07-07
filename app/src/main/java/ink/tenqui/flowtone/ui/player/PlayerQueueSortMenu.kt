package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun QueueDisplayOrderSelector(
    selectedOrder: QueueDisplayOrder,
    onOrderSelected: (QueueDisplayOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val nextOrder = when (selectedOrder) {
        QueueDisplayOrder.PlaybackOrder -> QueueDisplayOrder.ListOrder
        QueueDisplayOrder.ListOrder -> QueueDisplayOrder.PlaybackOrder
    }
    var fromOrder by remember { mutableStateOf(selectedOrder) }
    var toOrder by remember { mutableStateOf(selectedOrder) }
    val textProgress = remember { Animatable(1f) }
    val iconProgress = remember { Animatable(1f) }
    val density = LocalDensity.current
    val slotHeightPx = with(density) { PlayerQueueSortTextSlotHeight.toPx() }

    LaunchedEffect(selectedOrder) {
        if (selectedOrder != fromOrder && selectedOrder != toOrder) {
            fromOrder = toOrder
            toOrder = selectedOrder
            textProgress.snapTo(0f)
            iconProgress.snapTo(0f)
        } else if (fromOrder == toOrder && selectedOrder != toOrder) {
            fromOrder = toOrder
            toOrder = selectedOrder
            textProgress.snapTo(0f)
            iconProgress.snapTo(0f)
        }

        val targetProgress = if (selectedOrder == toOrder) 1f else 0f
        val textDistance = if (targetProgress > textProgress.value) {
            targetProgress - textProgress.value
        } else {
            textProgress.value - targetProgress
        }
        val textDurationMillis = (MINI_PLAYER_ANIMATION_DURATION_MS * textDistance)
            .toInt()
            .coerceAtLeast(1)
        val delayedIconStartMillis = if (textDurationMillis > PlayerQueueSortIconDelayMillis) {
            PlayerQueueSortIconDelayMillis
        } else {
            textDurationMillis / 2
        }
        val iconDurationMillis = (textDurationMillis - delayedIconStartMillis).coerceAtLeast(1)

        coroutineScope {
            launch {
                textProgress.animateTo(
                    targetValue = targetProgress,
                    animationSpec = tween(
                        durationMillis = textDurationMillis,
                        easing = MiniPlayerEasing
                    )
                )
            }
            launch {
                iconProgress.animateTo(
                    targetValue = targetProgress,
                    animationSpec = tween(
                        durationMillis = iconDurationMillis,
                        delayMillis = delayedIconStartMillis,
                        easing = MiniPlayerEasing
                    )
                )
            }
        }

        if (selectedOrder == toOrder && textProgress.value >= 0.999f) {
            fromOrder = toOrder
            textProgress.snapTo(1f)
            iconProgress.snapTo(1f)
        } else if (selectedOrder == fromOrder && textProgress.value <= 0.001f) {
            toOrder = fromOrder
            textProgress.snapTo(1f)
            iconProgress.snapTo(1f)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PlayerQueueSortMenuCornerRadius))
            .background(Color.White.copy(alpha = 0.82f))
            .clickable { onOrderSelected(nextOrder) }
            .padding(
                horizontal = PlayerQueueSortMenuHorizontalPadding,
                vertical = PlayerQueueSortMenuVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(PlayerQueueSortTextSlotHeight)
                .clipToBounds()
        ) {
            Text(
                text = fromOrder.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                modifier = Modifier.graphicsLayer {
                    translationY = slotHeightPx * textProgress.value
                }
            )
            Text(
                text = toOrder.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                modifier = Modifier.graphicsLayer {
                    translationY = -slotHeightPx * (1f - textProgress.value)
                }
            )
        }
        Box(
            modifier = Modifier
                .padding(start = PlayerQueueSortIconStartPadding)
                .size(PlayerQueueSortIconSize)
                .clipToBounds()
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "\u5207\u6362\u961f\u5217\u987a\u5e8f",
                tint = Color.Black.copy(alpha = 0.76f),
                modifier = Modifier
                    .size(PlayerQueueSortIconSize)
                    .graphicsLayer {
                        translationY = slotHeightPx * iconProgress.value
                    }
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.76f),
                modifier = Modifier
                    .size(PlayerQueueSortIconSize)
                    .graphicsLayer {
                        translationY = -slotHeightPx * (1f - iconProgress.value)
                    }
            )
        }
    }
}
