package ink.tenqui.flowtone.data.listening

import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.playback.PlaybackSourceType
import java.time.LocalDate

data class ListeningStatsSnapshot(
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val todaySongCount: Int = 0,
    val totalListeningDurationMs: Long = 0L,
    val lastPlayedSongId: Long? = null,
    val lastPlayedSongTitle: String? = null,
    val updatedAtMillis: Long = 0L,
    val schemaVersion: Int = 1,
    val today: ListeningPeriodStats = ListeningPeriodStats(
        effectivePlayCount = todaySongCount,
        listeningDurationMs = 0L
    ),
    val total: ListeningPeriodStats = ListeningPeriodStats(
        effectivePlayCount = todaySongCount,
        listeningDurationMs = totalListeningDurationMs
    ),
    val todaySources: List<ListeningSourceStats> = emptyList(),
    val totalSources: List<ListeningSourceStats> = emptyList(),
    val todaySongs: List<ListeningSongStats> = emptyList(),
    val totalSongs: List<ListeningSongStats> = emptyList(),
    val detailedStatsStartedAtMillis: Long = 0L,
    val includesLegacyAggregateData: Boolean = false
)

data class ListeningPeriodStats(
    val effectivePlayCount: Int = 0,
    val distinctSongCount: Int = 0,
    val listeningDurationMs: Long = 0L,
    val topSource: ListeningSourceStats? = null
)

data class ListeningSongStats(
    val songKey: String,
    val songId: Long,
    val title: String,
    val artist: String,
    val artworkUri: String? = null,
    val effectivePlayCount: Int = 0,
    val listeningDurationMs: Long = 0L
)

data class ListeningSourceStats(
    val sourceKey: String,
    val sourceType: PlaybackSourceType,
    val sourceId: String? = null,
    val displayName: String,
    val effectivePlayCount: Int = 0,
    val listeningDurationMs: Long = 0L
) {
    val source: PlaybackSource
        get() = PlaybackSource(
            type = sourceType,
            key = sourceKey,
            sourceId = sourceId,
            displayName = displayName
        )
}
