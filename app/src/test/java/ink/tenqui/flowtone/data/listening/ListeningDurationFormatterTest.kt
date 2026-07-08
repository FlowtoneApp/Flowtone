package ink.tenqui.flowtone.data.listening

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningDurationFormatterTest {
    @Test
    fun formatLessThanOneMinute() {
        assertEquals("不足 1 分钟", formatListeningDuration(0L))
        assertEquals("不足 1 分钟", formatListeningDuration(59_999L))
    }

    @Test
    fun formatMinutesAndHours() {
        assertEquals("1 分钟", formatListeningDuration(60_000L))
        assertEquals("59 分钟", formatListeningDuration(59 * 60_000L))
        assertEquals("1 小时", formatListeningDuration(60 * 60_000L))
        assertEquals("1 小时 1 分钟", formatListeningDuration(61 * 60_000L))
    }
}
