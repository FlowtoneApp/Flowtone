package ink.tenqui.flowtone.data.listening

import android.content.Context
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.ListeningStatsStore
import ink.tenqui.flowtone.playback.PlaybackSource
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ListeningStatsRepository(
    private val localStore: ListeningStatsStore,
    private val cloudBackup: ListeningStatsCloudBackup = NoopListeningStatsCloudBackup
) {
    private val _stats = MutableStateFlow(localStore.getSnapshot())

    val stats: StateFlow<ListeningStatsSnapshot> = _stats.asStateFlow()

    fun getStats(): ListeningStatsSnapshot {
        return localStore.getSnapshot().also { snapshot ->
            _stats.value = snapshot
        }
    }

    fun recordSongPlayed(
        song: Song,
        source: PlaybackSource = PlaybackSource.Unknown,
        today: LocalDate = LocalDate.now()
    ): ListeningStatsSnapshot {
        return localStore.recordSongPlayed(song, source, today).also { snapshot ->
            _stats.value = snapshot
        }
    }

    fun recordEffectivePlay(
        song: Song,
        source: PlaybackSource = PlaybackSource.Unknown,
        today: LocalDate = LocalDate.now(),
        includeTotal: Boolean,
        includeToday: Boolean
    ): ListeningStatsSnapshot {
        return localStore.recordEffectivePlay(
            song = song,
            source = source,
            today = today,
            includeTotal = includeTotal,
            includeToday = includeToday
        ).also { snapshot ->
            _stats.value = snapshot
        }
    }

    fun addListeningDuration(
        song: Song,
        source: PlaybackSource = PlaybackSource.Unknown,
        durationMs: Long,
        today: LocalDate = LocalDate.now()
    ): ListeningStatsSnapshot {
        return localStore.addListeningDuration(
            song = song,
            source = source,
            durationMs = durationMs,
            today = today
        ).also { snapshot ->
            _stats.value = snapshot
        }
    }

    fun addListeningDuration(durationMs: Long): ListeningStatsSnapshot {
        return localStore.addListeningDuration(durationMs).also { snapshot ->
            _stats.value = snapshot
        }
    }

    fun replaceLocalStats(snapshot: ListeningStatsSnapshot): ListeningStatsSnapshot {
        return localStore.replaceWith(snapshot).also { updatedSnapshot ->
            _stats.value = updatedSnapshot
        }
    }

    suspend fun backupToCloud(): Result<Unit> {
        return cloudBackup.push(localStore.getSnapshot())
    }

    suspend fun restoreFromCloud(): Result<ListeningStatsSnapshot?> {
        return cloudBackup.pull().fold(
            onSuccess = { snapshot ->
                Result.success(snapshot?.let(localStore::replaceWith))
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }
}

object ListeningStatsRepositoryProvider {
    @Volatile
    private var repository: ListeningStatsRepository? = null

    fun get(context: Context): ListeningStatsRepository {
        return repository ?: synchronized(this) {
            repository ?: ListeningStatsRepository(
                localStore = ListeningStatsStore(context.applicationContext)
            ).also { createdRepository ->
                repository = createdRepository
            }
        }
    }
}
