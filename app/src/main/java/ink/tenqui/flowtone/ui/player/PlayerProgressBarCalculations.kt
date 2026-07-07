package ink.tenqui.flowtone.ui.player

internal fun progressFraction(
    positionMs: Long,
    durationMs: Long
): Float {
    return if (durationMs > 0L) {
        positionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
}

internal fun progressFromX(
    x: Float,
    width: Float
): Float {
    return (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

internal fun positionFromProgress(
    durationMs: Long,
    progress: Float
): Long {
    return (durationMs * progress.coerceIn(0f, 1f))
        .toLong()
        .coerceIn(0L, durationMs.coerceAtLeast(0L))
}

internal fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
