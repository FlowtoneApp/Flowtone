package ink.tenqui.flowtone.data.listening

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.ListeningStatsStore

class ListeningStatsRepository(
    private val localStore: ListeningStatsStore,
    private val cloudBackup: ListeningStatsCloudBackup = NoopListeningStatsCloudBackup
) {
    fun getStats(): ListeningStatsSnapshot {
        return localStore.getSnapshot()
    }

    fun recordSongPlayed(song: Song): ListeningStatsSnapshot {
        return localStore.recordSongPlayed(song)
    }

    fun addListeningDuration(durationMs: Long): ListeningStatsSnapshot {
        return localStore.addListeningDuration(durationMs)
    }

    fun replaceLocalStats(snapshot: ListeningStatsSnapshot): ListeningStatsSnapshot {
        return localStore.replaceWith(snapshot)
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
