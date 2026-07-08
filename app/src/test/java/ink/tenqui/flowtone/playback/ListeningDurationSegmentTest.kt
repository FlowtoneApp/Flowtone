package ink.tenqui.flowtone.playback

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningDurationSegmentTest {
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun splitAcrossLocalMidnight() {
        val start = millisAt(2026, 7, 8, 23, 59, 58)
        val end = millisAt(2026, 7, 9, 0, 0, 3)

        val segments = splitListeningDurationByLocalDate(start, end, zoneId)

        assertEquals(2, segments.size)
        assertEquals(LocalDate.of(2026, 7, 8), segments[0].day)
        assertEquals(2_000L, segments[0].durationMs)
        assertEquals(LocalDate.of(2026, 7, 9), segments[1].day)
        assertEquals(3_000L, segments[1].durationMs)
    }

    @Test
    fun returnsEmptyWhenEndIsNotAfterStart() {
        val start = millisAt(2026, 7, 8, 12, 0, 0)

        assertTrue(splitListeningDurationByLocalDate(start, start, zoneId).isEmpty())
        assertTrue(splitListeningDurationByLocalDate(start, start - 1L, zoneId).isEmpty())
    }

    private fun millisAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
