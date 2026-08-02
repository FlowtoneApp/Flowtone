package ink.tenqui.flowtone.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.library.PlaylistSongSort
import ink.tenqui.flowtone.ui.library.PlaylistSongSortCriterion
import ink.tenqui.flowtone.ui.library.PlaylistSongSortDirection
import ink.tenqui.flowtone.ui.library.PlaylistSongTitleCharacterPriority

internal val PlaylistSortPanelHeight = 350.dp
internal val PlaylistSortPanelCollapsedHeight = 252.dp
internal val PlaylistSortContentBlurRadius = 14.dp

private val SortOptionShape = RoundedCornerShape(8.dp)
private const val SortOptionColorDurationMillis = 180

@Composable
internal fun PlaylistSortTopBar(
    visible: Boolean,
    progress: Float,
    sort: PlaylistSongSort,
    onSortChange: (PlaylistSongSort) -> Unit,
    onVisibleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val characterSectionProgress by animateFloatAsState(
        targetValue = if (sort.criterion == PlaylistSongSortCriterion.Title) 1f else 0f,
        animationSpec = tween(200),
        label = "SortCharacterSectionHeight"
    )
    val panelHeight = PlaylistSortPanelCollapsedHeight +
        (PlaylistSortPanelHeight - PlaylistSortPanelCollapsedHeight) *
        characterSectionProgress
    val height = FlowtoneTopBarContentHeight + panelHeight * clampedProgress
    val contentEnabled = visible && clampedProgress >= 0.98f
    val panelInteractionSource = remember { MutableInteractionSource() }

    BackHandler(enabled = visible) { onVisibleChange(false) }

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
                .height(panelHeight)
                .offset(y = FlowtoneTopBarContentHeight)
                .clickable(
                    enabled = contentEnabled,
                    interactionSource = panelInteractionSource,
                    indication = null,
                    onClick = {}
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = FlowtoneTopBarContentHeight)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .graphicsLayer {
                    alpha = clampedProgress
                    translationY = 12.dp.toPx() * (1f - clampedProgress)
                }
        ) {
            SortSectionLabel("排序依据")
            Spacer(modifier = Modifier.height(8.dp))
            SortCriterionGrid(
                selected = sort.criterion,
                enabled = contentEnabled,
                onSelect = { criterion -> onSortChange(sort.copy(criterion = criterion)) }
            )

            Spacer(modifier = Modifier.height(22.dp))
            SortSectionLabel("排序顺序")
            Spacer(modifier = Modifier.height(8.dp))
            SortDirectionRow(
                selected = sort.direction,
                enabled = contentEnabled,
                onSelect = { direction -> onSortChange(sort.copy(direction = direction)) }
            )

            AnimatedVisibility(
                visible = sort.criterion == PlaylistSongSortCriterion.Title,
                enter = fadeIn(tween(180)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(22.dp))
                    SortSectionLabel("字符优先级")
                    Spacer(modifier = Modifier.height(8.dp))
                    TitleCharacterPriorityRow(
                        selected = sort.titleCharacterPriority,
                        enabled = contentEnabled,
                        onSelect = { priority ->
                            onSortChange(sort.copy(titleCharacterPriority = priority))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SortCriterionGrid(
    selected: PlaylistSongSortCriterion,
    enabled: Boolean,
    onSelect: (PlaylistSongSortCriterion) -> Unit
) {
    val criteria = PlaylistSongSortCriterion.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        criteria.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { criterion ->
                    SortChoiceCell(
                        label = criterion.label,
                        selected = selected == criterion,
                        enabled = enabled,
                        onClick = { onSelect(criterion) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SortDirectionRow(
    selected: PlaylistSongSortDirection,
    enabled: Boolean,
    onSelect: (PlaylistSongSortDirection) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlaylistSongSortDirection.entries.forEach { direction ->
            SortChoiceCell(
                label = direction.label,
                selected = selected == direction,
                enabled = enabled,
                onClick = { onSelect(direction) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TitleCharacterPriorityRow(
    selected: PlaylistSongTitleCharacterPriority,
    enabled: Boolean,
    onSelect: (PlaylistSongTitleCharacterPriority) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlaylistSongTitleCharacterPriority.entries.forEach { priority ->
            SortChoiceCell(
                label = priority.label,
                selected = selected == priority,
                enabled = enabled,
                compact = true,
                showCheck = false,
                onClick = { onSelect(priority) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SortChoiceCell(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showCheck: Boolean = true
) {
    val selectedContainer = MaterialTheme.colorScheme.secondaryContainer
    val unselectedContainer = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
    val containerColor by animateColorAsState(
        targetValue = if (selected) selectedContainer else unselectedContainer,
        animationSpec = tween(SortOptionColorDurationMillis),
        label = "SortOptionContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(SortOptionColorDurationMillis),
        label = "SortOptionContent"
    )

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(SortOptionShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected && showCheck) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "已选择",
                tint = contentColor,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp)
            )
        }
    }
}
