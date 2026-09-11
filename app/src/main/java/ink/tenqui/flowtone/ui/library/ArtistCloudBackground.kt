package ink.tenqui.flowtone.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.ui.components.HomeBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.topLevelPageBackground
import ink.tenqui.flowtone.ui.theme.monochromeFlowtoneCloudPalette

internal fun artistCloudVisible(backgroundKind: ArtistHeroBackgroundKind): Boolean = true

@Composable
internal fun ArtistCloudBackground(
    accentColor: Color,
    ready: Boolean = true,
    modifier: Modifier = Modifier
) {
    val readinessAlpha = rememberArtistArtworkReadinessAlpha(
        ready = ready,
        label = "ArtistCloudReadinessFade"
    )
    ArtistCloudBackdrop(
        accentColor = accentColor,
        cloudAlpha = readinessAlpha,
        modifier = modifier
    )
}

@Composable
internal fun ArtistCloudBackdrop(
    accentColor: Color,
    cloudAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .topLevelPageBackground(
                cloudPalette = monochromeFlowtoneCloudPalette(accentColor),
                cloudAlpha = cloudAlpha,
                cloudPlacement = HomeBackgroundCloudPlacement
            )
    )
}
