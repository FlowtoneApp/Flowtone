package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import ink.tenqui.flowtone.ui.components.ProviderExtensionIcon
import ink.tenqui.flowtone.ui.components.extensionManifestColor

@Composable
internal fun LibrarySourceTitle(
    sources: List<LibrarySourceItem>,
    selectedSourceKey: String,
    isPageActive: Boolean,
    viewportBottom: Float,
    onSourceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    collapseProgress: Float = 0f,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val all = remember(sources) { listOf("mine" to null) + sources.map { it.identity.key to it } }
    val current = all.firstOrNull { it.first == selectedSourceKey } ?: all.first()
    val ordered = remember(all, selectedSourceKey) { listOf(current) + all.filterNot { it.first == current.first } }
    var showing by remember { mutableStateOf(false) }
    DisposableEffect(showing) {
        onExpandedChange(showing)
        onDispose { if (showing) onExpandedChange(false) }
    }
    var anchorTop by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPageActive) { if (!isPageActive) showing = false }
    val density = LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    fun widthFor(item: Pair<String, LibrarySourceItem?>): Dp = with(density) {
        (measurer.measure(item.second?.name ?: "我的音乐", style).size.width.toDp() + 72.dp).coerceIn(116.dp, 240.dp)
    }
    val fullWidth = widthFor(current)
    val collapsedWidth = 68.dp
    val currentWidth = fullWidth + (collapsedWidth - fullWidth) * collapseProgress.coerceIn(0f, 1f)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
    Box(modifier.onGloballyPositioned { anchorTop = it.boundsInWindow().top }) {
        val interaction = remember { MutableInteractionSource() }
        Box(Modifier.width(currentWidth).height(48.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = isPageActive && !showing) { showing = true },
            contentAlignment = Alignment.CenterStart) {
            SourceMorphRow(current, Modifier.height(40.dp).alpha(if (showing) 0f else 1f)
                .clip(RoundedCornerShape(20.dp))
                .background(containerColor)
                .indication(interaction, ripple()), true)
        }
        if (showing && isPageActive) {
            val maxWidth = (LocalConfiguration.current.screenWidthDp.dp - 40.dp).coerceAtMost(280.dp)
            val maxHeight = with(density) { (viewportBottom - anchorTop - 12.dp.toPx()).coerceAtLeast(40.dp.toPx()).toDp() }
            var state by remember { mutableStateOf<LibrarySourceSwitcherState>(LibrarySourceSwitcherState.Collapsed) }
            val frozen = remember { ordered }
            var target by remember { mutableStateOf<Pair<String, LibrarySourceItem?>?>(null) }
            var closing by remember { mutableStateOf(false) }
            val callback by rememberUpdatedState(onSourceSelected)
            LaunchedEffect(Unit) { state = LibrarySourceSwitcherState.Expanded }
            fun close() { if (state !is LibrarySourceSwitcherState.Switching) closing = true }
            LaunchedEffect(closing) {
                if (closing) { state = LibrarySourceSwitcherState.Collapsed; kotlinx.coroutines.delay(400); showing = false }
            }
            LaunchedEffect(state) {
                val switching = state as? LibrarySourceSwitcherState.Switching ?: return@LaunchedEffect
                kotlinx.coroutines.delay(348)
                target = frozen.first { it.first == switching.targetKey }
                callback(switching.targetKey)
                state = LibrarySourceSwitcherState.Collapsed
                closing = true
            }
            val open = state !is LibrarySourceSwitcherState.Collapsed
            val width by androidx.compose.animation.core.animateDpAsState(
                if (open) frozen.maxOf { widthFor(it) }.coerceAtMost(maxWidth) else {
                    val targetWidth = widthFor(target ?: frozen.first())
                    (targetWidth + (collapsedWidth - targetWidth) * collapseProgress.coerceIn(0f, 1f)).coerceAtMost(maxWidth)
                },
                tween(400, easing = ink.tenqui.flowtone.ui.components.FlowtoneMotion.Easing), label = "LibrarySourceMorphWidth")
            val height by androidx.compose.animation.core.animateDpAsState(
                if (open) (40.dp * frozen.size.coerceAtMost(5)).coerceAtMost(maxHeight) else 40.dp,
                tween(400, easing = ink.tenqui.flowtone.ui.components.FlowtoneMotion.Easing), label = "LibrarySourceMorphHeight")
            val scroll = androidx.compose.foundation.rememberScrollState()
            var selectedDistance by remember { mutableStateOf(0.dp) }
            val switchingKey = (state as? LibrarySourceSwitcherState.Switching)?.targetKey
            val anchorInset = with(density) { 4.dp.roundToPx() }
            Popup(popupPositionProvider = remember(anchorInset) { SourceMorphPosition(anchorInset) }, onDismissRequest = { close() },
                properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)) {
                Box(Modifier.width(width).height(height).clip(RoundedCornerShape(20.dp))
                    .background(containerColor)) {
                    if (target != null) {
                        SourceMorphRow(target!!, Modifier.height(40.dp), true)
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = switchingKey == null && !closing)) {
                            frozen.forEachIndexed { index, item -> key(item.first) {
                                val alpha by animateFloatAsState(if (switchingKey == null) 1f else 0f,
                                    tween(160), label = "LibrarySourceFade")
                                SourceMorphRow(item, Modifier.height(40.dp).alpha(alpha)
                                    .clickable(enabled = state is LibrarySourceSwitcherState.Expanded && !closing) {
                                        if (index == 0) close() else {
                                            selectedDistance = with(density) { (index * 40.dp.toPx() - scroll.value).toDp() }
                                            state = LibrarySourceSwitcherState.Switching(item.first)
                                        }
                                    }, index == 0)
                            } }
                        }
                        if (switchingKey != null) {
                            // 单独的动画起点保留点击时的实际滚动位置。
                            val movement = remember { androidx.compose.animation.core.Animatable(selectedDistance.value) }
                            LaunchedEffect(switchingKey) { movement.animateTo(0f, tween(300, easing = ink.tenqui.flowtone.ui.components.FlowtoneMotion.Easing)) }
                            SourceMorphRow(frozen.first { it.first == switchingKey }, Modifier.height(40.dp).offset(y = movement.value.dp), true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceMorphRow(item: Pair<String, LibrarySourceItem?>, modifier: Modifier, current: Boolean) {
    Row(modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LibrarySourceIcon(item.second)
        Spacer(Modifier.width(8.dp))
        Text(item.second?.name ?: "我的音乐", Modifier.weight(1f), fontSize = 15.sp,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(if (current) Icons.Filled.KeyboardArrowDown else Icons.Filled.Check, null,
            Modifier.size(16.dp).alpha(if (current) 1f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class SourceMorphPosition(private val inset: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), anchorBounds.top + inset)
}
@Composable
private fun LibrarySourceIcon(source: LibrarySourceItem?) {
    val size = 18.dp
    val tint = MaterialTheme.colorScheme.primary
    Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
    when (source?.identity) {
        null -> Icon(Icons.Filled.LibraryMusic, null, Modifier.size(size), tint = tint)
        LibrarySourceIdentity.Local -> Icon(Icons.Filled.AudioFile, null, Modifier.size(size), tint = tint)
        is LibrarySourceIdentity.Extension -> ProviderExtensionIcon(source.visual,
            source.color?.let(::extensionManifestColor) ?: MaterialTheme.colorScheme.onSurfaceVariant, size)
    }
    }
}

internal class LibrarySourceMenuPosition(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right - popupContentSize.width
        return IntOffset(x.coerceIn(margin, (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin)),
            (anchorBounds.bottom + gap).coerceIn(margin, (windowSize.height - margin - popupContentSize.height).coerceAtLeast(margin)))
    }
}
