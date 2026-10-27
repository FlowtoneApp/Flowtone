package ink.tenqui.flowtone.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ink.tenqui.flowtone.data.online.LibraryProviderOption
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.search.SearchProviderVisual
import ink.tenqui.flowtone.viewmodel.ProviderCatalogLoadState
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.ProviderExtensionIcon
import ink.tenqui.flowtone.ui.components.extensionManifestColor
import ink.tenqui.flowtone.ui.theme.LocalMainPagesCloudPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal class LibrarySourceSwitcherOverlayHostState {
    val progress = Animatable(0f)
    var overlayContent by mutableStateOf<(@Composable (Offset) -> Unit)?>(null)
        private set

    fun updateOverlayContent(content: (@Composable (Offset) -> Unit)?) {
        if (overlayContent !== content) {
            overlayContent = content
        }
    }
}

internal val LocalLibrarySourceSwitcherOverlayHost =
    staticCompositionLocalOf<LibrarySourceSwitcherOverlayHostState?> { null }

@Composable
internal fun LibrarySourceSwitcherOverlayHost(
    state: LibrarySourceSwitcherOverlayHostState,
    modifier: Modifier = Modifier
) {
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayOriginInRoot = coordinates.positionInRoot()
            }
    ) {
        state.overlayContent?.invoke(overlayOriginInRoot)
    }
}

internal sealed interface LibrarySourceIdentity {
    val key: String

    data object Local : LibrarySourceIdentity {
        override val key: String = "local"
    }

    data class Extension(val extensionId: String) : LibrarySourceIdentity {
        override val key: String = "extension:$extensionId"
    }
}

internal data class LibrarySourceItem(
    val identity: LibrarySourceIdentity,
    val name: String,
    val typeLabel: String,
    val color: String? = null,
    val visual: SearchProviderVisual? = null,
    val supportsSongs: Boolean = false,
    val supportsAlbums: Boolean = false,
    val supportsPlaylists: Boolean = false
)

internal sealed interface LibrarySourceSwitcherState {
    data object Collapsed : LibrarySourceSwitcherState
    data object Expanded : LibrarySourceSwitcherState
    data class Switching(val targetKey: String) : LibrarySourceSwitcherState
}

internal enum class LibrarySourceCardRenderOwner {
    Static,
    Overlay
}

internal data class LibrarySourceContentState(
    val sourceItems: List<LibrarySourceItem>,
    val selectedSource: LibrarySourceItem,
    val switcherState: LibrarySourceSwitcherState,
    val swipeEnabled: Boolean,
    val swipeDirection: Int,
    val providerPlaylists: List<ProviderPlaylist>?,
    val providerPlaylistLoadState: ProviderCatalogLoadState?
)

internal fun librarySourceItems(
    providers: List<LibraryProviderOption>
): List<LibrarySourceItem> {
    val uniqueProviders = providers
        .asSequence()
        .filter { it.extensionId.isNotBlank() }
        .distinctBy(LibraryProviderOption::extensionId)
        .sortedWith(
            compareBy<LibraryProviderOption, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy(LibraryProviderOption::extensionId)
        )
        .map { provider ->
            LibrarySourceItem(
                identity = LibrarySourceIdentity.Extension(provider.extensionId),
                name = provider.name,
                typeLabel = provider.sourceTypeLabel ?: "扩展来源",
                color = provider.color,
                visual = provider.visual,
                supportsSongs = provider.supportsSongs,
                supportsAlbums = provider.supportsAlbums,
                supportsPlaylists = provider.supportsPlaylists
            )
        }
        .toList()
    return listOf(
        LibrarySourceItem(
            identity = LibrarySourceIdentity.Local,
            name = "本地音乐",
            typeLabel = "此设备",
            supportsSongs = true
        )
    ) + uniqueProviders
}

internal fun librarySourceIndexAfterSwipe(
    selectedIndex: Int,
    sourceCount: Int,
    dragDistanceY: Float,
    thresholdPx: Float
): Int? {
    if (sourceCount <= 1 || abs(dragDistanceY) <= thresholdPx) return null
    val targetIndex = selectedIndex + if (dragDistanceY < 0f) 1 else -1
    return targetIndex.takeIf { it in 0 until sourceCount }
}

internal fun librarySourceSwitcherStateAfterSelection(
    state: LibrarySourceSwitcherState,
    targetKey: String,
    selectedKey: String
): LibrarySourceSwitcherState = when {
    state !is LibrarySourceSwitcherState.Expanded -> state
    targetKey == selectedKey -> LibrarySourceSwitcherState.Collapsed
    else -> LibrarySourceSwitcherState.Switching(targetKey)
}

internal data class LibrarySourceSwitcherGeometry(
    val collapsedBounds: Rect,
    val collapsedContentBounds: Rect,
    val expandedBounds: Rect,
    val rowHeightPx: Float,
    val rowSpacingPx: Float,
    val contentTopPaddingPx: Float,
    val contentBottomPaddingPx: Float,
    val contentHeightPx: Float,
    val initialScrollPx: Float,
    val selectedIndex: Int
) {
    val rowStridePx: Float get() = rowHeightPx + rowSpacingPx

    fun rowBounds(index: Int, scrollOffsetPx: Float = initialScrollPx): Rect {
        val top = expandedBounds.top + contentTopPaddingPx + index * rowStridePx - scrollOffsetPx
        return Rect(
            left = expandedBounds.left,
            top = top,
            right = expandedBounds.right,
            bottom = top + rowHeightPx
        )
    }
}

internal fun calculateLibrarySourceSwitcherGeometry(
    sourceCount: Int,
    selectedIndex: Int,
    collapsedBounds: Rect,
    collapsedContentBounds: Rect,
    viewportBounds: Rect,
    rowHeightPx: Float,
    rowSpacingPx: Float = 0f,
    edgeMarginPx: Float = 0f
): LibrarySourceSwitcherGeometry? {
    if (sourceCount <= 0 || rowHeightPx <= 0f) return null
    val safeSelectedIndex = selectedIndex.coerceIn(0, sourceCount - 1)
    val stride = rowHeightPx + rowSpacingPx
    val contentHeight = sourceCount * rowHeightPx + (sourceCount - 1) * rowSpacingPx
    val safeMargin = edgeMarginPx.coerceAtLeast(0f)
    val availableHeight = (viewportBounds.height - 2f * safeMargin).coerceAtLeast(0f)
    val expandedHeight = contentHeight.coerceAtMost(availableHeight)
    val minTop = viewportBounds.top + safeMargin
    val maxTop = (viewportBounds.bottom - safeMargin - expandedHeight).coerceAtLeast(minTop)
    val anchorCenteredTop = collapsedBounds.center.y - expandedHeight / 2f
    val expandedTop = anchorCenteredTop.coerceIn(minTop, maxTop)
    val expandedBounds = Rect(
        left = collapsedBounds.left,
        top = expandedTop,
        right = collapsedBounds.right,
        bottom = expandedTop + expandedHeight
    )
    val maxScroll = (contentHeight - expandedHeight).coerceAtLeast(0f)
    val selectedCenterInContent = safeSelectedIndex * stride + rowHeightPx / 2f
    val initialScroll =
        (selectedCenterInContent - expandedHeight / 2f).coerceIn(0f, maxScroll)

    return LibrarySourceSwitcherGeometry(
        collapsedBounds = collapsedBounds,
        collapsedContentBounds = collapsedContentBounds,
        expandedBounds = expandedBounds,
        rowHeightPx = rowHeightPx,
        rowSpacingPx = rowSpacingPx,
        contentTopPaddingPx = 0f,
        contentBottomPaddingPx = 0f,
        contentHeightPx = contentHeight,
        initialScrollPx = initialScroll,
        selectedIndex = safeSelectedIndex
    )
}

internal fun librarySourceSwitcherRectAtProgress(
    start: Rect,
    end: Rect,
    progress: Float
): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    fun lerp(startValue: Float, endValue: Float) = startValue + (endValue - startValue) * fraction
    return Rect(
        left = lerp(start.left, end.left),
        top = lerp(start.top, end.top),
        right = lerp(start.right, end.right),
        bottom = lerp(start.bottom, end.bottom)
    )
}

internal fun librarySourceRowStartBounds(
    collapsedBounds: Rect,
    sourceIndex: Int,
    selectedIndex: Int,
    rowHeightPx: Float,
    rowStridePx: Float
): Rect {
    val top = when {
        sourceIndex < selectedIndex -> collapsedBounds.top -
            (selectedIndex - sourceIndex) * rowStridePx
        sourceIndex > selectedIndex -> collapsedBounds.bottom +
            (sourceIndex - selectedIndex - 1) * rowStridePx
        else -> collapsedBounds.top
    }
    return Rect(
        left = collapsedBounds.left,
        top = top,
        right = collapsedBounds.right,
        bottom = top + rowHeightPx
    )
}

@Composable
internal fun LibraryProviderHeader(
    source: LibrarySourceItem,
    sourceIndex: Int,
    sourceCount: Int,
    canExpand: Boolean,
    swipeEnabled: Boolean,
    expanded: Boolean,
    swipeDirection: Int,
    expandProgress: Float,
    contentVisible: Boolean,
    sourceIconPainters: Map<String, Painter>,
    onSourceIconPainterLoaded: (String, Painter) -> Unit,
    onClick: () -> Unit,
    onSwipeToIndex: (Int) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    onContentBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { LibrarySourceSwipeThreshold.toPx() }
    val shape = RoundedCornerShape(LibrarySourceCornerRadius)
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(LibrarySourceCardHeight)
            .onGloballyPositioned { coordinates -> onBoundsChanged(coordinates.boundsInRoot()) }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .pointerInput(source.identity, sourceCount, sourceIndex, swipeEnabled, swipeThresholdPx) {
                if (swipeEnabled && sourceCount > 1) {
                    var dragDistanceY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragDistanceY = 0f },
                        onVerticalDrag = { _, dragAmount -> dragDistanceY += dragAmount },
                        onDragEnd = {
                            librarySourceIndexAfterSwipe(
                                selectedIndex = sourceIndex,
                                sourceCount = sourceCount,
                                dragDistanceY = dragDistanceY,
                                thresholdPx = swipeThresholdPx
                            )?.let(onSwipeToIndex)
                        },
                        onDragCancel = { dragDistanceY = 0f }
                    )
                }
            }
            .semantics {
                contentDescription = "曲库来源：${source.name}，${source.typeLabel}，点击展开或纵向滑动切换"
            },
        shape = shape,
        color = librarySourceCardContainerColor(),
        tonalElevation = 1.dp
    ) {
        AnimatedContent(
            targetState = source,
            transitionSpec = {
                if (swipeDirection == 0) {
                    EnterTransition.None togetherWith ExitTransition.None using null
                } else {
                    slideInVertically(
                        animationSpec = tween(
                            FlowtoneMotion.DurationMillis,
                            easing = FlowtoneMotion.Easing
                        )
                    ) { height -> -swipeDirection * height } +
                        fadeIn(tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing)) togetherWith
                        slideOutVertically(
                            animationSpec = tween(
                                FlowtoneMotion.DurationMillis,
                                easing = FlowtoneMotion.Easing
                            )
                        ) { height -> swipeDirection * height } +
                        fadeOut(tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing)) using null
                }
            },
            contentKey = { it.identity.key },
            label = "LibrarySourceHeaderContent"
        ) { displayedSource ->
            LibrarySourceRowContent(
                source = displayedSource,
                expanded = expanded,
                showExpandHint = canExpand,
                expandProgress = expandProgress,
                contentVisible = contentVisible,
                sourceIconPainters = sourceIconPainters,
                onSourceIconPainterLoaded = onSourceIconPainterLoaded,
                onBoundsChanged = onContentBoundsChanged
            )
        }
    }
}

@Composable
internal fun LibrarySourceSwitcherOverlay(
    sourceItems: List<LibrarySourceItem>,
    selectedIdentity: LibrarySourceIdentity,
    state: LibrarySourceSwitcherState,
    anchorBoundsInRoot: Rect?,
    anchorContentBoundsInRoot: Rect?,
    pageViewportBoundsInRoot: Rect?,
    overlayOriginInRoot: Offset?,
    pageActive: Boolean,
    sourceCardRenderOwner: LibrarySourceCardRenderOwner,
    onSourceCardRenderOwnerChange: (LibrarySourceCardRenderOwner) -> Unit,
    sourceIconPainters: Map<String, Painter>,
    onSourceIconPainterLoaded: (String, Painter) -> Unit,
    progress: Animatable<Float, AnimationVector1D>,
    onStateChange: (LibrarySourceSwitcherState) -> Unit,
    onPrepareSourceSwitch: suspend () -> Unit,
    onSourceSelected: (LibrarySourceItem) -> Unit
) {
    val latestSourceItems by rememberUpdatedState(sourceItems)
    val latestSelectedIdentity by rememberUpdatedState(selectedIdentity)
    var layoutSnapshot by remember { mutableStateOf<LibrarySourceSwitcherSnapshot?>(null) }
    var movingIdentity by remember { mutableStateOf<LibrarySourceIdentity?>(null) }

    BackHandler(
        enabled = pageActive &&
            (state != LibrarySourceSwitcherState.Collapsed || layoutSnapshot != null || progress.value > 0f)
    ) {
        if (state !is LibrarySourceSwitcherState.Switching) {
            onStateChange(LibrarySourceSwitcherState.Collapsed)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val edgeMarginPx = with(density) { 8.dp.toPx() }
        val rowHeightPx = with(density) { LibrarySourceRowHeight.toPx() }
        val localAnchorBounds = if (anchorBoundsInRoot != null && overlayOriginInRoot != null) {
            librarySourceRectInLocalCoordinates(anchorBoundsInRoot, overlayOriginInRoot)
        } else {
            null
        }
        val localContentBounds = if (anchorContentBoundsInRoot != null && overlayOriginInRoot != null) {
            librarySourceRectInLocalCoordinates(anchorContentBoundsInRoot, overlayOriginInRoot)
        } else {
            localAnchorBounds
        }
        val localViewportBounds = if (pageViewportBoundsInRoot != null && overlayOriginInRoot != null) {
            librarySourceRectInLocalCoordinates(pageViewportBoundsInRoot, overlayOriginInRoot)
        } else {
            null
        }
        val openingCandidate = if (
            state != LibrarySourceSwitcherState.Collapsed &&
            layoutSnapshot == null &&
            localAnchorBounds != null &&
            localContentBounds != null &&
            localViewportBounds != null
        ) {
            val frozenItems = sourceItems.toList()
            val selectedIndex = frozenItems.indexOfFirst {
                it.identity == selectedIdentity
            }.takeIf { it >= 0 } ?: 0
            calculateLibrarySourceSwitcherGeometry(
                sourceCount = frozenItems.size,
                selectedIndex = selectedIndex,
                collapsedBounds = localAnchorBounds,
                collapsedContentBounds = localContentBounds,
                viewportBounds = localViewportBounds,
                rowHeightPx = rowHeightPx,
                rowSpacingPx = 0f,
                edgeMarginPx = edgeMarginPx
            )?.let { geometry ->
                LibrarySourceSwitcherSnapshot(
                    sourceItems = frozenItems,
                    geometry = geometry,
                    initialSelectedIdentity = frozenItems[selectedIndex].identity
                )
            }
        } else {
            null
        }
        val snapshotForRender = layoutSnapshot ?: openingCandidate
        val transitionProgress = progress.value
        val overlayActive = pageActive && (
            sourceCardRenderOwner == LibrarySourceCardRenderOwner.Overlay ||
                state != LibrarySourceSwitcherState.Collapsed ||
                snapshotForRender != null ||
                transitionProgress > 0.001f
            )

        fun handoffToStatic(collapseSwitcher: Boolean = false) {
            Snapshot.withMutableSnapshot {
                if (collapseSwitcher) {
                    onStateChange(LibrarySourceSwitcherState.Collapsed)
                }
                onSourceCardRenderOwnerChange(LibrarySourceCardRenderOwner.Static)
                layoutSnapshot = null
                movingIdentity = null
            }
        }

        LaunchedEffect(pageActive, state) {
            if (!pageActive) {
                progress.snapTo(0f)
                handoffToStatic(collapseSwitcher = state != LibrarySourceSwitcherState.Collapsed)
                return@LaunchedEffect
            }

            when (state) {
                LibrarySourceSwitcherState.Collapsed -> {
                    if (layoutSnapshot != null || progress.value > 0.001f ||
                        sourceCardRenderOwner == LibrarySourceCardRenderOwner.Overlay
                    ) {
                        progress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                FlowtoneMotion.DurationMillis,
                                easing = FlowtoneMotion.Easing
                            )
                        )
                        val settledItem = latestSourceItems.firstOrNull {
                            it.identity == latestSelectedIdentity
                        } ?: latestSourceItems.firstOrNull {
                            it.identity == LibrarySourceIdentity.Local
                        }
                        Snapshot.withMutableSnapshot {
                            if (settledItem != null &&
                                settledItem.identity != latestSelectedIdentity
                            ) {
                                onSourceSelected(settledItem)
                            }
                            onSourceCardRenderOwnerChange(LibrarySourceCardRenderOwner.Static)
                            layoutSnapshot = null
                            movingIdentity = null
                        }
                    }
                }

                LibrarySourceSwitcherState.Expanded -> {
                    val snapshot = layoutSnapshot ?: openingCandidate
                    if (snapshot == null) {
                        handoffToStatic(collapseSwitcher = true)
                        return@LaunchedEffect
                    }
                    Snapshot.withMutableSnapshot {
                        if (layoutSnapshot == null) layoutSnapshot = snapshot
                        movingIdentity = snapshot.initialSelectedIdentity
                        onSourceCardRenderOwnerChange(LibrarySourceCardRenderOwner.Overlay)
                    }
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            FlowtoneMotion.DurationMillis,
                            easing = FlowtoneMotion.Easing
                        )
                    )
                }

                is LibrarySourceSwitcherState.Switching -> {
                    val snapshot = layoutSnapshot ?: openingCandidate
                    val target = snapshot?.sourceItems?.firstOrNull {
                        it.identity.key == state.targetKey
                    }
                    val currentTarget = target?.let { targetItem ->
                        latestSourceItems.firstOrNull { it.identity == targetItem.identity }
                    }

                    if (snapshot == null || target == null || currentTarget == null) {
                        val fallback = latestSourceItems.firstOrNull {
                            it.identity == latestSelectedIdentity
                        } ?: latestSourceItems.firstOrNull {
                            it.identity == LibrarySourceIdentity.Local
                        }
                        if (fallback != null) onSourceSelected(fallback)
                        if (progress.value > 0.001f) {
                            progress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    FlowtoneMotion.DurationMillis,
                                    easing = FlowtoneMotion.Easing
                                )
                            )
                        }
                        handoffToStatic(collapseSwitcher = true)
                        return@LaunchedEffect
                    }

                    if (layoutSnapshot == null) layoutSnapshot = snapshot
                    movingIdentity = target.identity
                    coroutineScope {
                        val returnToTop = launch { onPrepareSourceSwitch() }
                        onSourceSelected(currentTarget)
                        progress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                FlowtoneMotion.DurationMillis,
                                easing = FlowtoneMotion.Easing
                            )
                        )
                        returnToTop.join()
                    }
                    handoffToStatic(collapseSwitcher = true)
                }
            }
        }

        if (overlayActive) {
            val scrimInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .background(Color.Black.copy(alpha = transitionProgress * 0.14f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        enabled = state !is LibrarySourceSwitcherState.Switching
                    ) {
                        onStateChange(LibrarySourceSwitcherState.Collapsed)
                    }
            )
        }

        if (sourceCardRenderOwner == LibrarySourceCardRenderOwner.Overlay &&
            snapshotForRender != null &&
            localAnchorBounds != null
        ) {
            val snapshot = snapshotForRender
            val geometry = if (state is LibrarySourceSwitcherState.Switching) {
                snapshot.geometry.copy(
                    collapsedBounds = localAnchorBounds,
                    collapsedContentBounds = localContentBounds ?: localAnchorBounds
                )
            } else snapshot.geometry
            val items = snapshot.sourceItems
            val scrollState = rememberScrollState(
                initial = geometry.initialScrollPx.roundToInt()
            )
            val currentIdentity = movingIdentity ?: snapshot.initialSelectedIdentity
            val currentItem = items.firstOrNull { it.identity == currentIdentity }
                ?: items.firstOrNull { it.identity == snapshot.initialSelectedIdentity }
            val movingIndex = items.indexOfFirst { it.identity == currentIdentity }
                .takeIf { it >= 0 } ?: snapshot.geometry.selectedIndex
            val scrollOffset by remember(scrollState) {
                derivedStateOf { scrollState.value.toFloat() }
            }
            val containerBounds = librarySourceSwitcherRectAtProgress(
                start = geometry.collapsedBounds,
                end = geometry.expandedBounds,
                progress = transitionProgress
            )
            val movingTargetBounds = geometry.rowBounds(
                index = movingIndex,
                scrollOffsetPx = scrollOffset
            )
            val movingBounds = librarySourceSwitcherRectAtProgress(
                start = geometry.collapsedContentBounds,
                end = movingTargetBounds,
                progress = transitionProgress
            )
            val shape = RoundedCornerShape(LibrarySourceCornerRadius)
            val width = with(density) { containerBounds.width.toDp() }
            val height = with(density) { containerBounds.height.toDp() }
            val contentHeight = with(density) { geometry.contentHeightPx.toDp() }
            val rowHeight = with(density) { geometry.rowHeightPx.toDp() }

            Surface(
                modifier = Modifier
                    .offset(
                        x = with(density) { containerBounds.left.toDp() },
                        y = with(density) { containerBounds.top.toDp() }
                    )
                    .width(width)
                    .height(height)
                    .zIndex(1f)
                    .shadow(elevation = 8.dp * transitionProgress, shape = shape),
                shape = shape,
                color = librarySourceCardContainerColor(),
                tonalElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                state = scrollState,
                                enabled = state is LibrarySourceSwitcherState.Expanded &&
                                    transitionProgress >= 0.999f
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(contentHeight)
                        ) {
                            items.forEachIndexed { index, item ->
                                if (item.identity != currentIdentity) {
                                    val targetBounds = geometry.rowBounds(
                                        index = index,
                                        scrollOffsetPx = scrollOffset
                                    )
                                    val startBounds = librarySourceRowStartBounds(
                                        collapsedBounds = geometry.collapsedBounds,
                                        sourceIndex = index,
                                        selectedIndex = movingIndex,
                                        rowHeightPx = geometry.rowHeightPx,
                                        rowStridePx = geometry.rowStridePx
                                    )
                                    val rowBounds = librarySourceSwitcherRectAtProgress(
                                        start = startBounds,
                                        end = targetBounds,
                                        progress = transitionProgress
                                    )
                                    val rowTopInContent = rowBounds.top -
                                        containerBounds.top +
                                        scrollOffset
                                    val targetIsClickable =
                                        state is LibrarySourceSwitcherState.Expanded &&
                                            transitionProgress >= 0.999f
                                    Box(
                                        modifier = Modifier
                                            .offset(y = with(density) { rowTopInContent.toDp() })
                                            .fillMaxWidth()
                                            .height(rowHeight)
                                            .alpha(transitionProgress)
                                            .clickable(
                                                enabled = targetIsClickable,
                                                interactionSource = remember {
                                                    MutableInteractionSource()
                                                },
                                                indication = null
                                            ) {
                                                if (item.identity == selectedIdentity) {
                                                    onStateChange(LibrarySourceSwitcherState.Collapsed)
                                                } else if (state is LibrarySourceSwitcherState.Expanded &&
                                                    latestSourceItems.any {
                                                        it.identity == item.identity
                                                    }
                                                ) {
                                                    onStateChange(
                                                        librarySourceSwitcherStateAfterSelection(
                                                            state = state,
                                                            targetKey = item.identity.key,
                                                            selectedKey = selectedIdentity.key
                                                        )
                                                    )
                                                }
                                            }
                                    ) {
                                        LibrarySourceRowContent(
                                            source = item,
                                            expanded = true,
                                            showExpandHint = false,
                                            current = item.identity == selectedIdentity,
                                            sourceIconPainters = sourceIconPainters,
                                            onSourceIconPainterLoaded = onSourceIconPainterLoaded
                                        )
                                    }
                                }
                            }

                            if (currentItem != null) {
                                val movingTopInContent = movingBounds.top -
                                    containerBounds.top +
                                    scrollOffset
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            y = with(density) {
                                                movingTopInContent.toDp()
                                            }
                                        )
                                        .fillMaxWidth()
                                        .height(with(density) { movingBounds.height.toDp() })
                                        .zIndex(1f)
                                ) {
                                    LibrarySourceRowContent(
                                        source = currentItem,
                                        expanded = transitionProgress >= 0.5f,
                                        showExpandHint = true,
                                        current = true,
                                        expandProgress = transitionProgress,
                                        sourceIconPainters = sourceIconPainters,
                                        onSourceIconPainterLoaded = onSourceIconPainterLoaded
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

private data class LibrarySourceSwitcherSnapshot(
    val sourceItems: List<LibrarySourceItem>,
    val geometry: LibrarySourceSwitcherGeometry,
    val initialSelectedIdentity: LibrarySourceIdentity
)

internal fun librarySourceRectInLocalCoordinates(boundsInRoot: Rect, rootOriginInRoot: Offset): Rect =
    Rect(
        left = boundsInRoot.left - rootOriginInRoot.x,
        top = boundsInRoot.top - rootOriginInRoot.y,
        right = boundsInRoot.right - rootOriginInRoot.x,
        bottom = boundsInRoot.bottom - rootOriginInRoot.y
    )

@Composable
private fun LibrarySourceRowContent(
    source: LibrarySourceItem,
    expanded: Boolean,
    showExpandHint: Boolean,
    modifier: Modifier = Modifier,
    current: Boolean = true,
    contentVisible: Boolean = true,
    expandProgress: Float? = null,
    sourceIconPainters: Map<String, Painter>,
    onSourceIconPainterLoaded: (String, Painter) -> Unit,
    onBoundsChanged: ((Rect) -> Unit)? = null
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val fallbackIconColor = source.color?.let(::extensionManifestColor)
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val animatedArrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing),
        label = "LibrarySourceArrowRotation"
    )
    val arrowRotation = expandProgress?.coerceIn(0f, 1f)?.times(180f) ?: animatedArrowRotation
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(LibrarySourceRowHeight)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged?.invoke(coordinates.boundsInRoot())
            }
            .alpha(if (contentVisible) 1f else 0f)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when (source.identity) {
                LibrarySourceIdentity.Local -> Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                is LibrarySourceIdentity.Extension -> ProviderExtensionIcon(
                    visual = source.visual,
                    fallbackColor = fallbackIconColor,
                    iconSize = 26.dp,
                    cachedPainter = sourceIconPainters[source.identity.key],
                    onPainterLoaded = { painter ->
                        onSourceIconPainterLoaded(source.identity.key, painter)
                    }
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.typeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showExpandHint) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起来源" else "展开来源",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
        } else {
            Spacer(Modifier.width(24.dp))
        }
    }
}

@Composable
private fun librarySourceCardContainerColor(): Color = lerp(
    MaterialTheme.colorScheme.background,
    LocalMainPagesCloudPalette.current.libraryAccent,
    LibrarySourceCardAccentBlend
)

private const val LibrarySourceCardAccentBlend = 0.08f

private val LibrarySourceCardHeight: Dp = 76.dp
private val LibrarySourceRowHeight: Dp = 76.dp
private val LibrarySourceCornerRadius: Dp = 20.dp
private val LibrarySourceSwipeThreshold: Dp = 48.dp
