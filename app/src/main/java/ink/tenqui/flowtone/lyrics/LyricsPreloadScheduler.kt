package ink.tenqui.flowtone.lyrics

import android.util.Log
import ink.tenqui.flowtone.core.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LyricsPreloadScheduler(
    private val scope: CoroutineScope,
    private val repository: LocalLyricsRepository,
    maxConcurrentRequests: Int = 2
) {
    private val semaphore = Semaphore(maxConcurrentRequests)
    private val jobs = mutableMapOf<String, Job>()
    private val completedUris = mutableSetOf<String>()
    private var desiredPreloadUris: Set<String> = emptySet()
    private var generation = 0L

    fun update(
        currentIndex: Int,
        currentSong: Song?,
        songsInPlaybackOrder: List<Song>
    ) {
        val distinctSongs = songsInPlaybackOrder.distinctBy { it.uri.toString() }
        val nextDesiredUris = distinctSongs.mapTo(linkedSetOf()) { it.uri.toString() }
        if (nextDesiredUris == desiredPreloadUris) {
            return
        }

        generation += 1
        val currentGeneration = generation
        val previousDesiredUris = desiredPreloadUris
        desiredPreloadUris = nextDesiredUris
        completedUris.retainAll(nextDesiredUris)

        var cancelledCount = 0
        jobs.keys.filter { it !in nextDesiredUris }.forEach { uri ->
            jobs.remove(uri)?.cancel()
            repository.cancelPreload(uri)
            cancelledCount += 1
        }

        repository.updatePreloadWindow(
            generation = currentGeneration,
            currentSong = currentSong,
            preloadSongs = distinctSongs
        )

        var retainedCount = 0
        var startedCount = 0
        distinctSongs.forEach { song ->
            val uri = song.uri.toString()
            if (
                uri in completedUris ||
                repository.getCachedLyrics(song) != null ||
                jobs[uri]?.isActive == true
            ) {
                retainedCount += 1
                return@forEach
            }

            val job = scope.launch {
                semaphore.withPermit {
                    repository.preload(song)
                }
            }
            jobs[uri] = job
            startedCount += 1
            job.invokeOnCompletion { cause ->
                if (cause is CancellationException) {
                    repository.cancelPreload(uri)
                }
                scope.launch {
                    if (jobs[uri] === job) {
                        jobs.remove(uri)
                    }
                    if (cause == null && uri in desiredPreloadUris) {
                        completedUris.add(uri)
                    }
                }
            }
        }

        Log.d(
            TAG,
            "window changed currentIndex=$currentIndex generation=$currentGeneration " +
                "targets=${nextDesiredUris.size} started=$startedCount " +
                "retained=$retainedCount cancelled=$cancelledCount " +
                "removed=${(previousDesiredUris - nextDesiredUris).size}"
        )
    }

    fun clear() {
        generation += 1
        desiredPreloadUris = emptySet()
        completedUris.clear()
        jobs.values.forEach(Job::cancel)
        jobs.clear()
        repository.updatePreloadWindow(
            generation = generation,
            currentSong = null,
            preloadSongs = emptyList()
        )
    }

    private companion object {
        const val TAG = "LyricsPreload"
    }
}
