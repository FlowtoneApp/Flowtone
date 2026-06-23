package ink.tenqui.flowtone.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import ink.tenqui.flowtone.core.model.Song
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
    private var pendingPlaybackRequest: PendingPlaybackRequest? = null
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
                playPendingRequest()
            },
            onConnectionFailed = { error ->
                if (isReleased) {
                    return@connect
                }

                if (pendingPlaybackRequest is PendingPlaybackRequest.SingleSong) {
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
        val controller = currentControllerOrNull()
        if (controller == null) {
            setPendingSingleSong(song)
            return
        }

        runCatching {
            val mediaItem = song.toMediaItem()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            updatePlaybackStarted(song)
        }.onFailure { error ->
            updatePlaybackFailed(song, error)
        }
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) {
            return
        }

        val controller = currentControllerOrNull()
        if (controller == null) {
            pendingPlaybackRequest = PendingPlaybackRequest.Queue(songs, startIndex)
            return
        }

        val mediaItems = songs.map { it.toMediaItem() }
        val startSong = songs[startIndex]

        runCatching {
            controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            controller.prepare()
            controller.play()
            updatePlaybackStarted(startSong)
        }.onFailure { error ->
            updatePlaybackFailed(startSong, error)
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
        val position = currentControllerOrNull()?.currentPosition ?: 0L
        return position.coerceAtLeast(0L)
    }

    fun getDurationMs(): Long {
        val duration = currentControllerOrNull()?.duration ?: 0L
        return safeDuration(duration)
    }

    fun seekTo(positionMs: Long) {
        currentControllerOrNull()?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun getPlaybackSnapshot(): PlaybackSnapshot? {
        val controller = currentControllerOrNull() ?: return null
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
        return skipToNext(playWhenReady)
    }

    fun skipToNext(playWhenReady: Boolean = false): Boolean {
        val controller = currentControllerOrNull() ?: return false
        return if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            if (playWhenReady) {
                resume()
            }
            true
        } else {
            false
        }
    }

    fun playPrevious(playWhenReady: Boolean = false): Boolean {
        return skipToPrevious(playWhenReady)
    }

    fun skipToPrevious(playWhenReady: Boolean = false): Boolean {
        val controller = currentControllerOrNull() ?: return false
        return if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            if (playWhenReady) {
                resume()
            }
            true
        } else {
            false
        }
    }

    fun getPlaybackOrderMode(): PlaybackOrderMode {
        val controller = currentControllerOrNull()
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
        val controller = currentControllerOrNull()
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
        resume()
    }

    fun resume() {
        val controller = currentControllerOrNull() ?: return
        controller.play()
        _playbackState.update {
            it.copy(
                isPlaying = true,
                errorMessage = null
            )
        }
    }

    fun pause() {
        val controller = currentControllerOrNull() ?: return
        controller.pause()
        _playbackState.update {
            it.copy(isPlaying = false)
        }
    }

    fun togglePlayPause() {
        val controller = currentControllerOrNull()
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
        pendingPlaybackRequest = null
        pendingPlaybackOrderMode = null
        currentControllerOrNull()?.removeListener(listener)
        mediaControllerConnection.release()
    }

    private fun currentControllerOrNull(): MediaController? {
        return mediaControllerConnection.currentController
    }

    private fun setPendingSingleSong(song: Song) {
        pendingPlaybackRequest = PendingPlaybackRequest.SingleSong(song)
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = false,
                positionMs = 0L,
                durationMs = song.durationMs.coerceAtLeast(0L),
                errorMessage = null
            )
        }
    }

    private fun playPendingRequest() {
        when (val request = pendingPlaybackRequest) {
            is PendingPlaybackRequest.Queue -> {
                pendingPlaybackRequest = null
                playQueue(request.songs, request.startIndex)
            }

            is PendingPlaybackRequest.SingleSong -> {
                pendingPlaybackRequest = null
                play(request.song)
            }

            null -> Unit
        }
    }

    private fun updatePlaybackStarted(song: Song) {
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = true,
                positionMs = 0L,
                durationMs = song.durationMs.coerceAtLeast(0L),
                errorMessage = null
            )
        }
    }

    private fun updatePlaybackFailed(song: Song, error: Throwable) {
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = false,
                durationMs = song.durationMs.coerceAtLeast(0L),
                errorMessage = error.message ?: "\u64ad\u653e\u5931\u8d25"
            )
        }
    }

    private fun safeDuration(durationMs: Long): Long {
        return if (durationMs == C.TIME_UNSET || durationMs < 0L) {
            0L
        } else {
            durationMs
        }
    }

    private fun syncPlaybackOrderMode(
        controller: MediaController? = currentControllerOrNull()
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

    private sealed interface PendingPlaybackRequest {
        data class SingleSong(val song: Song) : PendingPlaybackRequest
        data class Queue(val songs: List<Song>, val startIndex: Int) : PendingPlaybackRequest
    }
}
