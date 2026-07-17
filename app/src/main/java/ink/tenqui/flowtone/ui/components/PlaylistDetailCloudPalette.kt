package ink.tenqui.flowtone.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.ui.theme.FlowtoneCloudPalette
import ink.tenqui.flowtone.ui.theme.monochromeFlowtoneCloudPalette

internal fun playlistDetailCloudPaletteFor(
    visualType: PlaylistCardVisualType,
    appearanceColorKey: PlaylistAppearanceColorKey?,
    isDarkTheme: Boolean,
    fallbackAccent: Color
): FlowtoneCloudPalette {
    return when (visualType) {
        PlaylistCardVisualType.LikedMusic -> likedMusicDetailCloudPalette(isDarkTheme)
        PlaylistCardVisualType.LocalLibrary -> safeDetailCloudPaletteFromBase(
            baseColor = if (isDarkTheme) {
                LocalLibraryDarkBackground
            } else {
                LocalLibraryLightBackground
            },
            isDarkTheme = isDarkTheme
        )

        PlaylistCardVisualType.UserPlaylist -> safeDetailCloudPaletteFromBase(
            baseColor = playlistAppearanceColors(
                key = appearanceColorKey ?: PlaylistAppearanceColorKey.PURPLE,
                isDarkTheme = isDarkTheme
            ).backgroundColor,
            isDarkTheme = isDarkTheme
        )

        PlaylistCardVisualType.CreatePlaylist -> safeDetailCloudPaletteFromBase(
            baseColor = if (isDarkTheme) {
                CreatePlaylistDarkBackground
            } else {
                CreatePlaylistLightBackground
            },
            isDarkTheme = isDarkTheme
        )

        PlaylistCardVisualType.Default -> monochromeFlowtoneCloudPalette(fallbackAccent)
    }
}

private fun likedMusicDetailCloudPalette(isDarkTheme: Boolean): FlowtoneCloudPalette {
    val colors = if (isDarkTheme) {
        LikedMusicDarkCloudColors
    } else {
        LikedMusicLightCloudColors
    }
    return FlowtoneCloudPalette(
        primary = colors[0],
        secondary = colors[1],
        tertiary = colors[2]
    )
}

private fun safeDetailCloudPaletteFromBase(
    baseColor: Color,
    isDarkTheme: Boolean
): FlowtoneCloudPalette {
    return if (isDarkTheme) {
        FlowtoneCloudPalette(
            primary = lerp(baseColor, Color.White, 0.08f),
            secondary = lerp(baseColor, Color.White, 0.16f),
            tertiary = lerp(baseColor, Color.Black, 0.12f)
        )
    } else {
        FlowtoneCloudPalette(
            primary = lerp(baseColor, Color.Black, 0.06f),
            secondary = lerp(baseColor, Color.White, 0.08f),
            tertiary = lerp(baseColor, Color.Black, 0.16f)
        )
    }
}
