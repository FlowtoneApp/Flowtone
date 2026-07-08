package ink.tenqui.flowtone.playback

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ListeningDurationSegment(
    val day: LocalDate,
    val durationMs: Long
)

internal fun splitListeningDurationByLocalDate(
    startWallMs: Long,
    endWallMs: Long,
    zoneId: ZoneId
): List<ListeningDurationSegment> {
    if (endWallMs <= startWallMs) {
        return emptyList()
    }

    val segments = mutableListOf<ListeningDurationSegment>()
    var cursor = startWallMs
    while (cursor < endWallMs) {
        val startDateTime = Instant.ofEpochMilli(cursor).atZone(zoneId)
        val day = startDateTime.toLocalDate()
        val nextMidnightMs = day
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val segmentEnd = minOf(endWallMs, nextMidnightMs)
        val durationMs = segmentEnd - cursor
        if (durationMs > 0L) {
            segments += ListeningDurationSegment(day, durationMs)
        }
        cursor = segmentEnd
    }
    return segments
}
