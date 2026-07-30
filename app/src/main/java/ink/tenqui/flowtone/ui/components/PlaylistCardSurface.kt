package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
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
    appearanceColorKey: PlaylistAppearanceColorKey? = null,
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
    val visuals = rememberPlaylistCardVisuals(
        style = style,
        appearanceColorKey = appearanceColorKey
    )
    val animatesAppearanceColor = style.type == PlaylistCardVisualType.UserPlaylist
    val contentColors = animatedPlaylistCardContentColors(
        targetColors = visuals.contentColors,
        enabled = animatesAppearanceColor
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(visuals.containerColor)
            .then(clickModifier)
    ) {
        if (animatesAppearanceColor) {
            PlaylistCardAppearanceBackground(
                targetColor = visuals.containerColor,
                modifier = Modifier.matchParentSize()
            )
        }
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
            content(contentColors)
        }
    }
}

@Composable
private fun PlaylistCardAppearanceBackground(
    targetColor: Color,
    modifier: Modifier = Modifier
) {
    Crossfade(
        targetState = targetColor,
        animationSpec = tween(
            durationMillis = PlaylistAppearanceColorTransitionDurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        modifier = modifier,
        label = "playlistAppearanceBackground"
    ) { backgroundColor ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        )
    }
}

@Composable
private fun animatedPlaylistCardContentColors(
    targetColors: PlaylistCardContentColors,
    enabled: Boolean
): PlaylistCardContentColors {
    if (!enabled) {
        return targetColors
    }
    val titleColor by animateColorAsState(
        targetValue = targetColors.titleColor,
        animationSpec = tween(
            durationMillis = PlaylistAppearanceColorTransitionDurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "playlistAppearanceTitleColor"
    )
    val subtitleColor by animateColorAsState(
        targetValue = targetColors.subtitleColor,
        animationSpec = tween(
            durationMillis = PlaylistAppearanceColorTransitionDurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "playlistAppearanceSubtitleColor"
    )
    val iconContainerColor by animateColorAsState(
        targetValue = targetColors.iconContainerColor,
        animationSpec = tween(
            durationMillis = PlaylistAppearanceColorTransitionDurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "playlistAppearanceIconContainerColor"
    )
    val iconColor by animateColorAsState(
        targetValue = targetColors.iconColor,
        animationSpec = tween(
            durationMillis = PlaylistAppearanceColorTransitionDurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "playlistAppearanceIconColor"
    )
    val actionColor by animateColorAsState(
        targetValue = targetColors.actionColor,
        animationSpec = tween(
            durationMillis = PlaylistAppearanceColorTransitionDurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "playlistAppearanceActionColor"
    )
    return remember(
        titleColor,
        subtitleColor,
        iconContainerColor,
        iconColor,
        actionColor
    ) {
        PlaylistCardContentColors(
            titleColor = titleColor,
            subtitleColor = subtitleColor,
            iconContainerColor = iconContainerColor,
            iconColor = iconColor,
            actionColor = actionColor
        )
    }
}

@Composable
private fun rememberPlaylistCardVisuals(
    style: PlaylistCardVisualStyle,
    appearanceColorKey: PlaylistAppearanceColorKey?
): PlaylistCardVisuals {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() <= 0.5f
    return remember(
        style.type,
        appearanceColorKey,
        isDarkTheme,
        colorScheme.surfaceContainer,
        colorScheme.onSurface,
        colorScheme.onSurfaceVariant,
        colorScheme.primaryContainer,
        colorScheme.onPrimaryContainer
    ) {
        playlistCardVisualsFor(
            type = style.type,
            appearanceColorKey = appearanceColorKey,
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
    appearanceColorKey: PlaylistAppearanceColorKey?,
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
                    iconColor = Color.White,
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

        PlaylistCardVisualType.UserPlaylist -> {
            val appearanceColors = playlistAppearanceColors(
                key = appearanceColorKey ?: PlaylistAppearanceColorKey.PURPLE,
                isDarkTheme = isDarkTheme
            )
            PlaylistCardVisuals(
                containerColor = appearanceColors.backgroundColor,
                contentColors = PlaylistCardContentColors(
                    titleColor = appearanceColors.titleColor,
                    subtitleColor = appearanceColors.subtitleColor,
                    iconContainerColor = appearanceColors.iconContainerColor,
                    iconColor = appearanceColors.iconColor,
                    actionColor = appearanceColors.actionColor
                )
            )
        }

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
                if (style.type == PlaylistCardVisualType.LikedMusic) {
                    flowCloudSpeed * LikedMusicFlowCloudSpeedMultiplier
                } else {
                    flowCloudSpeed
                }
            } else {
                DefaultFlowCloudSpeed
            },
            motionRangeMultiplier = if (style.type == PlaylistCardVisualType.LikedMusic) {
                LikedMusicFlowCloudMotionRangeMultiplier
            } else {
                1f
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
    Color(0xFFF2C6D9),
    Color(0xFFDCCEEF),
    Color(0xFFCDDDF3)
)
internal val LikedMusicDarkCloudColors = listOf(
    Color(0xFFA96A84),
    Color(0xFF7A69A7),
    Color(0xFF637CA9)
)

private val LikedMusicLightBackground = Color(0xFFEED1E0)
private val LikedMusicDarkBackground = Color(0xFF2A1C25)
private const val LikedMusicFlowCloudSpeedMultiplier = 2f
private const val LikedMusicFlowCloudMotionRangeMultiplier = 1.4f
private const val PlaylistAppearanceColorTransitionDurationMillis = 240
private const val LikedSongsCloudProgress = 0.54f
