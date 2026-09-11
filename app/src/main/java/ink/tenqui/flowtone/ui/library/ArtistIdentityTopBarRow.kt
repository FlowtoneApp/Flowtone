package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarPathBaselineCorrection
import ink.tenqui.flowtone.ui.components.canOpenFullTitleOverlay
import kotlin.math.roundToInt

internal data class ArtistTopBarLayoutContract(
    val contentHeight: Dp,
    val avatarSize: Dp,
    val breadcrumbBaselineOffsetY: Dp
)

internal val ArtistTopBarLayout = ArtistTopBarLayoutContract(
    contentHeight = FlowtoneTopBarContentHeight,
    avatarSize = 36.dp,
    breadcrumbBaselineOffsetY = FlowtoneTopBarPathBaselineCorrection
)

private val ArtistTopBarTitleGap = 10.dp

// Breadcrumb 内容层进入与退出时使用的短距离水平位移，不改变 slot 的 layout X。
private val ArtistTopBarBreadcrumbContentMotionDistance = 8.dp

internal enum class ArtistTopBarPathLayout {
    FullPath,
    CollapsedAncestors,
    EllipsizedCurrent
}

internal fun artistTopBarPathLayout(
    fullPathNaturalWidthPx: Float,
    collapsedPathNaturalWidthPx: Float,
    availableWidthPx: Float
): ArtistTopBarPathLayout = when {
    fullPathNaturalWidthPx <= availableWidthPx.coerceAtLeast(0f) ->
        ArtistTopBarPathLayout.FullPath
    collapsedPathNaturalWidthPx <= availableWidthPx.coerceAtLeast(0f) ->
        ArtistTopBarPathLayout.CollapsedAncestors
    else -> ArtistTopBarPathLayout.EllipsizedCurrent
}

internal enum class ArtistVisualBreadcrumbRole { Ancestor, Separator, Current }

internal data class ArtistVisualBreadcrumbSegment(
    val stableKey: String,
    val text: String,
    val role: ArtistVisualBreadcrumbRole
)

internal data class ArtistVisualBreadcrumbModel(
    val leadingText: String,
    val pathSlots: List<String?>,
    val currentSlotIndex: Int?,
    val collapsedAncestors: Boolean,
    val ellipsizeCurrent: Boolean
) {
    val segments: List<ArtistVisualBreadcrumbSegment>
        get() = buildList {
            add(
                ArtistVisualBreadcrumbSegment(
                    stableKey = "ancestor",
                    text = leadingText,
                    role = ArtistVisualBreadcrumbRole.Ancestor
                )
            )
            pathSlots.forEachIndexed { index, text ->
                if (text != null) {
                    add(
                        ArtistVisualBreadcrumbSegment(
                            stableKey = "separator:$index",
                            text = "/",
                            role = ArtistVisualBreadcrumbRole.Separator
                        )
                    )
                    add(
                        ArtistVisualBreadcrumbSegment(
                            stableKey = "path:$index",
                            text = text,
                            role = if (index == currentSlotIndex) {
                                ArtistVisualBreadcrumbRole.Current
                            } else {
                                ArtistVisualBreadcrumbRole.Ancestor
                            }
                        )
                    )
                }
            }
        }
}

internal fun artistVisualBreadcrumbModel(
    artistName: String,
    pathSegments: List<String>,
    layout: ArtistTopBarPathLayout = ArtistTopBarPathLayout.FullPath
): ArtistVisualBreadcrumbModel {
    if (pathSegments.isEmpty()) {
        return ArtistVisualBreadcrumbModel(
            leadingText = artistName,
            pathSlots = emptyList(),
            currentSlotIndex = null,
            collapsedAncestors = false,
            ellipsizeCurrent = false
        )
    }

    val collapsed = layout != ArtistTopBarPathLayout.FullPath
    val currentIndex = pathSegments.lastIndex
    return ArtistVisualBreadcrumbModel(
        leadingText = if (collapsed) "…" else artistName,
        pathSlots = if (collapsed) {
            pathSegments.mapIndexed { index, title ->
                title.takeIf { index == currentIndex }
            }
        } else {
            pathSegments
        },
        currentSlotIndex = currentIndex,
        collapsedAncestors = collapsed,
        ellipsizeCurrent = layout == ArtistTopBarPathLayout.EllipsizedCurrent
    )
}

private data class ArtistTopBarTransitionRun(
    val transition: ArtistTopBarVisualTransition,
    val masterStart: Float,
    val masterEnd: Float
) {
    fun presentation(masterProgress: Float): List<ArtistTopBarPresentedElement> {
        val duration = masterEnd - masterStart
        val progress = if (duration == 0f) 1f else {
            (masterProgress - masterStart) / duration
        }
        return transition.presentation(progress)
    }
}

@Composable
internal fun ArtistIdentityTopBarRow(
    artistName: String,
    avatarImage: ExtensionImage?,
    pathSegments: List<String>,
    contentColor: Color,
    onFullTitleRequest: (String) -> Unit,
    interactionEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val separator = " / "
    val separatorWidthPx = textMeasurer.measure(separator, textStyle).size.width.toFloat()
    val artistWidthPx = textMeasurer.measure(artistName, textStyle).size.width.toFloat()
    val currentTitle = pathSegments.lastOrNull()
    var artistHasVisualOverflow by remember(artistName) { mutableStateOf(false) }
    var currentHasVisualOverflow by remember(currentTitle) { mutableStateOf(false) }
    val artistInteractionSource = remember(artistName) { MutableInteractionSource() }
    val currentInteractionSource = remember(currentTitle) { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier.height(ArtistTopBarLayout.contentHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        val avatarAndGapWidthPx = with(density) {
            ArtistTopBarLayout.avatarSize.toPx() + ArtistTopBarTitleGap.toPx()
        }
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val currentWidthPx = currentTitle?.let { title ->
            textMeasurer.measure(title, textStyle).size.width.toFloat()
        } ?: 0f
        val segmentWidthsPx = pathSegments.sumOf { segment ->
            textMeasurer.measure(segment, textStyle).size.width.toDouble()
        }.toFloat()
        val fullPathWidthPx = avatarAndGapWidthPx + artistWidthPx +
            separatorWidthPx * pathSegments.size + segmentWidthsPx
        val collapsedPathWidthPx = avatarAndGapWidthPx +
            textMeasurer.measure("…$separator", textStyle).size.width + currentWidthPx
        val pathLayout = if (pathSegments.isEmpty()) {
            ArtistTopBarPathLayout.FullPath
        } else {
            artistTopBarPathLayout(
                fullPathNaturalWidthPx = fullPathWidthPx,
                collapsedPathNaturalWidthPx = collapsedPathWidthPx,
                availableWidthPx = availableWidthPx
            )
        }
        val visualModel = artistVisualBreadcrumbModel(
            artistName = artistName,
            pathSegments = pathSegments,
            layout = pathLayout
        )
        val leadingTextWidthPx = if (visualModel.collapsedAncestors) {
            textMeasurer.measure("…", textStyle).size.width.toFloat()
        } else {
            artistWidthPx
        }
        val pathTextWidthsPx = List(visualModel.pathSlots.size) { index ->
            visualModel.pathSlots.getOrNull(index)?.let { title ->
                textMeasurer.measure(title, textStyle).size.width.toFloat()
            } ?: 0f
        }
        val targetVisualState = artistTopBarVisualState(
            visualModel = visualModel,
            totalWidthPx = availableWidthPx,
            avatarWidthPx = with(density) { ArtistTopBarLayout.avatarSize.toPx() },
            titleGapPx = with(density) { ArtistTopBarTitleGap.toPx() },
            leadingTextWidthPx = leadingTextWidthPx,
            separatorWidthPx = separatorWidthPx,
            pathTextWidthsPx = pathTextWidthsPx
        )
        val contentMotionDistancePx = with(density) {
            ArtistTopBarBreadcrumbContentMotionDistance.toPx()
        }
        var settledVisualState by remember { mutableStateOf(targetVisualState) }
        var activeTransition by remember {
            mutableStateOf<ArtistTopBarTransitionRun?>(null)
        }
        val masterProgress = remember { Animatable(0f) }

        LaunchedEffect(targetVisualState) {
            if (activeTransition == null && settledVisualState == targetVisualState) {
                return@LaunchedEffect
            }

            val currentRun = activeTransition
            val transition = if (currentRun == null) {
                artistTopBarVisualTransition(
                    oldState = settledVisualState,
                    targetState = targetVisualState,
                    contentMotionDistancePx = contentMotionDistancePx
                )
            } else {
                artistTopBarRetargetedVisualTransition(
                    startPresentation = currentRun.presentation(masterProgress.value),
                    targetState = targetVisualState,
                    contentMotionDistancePx = contentMotionDistancePx
                )
            }
            if (!transition.hasAnimation) {
                settledVisualState = targetVisualState
                activeTransition = null
                return@LaunchedEffect
            }

            val masterStart = masterProgress.value
            val run = ArtistTopBarTransitionRun(
                transition = transition,
                masterStart = masterStart,
                masterEnd = masterStart + 1f
            )
            activeTransition = run
            masterProgress.animateTo(
                targetValue = run.masterEnd,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.ShortDurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
            settledVisualState = targetVisualState
            activeTransition = null
            masterProgress.snapTo(0f)
        }

        val presentedElements = activeTransition?.presentation(masterProgress.value)
            ?: settledVisualState.asPresentation()
        ArtistTopBarVisualLayout(
            presentedElements = presentedElements,
            targetVisualState = targetVisualState,
            avatarImage = avatarImage,
            currentTitle = currentTitle,
            contentColor = contentColor,
            interactionEnabled = interactionEnabled,
            artistName = artistName,
            artistHasVisualOverflow = artistHasVisualOverflow,
            currentHasVisualOverflow = currentHasVisualOverflow,
            artistInteractionSource = artistInteractionSource,
            currentInteractionSource = currentInteractionSource,
            onArtistOverflowChange = { artistHasVisualOverflow = it },
            onCurrentOverflowChange = { currentHasVisualOverflow = it },
            onFullTitleRequest = onFullTitleRequest,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun artistTopBarVisualState(
    visualModel: ArtistVisualBreadcrumbModel,
    totalWidthPx: Float,
    avatarWidthPx: Float,
    titleGapPx: Float,
    leadingTextWidthPx: Float,
    separatorWidthPx: Float,
    pathTextWidthsPx: List<Float>
): ArtistTopBarVisualState {
    val positions = artistVisualBreadcrumbTargetPositions(
        visualModel = visualModel,
        renderedPathSlotCount = visualModel.pathSlots.size,
        totalWidthPx = totalWidthPx,
        avatarWidthPx = avatarWidthPx,
        titleGapPx = titleGapPx,
        leadingTextWidthPx = leadingTextWidthPx,
        separatorWidthPx = separatorWidthPx,
        pathTextWidthsPx = pathTextWidthsPx
    )
    return ArtistTopBarVisualState(
        elements = buildList {
            val leadingX = positions.getValue("ancestor")
            add(
                ArtistTopBarVisualElement(
                    stableKey = "ancestor",
                    text = visualModel.leadingText,
                    role = ArtistVisualBreadcrumbRole.Ancestor,
                    x = leadingX,
                    width = if (visualModel.currentSlotIndex == null) {
                        (totalWidthPx - leadingX).coerceAtLeast(0f)
                    } else {
                        leadingTextWidthPx
                    },
                    ellipsize = visualModel.currentSlotIndex == null
                )
            )
            visualModel.pathSlots.forEachIndexed { index, title ->
                if (title == null) return@forEachIndexed
                add(
                    ArtistTopBarVisualElement(
                        stableKey = "separator:$index",
                        text = " / ",
                        role = ArtistVisualBreadcrumbRole.Separator,
                        x = positions.getValue("separator:$index"),
                        width = separatorWidthPx
                    )
                )
                val titleX = positions.getValue("path:$index")
                val isCurrent = index == visualModel.currentSlotIndex
                add(
                    ArtistTopBarVisualElement(
                        stableKey = "path:$index",
                        text = title,
                        role = if (isCurrent) {
                            ArtistVisualBreadcrumbRole.Current
                        } else {
                            ArtistVisualBreadcrumbRole.Ancestor
                        },
                        x = titleX,
                        width = if (isCurrent) {
                            (totalWidthPx - titleX).coerceAtLeast(0f)
                        } else {
                            pathTextWidthsPx.getOrElse(index) { 0f }
                        },
                        ellipsize = isCurrent && visualModel.ellipsizeCurrent
                    )
                )
            }
        }
    )
}

internal fun artistVisualBreadcrumbTargetPositions(
    visualModel: ArtistVisualBreadcrumbModel,
    renderedPathSlotCount: Int,
    totalWidthPx: Float,
    avatarWidthPx: Float,
    titleGapPx: Float,
    leadingTextWidthPx: Float,
    separatorWidthPx: Float,
    pathTextWidthsPx: List<Float>
): Map<String, Float> = buildMap {
    put("ancestor", avatarWidthPx + titleGapPx)
    var x = if (visualModel.currentSlotIndex == null) {
        totalWidthPx
    } else {
        avatarWidthPx + titleGapPx + leadingTextWidthPx
    }
    repeat(renderedPathSlotCount) { index ->
        val title = visualModel.pathSlots.getOrNull(index)
        put("separator:$index", x)
        put("path:$index", x + if (title != null) separatorWidthPx else 0f)
        if (title != null) {
            x = if (index == visualModel.currentSlotIndex) {
                totalWidthPx
            } else {
                x + separatorWidthPx + pathTextWidthsPx.getOrElse(index) { 0f }
            }
        }
    }
}

@Composable
private fun ArtistTopBarVisualLayout(
    presentedElements: List<ArtistTopBarPresentedElement>,
    targetVisualState: ArtistTopBarVisualState,
    avatarImage: ExtensionImage?,
    currentTitle: String?,
    artistName: String,
    contentColor: Color,
    interactionEnabled: Boolean,
    artistHasVisualOverflow: Boolean,
    currentHasVisualOverflow: Boolean,
    artistInteractionSource: MutableInteractionSource,
    currentInteractionSource: MutableInteractionSource,
    onArtistOverflowChange: (Boolean) -> Unit,
    onCurrentOverflowChange: (Boolean) -> Unit,
    onFullTitleRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val baselineOffsetPx = with(density) {
        ArtistTopBarLayout.breadcrumbBaselineOffsetY.roundToPx()
    }
    val avatarSizePx = with(density) { ArtistTopBarLayout.avatarSize.roundToPx() }
    val targetLayerKeys = targetVisualState.elements
        .map { it.stableKey to it.text }
        .toSet()
    Layout(
        content = {
            Box(modifier = Modifier.layoutId(ArtistTopBarLayoutId.Avatar)) {
                ArtistAvatar(
                    size = ArtistTopBarLayout.avatarSize,
                    image = avatarImage,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            presentedElements.forEachIndexed { index, presented ->
                val isTargetElement =
                    (presented.element.stableKey to presented.element.text) in targetLayerKeys
                Box(
                    modifier = Modifier
                        .layoutId(ArtistTopBarLayoutId.Breadcrumb(index))
                        .graphicsLayer {
                            alpha = presented.alpha
                            translationX = presented.contentTranslationX
                        }
                ) {
                    ArtistTopBarVisualText(
                        element = presented.element,
                        isTargetElement = isTargetElement,
                        currentTitle = currentTitle,
                        artistName = artistName,
                        contentColor = contentColor,
                        interactionEnabled = interactionEnabled,
                        artistHasVisualOverflow = artistHasVisualOverflow,
                        currentHasVisualOverflow = currentHasVisualOverflow,
                        artistInteractionSource = artistInteractionSource,
                        currentInteractionSource = currentInteractionSource,
                        onArtistOverflowChange = onArtistOverflowChange,
                        onCurrentOverflowChange = onCurrentOverflowChange,
                        onFullTitleRequest = onFullTitleRequest
                    )
                }
            }
        },
        modifier = modifier.height(ArtistTopBarLayout.contentHeight)
    ) { measurables, constraints ->
        val avatarPlaceable = measurables
            .first { it.layoutId == ArtistTopBarLayoutId.Avatar }
            .measure(
                constraints.copy(
                    minWidth = avatarSizePx,
                    maxWidth = avatarSizePx,
                    minHeight = avatarSizePx,
                    maxHeight = avatarSizePx
                )
            )
        val breadcrumbPlaceables = presentedElements.mapIndexed { index, presented ->
            val elementWidth = presented.element.width.roundToInt()
                .coerceIn(0, constraints.maxWidth)
            measurables
                .first { it.layoutId == ArtistTopBarLayoutId.Breadcrumb(index) }
                .measure(
                    constraints.copy(
                        minWidth = elementWidth,
                        maxWidth = elementWidth,
                        minHeight = 0
                    )
                )
        }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            avatarPlaceable.placeRelative(
                x = 0,
                y = (height - avatarPlaceable.height) / 2
            )
            breadcrumbPlaceables.forEachIndexed { index, placeable ->
                placeable.placeRelative(
                    x = presentedElements[index].x.roundToInt(),
                    y = (height - placeable.height) / 2 + baselineOffsetPx
                )
            }
        }
    }
}

private sealed interface ArtistTopBarLayoutId {
    data object Avatar : ArtistTopBarLayoutId
    data class Breadcrumb(val index: Int) : ArtistTopBarLayoutId
}

@Composable
private fun ArtistTopBarVisualText(
    element: ArtistTopBarVisualElement,
    isTargetElement: Boolean,
    currentTitle: String?,
    artistName: String,
    contentColor: Color,
    interactionEnabled: Boolean,
    artistHasVisualOverflow: Boolean,
    currentHasVisualOverflow: Boolean,
    artistInteractionSource: MutableInteractionSource,
    currentInteractionSource: MutableInteractionSource,
    onArtistOverflowChange: (Boolean) -> Unit,
    onCurrentOverflowChange: (Boolean) -> Unit,
    onFullTitleRequest: (String) -> Unit
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    val isArtist = element.stableKey == "ancestor" && currentTitle == null
    val isCurrent = element.role == ArtistVisualBreadcrumbRole.Current
    val interactionSource = if (isArtist) {
        artistInteractionSource
    } else {
        currentInteractionSource
    }
    val canOpenOverlay = when {
        !isTargetElement -> false
        isArtist -> canOpenFullTitleOverlay(artistHasVisualOverflow)
        isCurrent && element.text == currentTitle ->
            canOpenFullTitleOverlay(currentHasVisualOverflow)
        else -> false
    }
    Text(
        text = element.text,
        style = textStyle,
        color = if (
            element.role == ArtistVisualBreadcrumbRole.Separator || element.text == "…"
        ) {
            contentColor.copy(alpha = 0.72f)
        } else {
            contentColor
        },
        maxLines = 1,
        overflow = if (element.ellipsize) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = { result ->
            if (isTargetElement && isArtist) {
                onArtistOverflowChange(result.hasVisualOverflow)
            } else if (isTargetElement && isCurrent) {
                onCurrentOverflowChange(result.hasVisualOverflow)
            }
        },
        modifier = Modifier
            .then(if (isCurrent) Modifier.fillMaxWidth() else Modifier)
            .clickable(
                enabled = interactionEnabled && canOpenOverlay,
                interactionSource = interactionSource,
                indication = null
            ) {
                onFullTitleRequest(if (isArtist) artistName else element.text)
            }
    )
}
