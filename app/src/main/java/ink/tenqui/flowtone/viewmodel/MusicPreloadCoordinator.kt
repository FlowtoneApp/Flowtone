package ink.tenqui.flowtone.viewmodel

import android.util.Log
import coil3.request.ImageRequest
import ink.tenqui.flowtone.lyrics.LyricsPreloadScheduler
import ink.tenqui.flowtone.data.local.SongMetadataPreloader
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.content.Context
import kotlinx.coroutines.CoroutineScope

internal data class PlaybackPreloadSnapshot(
    val sourceSongs: List<Song>,
    val playbackSongs: List<Song>,
    val entries: List<QueueTrackEntry>,
    val currentIndex: Int
)

/** 窗口计算、元数据和封面任务共用同一快照；不会请求在线播放资源。 */
internal class MusicPreloadCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playbackController: PlaybackController,
    private val songMetadataPreloader: SongMetadataPreloader,
    private val lyricsPreloadScheduler: LyricsPreloadScheduler,
    private val extensionManager: ExtensionManager,
    private val snapshotProvider: () -> PlaybackPreloadSnapshot,
    private val onHydrated: (Map<String, ProviderSong>) -> Unit
) {
    private val playbackState = playbackController.playbackState
    private var preloadSongMetadataCount = 5
    private var preloadLyricsCount = 5
    private var preloadJob: Job? = null
    private var onlineArtworkPreloadJob: Job? = null
    private var onlinePreloadGeneration = 0L
    private val onlinePresentationCache = mutableMapOf<String, ProviderSong>()
    private var metadataPreloadUris: List<String> = emptyList()

    fun invalidateRuntime() {
        onlineArtworkPreloadJob?.cancel()
        onlinePreloadGeneration += 1L
        onlinePresentationCache.clear()
    }

    fun update() {
        val snapshot = snapshotProvider()
        val metadataSongs = songsInPlaybackPreloadWindow(snapshot, preloadSongMetadataCount)
        val nextMetadataPreloadUris = metadataSongs.map { it.uri.toString() }
        if (nextMetadataPreloadUris != metadataPreloadUris) {
            metadataPreloadUris = nextMetadataPreloadUris
            preloadJob?.cancel()
            preloadJob = scope.launch {
                songMetadataPreloader.preload(metadataSongs)
            }
        }

        val songsToPreload = songsInPlaybackPreloadWindow(snapshot, preloadLyricsCount)
        lyricsPreloadScheduler.update(
            currentIndex = playbackController.getCurrentMediaItemIndex() ?: snapshot.currentIndex,
            currentSong = playbackState.value.currentSong
                ?: snapshot.playbackSongs.getOrNull(snapshot.currentIndex),
            songsInPlaybackOrder = songsToPreload
        )
        scheduleOnlineArtworkPreload(snapshot)
        // Service owns online playback-resource/content preload from the logical next item.
    }

    private fun scheduleOnlineArtworkPreload(snapshot: PlaybackPreloadSnapshot) {
        onlineArtworkPreloadJob?.cancel()
        val generation = ++onlinePreloadGeneration
        val entries = queueItemsInPreloadWindow(
            items = snapshot.entries,
            currentIndex = snapshot.currentIndex,
            totalCount = preloadSongMetadataCount
        )
            .filter { it.persistentTrack is PersistentTrack.Online || it.runtimeProviderSong != null }
        Log.d(
            "FlowtonePlayback",
            "online.artwork.preload.window currentIndex=$snapshot.currentIndex count=$preloadSongMetadataCount entries=${entries.size}"
        )
        if (entries.isEmpty()) return
        onlineArtworkPreloadJob = scope.launch {
            val hydratedEntries = entries.mapNotNull { entry ->
                if (generation != onlinePreloadGeneration) {
                    Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=staleGeneration")
                    return@launch
                }
                val song = entry.runtimeProviderSong ?: hydrateOnlinePresentation(
                    entry.persistentTrack as? PersistentTrack.Online
                ).also { hydrated ->
                    if (hydrated == null) Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=hydrationFailed")
                }
                song?.let { entry to it }
            }
            if (generation != onlinePreloadGeneration) return@launch
            val hydratedByIdentity = hydratedEntries.associate { (entry, song) ->
                entry.stableIdentity to song
            }
            onHydrated(hydratedByIdentity)
            val songs = hydratedEntries.map { it.second }
            songs.forEach { song ->
                song.artwork?.let { image ->
                    Log.d("FlowtonePlayback", "online.artwork.preload.small identityHash=${song.trackRef.opaqueId.hashCode()}")
                    extensionManager.extensionImageLoader.execute(
                        ImageRequest.Builder(context).data(image).build()
                    )
                } ?: Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=noArtwork")
            }
            if (generation != onlinePreloadGeneration) return@launch
            songs.forEach { song ->
                song.largeArtwork?.let { image ->
                    Log.d("FlowtonePlayback", "online.artwork.preload.large identityHash=${song.trackRef.opaqueId.hashCode()}")
                    extensionManager.extensionImageLoader.execute(
                        ImageRequest.Builder(context).data(image).build()
                    )
                } ?: Log.d("FlowtonePlayback", "online.artwork.preload.skip reason=noLargeArtwork")
            }
        }
    }

    private suspend fun hydrateOnlinePresentation(track: PersistentTrack.Online?): ProviderSong? {
        track ?: return null
        onlinePresentationCache[track.identityKey]?.let { return it }
        Log.d("FlowtonePlayback", "online.hydration.started source=${track.sourceHost}")
        val result = extensionManager.resolvePersistentPlaylistSong(track)
        val song = (result as? ink.tenqui.flowtone.data.online.PersistentSongResolution.Resolved)?.song
        if (song != null) {
            onlinePresentationCache[track.identityKey] = song
            Log.d("FlowtonePlayback", "online.hydration.success source=${track.sourceHost}")
        } else {
            Log.d("FlowtonePlayback", "online.hydration.failed source=${track.sourceHost}")
        }
        return song
    }

    private fun upcomingSongsInPlaybackOrder(snapshot: PlaybackPreloadSnapshot, limit: Int): List<Song> {
        if (limit <= 0 || playbackState.value.playbackOrderMode == PlaybackOrderMode.RepeatOne) {
            return emptyList()
        }

        val mediaIds = playbackController.getUpcomingMediaIdsInPlaybackOrder(limit)
        if (mediaIds != null) {
            return mediaIds.mapNotNull { mediaId ->
                val songId = mediaId.toLongOrNull()
                snapshot.sourceSongs.firstOrNull { it.id == songId }
                    ?: snapshot.playbackSongs.firstOrNull { it.id == songId }
            }
        }

        val startIndex = snapshot.currentIndex + 1
        return if (startIndex in snapshot.playbackSongs.indices) {
            snapshot.playbackSongs.drop(startIndex).take(limit)
        } else {
            emptyList()
        }
    }

    private fun previousSongsInPlaybackOrder(snapshot: PlaybackPreloadSnapshot, limit: Int): List<Song> {
        if (limit <= 0 || playbackState.value.playbackOrderMode == PlaybackOrderMode.RepeatOne) {
            return emptyList()
        }

        val mediaIds = playbackController.getPreviousMediaIdsInPlaybackOrder(limit)
        if (mediaIds != null) {
            return mediaIds.mapNotNull { mediaId ->
                val songId = mediaId.toLongOrNull()
                snapshot.sourceSongs.firstOrNull { it.id == songId }
                    ?: snapshot.playbackSongs.firstOrNull { it.id == songId }
            }
        }

        return queueItemsBefore(snapshot.playbackSongs, snapshot.currentIndex, limit)
    }

    private fun songsInPlaybackPreloadWindow(snapshot: PlaybackPreloadSnapshot, totalCount: Int): List<Song> {
        val counts = preloadWindowCounts(totalCount)
        return (
            upcomingSongsInPlaybackOrder(snapshot, counts.next) +
                previousSongsInPlaybackOrder(snapshot, counts.previous)
            ).distinctBy { it.uri.toString() }
    }

    fun setPreloadSongMetadataCount(count: Int) {
        val allowedValues = listOf(1, 3, 5, 7, 10)
        val sanitizedCount = allowedValues.minBy { kotlin.math.abs(it - count) }
        if (preloadSongMetadataCount == sanitizedCount) {
            return
        }

        preloadSongMetadataCount = sanitizedCount
        update()
    }

    fun setPreloadLyricsCount(count: Int) {
        val allowedValues = listOf(1, 3, 5, 7, 10)
        val sanitizedCount = allowedValues.minBy { kotlin.math.abs(it - count) }
        if (preloadLyricsCount == sanitizedCount) {
            return
        }

        preloadLyricsCount = sanitizedCount
        update()
    }

    fun close() {
        preloadJob?.cancel()
        onlineArtworkPreloadJob?.cancel()
    }
}

internal data class PreloadWindowCounts(
    val previous: Int,
    val next: Int
)

internal fun preloadWindowCounts(totalCount: Int): PreloadWindowCounts {
    val safeTotalCount = totalCount.coerceAtLeast(0)
    val previousCount = (safeTotalCount * 0.2f).roundToInt()
    return PreloadWindowCounts(
        previous = previousCount,
        next = safeTotalCount - previousCount
    )
}

internal fun <T> queueItemsInPreloadWindow(
    items: List<T>,
    currentIndex: Int,
    totalCount: Int
): List<T> {
    val counts = preloadWindowCounts(totalCount)
    val nextItems = if (currentIndex + 1 in items.indices) {
        items.drop(currentIndex + 1).take(counts.next)
    } else {
        emptyList()
    }
    return nextItems + queueItemsBefore(items, currentIndex, counts.previous)
}

private fun <T> queueItemsBefore(
    items: List<T>,
    currentIndex: Int,
    limit: Int
): List<T> {
    if (limit <= 0 || currentIndex <= 0 || currentIndex > items.size) return emptyList()
    return (currentIndex - 1 downTo 0)
        .take(limit)
        .map(items::get)
}
