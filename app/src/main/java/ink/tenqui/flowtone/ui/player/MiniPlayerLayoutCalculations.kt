package ink.tenqui.flowtone.ui.player

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song

internal fun Song?.toBackdropKey(): String {
    return this?.let { song ->
        "${song.id}|${song.title}|${song.artist}|${song.uri}"
    } ?: "empty_backdrop"
}

internal fun normalizeBackdropColors(
    colors: List<Color>,
    isDarkTheme: Boolean
): List<Color> {
    val fallbackColors = if (isDarkTheme) {
        listOf(
            Color(0xFF5D6C8F),
            Color(0xFF77658E),
            Color(0xFF4E7A73)
        )
    } else {
        listOf(
            Color(0xFF7185B7),
            Color(0xFF9B7EB3),
            Color(0xFF72A79C)
        )
    }
    val sourceColors = colors.ifEmpty { fallbackColors }
    val lastColor = sourceColors.lastOrNull() ?: fallbackColors.last()

    return List(3) { index ->
        sourceColors.getOrElse(index) { lastColor }.copy(alpha = 1f)
    }
}

internal fun coverTintDialogBackgroundColor(colors: List<Color>): Color {
    val seedColor = colors.firstOrNull() ?: Color(0xFF24212B)
    val darkened = mixWithBlack(seedColor, amount = 0.62f)
    return if (darkened.luminance() <= 0.24f) {
        darkened
    } else {
        mixWithBlack(darkened, amount = 0.45f)
    }
}

private fun mixWithBlack(color: Color, amount: Float): Color {
    val blackAmount = amount.coerceIn(0f, 1f)
    val colorAmount = 1f - blackAmount
    return Color(
        red = color.red * colorAmount,
        green = color.green * colorAmount,
        blue = color.blue * colorAmount,
        alpha = 1f
    )
}

internal fun songFallbackCloudSeedColors(
    song: Song?,
    fallbackColor: Int
): List<Int> {
    song ?: return listOf(fallbackColor, fallbackColor, fallbackColor)

    val baseHash = "${song.id}|${song.title}|${song.artist}|${song.uri}".hashCode()
    return List(3) { index ->
        val hue = Math.floorMod(baseHash + index * 47, 360).toFloat()
        AndroidColor.HSVToColor(floatArrayOf(hue, 0.62f, 0.78f))
    }
}

internal data class MiniPlayerFullscreenLayoutMetrics(
    val fullscreenProgressTrackWidth: Dp,
    val fullscreenArtworkX: Dp,
    val fullscreenMetadataTop: Dp,
    val addToPlaylistArtworkSize: Dp,
    val addToPlaylistArtworkLeft: Dp,
    val addToPlaylistArtworkTop: Dp,
    val addToPlaylistTextStart: Dp,
    val addToPlaylistTextWidth: Dp,
    val addToPlaylistCardsTop: Dp,
    val addToPlaylistCardsHeight: Dp,
    val artistExitProgress: Float,
    val fullscreenContentExitSharedProgress: Float,
    val artworkContentExitProgress: Float,
    val addToPlaylistSharedProgress: Float,
    val playbackContentAlpha: Float,
    val playbackContentOffsetY: Dp
)

internal fun miniPlayerFullscreenLayoutMetrics(
    playerWidth: Dp,
    visualPanelHeight: Dp,
    fullscreenCoverCenterY: Dp,
    addToPlaylistStatusBarsTop: Dp,
    fullscreenContentExitProgress: Float,
    artistPlaceholderProgress: Float,
    addToPlaylistProgress: Float
): MiniPlayerFullscreenLayoutMetrics {
    val fullscreenProgressTrackWidth = playerWidth * 0.76f
    val fullscreenArtworkX = (playerWidth - fullscreenProgressTrackWidth) / 2f
    val fullscreenMetadataTop =
        fullscreenCoverCenterY + fullscreenProgressTrackWidth / 2f + 14.dp
    val addToPlaylistArtworkSize = 56.dp
    val addToPlaylistArtworkLeft = 20.dp
    val addToPlaylistArtworkTop = addToPlaylistStatusBarsTop + 72.dp
    val addToPlaylistTextStart =
        addToPlaylistArtworkLeft + addToPlaylistArtworkSize + 12.dp
    val addToPlaylistTextWidth =
        (playerWidth - addToPlaylistTextStart - 20.dp).coerceAtLeast(80.dp)
    val addToPlaylistCardsTop =
        addToPlaylistArtworkTop + addToPlaylistArtworkSize + 24.dp
    val addToPlaylistCardsHeight =
        (visualPanelHeight - addToPlaylistCardsTop - 20.dp)
            .coerceAtLeast(AddToPlaylistCardHeight)
    val artistExitProgress = artistPlaceholderProgress.coerceIn(0f, 1f)
    val fullscreenContentExitSharedProgress = maxOf(
        fullscreenContentExitProgress.coerceIn(0f, 1f),
        artistExitProgress
    )
    val artworkContentExitProgress = fullscreenContentExitProgress.coerceIn(0f, 1f)
    val addToPlaylistSharedProgress = addToPlaylistProgress.coerceIn(0f, 1f)
    val playbackContentAlpha = 1f - fullscreenContentExitSharedProgress
    val playbackContentOffsetY = 32.dp * fullscreenContentExitSharedProgress

    return MiniPlayerFullscreenLayoutMetrics(
        fullscreenProgressTrackWidth = fullscreenProgressTrackWidth,
        fullscreenArtworkX = fullscreenArtworkX,
        fullscreenMetadataTop = fullscreenMetadataTop,
        addToPlaylistArtworkSize = addToPlaylistArtworkSize,
        addToPlaylistArtworkLeft = addToPlaylistArtworkLeft,
        addToPlaylistArtworkTop = addToPlaylistArtworkTop,
        addToPlaylistTextStart = addToPlaylistTextStart,
        addToPlaylistTextWidth = addToPlaylistTextWidth,
        addToPlaylistCardsTop = addToPlaylistCardsTop,
        addToPlaylistCardsHeight = addToPlaylistCardsHeight,
        artistExitProgress = artistExitProgress,
        fullscreenContentExitSharedProgress = fullscreenContentExitSharedProgress,
        artworkContentExitProgress = artworkContentExitProgress,
        addToPlaylistSharedProgress = addToPlaylistSharedProgress,
        playbackContentAlpha = playbackContentAlpha,
        playbackContentOffsetY = playbackContentOffsetY
    )
}
