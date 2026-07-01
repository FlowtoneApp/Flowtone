package ink.tenqui.flowtone.data.listening

import java.time.LocalDate

data class ListeningStatsSnapshot(
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val todaySongCount: Int = 0,
    val totalListeningDurationMs: Long = 0L,
    val lastPlayedSongId: Long? = null,
    val lastPlayedSongTitle: String? = null,
    val updatedAtMillis: Long = 0L
)
