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

internal enum class ArtistTopBarAlbumTitleLayout { Breadcrumb, AlbumOnly }

internal fun artistTopBarAlbumTitleLayout(
    breadcrumbNaturalWidthPx: Float,
    availableWidthPx: Float
): ArtistTopBarAlbumTitleLayout = if (
    breadcrumbNaturalWidthPx <= availableWidthPx.coerceAtLeast(0f)
) {
    ArtistTopBarAlbumTitleLayout.Breadcrumb
} else {
    ArtistTopBarAlbumTitleLayout.AlbumOnly
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
    albumTitle: String?,
    albumPresentationKey: String?,
    albumBreadcrumbProgress: Float,
    albumSeparatorProgress: Float,
    albumTitleProgress: Float,
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
    val bridgeProgress = albumBreadcrumbProgress.coerceIn(0f, 1f)
    val separatorTransition = artistAlbumSuffixTransition(albumSeparatorProgress)
    val titleTransition = artistAlbumSuffixTransition(albumTitleProgress)
    val replacementTransition = artistAlbumReplacementTransition(bridgeProgress)
    val pathMotionDistancePx = with(density) { 8.dp.toPx() }
    var artistHasVisualOverflow by remember(artistName) { mutableStateOf(false) }
    var albumHasVisualOverflow by remember(albumTitle) { mutableStateOf(false) }
    val artistInteractionSource = remember(artistName) { MutableInteractionSource() }
    val albumInteractionSource = remember(albumTitle) { MutableInteractionSource() }

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
        if (albumTitle == null) {
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
            val separatorWidthPx = textMeasurer.measure(" / ", textStyle).size.width.toFloat()
            val albumWidthPx = textMeasurer.measure(albumTitle, textStyle).size.width.toFloat()
            val albumLayout = artistTopBarAlbumTitleLayout(
                breadcrumbNaturalWidthPx = avatarAndGapWidthPx + artistWidthPx +
                    separatorWidthPx + albumWidthPx,
                availableWidthPx = availableWidthPx
            )
            key(checkNotNull(albumPresentationKey)) {
                if (albumLayout == ArtistTopBarAlbumTitleLayout.Breadcrumb) {
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
                        Text(
                            text = " / ",
                            style = textStyle,
                            color = contentColor.copy(alpha = 0.72f),
                            maxLines = 1,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = separatorTransition.alpha
                                    translationX = pathMotionDistancePx *
                                        separatorTransition.translationXFraction
                                }
                                .blur(
                                    radius = PageMotion.PageBlurRadius *
                                        separatorTransition.blurFraction,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                )
                        )
                        Text(
                            text = albumTitle,
                            style = textStyle,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result -> albumHasVisualOverflow = result.hasVisualOverflow },
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    alpha = titleTransition.alpha
                                    translationX = pathMotionDistancePx *
                                        titleTransition.translationXFraction
                                }
                                .blur(
                                    radius = PageMotion.PageBlurRadius * titleTransition.blurFraction,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                )
                                .clickable(
                                    enabled = interactionEnabled &&
                                        titleTransition.alpha >= 0.999f &&
                                        canOpenFullTitleOverlay(albumHasVisualOverflow),
                                    interactionSource = albumInteractionSource,
                                    indication = null
                                ) { onFullTitleRequest(albumTitle) }
                        )
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
                        Text(
                            text = albumTitle,
                            style = textStyle,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result -> albumHasVisualOverflow = result.hasVisualOverflow },
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
                                .clickable(
                                    enabled = interactionEnabled &&
                                        replacementTransition.albumAlpha >= 0.999f &&
                                        canOpenFullTitleOverlay(albumHasVisualOverflow),
                                    interactionSource = albumInteractionSource,
                                    indication = null
                                ) { onFullTitleRequest(albumTitle) }
                        )
                    }
                }
            }
        }
    }
}
