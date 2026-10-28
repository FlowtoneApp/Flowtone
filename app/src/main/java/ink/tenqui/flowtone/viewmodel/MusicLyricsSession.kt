package ink.tenqui.flowtone.viewmodel

import android.net.Uri
import android.util.Log
import ink.tenqui.flowtone.lyrics.LocalLyricsRepository
import ink.tenqui.flowtone.lyrics.LyricsLoadResult
import ink.tenqui.flowtone.lyrics.LyricsLoadSource
import ink.tenqui.flowtone.lyrics.LyricsPreloadScheduler
import ink.tenqui.flowtone.lyrics.LyricsState
import ink.tenqui.flowtone.lyrics.SongLyricsState
import ink.tenqui.flowtone.lyrics.LyricsFolder
import ink.tenqui.flowtone.playback.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/** 前台歌词、目录变更和歌词预载共享同一个 Repository 生命周期。 */
internal class MusicLyricsSession(
    private val scope: CoroutineScope,
    private val localLyricsRepository: LocalLyricsRepository,
    private val playbackState: StateFlow<PlaybackState>,
    private val onDirectoriesChanged: () -> Unit
) {
    private val _lyricsState = MutableStateFlow<LyricsState>(LyricsState.Idle)
    private val _songLyricsState = MutableStateFlow(SongLyricsState())
    private val _lyricsFolders = MutableStateFlow(localLyricsRepository.getLyricsFolders())
    private val lyricsReloadVersion = MutableStateFlow(0)
    val preloadScheduler = LyricsPreloadScheduler(scope, localLyricsRepository)

    val lyricsState: StateFlow<LyricsState> = _lyricsState.asStateFlow()
    val songLyricsState: StateFlow<SongLyricsState> = _songLyricsState.asStateFlow()
    val folders: StateFlow<List<LyricsFolder>> = _lyricsFolders.asStateFlow()

    fun start() {
        observeLyrics()
    }

    fun setLyricsDirectory(treeUri: Uri) {
        addLyricsFolder(treeUri)
    }

    fun addLyricsFolder(treeUri: Uri): Boolean {
        val added = localLyricsRepository.addLyricsDirectory(treeUri)
        if (!added) {
            return false
        }
        preloadScheduler.clear()
        _lyricsFolders.value = localLyricsRepository.getLyricsFolders()
        lyricsReloadVersion.update { it + 1 }
        onDirectoriesChanged()
        return true
    }

    fun removeLyricsFolder(treeUri: Uri) {
        preloadScheduler.clear()
        localLyricsRepository.removeLyricsDirectory(treeUri)
        _lyricsFolders.value = localLyricsRepository.getLyricsFolders()
        lyricsReloadVersion.update { it + 1 }
        onDirectoriesChanged()
    }

    fun refreshLyricsFolders() {
        _lyricsFolders.value = localLyricsRepository.getLyricsFolders()
    }

    private fun observeLyrics() {
        scope.launch {
            combine(
                playbackState.map { it.currentSong }.distinctUntilChanged(),
                lyricsReloadVersion
            ) { song, version -> song to version }
                .collectLatest { song ->
                    val currentSong = song.first
                    if (currentSong == null) {
                        publishLyricsState(songId = null, state = LyricsState.Idle)
                        return@collectLatest
                    }

                    val request = localLyricsRepository.request(currentSong)
                    Log.d(
                        "Lyrics",
                        "foreground request song=${currentSong.id} source=${request.source}"
                    )
                    publishLyricsState(
                        songId = currentSong.id,
                        state = if (request.source == LyricsLoadSource.NewRead) {
                            LyricsState.Loading
                        } else {
                            LyricsState.Idle
                        }
                    )
                    val result = try {
                        request.deferred.await()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        LyricsLoadResult.Failed(error)
                    }

                    publishLyricsState(
                        songId = currentSong.id,
                        state = when (result) {
                            is LyricsLoadResult.Found -> LyricsState.Available(result.lines)
                            LyricsLoadResult.DirectoryNotSelected ->
                                LyricsState.DirectoryNotSelected
                            LyricsLoadResult.DirectoryPermissionLost ->
                                LyricsState.DirectoryPermissionLost
                            LyricsLoadResult.OutsideSelectedDirectory ->
                                LyricsState.OutsideSelectedDirectory
                            LyricsLoadResult.NotFound -> LyricsState.NotFound
                            is LyricsLoadResult.Failed -> {
                                Log.d(
                                    "Lyrics",
                                    "failed=${result.throwable::class.simpleName}"
                                )
                                LyricsState.Error(result.throwable.message)
                            }
                        }
                    )
                }
        }
    }

    private fun publishLyricsState(songId: Long?, state: LyricsState) {
        _songLyricsState.value = SongLyricsState(songId = songId, state = state)
        _lyricsState.value = state
    }

    fun close() {
        preloadScheduler.clear()
        localLyricsRepository.close()
    }
}
