package ink.tenqui.flowtone.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.core.model.StablePlaylistAppearanceColorKeys

@Immutable
internal data class PlaylistAppearanceColors(
    val backgroundColor: Color,
    val titleColor: Color,
    val subtitleColor: Color,
    val iconContainerColor: Color,
    val iconColor: Color,
    val actionColor: Color
)

internal val PlaylistAppearanceColorKeys: List<PlaylistAppearanceColorKey> =
    StablePlaylistAppearanceColorKeys

internal fun playlistAppearanceColorLabel(key: PlaylistAppearanceColorKey): String {
    return when (key) {
        PlaylistAppearanceColorKey.ROSE -> "粉色"
        PlaylistAppearanceColorKey.PURPLE -> "紫色"
        PlaylistAppearanceColorKey.INDIGO -> "靛蓝色"
        PlaylistAppearanceColorKey.BLUE -> "蓝色"
        PlaylistAppearanceColorKey.TEAL -> "青绿色"
        PlaylistAppearanceColorKey.GREEN -> "绿色"
        PlaylistAppearanceColorKey.AMBER -> "琥珀色"
        PlaylistAppearanceColorKey.ORANGE -> "橙色"
    }
}

internal fun playlistAppearanceColors(
    key: PlaylistAppearanceColorKey,
    isDarkTheme: Boolean
): PlaylistAppearanceColors {
    return if (isDarkTheme) {
        darkPlaylistAppearanceColors(key)
    } else {
        lightPlaylistAppearanceColors(key)
    }
}

private fun lightPlaylistAppearanceColors(
    key: PlaylistAppearanceColorKey
): PlaylistAppearanceColors {
    val backgroundColor: Color
    val titleColor: Color
    val subtitleColor: Color
    when (key) {
        PlaylistAppearanceColorKey.ROSE -> {
            backgroundColor = Color(0xFFE8B9C7)
            titleColor = Color(0xFF321E25)
            subtitleColor = Color(0xFF684852)
        }

        PlaylistAppearanceColorKey.PURPLE -> {
            backgroundColor = Color(0xFFD3C1E8)
            titleColor = Color(0xFF2B2134)
            subtitleColor = Color(0xFF5C4B68)
        }

        PlaylistAppearanceColorKey.INDIGO -> {
            backgroundColor = Color(0xFFC5C9E8)
            titleColor = Color(0xFF22263B)
            subtitleColor = Color(0xFF4B506C)
        }

        PlaylistAppearanceColorKey.BLUE -> {
            backgroundColor = Color(0xFFBBD2E8)
            titleColor = Color(0xFF1D2A36)
            subtitleColor = Color(0xFF435C6D)
        }

        PlaylistAppearanceColorKey.TEAL -> {
            backgroundColor = Color(0xFFB7D9D3)
            titleColor = Color(0xFF18312E)
            subtitleColor = Color(0xFF395E59)
        }

        PlaylistAppearanceColorKey.GREEN -> {
            backgroundColor = Color(0xFFC6DDBB)
            titleColor = Color(0xFF22321D)
            subtitleColor = Color(0xFF46613D)
        }

        PlaylistAppearanceColorKey.AMBER -> {
            backgroundColor = Color(0xFFE5D2A7)
            titleColor = Color(0xFF342A16)
            subtitleColor = Color(0xFF6A5730)
        }

        PlaylistAppearanceColorKey.ORANGE -> {
            backgroundColor = Color(0xFFE5C0A5)
            titleColor = Color(0xFF372318)
            subtitleColor = Color(0xFF704A35)
        }
    }
    return PlaylistAppearanceColors(
        backgroundColor = backgroundColor,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        iconContainerColor = titleColor.copy(alpha = 0.14f),
        iconColor = titleColor,
        actionColor = titleColor
    )
}

private fun darkPlaylistAppearanceColors(
    key: PlaylistAppearanceColorKey
): PlaylistAppearanceColors {
    val backgroundColor: Color
    val titleColor: Color
    val subtitleColor: Color
    when (key) {
        PlaylistAppearanceColorKey.ROSE -> {
            backgroundColor = Color(0xFF3D2931)
            titleColor = Color(0xFFF5EAF0)
            subtitleColor = Color(0xFFD9C3CD)
        }

        PlaylistAppearanceColorKey.PURPLE -> {
            backgroundColor = Color(0xFF352C42)
            titleColor = Color(0xFFF2ECF8)
            subtitleColor = Color(0xFFD1C5DD)
        }

        PlaylistAppearanceColorKey.INDIGO -> {
            backgroundColor = Color(0xFF2C3045)
            titleColor = Color(0xFFECEEF9)
            subtitleColor = Color(0xFFC4C8DD)
        }

        PlaylistAppearanceColorKey.BLUE -> {
            backgroundColor = Color(0xFF263746)
            titleColor = Color(0xFFE9F1F8)
            subtitleColor = Color(0xFFBDD0DE)
        }

        PlaylistAppearanceColorKey.TEAL -> {
            backgroundColor = Color(0xFF243D3A)
            titleColor = Color(0xFFE7F4F1)
            subtitleColor = Color(0xFFBBD4CF)
        }

        PlaylistAppearanceColorKey.GREEN -> {
            backgroundColor = Color(0xFF2D3D29)
            titleColor = Color(0xFFEDF5E9)
            subtitleColor = Color(0xFFC6D6BE)
        }

        PlaylistAppearanceColorKey.AMBER -> {
            backgroundColor = Color(0xFF453A24)
            titleColor = Color(0xFFF7F0DF)
            subtitleColor = Color(0xFFDCCDAC)
        }

        PlaylistAppearanceColorKey.ORANGE -> {
            backgroundColor = Color(0xFF463126)
            titleColor = Color(0xFFF8EEE8)
            subtitleColor = Color(0xFFDCC4B5)
        }
    }
    return PlaylistAppearanceColors(
        backgroundColor = backgroundColor,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        iconContainerColor = titleColor.copy(alpha = 0.16f),
        iconColor = titleColor,
        actionColor = titleColor
    )
}
