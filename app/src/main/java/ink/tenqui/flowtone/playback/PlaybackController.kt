package ink.tenqui.flowtone.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import ink.tenqui.flowtone.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackSnapshot(
    val currentMediaItem: MediaItem?,
    val currentMediaItemIndex: Int,
    val mediaItemCount: Int,
    val queueMediaItems: List<MediaItem>,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val playbackOrderMode: PlaybackOrderMode
)

class PlaybackController(
    context: Context,
    initialPlaybackOrderMode: PlaybackOrderMode = PlaybackOrderMode.Sequence,
    private val onPlaybackEnded: () -> Unit,
    private val onMediaItemChanged: (String) -> Unit = {}
) {
    private val mediaControllerConnection = FlowtoneMediaControllerConnection(context.applicationContext)
    private val _playbackState = MutableStateFlow(
        PlaybackState(playbackOrderMode = initialPlaybackOrderMode)
    )
    private var pendingSong: Song? = null
    private var pendingQueueSongs: List<Song>? = null
    private var pendingQueueStartIndex: Int? = null
    private var pendingPlaybackOrderMode: PlaybackOrderMode? = initialPlaybackOrderMode
    private var isReleased = false

    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    val isConnected: StateFlow<Boolean> = mediaControllerConnection.isConnected

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update {
                it.copy(isPlaying = isPlaying)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    errorMessage = error.message ?: "\u64ad\u653e\u5931\u8d25"
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _playbackState.update {
                    it.copy(isPlaying = false)
                }
                onPlaybackEnded()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId
            if (!mediaId.isNullOrBlank()) {
                onMediaItemChanged(mediaId)
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            syncPlaybackOrderMode()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            syncPlaybackOrderMode()
        }
    }

    init {
        mediaControllerConnection.connect(
            onConnected = { controller ->
                if (isReleased) {
                    return@connect
                }

                controller.addListener(listener)
                pendingPlaybackOrderMode?.let { mode ->
                    pendingPlaybackOrderMode = null
                    applyPlaybackOrderMode(controller, mode)
                }
                syncPlaybackOrderMode(controller)
                val queuedSongs = pendingQueueSongs
                val queuedStartIndex = pendingQueueStartIndex
                if (queuedSongs != null && queuedStartIndex != null) {
                    pendingQueueSongs = null
                    pendingQueueStartIndex = null
                    playQueue(queuedSongs, queuedStartIndex)
                    return@connect
                }

                pendingSong?.let { song ->
                    pendingSong = null
                    play(song)
                }
            },
            onConnectionFailed = { error ->
                if (isReleased) {
                    return@connect
                }

                if (pendingSong != null) {
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            errorMessage = error.message ?: "\u64ad\u653e\u5668\u8fde\u63a5\u5931\u8d25"
                        )
                    }
                }
            }
        )
    }

    fun play(song: Song) {
        val controller = mediaControllerConnection.currentController
        if (controller == null) {
            pendingSong = song
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = song.durationMs.coerceAtLeast(0L),
                    errorMessage = null
                )
            }
            return
        }

        runCatching {
            val mediaItem = song.toMediaItem()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = song.durationMs.coerceAtLeast(0L),
                    errorMessage = null
                )
            }
        }.onFailure { error ->
            _playbackState.update {
                it.copy(
                    currentSong = song,
                    isPlaying = false,
                    durationMs = song.durationMs.coerceAtLeast(0L),
                    errorMessage = error.message ?: "\u64ad\u653e\u5931\u8d25"
                )
            }
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) {
            return
        }

        val controller = mediaControllerConnection.currentController
        if (controller == null) {
            pendingQueueSongs = songs
            pendingQueueStartIndex = startIndex
            return
        }

        val mediaItems = songs.map { it.toMediaItem() }
        val startSong = songs[startIndex]

        runCatching {
            controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            controller.prepare()
            controller.play()
            _playbackState.update {
                it.copy(
                    currentSong = startSong,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = startSong.durationMs.coerceAtLeast(0L),
                    errorMessage = null
                )
            }
        }.onFailure { error ->
            _playbackState.update {
                it.copy(
                    currentSong = startSong,
                    isPlaying = false,
                    durationMs = startSong.durationMs.coerceAtLeast(0L),
                    errorMessage = error.message ?: "\u64ad\u653e\u5931\u8d25"
                )
            }
        }
    }

    fun updateCurrentSong(song: Song) {
        _playbackState.update {
            it.copy(
                currentSong = song,
                positionMs = 0L,
                durationMs = song.durationMs.coerceAtLeast(0L)
            )
        }
    }

    fun updateProgress(positionMs: Long, durationMs: Long) {
        _playbackState.update {
            it.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L)
            )
        }
    }

    fun updatePlaybackOrderMode(mode: PlaybackOrderMode) {
        _playbackState.update {
            it.copy(playbackOrderMode = mode)
        }
    }

    fun updateFromSnapshot(
        currentSong: Song,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        playbackOrderMode: PlaybackOrderMode = getPlaybackOrderMode()
    ) {
        _playbackState.update {
            it.copy(
                currentSong = currentSong,
                isPlaying = isPlaying,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                playbackOrderMode = playbackOrderMode,
                errorMessage = null
            )
        }
    }

    fun getCurrentPositionMs(): Long {
        val position = mediaControllerConnection.currentController?.currentPosition ?: 0L
        return position.coerceAtLeast(0L)
    }

    fun getDurationMs(): Long {
        val duration = mediaControllerConnection.currentController?.duration ?: 0L
        return safeDuration(duration)
    }

    fun seekTo(positionMs: Long) {
        mediaControllerConnection.currentController?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun getPlaybackSnapshot(): PlaybackSnapshot? {
        val controller = mediaControllerConnection.currentController ?: return null
        val mediaItemCount = controller.mediaItemCount
        val queueMediaItems = (0 until mediaItemCount).mapNotNull { index ->
            runCatching { controller.getMediaItemAt(index) }.getOrNull()
        }

        return PlaybackSnapshot(
            currentMediaItem = controller.currentMediaItem,
            currentMediaItemIndex = controller.currentMediaItemIndex,
            mediaItemCount = mediaItemCount,
            queueMediaItems = queueMediaItems,
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = safeDuration(controller.duration),
            playbackOrderMode = playbackOrderModeFromController(controller)
        )
    }

    fun playNext(playWhenReady: Boolean = false): Boolean {
        val controller = mediaControllerConnection.currentController ?: return false
        return if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            if (playWhenReady) {
                controller.play()
                _playbackState.update {
                    it.copy(
                        isPlaying = true,
                        errorMessage = null
                    )
                }
            }
            true
        } else {
            false
        }
    }

    fun playPrevious(playWhenReady: Boolean = false): Boolean {
        val controller = mediaControllerConnection.currentController ?: return false
        return if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            if (playWhenReady) {
                controller.play()
                _playbackState.update {
                    it.copy(
                        isPlaying = true,
                        errorMessage = null
                    )
                }
            }
            true
        } else {
            false
        }
    }

    fun getPlaybackOrderMode(): PlaybackOrderMode {
        val controller = mediaControllerConnection.currentController
        pendingPlaybackOrderMode?.let { pendingMode ->
            if (controller != null) {
                pendingPlaybackOrderMode = null
                applyPlaybackOrderMode(controller, pendingMode)
                return pendingMode
            }
            return pendingMode
        }

        return controller?.let(::playbackOrderModeFromController)
            ?: playbackState.value.playbackOrderMode
    }

    fun setPlaybackOrderMode(mode: PlaybackOrderMode) {
        val controller = mediaControllerConnection.currentController
        if (controller == null) {
            pendingPlaybackOrderMode = mode
            updatePlaybackOrderMode(mode)
            return
        }

        pendingPlaybackOrderMode = null
        applyPlaybackOrderMode(controller, mode)
        updatePlaybackOrderMode(mode)
    }

    fun togglePlaybackOrderMode() {
        setPlaybackOrderMode(nextPlaybackOrderMode(getPlaybackOrderMode()))
    }

    fun play() {
        val controller = mediaControllerConnection.currentController ?: return
        controller.play()
        _playbackState.update {
            it.copy(
                isPlaying = true,
                errorMessage = null
            )
        }
    }

    fun pause() {
        val controller = mediaControllerConnection.currentController ?: return
        controller.pause()
        _playbackState.update {
            it.copy(isPlaying = false)
        }
    }

    fun togglePlayPause() {
        val controller = mediaControllerConnection.currentController
        val isPlaying = controller?.isPlaying ?: playbackState.value.isPlaying
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun release() {
        if (isReleased) {
            return
        }

        isReleased = true
        pendingSong = null
        pendingQueueSongs = null
        pendingQueueStartIndex = null
        pendingPlaybackOrderMode = null
        mediaControllerConnection.currentController?.removeListener(listener)
        mediaControllerConnection.release()
    }

    private fun safeDuration(durationMs: Long): Long {
        return if (durationMs == C.TIME_UNSET || durationMs < 0L) {
            0L
        } else {
            durationMs
        }
    }

    private fun syncPlaybackOrderMode(
        controller: MediaController? = mediaControllerConnection.currentController
    ) {
        controller ?: return
        updatePlaybackOrderMode(playbackOrderModeFromController(controller))
    }

    private fun applyPlaybackOrderMode(controller: MediaController, mode: PlaybackOrderMode) {
        when (mode) {
            PlaybackOrderMode.Sequence -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }

            PlaybackOrderMode.RepeatOne -> {
                controller.repeatMode = Player.REPEAT_MODE_ONE
                controller.shuffleModeEnabled = false
            }

            PlaybackOrderMode.Shuffle -> {
                controller.shuffleModeEnabled = true
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun playbackOrderModeFromController(controller: MediaController): PlaybackOrderMode {
        return when {
            controller.repeatMode == Player.REPEAT_MODE_ONE -> PlaybackOrderMode.RepeatOne
            controller.shuffleModeEnabled -> PlaybackOrderMode.Shuffle
            else -> PlaybackOrderMode.Sequence
        }
    }

    private fun nextPlaybackOrderMode(mode: PlaybackOrderMode): PlaybackOrderMode {
        return when (mode) {
            PlaybackOrderMode.Sequence -> PlaybackOrderMode.RepeatOne
            PlaybackOrderMode.RepeatOne -> PlaybackOrderMode.Shuffle
            PlaybackOrderMode.Shuffle -> PlaybackOrderMode.Sequence
        }
    }
}
