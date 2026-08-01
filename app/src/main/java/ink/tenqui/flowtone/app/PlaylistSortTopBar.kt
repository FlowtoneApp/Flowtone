package ink.tenqui.flowtone.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.library.PlaylistSongSort
import ink.tenqui.flowtone.ui.library.PlaylistSongSortCriterion
import ink.tenqui.flowtone.ui.library.PlaylistSongSortOrder

internal val PlaylistSortPanelHeight = 260.dp
internal val PlaylistSortOrderMenuHeight = 152.dp

@Composable
internal fun PlaylistSortTopBar(
    visible: Boolean,
    progress: Float,
    orderMenuExpanded: Boolean,
    orderMenuProgress: Float,
    sort: PlaylistSongSort,
    onSortChange: (PlaylistSongSort) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    onOrderMenuExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val clampedOrderProgress = orderMenuProgress.coerceIn(0f, 1f)
    val expandedMenuHeight = PlaylistSortOrderMenuHeight * clampedOrderProgress
    val height = FlowtoneTopBarContentHeight +
        PlaylistSortPanelHeight * clampedProgress +
        expandedMenuHeight * clampedProgress
    val contentAlpha = clampedProgress
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val contentEnabled = visible && clampedProgress >= 0.98f

    BackHandler(enabled = visible) {
        if (orderMenuExpanded) {
            onOrderMenuExpandedChange(false)
        } else {
            onVisibleChange(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlowtoneTopBarContentHeight)
        ) {
            val closedIconX = (maxWidth - 48.dp).coerceAtLeast(0.dp)
            val iconX = closedIconX * (1f - clampedProgress)

            IconButton(
                onClick = { onVisibleChange(!visible) },
                modifier = Modifier.offset(x = iconX, y = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = if (visible) "关闭排序方式" else "排序方式",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = iconX + 48.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        alpha = clampedProgress
                        translationX = 12.dp.toPx() * (1f - clampedProgress)
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlaylistSortPanelHeight + expandedMenuHeight)
                .offset(y = FlowtoneTopBarContentHeight)
                .clickable(
                    enabled = visible,
                    interactionSource = noRippleInteractionSource,
                    indication = null,
                    onClick = { onVisibleChange(false) }
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = FlowtoneTopBarContentHeight)
                .padding(horizontal = 4.dp, vertical = 5.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = -10.dp.toPx() * (1f - clampedProgress)
                }
        ) {
            PlaylistSortSectionLabel(text = "排序")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                PlaylistSortRow(
                    label = sort.order.label,
                    selected = false,
                    enabled = contentEnabled,
                    onClick = { onOrderMenuExpandedChange(!orderMenuExpanded) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (orderMenuExpanded) "收起排序菜单" else "展开排序菜单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = 180f * clampedOrderProgress
                            }
                        )
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(expandedMenuHeight)
                        .clipToBounds()
                        .graphicsLayer { alpha = clampedOrderProgress }
                ) {
                    PlaylistSongSortOrder.entries.forEach { order ->
                        PlaylistSortRow(
                            label = order.label,
                            selected = sort.order == order,
                            enabled = contentEnabled && clampedOrderProgress >= 0.98f,
                            onClick = {
                                onSortChange(sort.copy(order = order))
                                onOrderMenuExpandedChange(false)
                            }
                        )
                    }
                }
            }

            PlaylistSortSectionLabel(
                text = "筛选",
                modifier = Modifier.padding(top = 10.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                PlaylistSongSortCriterion.entries.forEach { criterion ->
                    PlaylistSortRow(
                        label = criterion.label,
                        selected = sort.criterion == criterion,
                        enabled = contentEnabled,
                        onClick = { onSortChange(sort.copy(criterion = criterion)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSortSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, bottom = 3.dp)
    )
}

@Composable
private fun PlaylistSortRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
    }
}
