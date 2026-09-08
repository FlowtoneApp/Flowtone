package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.canOpenFullTitleOverlay

private val ArtistTopBarAvatarSize = 36.dp
private val ArtistTopBarTitleGap = 10.dp

internal data class ArtistTopBarTitlePresentation(
    val showAvatarAndBreadcrumb: Boolean
)

internal fun artistTopBarTitlePresentation(
    naturalContentWidthPx: Float,
    availableWidthPx: Float
): ArtistTopBarTitlePresentation = ArtistTopBarTitlePresentation(
    showAvatarAndBreadcrumb = naturalContentWidthPx <= availableWidthPx.coerceAtLeast(0f)
)

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

internal data class ArtistAlbumSuffixTransition(
    val alpha: Float,
    val translationXFraction: Float,
    val blurFraction: Float
)

internal fun artistAlbumSuffixTransition(progress: Float): ArtistAlbumSuffixTransition {
    val localProgress = progress.coerceIn(0f, 1f)
    return ArtistAlbumSuffixTransition(
        alpha = localProgress,
        translationXFraction = 1f - localProgress,
        blurFraction = 1f - localProgress
    )
}

internal data class ArtistAlbumReplacementTransition(
    val artistAlpha: Float,
    val artistTranslationYFraction: Float,
    val albumAlpha: Float,
    val albumTranslationYFraction: Float
)

internal fun artistAlbumReplacementTransition(
    progress: Float
): ArtistAlbumReplacementTransition {
    val localProgress = progress.coerceIn(0f, 1f)
    return ArtistAlbumReplacementTransition(
        artistAlpha = 1f - localProgress,
        artistTranslationYFraction = -localProgress,
        albumAlpha = localProgress,
        albumTranslationYFraction = 1f - localProgress
    )
}

@Composable
internal fun ArtistIdentityTopBarRow(
    artistName: String,
    avatarImage: ExtensionImage?,
    pathSegments: List<String>,
    pathPresentationKey: String?,
    pathProgress: Float,
    pathSeparatorProgress: Float,
    pathTitleProgress: Float,
    avatarProgress: Float,
    artistTitleProgress: Float,
    identityMotionDistancePx: Float,
    contentColor: Color,
    onFullTitleRequest: (String) -> Unit,
    interactionEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bridgeProgress = pathProgress.coerceIn(0f, 1f)
    val separatorTransition = artistAlbumSuffixTransition(pathSeparatorProgress)
    val titleTransition = artistAlbumSuffixTransition(pathTitleProgress)
    val replacementTransition = artistAlbumReplacementTransition(bridgeProgress)
    val pathMotionDistancePx = with(density) { 8.dp.toPx() }
    var artistHasVisualOverflow by remember(artistName) { mutableStateOf(false) }
    val currentTitle = pathSegments.lastOrNull()
    var currentHasVisualOverflow by remember(currentTitle) { mutableStateOf(false) }
    val artistInteractionSource = remember(artistName) { MutableInteractionSource() }
    val currentInteractionSource = remember(currentTitle) { MutableInteractionSource() }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val avatarAndGapWidthPx = with(density) {
            ArtistTopBarAvatarSize.toPx() + ArtistTopBarTitleGap.toPx()
        }
        val artistWidthPx = textMeasurer.measure(artistName, textStyle).size.width.toFloat()
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val artistPresentation = artistTopBarTitlePresentation(
            naturalContentWidthPx = avatarAndGapWidthPx + artistWidthPx,
            availableWidthPx = availableWidthPx
        )
        if (currentTitle == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (artistPresentation.showAvatarAndBreadcrumb) {
                    ArtistAvatar(
                        size = ArtistTopBarAvatarSize,
                        image = avatarImage,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.graphicsLayer {
                            alpha = avatarProgress
                            translationY = -identityMotionDistancePx * (1f - avatarProgress)
                        }
                    )
                }
                Text(
                    text = artistName,
                    style = textStyle,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result -> artistHasVisualOverflow = result.hasVisualOverflow },
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (artistPresentation.showAvatarAndBreadcrumb) {
                                Modifier.padding(start = ArtistTopBarTitleGap)
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer {
                            alpha = artistTitleProgress
                            translationY = -identityMotionDistancePx * (1f - artistTitleProgress)
                        }
                        .clickable(
                            enabled = interactionEnabled &&
                                canOpenFullTitleOverlay(artistHasVisualOverflow),
                            interactionSource = artistInteractionSource,
                            indication = null
                        ) { onFullTitleRequest(artistName) }
                )
            }
        } else {
            val separator = " / "
            val collapsedPrefix = "… / "
            val separatorWidthPx = textMeasurer.measure(separator, textStyle).size.width.toFloat()
            val currentWidthPx = textMeasurer.measure(currentTitle, textStyle).size.width.toFloat()
            val segmentWidthsPx = pathSegments.sumOf { segment ->
                textMeasurer.measure(segment, textStyle).size.width.toDouble()
            }.toFloat()
            val fullPathWidthPx = avatarAndGapWidthPx + artistWidthPx +
                separatorWidthPx * pathSegments.size + segmentWidthsPx
            val collapsedPathWidthPx = avatarAndGapWidthPx +
                textMeasurer.measure(collapsedPrefix, textStyle).size.width + currentWidthPx
            val pathLayout = artistTopBarPathLayout(
                fullPathNaturalWidthPx = fullPathWidthPx,
                collapsedPathNaturalWidthPx = collapsedPathWidthPx,
                availableWidthPx = availableWidthPx
            )
            key(checkNotNull(pathPresentationKey)) {
                if (pathLayout == ArtistTopBarPathLayout.FullPath) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ArtistAvatar(
                            size = ArtistTopBarAvatarSize,
                            image = avatarImage,
                            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.graphicsLayer {
                                alpha = avatarProgress
                                translationY = -identityMotionDistancePx * (1f - avatarProgress)
                            }
                        )
                        Text(
                            text = artistName,
                            style = textStyle,
                            color = contentColor,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = ArtistTopBarTitleGap)
                                .graphicsLayer {
                                    alpha = artistTitleProgress
                                    translationY = -identityMotionDistancePx *
                                        (1f - artistTitleProgress)
                                }
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = titleTransition.alpha
                                    translationX = pathMotionDistancePx *
                                        titleTransition.translationXFraction
                                }
                                .blur(
                                    radius = PageMotion.PageBlurRadius *
                                        titleTransition.blurFraction,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                )
                        ) {
                            pathSegments.forEach { segment ->
                                Text(
                                    text = separator,
                                    style = textStyle,
                                    color = contentColor.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = separatorTransition.alpha
                                    }
                                )
                                Text(
                                    text = segment,
                                    style = textStyle,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = replacementTransition.artistAlpha
                                    translationY = pathMotionDistancePx *
                                        replacementTransition.artistTranslationYFraction
                                }
                                .blur(
                                    radius = PageMotion.PageBlurRadius *
                                        (1f - replacementTransition.artistAlpha),
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                )
                        ) {
                            if (artistPresentation.showAvatarAndBreadcrumb) {
                                ArtistAvatar(
                                    size = ArtistTopBarAvatarSize,
                                    image = avatarImage,
                                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = avatarProgress
                                        translationY = -identityMotionDistancePx *
                                            (1f - avatarProgress)
                                    }
                                )
                            }
                            Text(
                                text = artistName,
                                style = textStyle,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (artistPresentation.showAvatarAndBreadcrumb) {
                                            Modifier.padding(start = ArtistTopBarTitleGap)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .graphicsLayer {
                                        alpha = artistTitleProgress
                                        translationY = -identityMotionDistancePx *
                                            (1f - artistTitleProgress)
                                    }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = replacementTransition.albumAlpha
                                    translationY = pathMotionDistancePx *
                                        replacementTransition.albumTranslationYFraction
                                }
                                .blur(
                                    radius = PageMotion.PageBlurRadius *
                                        (1f - replacementTransition.albumAlpha),
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                )
                        ) {
                            ArtistAvatar(
                                size = ArtistTopBarAvatarSize,
                                image = avatarImage,
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = collapsedPrefix,
                                style = textStyle,
                                color = contentColor.copy(alpha = 0.72f),
                                maxLines = 1,
                                modifier = Modifier.padding(start = ArtistTopBarTitleGap)
                            )
                            Text(
                                text = currentTitle,
                                style = textStyle,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    currentHasVisualOverflow = result.hasVisualOverflow
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        enabled = interactionEnabled &&
                                            replacementTransition.albumAlpha >= 0.999f &&
                                            canOpenFullTitleOverlay(currentHasVisualOverflow),
                                        interactionSource = currentInteractionSource,
                                        indication = null
                                    ) { onFullTitleRequest(currentTitle) }
                            )
                        }
                    }
                }
            }
        }
    }
}
