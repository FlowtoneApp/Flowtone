package ink.tenqui.flowtone.app

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor

internal data class TopLevelSearchColors(
    val accent: Color,
    val container: Color,
    val content: Color
)

internal data class TopLevelSearchColorSnapshot(
    val accentArgb: Int,
    val containerArgb: Int,
    val contentArgb: Int
)

@Composable
internal fun topLevelSearchColorsForPager(pagerState: PagerState): TopLevelSearchColors {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
    return topLevelSearchColorsForPagePosition(
        pagePosition = pagePosition,
        isDarkTheme = isDarkTheme
    )
}

internal fun topLevelSearchColorsForPagePosition(
    pagePosition: Float,
    isDarkTheme: Boolean
): TopLevelSearchColors {
    val accents = if (isDarkTheme) {
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
    val safePosition = pagePosition.coerceIn(
        minimumValue = 0f,
        maximumValue = (TopLevelPage.entries.lastIndex).toFloat()
    )
    val startIndex = floor(safePosition).toInt().coerceIn(0, accents.lastIndex)
    val endIndex = (startIndex + 1).coerceAtMost(accents.lastIndex)
    val fraction = (safePosition - startIndex).coerceIn(0f, 1f)
    val accent = lerp(accents[startIndex], accents[endIndex], fraction)
    val container = if (isDarkTheme) {
        lerp(Color(0xFF202431), accent, 0.34f)
    } else {
        lerp(Color(0xFFF4F1FB), accent, 0.20f)
    }
    val content = if (isDarkTheme) {
        lerp(Color.White, accent, 0.18f)
    } else {
        mixWithBlack(accent, amount = 0.28f)
    }

    return TopLevelSearchColors(
        accent = accent,
        container = container,
        content = content
    )
}

internal fun TopLevelSearchColors.snapshot(): TopLevelSearchColorSnapshot {
    return TopLevelSearchColorSnapshot(
        accentArgb = accent.toArgb(),
        containerArgb = container.toArgb(),
        contentArgb = content.toArgb()
    )
}

internal fun TopLevelSearchColorSnapshot.toColors(): TopLevelSearchColors {
    return TopLevelSearchColors(
        accent = Color(accentArgb),
        container = Color(containerArgb),
        content = Color(contentArgb)
    )
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
