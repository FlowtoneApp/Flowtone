package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

internal enum class ArtistVisualBreadcrumbChange { Unchanged, Changed, Inserted, Removed }

internal fun artistVisualBreadcrumbDiff(
    previous: ArtistVisualBreadcrumbModel,
    current: ArtistVisualBreadcrumbModel
): Map<String, ArtistVisualBreadcrumbChange> {
    val previousText = previous.segments.associate { it.stableKey to it.text }
    val currentText = current.segments.associate { it.stableKey to it.text }
    return (previousText.keys + currentText.keys).associateWith { key ->
        when {
            key !in previousText -> ArtistVisualBreadcrumbChange.Inserted
            key !in currentText -> ArtistVisualBreadcrumbChange.Removed
            previousText.getValue(key) == currentText.getValue(key) ->
                ArtistVisualBreadcrumbChange.Unchanged
            else -> ArtistVisualBreadcrumbChange.Changed
        }
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
    var maximumPathSlotCount by remember { mutableIntStateOf(pathSegments.size) }
    val renderedPathSlotCount = maxOf(maximumPathSlotCount, pathSegments.size)
    SideEffect {
        if (pathSegments.size > maximumPathSlotCount) {
            maximumPathSlotCount = pathSegments.size
        }
    }

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
        val pathMotionDistancePx = with(density) { 8.dp.toPx() }.roundToInt()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ArtistAvatar(
                size = ArtistTopBarLayout.avatarSize,
                image = avatarImage,
                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                iconColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            AnimatedContent(
                targetState = visualModel.leadingText,
                transitionSpec = {
                    (fadeIn(
                        tween(FlowtoneMotion.ShortDurationMillis, easing = FlowtoneMotion.Easing)
                    ) + slideInHorizontally { pathMotionDistancePx }) togetherWith
                        (fadeOut(
                            tween(
                                FlowtoneMotion.ShortDurationMillis,
                                easing = FlowtoneMotion.Easing
                            )
                        ) + slideOutHorizontally { -pathMotionDistancePx }) using
                        SizeTransform(clip = false)
                },
                contentKey = { it },
                modifier = Modifier
                    .padding(start = ArtistTopBarTitleGap)
                    .offset(y = ArtistTopBarLayout.breadcrumbBaselineOffsetY)
                    .then(if (currentTitle == null) Modifier.weight(1f) else Modifier)
            ) { leadingText ->
                Text(
                    text = leadingText,
                    style = textStyle,
                    color = if (visualModel.collapsedAncestors) {
                        contentColor.copy(alpha = 0.72f)
                    } else {
                        contentColor
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (currentTitle == null) {
                            artistHasVisualOverflow = result.hasVisualOverflow
                        }
                    },
                    modifier = Modifier.clickable(
                        enabled = interactionEnabled && currentTitle == null &&
                            canOpenFullTitleOverlay(artistHasVisualOverflow),
                        interactionSource = artistInteractionSource,
                        indication = null
                    ) { onFullTitleRequest(artistName) }
                )
            }

            repeat(renderedPathSlotCount) { index ->
                val targetText = visualModel.pathSlots.getOrNull(index)
                var presentedText by remember(index) { mutableStateOf<String?>(null) }
                LaunchedEffect(targetText) {
                    presentedText = targetText
                }
                AnimatedContent(
                    targetState = presentedText,
                    transitionSpec = {
                        (fadeIn(
                            tween(
                                FlowtoneMotion.ShortDurationMillis,
                                easing = FlowtoneMotion.Easing
                            )
                        ) + slideInHorizontally { pathMotionDistancePx }) togetherWith
                            (fadeOut(
                                tween(
                                    FlowtoneMotion.ShortDurationMillis,
                                    easing = FlowtoneMotion.Easing
                                )
                            ) + slideOutHorizontally { -pathMotionDistancePx }) using
                            SizeTransform(clip = false)
                    },
                    contentKey = { it },
                    modifier = Modifier
                        .offset(y = ArtistTopBarLayout.breadcrumbBaselineOffsetY)
                        .then(
                            if (index == visualModel.currentSlotIndex) {
                                Modifier.weight(1f)
                            } else {
                                Modifier
                            }
                        )
                ) { title ->
                    if (title != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = separator,
                                style = textStyle,
                                color = contentColor.copy(alpha = 0.72f),
                                maxLines = 1
                            )
                            Text(
                                text = title,
                                style = textStyle,
                                color = contentColor,
                                maxLines = 1,
                                overflow = if (
                                    index == visualModel.currentSlotIndex &&
                                    visualModel.ellipsizeCurrent
                                ) {
                                    TextOverflow.Ellipsis
                                } else {
                                    TextOverflow.Clip
                                },
                                onTextLayout = { result ->
                                    if (index == visualModel.currentSlotIndex) {
                                        currentHasVisualOverflow = result.hasVisualOverflow
                                    }
                                },
                                modifier = Modifier
                                    .then(
                                        if (index == visualModel.currentSlotIndex) {
                                            Modifier.weight(1f)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable(
                                        enabled = interactionEnabled &&
                                            index == visualModel.currentSlotIndex &&
                                            title == currentTitle &&
                                            canOpenFullTitleOverlay(currentHasVisualOverflow),
                                        interactionSource = currentInteractionSource,
                                        indication = null
                                    ) { onFullTitleRequest(title) }
                            )
                        }
                    }
                }
            }
        }
    }
}
