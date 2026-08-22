package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.ui.components.HomeBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.LibraryBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.MineBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.TopLevelBackgroundCloudPlacement
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

internal fun TopLevelPage.backgroundCloudPlacement(): TopLevelBackgroundCloudPlacement {
    return when (this) {
        TopLevelPage.Home -> HomeBackgroundCloudPlacement
        TopLevelPage.Library -> LibraryBackgroundCloudPlacement
        TopLevelPage.Mine -> MineBackgroundCloudPlacement
    }
}

internal fun topLevelCloudPlacementForPagePosition(
    pagePosition: Float
): TopLevelBackgroundCloudPlacement {
    val safePosition = snapExactPagePosition(
        pagePosition.coerceIn(
            minimumValue = 0f,
            maximumValue = TopLevelPage.entries.lastIndex.toFloat()
        )
    )
    val startIndex = floor(safePosition).toInt().coerceIn(
        minimumValue = 0,
        maximumValue = TopLevelPage.entries.lastIndex
    )
    val endIndex = (startIndex + 1).coerceAtMost(TopLevelPage.entries.lastIndex)
    val fraction = (safePosition - startIndex).coerceIn(0f, 1f)

    return lerpCloudPlacement(
        start = TopLevelPage.entries[startIndex].backgroundCloudPlacement(),
        stop = TopLevelPage.entries[endIndex].backgroundCloudPlacement(),
        fraction = fraction
    )
}

private fun lerpCloudPlacement(
    start: TopLevelBackgroundCloudPlacement,
    stop: TopLevelBackgroundCloudPlacement,
    fraction: Float
): TopLevelBackgroundCloudPlacement {
    val safeFraction = fraction.coerceIn(0f, 1f)
    return TopLevelBackgroundCloudPlacement(
        cloudCenterWidthFraction = lerpFloat(
            start.cloudCenterWidthFraction,
            stop.cloudCenterWidthFraction,
            safeFraction
        ),
        cloudCenterRadiusOffsetXFactor = lerpFloat(
            start.cloudCenterRadiusOffsetXFactor,
            stop.cloudCenterRadiusOffsetXFactor,
            safeFraction
        ),
        cloudCenterRadiusOffsetYFactor = lerpFloat(
            start.cloudCenterRadiusOffsetYFactor,
            stop.cloudCenterRadiusOffsetYFactor,
            safeFraction
        ),
        clearCenterWidthFraction = lerpFloat(
            start.clearCenterWidthFraction,
            stop.clearCenterWidthFraction,
            safeFraction
        ),
        clearCenterHeightFraction = lerpFloat(
            start.clearCenterHeightFraction,
            stop.clearCenterHeightFraction,
            safeFraction
        )
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun snapExactPagePosition(pagePosition: Float): Float {
    val roundedPage = pagePosition.roundToInt().coerceIn(
        minimumValue = 0,
        maximumValue = TopLevelPage.entries.lastIndex
    )
    return if (abs(pagePosition - roundedPage) < ExactPagePositionEpsilon) {
        roundedPage.toFloat()
    } else {
        pagePosition
    }
}

private const val ExactPagePositionEpsilon = 0.0001f
