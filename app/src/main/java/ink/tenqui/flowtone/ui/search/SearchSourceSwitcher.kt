package ink.tenqui.flowtone.ui.search

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.search.SearchProviderOption
import ink.tenqui.flowtone.data.search.SearchScope
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal sealed interface SearchSourceSwitcherState {
    data object Collapsed : SearchSourceSwitcherState
    data object Expanded : SearchSourceSwitcherState
    data class Switching(val target: SearchScope) : SearchSourceSwitcherState
}

internal data class SearchSourceSwitcherItem(
    val scope: SearchScope,
    val label: String,
    val color: String? = null
)

internal fun searchSourceSwitcherItems(
    current: SearchScope,
    providers: List<SearchProviderOption>
): List<SearchSourceSwitcherItem> {
    val all = listOf(
        SearchSourceSwitcherItem(SearchScope.All, "全部"),
        SearchSourceSwitcherItem(SearchScope.Local, "本地")
    ) + providers.map { option ->
        SearchSourceSwitcherItem(SearchScope.Provider(option.extensionId), option.name, option.color)
    }
    val currentItem = all.firstOrNull { it.scope == current } ?: all.first()
    return listOf(currentItem) + all.filterNot { it.scope == currentItem.scope }
}

@Composable
internal fun SearchSourceSwitcher(
    currentScope: SearchScope,
    providers: List<SearchProviderOption>,
    state: SearchSourceSwitcherState,
    onStateChange: (SearchSourceSwitcherState) -> Unit,
    onScopeSelected: (SearchScope) -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceItems = searchSourceSwitcherItems(currentScope, providers)
    // 选择动画结束时仍保留旧行顺序与目标行的位置，避免 scope 更新后 row 被重排。
    var collapsingItems by remember { mutableStateOf<List<SearchSourceSwitcherItem>?>(null) }
    var collapsingTarget by remember { mutableStateOf<SearchScope?>(null) }
    var hideNameAfterCollapse by remember { mutableStateOf(true) }
    val items = collapsingItems ?: sourceItems
    val current = items.first()
    val canExpand = items.size > 1
    val textMeasurer = rememberTextMeasurer()
    val longestLabelWidth = items.maxOf { item ->
        textMeasurer.measure(item.label, MaterialTheme.typography.labelLarge).size.width
    }
    val labelWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
        longestLabelWidth.toDp()
    }
    val maxWidth = 224.dp
    val expandedWidth = (labelWidth + 72.dp).coerceIn(116.dp, maxWidth)
    val collapsedWidth = 40.dp
    val expanded = state !is SearchSourceSwitcherState.Collapsed
    val width by animateDpAsState(
        if (expanded) expandedWidth else collapsedWidth,
        tween(durationMillis = FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "SearchSourceSwitcherWidth"
    )
    val heightTarget = if (expanded) (RowHeight * items.size.coerceAtMost(MaxVisibleRows)) else RowHeight
    val height by animateDpAsState(
        heightTarget,
        tween(durationMillis = FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "SearchSourceSwitcherHeight"
    )
    val nameAlpha by animateFloatAsState(
        // 收起时文字由 surface 的 clip 直接裁掉；完全收起后才复位透明度，
        // 下次展开仍能保持名称渐入。
        if (expanded || !hideNameAfterCollapse) 1f else 0f,
        tween(durationMillis = 220, easing = FlowtoneMotion.Easing),
        label = "SearchSourceSwitcherNameAlpha"
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(state) {
        if (state is SearchSourceSwitcherState.Collapsed) {
            delay(FlowtoneMotion.DurationMillis.toLong())
            hideNameAfterCollapse = true
        } else {
            hideNameAfterCollapse = false
        }
    }

    BoxWithConstraints(modifier = modifier.width(width).height(height)) {
        Column(
            modifier = Modifier
                // 收起专用 row 只有一行，背景仍必须填满外层的动画高度。
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                .then(if (items.size > MaxVisibleRows) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        ) {
            val collapseTargetItem = collapsingTarget?.let { target ->
                collapsingItems?.firstOrNull { it.scope == target }
            }
            if (collapseTargetItem != null) {
                // 目标行已经移动到第一行后，使用无 offset 的独立 row 收起。
                // 这样父容器缩高时它不会再被原列表的负位移带出胶囊。
                SearchSourceSwitcherRow(
                    item = collapseTargetItem,
                    current = true,
                    showName = nameAlpha,
                    alpha = 1f,
                    modifier = Modifier,
                    onClick = {}
                )
            } else {
                items.forEachIndexed { index, item -> key(item.scope) {
                val activeTarget = (state as? SearchSourceSwitcherState.Switching)?.target
                val isTarget = activeTarget == item.scope
                val rowAlpha by animateFloatAsState(
                    targetValue = when {
                        activeTarget == null -> 1f
                        isTarget -> 1f
                        else -> 0f
                    },
                    animationSpec = tween(durationMillis = 160, easing = FlowtoneMotion.Easing),
                    label = "SearchSourceSwitcherRowAlpha$index"
                )
                val targetOffset = if (isTarget) RowHeight * -index else 0.dp
                val rowOffset by animateDpAsState(
                    targetOffset,
                    tween(durationMillis = SelectedRowMoveDurationMillis, easing = FlowtoneMotion.Easing),
                    label = "SearchSourceSwitcherRowOffset$index"
                )
                SearchSourceSwitcherRow(
                    item = item,
                    current = item.scope == currentScope,
                    showName = nameAlpha,
                    alpha = rowAlpha,
                    modifier = Modifier.offset(y = rowOffset),
                    onClick = {
                        when {
                            !canExpand -> Unit
                            state is SearchSourceSwitcherState.Collapsed -> onStateChange(SearchSourceSwitcherState.Expanded)
                            state is SearchSourceSwitcherState.Expanded && item.scope != currentScope -> {
                                onStateChange(SearchSourceSwitcherState.Switching(item.scope))
                                scope.launch {
                                    // 等待位移动画和最后一帧完成，再开始收起，避免目标行
                                    // 与胶囊收缩同时发生而短暂越过上边界。
                                    delay((SelectedRowMoveDurationMillis + SelectionMoveSettleMillis).toLong())
                                    // 先冻结旧列表和目标位置，再提交 scope，避免收起瞬间重排。
                                    collapsingItems = items
                                    collapsingTarget = item.scope
                                    onScopeSelected(item.scope)
                                    onStateChange(SearchSourceSwitcherState.Collapsed)
                                    delay(FlowtoneMotion.DurationMillis.toLong())
                                    collapsingItems = null
                                    collapsingTarget = null
                                }
                            }
                            state is SearchSourceSwitcherState.Expanded -> onStateChange(SearchSourceSwitcherState.Collapsed)
                        }
                    }
                )
            }
            }
            }
        }
    }
}

@Composable
private fun SearchSourceSwitcherRow(
    item: SearchSourceSwitcherItem,
    current: Boolean,
    showName: Float,
    alpha: Float,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val background = item.color?.let(::providerRowColor)
    val contentColor = background?.let { color ->
        if (color.luminance() > 0.45f) Color.Black else Color.White
    } ?: MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(RowHeight)
            .alpha(alpha)
            .then(if (background != null) Modifier.background(background) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (current) "当前搜索来源：${item.label}，点击切换" else "切换到${item.label}" }
            .padding(horizontal = 9.dp)
    ) {
        SearchScopeIcon(item.scope, contentColor)
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .padding(start = 8.dp)
                .alpha(showName)
        )
    }
}

@Composable
private fun SearchScopeIcon(scope: SearchScope, tint: Color) {
    val icon = when (scope) {
        SearchScope.All -> Icons.Rounded.Language
        SearchScope.Local -> Icons.Rounded.LibraryMusic
        is SearchScope.Provider -> Icons.Rounded.Radio
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
}

private fun providerRowColor(hex: String): Color =
    Color((0xFF000000L or hex.removePrefix("#").toLong(16)).toInt())

private val RowHeight: Dp = 40.dp
private const val MaxVisibleRows = 5
private const val SelectedRowMoveDurationMillis = 300
private const val SelectionMoveSettleMillis = 48
