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
import ink.tenqui.flowtone.ui.player.CrossfadeFlowCloudBackground
import ink.tenqui.flowtone.ui.player.DefaultFlowCloudSpeed

@Immutable
internal data class PlaylistCardContentColors(
    val titleColor: Color,
    val subtitleColor: Color,
    val iconContainerColor: Color,
    val iconColor: Color,
    val actionColor: Color
)

@Composable
internal fun PlaylistCardSurface(
    visualType: PlaylistCardVisualType,
    shape: Shape,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    clickModifier: Modifier = Modifier,
    flowCloudSpeed: Float = DefaultFlowCloudSpeed,
    isFlowCloudPlaying: Boolean = true,
    content: @Composable BoxScope.(PlaylistCardContentColors) -> Unit
) {
    val style = remember(visualType) {
        playlistCardVisualStyleFor(visualType)
    }
    val visuals = rememberPlaylistCardVisuals(style)

    Box(
        modifier = modifier
            .clip(shape)
            .background(visuals.containerColor)
            .then(clickModifier)
    ) {
        PlaylistCardBackgroundLayer(
            style = style,
            visuals = visuals,
            flowCloudSpeed = flowCloudSpeed,
            isFlowCloudPlaying = isFlowCloudPlaying,
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
    style: PlaylistCardVisualStyle
): PlaylistCardVisuals {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() <= 0.5f
    return remember(
        style.type,
        isDarkTheme,
        colorScheme.surfaceContainer,
        colorScheme.onSurface,
        colorScheme.onSurfaceVariant,
        colorScheme.primaryContainer,
        colorScheme.onPrimaryContainer
    ) {
        playlistCardVisualsFor(
            type = style.type,
            isDarkTheme = isDarkTheme,
            defaultContainerColor = colorScheme.surfaceContainer,
            defaultContentColors = PlaylistCardContentColors(
                titleColor = colorScheme.onSurface,
                subtitleColor = colorScheme.onSurfaceVariant,
                iconContainerColor = colorScheme.primaryContainer,
                iconColor = colorScheme.onPrimaryContainer,
                actionColor = colorScheme.onSurface
            )
        )
    }
}

private fun playlistCardVisualsFor(
    type: PlaylistCardVisualType,
    isDarkTheme: Boolean,
    defaultContainerColor: Color,
    defaultContentColors: PlaylistCardContentColors
): PlaylistCardVisuals {
    return when (type) {
        PlaylistCardVisualType.LocalLibrary -> if (isDarkTheme) {
            PlaylistCardVisuals(
                containerColor = LocalLibraryDarkBackground,
                contentColors = PlaylistCardContentColors(
                    titleColor = Color(0xFFF1EAFB),
                    subtitleColor = Color(0xFFD2C5E4),
                    iconContainerColor = Color(0xFF594A73),
                    iconColor = Color(0xFFF1EAFB),
                    actionColor = Color(0xFFF1EAFB)
                )
            )
        } else {
            PlaylistCardVisuals(
                containerColor = LocalLibraryLightBackground,
                contentColors = PlaylistCardContentColors(
                    titleColor = Color(0xFF2B2140),
                    subtitleColor = Color(0xFF574A6D),
                    iconContainerColor = Color(0xFF665182),
                    iconColor = Color(0xFFF7F1FC),
                    actionColor = Color(0xFF2B2140)
                )
            )
        }

        PlaylistCardVisualType.LikedMusic -> if (isDarkTheme) {
            PlaylistCardVisuals(
                containerColor = LikedMusicDarkBackground,
                contentColors = PlaylistCardContentColors(
                    titleColor = Color(0xFFF8EDF3),
                    subtitleColor = Color(0xFFE2C9D6),
                    iconContainerColor = Color(0xFFD69AB8),
                    iconColor = Color(0xFF3B1D2C),
                    actionColor = Color(0xFFF8EDF3)
                ),
                cloudVisuals = PlaylistCardCloudVisuals(
                    colors = LikedMusicDarkCloudColors,
                    alpha = 0.72f,
                    stableScrim = Color.Black.copy(alpha = 0.26f),
                    topScrim = Color.Black.copy(alpha = 0.16f),
                    bottomScrim = Color.Black.copy(alpha = 0.22f)
                )
            )
        } else {
            PlaylistCardVisuals(
                containerColor = LikedMusicLightBackground,
                contentColors = PlaylistCardContentColors(
                    titleColor = Color(0xFF2B1822),
                    subtitleColor = Color(0xFF624654),
                    iconContainerColor = Color(0xFF71415A),
                    iconColor = Color(0xFFFFEDF5),
                    actionColor = Color(0xFF2B1822)
                ),
                cloudVisuals = PlaylistCardCloudVisuals(
                    colors = LikedMusicLightCloudColors,
                    alpha = 0.50f,
                    stableScrim = Color(0xFF3B2431).copy(alpha = 0.04f),
                    topScrim = Color.Black.copy(alpha = 0.02f),
                    bottomScrim = Color.Black.copy(alpha = 0.05f)
                )
            )
        }

        PlaylistCardVisualType.CreatePlaylist -> if (isDarkTheme) {
            PlaylistCardVisuals(
                containerColor = CreatePlaylistDarkBackground,
                contentColors = createPlaylistContentColors(
                    subtitleColor = Color(0xFFD3C7DF),
                    iconContainerColor = Color(0xFF6A557E)
                )
            )
        } else {
            PlaylistCardVisuals(
                containerColor = CreatePlaylistLightBackground,
                contentColors = createPlaylistContentColors(
                    subtitleColor = Color(0xFFDDD0E9),
                    iconContainerColor = Color(0xFF80689A)
                )
            )
        }

        PlaylistCardVisualType.UserPlaylist,
        PlaylistCardVisualType.Default -> PlaylistCardVisuals(
            containerColor = defaultContainerColor,
            contentColors = defaultContentColors
        )
    }
}

private fun createPlaylistContentColors(
    subtitleColor: Color,
    iconContainerColor: Color
): PlaylistCardContentColors {
    val foregroundColor = Color(0xFFF5EFFA)
    return PlaylistCardContentColors(
        titleColor = foregroundColor,
        subtitleColor = subtitleColor,
        iconContainerColor = iconContainerColor,
        iconColor = foregroundColor,
        actionColor = foregroundColor
    )
}

@Composable
private fun PlaylistCardBackgroundLayer(
    style: PlaylistCardVisualStyle,
    visuals: PlaylistCardVisuals,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (style.backgroundType != PlaylistCardBackgroundType.FlowCloud) {
        return
    }
    val cloudVisuals = visuals.cloudVisuals ?: return

    Box(
        modifier = modifier.background(visuals.containerColor)
    ) {
        CrossfadeFlowCloudBackground(
            colors = cloudVisuals.colors,
            progress = LikedSongsCloudProgress,
            isPlaying = isFlowCloudPlaying,
            alpha = cloudVisuals.alpha,
            flowCloudSpeed = if (style.usesFlowCloudSpeed) {
                flowCloudSpeed
            } else {
                DefaultFlowCloudSpeed
            },
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
    val cloudVisuals: PlaylistCardCloudVisuals? = null
)

private data class PlaylistCardCloudVisuals(
    val colors: List<Color>,
    val alpha: Float,
    val stableScrim: Color,
    val topScrim: Color,
    val bottomScrim: Color
)

internal val LocalLibraryLightBackground = Color(0xFFD9CDF2)
internal val LocalLibraryDarkBackground = Color(0xFF332A47)
internal val CreatePlaylistLightBackground = Color(0xFF50396D)
internal val CreatePlaylistDarkBackground = Color(0xFF2D223D)

internal val LikedMusicLightCloudColors = listOf(
    Color(0xFFE6A1BE),
    Color(0xFFB9A3DD),
    Color(0xFFA6B9E1)
)
internal val LikedMusicDarkCloudColors = listOf(
    Color(0xFF8F496B),
    Color(0xFF66518E),
    Color(0xFF4D608D)
)

private val LikedMusicLightBackground = Color(0xFFEED1E0)
private val LikedMusicDarkBackground = Color(0xFF2A1C25)
private const val LikedSongsCloudProgress = 0.54f
