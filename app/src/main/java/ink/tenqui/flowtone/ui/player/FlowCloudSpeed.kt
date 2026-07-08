package ink.tenqui.flowtone.ui.player

import java.math.BigDecimal
import java.math.RoundingMode

internal const val FlowCloudSpeedOff = 0f
internal const val MinFlowCloudSpeed = 0.1f
internal const val MaxFlowCloudSpeed = 3f
internal const val DefaultFlowCloudSpeed = 1f

private const val FlowCloudSpeedSliderActiveStart = 0.01f

internal fun Float.coerceFlowCloudSpeed(): Float {
    if (isNaN() || isInfinite()) {
        return DefaultFlowCloudSpeed
    }
    return when {
        this < 0f -> FlowCloudSpeedOff
        this == 0f -> FlowCloudSpeedOff
        this < MinFlowCloudSpeed -> MinFlowCloudSpeed
        this > MaxFlowCloudSpeed -> MaxFlowCloudSpeed
        else -> this
    }
}

internal fun flowCloudSpeedToSliderProgress(speed: Float): Float {
    val safeSpeed = speed.coerceFlowCloudSpeed()
    if (safeSpeed == FlowCloudSpeedOff) {
        return 0f
    }
    val activeProgress = (safeSpeed - MinFlowCloudSpeed) /
        (MaxFlowCloudSpeed - MinFlowCloudSpeed)
    return (FlowCloudSpeedSliderActiveStart +
        (1f - FlowCloudSpeedSliderActiveStart) * activeProgress)
        .coerceIn(FlowCloudSpeedSliderActiveStart, 1f)
}

internal fun sliderProgressToFlowCloudSpeed(progress: Float): Float {
    val safeProgress = progress.coerceIn(0f, 1f)
    if (safeProgress <= 0f) {
        return FlowCloudSpeedOff
    }
    val activeProgress = ((safeProgress - FlowCloudSpeedSliderActiveStart) /
        (1f - FlowCloudSpeedSliderActiveStart))
        .coerceIn(0f, 1f)
    return (MinFlowCloudSpeed + (MaxFlowCloudSpeed - MinFlowCloudSpeed) * activeProgress)
        .coerceFlowCloudSpeed()
}

internal fun formatFlowCloudSpeed(speed: Float): String {
    val safeSpeed = speed.coerceFlowCloudSpeed()
    if (safeSpeed == FlowCloudSpeedOff) {
        return "\u5173"
    }
    val rounded = BigDecimal.valueOf(safeSpeed.toDouble())
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    return "${rounded}x"
}
