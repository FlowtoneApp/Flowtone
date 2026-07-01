package ink.tenqui.flowtone.data.listening

enum class ListeningStatsCloudProvider {
    OneDrive,
    GoogleDrive
}

interface ListeningStatsCloudBackup {
    suspend fun push(snapshot: ListeningStatsSnapshot): Result<Unit>

    suspend fun pull(): Result<ListeningStatsSnapshot?>
}

object NoopListeningStatsCloudBackup : ListeningStatsCloudBackup {
    override suspend fun push(snapshot: ListeningStatsSnapshot): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun pull(): Result<ListeningStatsSnapshot?> {
        return Result.success(null)
    }
}
