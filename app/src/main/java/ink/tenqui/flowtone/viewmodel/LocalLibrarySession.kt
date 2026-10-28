package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.core.model.LocalAlbum
import ink.tenqui.flowtone.core.model.localAlbumsFrom
import ink.tenqui.flowtone.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope

internal data class LocalLibraryState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val albums: List<LocalAlbum> = emptyList(),
    val errorMessage: String? = null,
    val hasScanned: Boolean = false
)

/** 扫描及完成回调共用互斥锁，保证索引和队列更新完成后才开始下一轮扫描。 */
internal class LocalLibrarySession(
    private val scope: CoroutineScope,
    private val loadSongs: () -> List<Song>,
    private val onSongsDiscovered: (List<Song>, Boolean) -> Unit,
    private val onScanCompleted: suspend (List<Song>, List<LocalAlbum>, Boolean) -> Unit
) {
    private val _state = MutableStateFlow(LocalLibraryState())
    val state: StateFlow<LocalLibraryState> = _state.asStateFlow()
    private val localScanMutex = Mutex()

    fun setPermissionStatus(hasPermission: Boolean) {
        _state.update {
            it.copy(
                hasPermission = hasPermission,
                errorMessage = null
            )
        }
    }

    fun scanSongs() {
        if (!_state.value.hasPermission || _state.value.isLoading) {
            return
        }

        scope.launch { performLocalScan(preservePlayback = false) }
    }

    suspend fun refresh(): Boolean {
        _state.first { !it.isLoading }
        return _state.value.hasPermission && performLocalScan(preservePlayback = true)
    }

    private suspend fun performLocalScan(preservePlayback: Boolean): Boolean = localScanMutex.withLock {
            _state.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val songs = loadSongs()
                    songs to localAlbumsFrom(songs)
                }
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }

            _state.update { currentState ->
                result.fold(
                    onSuccess = { (songs, albums) ->
                        onSongsDiscovered(songs, preservePlayback)
                        currentState.copy(
                            isLoading = false,
                            songs = songs,
                            albums = albums,
                            errorMessage = null,
                            hasScanned = true
                        )
                    },
                    onFailure = { error ->
                        currentState.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "\u626b\u63cf\u672c\u5730\u97f3\u4e50\u5931\u8d25",
                            hasScanned = true
                        )
                    }
                )
            }

            if (result.isSuccess) {
                val (songs, albums) = result.getOrThrow()
                onScanCompleted(songs, albums, preservePlayback)
            }
            result.isSuccess
    }
}
