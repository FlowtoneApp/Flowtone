package ink.tenqui.flowtone.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.listening.ListeningStatsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ListeningStatsTracker(
    private val repository: ListeningStatsRepository,
    private val scope: CoroutineScope,
    private val thresholdMsProvider: () -> Long,
    private val elapsedRealtimeMs: () -> Long,
    private val currentTimeMillis: () -> Long,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    private var player: Player? = null
    private var tickerJob: Job? = null
    private var activeSession: ActiveListeningSession? = null
    private var lastTickElapsedMs: Long? = null
    private var lastTickWallMs: Long? = null
    private var lastPlaybackPositionMs: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateFromPlayer()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                flushPlayingDuration()
            }
            updateFromPlayer()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            flushPlayingDuration()
            lastTickElapsedMs = null
            lastTickWallMs = null
        }
    }

    fun attach(player: Player) {
        detach()
        this.player = player
        player.addListener(playerListener)
        updateFromPlayer()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                updateFromPlayer()
            }
        }
    }

    fun detach() {
        flushPlayingDuration()
        tickerJob?.cancel()
        tickerJob = null
        player?.removeListener(playerListener)
        player = null
        activeSession = null
        lastTickElapsedMs = null
        lastTickWallMs = null
        lastPlaybackPositionMs = 0L
    }

    fun release() {
        detach()
    }

    private fun updateFromPlayer() {
        val currentPlayer = player ?: return
        val mediaItem = currentPlayer.currentMediaItem
        val currentSong = mediaItem?.toSongOrNull(emptyList())
        val currentSource = mediaItem.toPlaybackSource()
        val currentSongKey = currentSong?.sessionSongKey()
        val currentPositionMs = currentPlayer.currentPosition.coerceAtLeast(0L)
        val isPlaying = currentPlayer.isPlaying
        val currentSession = activeSession
        val isNewSong = currentSongKey != null && currentSession?.songKey != currentSongKey
        val restartedSameSong = currentSession?.songKey == currentSongKey &&
            lastPlaybackPositionMs > LISTENING_RESTART_PREVIOUS_POSITION_MS &&
            currentPositionMs <= LISTENING_RESTART_POSITION_MS

        if (currentSong == null) {
            flushPlayingDuration()
            activeSession = null
            lastPlaybackPositionMs = 0L
            return
        }

        if (currentSession == null || isNewSong || restartedSameSong) {
            flushPlayingDuration()
            activeSession = ActiveListeningSession(
                song = currentSong,
                source = currentSource,
                songKey = currentSong.sessionSongKey(),
                currentDayEpoch = currentLocalDate().toEpochDay()
            )
            lastPlaybackPositionMs = currentPositionMs
            if (isPlaying) {
                rememberTickAnchor()
            }
            return
        }

        activeSession = currentSession.copy(
            song = currentSong,
            source = currentSource
        )

        if (isPlaying) {
            flushPlayingDuration()
            rememberTickAnchor()
        } else {
            flushPlayingDuration()
        }
        lastPlaybackPositionMs = currentPositionMs
    }

    private fun flushPlayingDuration() {
        val session = activeSession ?: return
        val previousElapsedMs = lastTickElapsedMs ?: return
        val previousWallMs = lastTickWallMs ?: return
        val nowElapsedMs = elapsedRealtimeMs()
        val durationMs = nowElapsedMs - previousElapsedMs
        if (durationMs <= 0L) {
            lastTickElapsedMs = null
            lastTickWallMs = null
            return
        }

        val segmentStartWallMs = previousWallMs
        val segmentEndWallMs = previousWallMs + durationMs
        splitListeningDurationByLocalDate(
            startWallMs = segmentStartWallMs,
            endWallMs = segmentEndWallMs,
            zoneId = zoneIdProvider()
        ).forEach { segment ->
            recordDurationSegment(
                session = session,
                durationMs = segment.durationMs,
                day = segment.day
            )
        }

        lastTickElapsedMs = null
        lastTickWallMs = null
        activeSession = session
    }

    private fun recordDurationSegment(
        session: ActiveListeningSession,
        durationMs: Long,
        day: LocalDate
    ) {
        if (durationMs <= 0L) {
            return
        }

        val dayEpoch = day.toEpochDay()
        if (session.currentDayEpoch != dayEpoch) {
            session.currentDayEpoch = dayEpoch
            session.currentDayListeningDurationMs = 0L
            session.recordedTodayEpochDay = null
        }

        session.totalListeningDurationMs += durationMs
        session.currentDayListeningDurationMs += durationMs
        repository.addListeningDuration(
            song = session.song,
            source = session.source,
            durationMs = durationMs,
            today = day
        )

        val thresholdMs = thresholdMsProvider()
            .coerceIn(MIN_RECORD_THRESHOLD_MS, MAX_RECORD_THRESHOLD_MS)
        val shouldRecordTotal = !session.recordedTotal &&
            session.totalListeningDurationMs >= thresholdMs
        val shouldRecordToday = session.recordedTodayEpochDay != dayEpoch &&
            session.currentDayListeningDurationMs >= thresholdMs
        if (!shouldRecordTotal && !shouldRecordToday) {
            return
        }

        repository.recordEffectivePlay(
            song = session.song,
            source = session.source,
            today = day,
            includeTotal = shouldRecordTotal,
            includeToday = shouldRecordToday
        )
        if (shouldRecordTotal) {
            session.recordedTotal = true
        }
        if (shouldRecordToday) {
            session.recordedTodayEpochDay = dayEpoch
        }
    }

    private fun rememberTickAnchor() {
        lastTickElapsedMs = elapsedRealtimeMs()
        lastTickWallMs = currentTimeMillis()
    }

    private fun currentLocalDate(): LocalDate {
        return Instant.ofEpochMilli(currentTimeMillis())
            .atZone(zoneIdProvider())
            .toLocalDate()
    }

    private data class ActiveListeningSession(
        val song: Song,
        val source: PlaybackSource,
        val songKey: String,
        var totalListeningDurationMs: Long = 0L,
        var currentDayEpoch: Long,
        var currentDayListeningDurationMs: Long = 0L,
        var recordedTotal: Boolean = false,
        var recordedTodayEpochDay: Long? = null
    )

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
        const val LISTENING_RESTART_PREVIOUS_POSITION_MS = 5_000L
        const val LISTENING_RESTART_POSITION_MS = 1_500L
        const val MIN_RECORD_THRESHOLD_MS = 1_000L
        const val MAX_RECORD_THRESHOLD_MS = 60_000L
    }
}

private fun Song.sessionSongKey(): String {
    return if (id > 0L) {
        "${sourceType.name}:$id"
    } else {
        "${sourceType.name}:$uri"
    }
}
