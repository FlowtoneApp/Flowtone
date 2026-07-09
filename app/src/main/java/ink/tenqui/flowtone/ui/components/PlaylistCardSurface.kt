package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import android.graphics.Color as AndroidColor
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.ui.player.CrossfadeFlowCloudBackground

@Immutable
internal data class PlaylistCardContentColors(
    val titleColor: Color,
    val subtitleColor: Color,
    val iconContainerColor: Color,
    val iconColor: Color,
    val actionColor: Color
)

@Composable
internal fun defaultPlaylistCardContentColors(): PlaylistCardContentColors {
    val colorScheme = MaterialTheme.colorScheme
    return PlaylistCardContentColors(
        titleColor = colorScheme.onSurface,
        subtitleColor = colorScheme.onSurfaceVariant,
        iconContainerColor = colorScheme.primaryContainer,
        iconColor = colorScheme.onPrimaryContainer,
        actionColor = colorScheme.onSurface
    )
}

@Composable
internal fun PlaylistCardSurface(
    playlist: LibraryPlaylistCard,
    flowCloudSpeed: Float,
    visualState: PlaylistCardVisualState,
    shape: Shape,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    clickModifier: Modifier = Modifier,
    content: @Composable BoxScope.(PlaylistCardContentColors) -> Unit
) {
    val visuals = rememberPlaylistCardVisuals(visualState)

    Box(
        modifier = modifier
            .clip(shape)
            .background(visuals.containerColor)
            .then(clickModifier)
    ) {
        PlaylistCardBackgroundLayer(
            visuals = visuals,
            flowCloudSpeed = flowCloudSpeed,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(contentPadding)
        ) {
            content(visuals.contentColors)
        }
    }
}

@Composable
private fun rememberPlaylistCardVisuals(
    visualState: PlaylistCardVisualState
): PlaylistCardVisuals {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() <= 0.5f
    return remember(
        visualState.baseColorArgb,
        isDarkTheme,
        colorScheme.surfaceContainer
    ) {
        playlistCardVisualsFromBaseColor(
            baseColorArgb = visualState.baseColorArgb,
            isDarkTheme = isDarkTheme
        )
    }
}

private fun playlistCardVisualsFromBaseColor(
    baseColorArgb: Int?,
    isDarkTheme: Boolean
): PlaylistCardVisuals {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(
        baseColorArgb ?: defaultPlaylistBaseColorArgb(isDarkTheme),
        hsv
    )
    val hue = hsv[0]
    val sourceSaturation = hsv[1]
    val safeSaturation = sourceSaturation.coerceIn(0.18f, 0.56f)

    return if (isDarkTheme) {
        val containerColor = hsvColor(
            hue = hue,
            saturation = (safeSaturation * 0.54f).coerceIn(0.14f, 0.30f),
            value = 0.13f
        )
        PlaylistCardVisuals(
            containerColor = containerColor,
            contentColors = PlaylistCardContentColors(
                titleColor = Color(0xFFF6F0FA),
                subtitleColor = Color(0xFFD7CBDF),
                iconContainerColor = hsvColor(
                    hue = hue,
                    saturation = (safeSaturation * 0.62f).coerceIn(0.18f, 0.38f),
                    value = 0.78f
                ),
                iconColor = hsvColor(
                    hue = hue,
                    saturation = 0.42f,
                    value = 0.18f
                ),
                actionColor = Color(0xFFF6F0FA)
            ),
            cloudVisuals = PlaylistCardCloudVisuals(
                colors = listOf(
                    hsvColor(
                        hue = hue,
                        saturation = safeSaturation.coerceIn(0.24f, 0.48f),
                        value = 0.48f
                    ),
                    hsvColor(
                        hue = shiftHue(hue, 16f),
                        saturation = (safeSaturation * 0.78f).coerceIn(0.20f, 0.40f),
                        value = 0.43f
                    ),
                    hsvColor(
                        hue = shiftHue(hue, -18f),
                        saturation = (safeSaturation * 0.68f).coerceIn(0.18f, 0.34f),
                        value = 0.38f
                    )
                ),
                alpha = 0.76f,
                stableScrim = containerColor.copy(alpha = 0.28f),
                topScrim = Color.Black.copy(alpha = 0.30f),
                bottomScrim = Color.Black.copy(alpha = 0.18f)
            )
        )
    } else {
        val containerColor = hsvColor(
            hue = hue,
            saturation = (safeSaturation * 0.34f).coerceIn(0.08f, 0.20f),
            value = 0.88f
        )
        PlaylistCardVisuals(
            containerColor = containerColor,
            contentColors = PlaylistCardContentColors(
                titleColor = Color(0xFF221829),
                subtitleColor = Color(0xFF5A4C63),
                iconContainerColor = hsvColor(
                    hue = hue,
                    saturation = safeSaturation.coerceIn(0.22f, 0.42f),
                    value = 0.46f
                ),
                iconColor = Color(0xFFFFEAF5),
                actionColor = Color(0xFF221829)
            ),
            cloudVisuals = PlaylistCardCloudVisuals(
                colors = listOf(
                    hsvColor(
                        hue = hue,
                        saturation = (safeSaturation * 0.66f).coerceIn(0.16f, 0.34f),
                        value = 0.68f
                    ),
                    hsvColor(
                        hue = shiftHue(hue, 14f),
                        saturation = (safeSaturation * 0.54f).coerceIn(0.12f, 0.28f),
                        value = 0.74f
                    ),
                    hsvColor(
                        hue = shiftHue(hue, -16f),
                        saturation = (safeSaturation * 0.46f).coerceIn(0.10f, 0.24f),
                        value = 0.62f
                    )
                ),
                alpha = 0.48f,
                stableScrim = Color(0xFF32243D).copy(alpha = 0.05f),
                topScrim = Color(0xFFFFF7FB).copy(alpha = 0.10f),
                bottomScrim = Color(0xFFB2A3BA).copy(alpha = 0.08f)
            )
        )
    }
}

private fun defaultPlaylistBaseColorArgb(isDarkTheme: Boolean): Int {
    return if (isDarkTheme) {
        0xFF665E88.toInt()
    } else {
        0xFF8B789C.toInt()
    }
}

private fun hsvColor(
    hue: Float,
    saturation: Float,
    value: Float
): Color {
    return Color(
        AndroidColor.HSVToColor(
            floatArrayOf(
                shiftHue(hue, 0f),
                saturation.coerceIn(0f, 1f),
                value.coerceIn(0f, 1f)
            )
        )
    )
}

private fun shiftHue(
    hue: Float,
    delta: Float
): Float {
    return (hue + delta + 360f) % 360f
}

@Composable
private fun PlaylistCardBackgroundLayer(
    visuals: PlaylistCardVisuals,
    flowCloudSpeed: Float,
    modifier: Modifier = Modifier
) {
    val cloudVisuals = visuals.cloudVisuals ?: return

    Box(
        modifier = modifier.background(visuals.containerColor)
    ) {
        CrossfadeFlowCloudBackground(
            colors = cloudVisuals.colors,
            progress = LikedSongsCloudProgress,
            isPlaying = true,
            alpha = cloudVisuals.alpha,
            flowCloudSpeed = flowCloudSpeed,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            cloudVisuals.topScrim,
                            Color.Transparent,
                            cloudVisuals.bottomScrim
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(cloudVisuals.stableScrim)
        )
    }
}

private data class PlaylistCardVisuals(
    val containerColor: Color,
    val contentColors: PlaylistCardContentColors,
    val cloudVisuals: PlaylistCardCloudVisuals?
)

private data class PlaylistCardCloudVisuals(
    val colors: List<Color>,
    val alpha: Float,
    val stableScrim: Color,
    val topScrim: Color,
    val bottomScrim: Color
)

private const val LikedSongsCloudProgress = 0.54f
