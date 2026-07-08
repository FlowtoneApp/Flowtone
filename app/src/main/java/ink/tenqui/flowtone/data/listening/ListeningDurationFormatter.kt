package ink.tenqui.flowtone.data.listening

fun formatListeningDuration(durationMs: Long): String {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    if (safeDurationMs < 60_000L) {
        return "不足 1 分钟"
    }

    val totalMinutes = safeDurationMs / 60_000L
    if (totalMinutes < 60L) {
        return "$totalMinutes 分钟"
    }

    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (minutes == 0L) {
        "$hours 小时"
    } else {
        "$hours 小时 $minutes 分钟"
    }
}
