package ink.tenqui.flowtone.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.toPersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionImage
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
    val playWhenReady: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val durationMs: Long,
    val playbackOrderMode: PlaybackOrderMode,
    val logicalQueue: List<PlaybackQueueItem> = emptyList(),
    val sourceQueue: List<PlaybackQueueItem> = emptyList()
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
    private var pendingShuffleOrderIndices: IntArray? = null
    private var logicalPlaybackOrderMode: PlaybackOrderMode = initialPlaybackOrderMode
    private var playbackStateMediaId: String? = null
    private var isReleased = false
    private val setPlaybackOrderCommand = SessionCommand(
        ACTION_SET_PLAYBACK_ORDER,
        Bundle.EMPTY
    )

    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    val isConnected: StateFlow<Boolean> = mediaControllerConnection.isConnected

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update {
                it.copy(isPlaying = isPlaying)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _playbackState.update {
                it.copy(playWhenReady = playWhenReady)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    playWhenReady = false,
                    isBuffering = false,
                    errorMessage = error.message ?: "\u64ad\u653e\u5931\u8d25"
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ended = playbackState == Player.STATE_ENDED
            _playbackState.update {
                it.copy(
                    isPlaying = if (ended) false else it.isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING
                )
            }
            // Logical auto-advance is owned by SessionPlaybackPlayer, including online items.
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncLogicalQueueState()
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
            syncLogicalQueueState()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncLogicalQueueState()
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
                    val shuffleOrderIndices = pendingShuffleOrderIndices
                    pendingPlaybackOrderMode = null
                    pendingShuffleOrderIndices = null
                    applyPlaybackOrderMode(controller, mode, shuffleOrderIndices)
                }
                playPendingRequest()
                syncPlaybackOrderMode(controller)
                syncLogicalQueueState(controller)
            },
            onConnectionFailed = { error ->
                if (isReleased) {
                    return@connect
                }

                if (pendingPlaybackRequest is PendingPlaybackRequest.SingleSong) {
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            playWhenReady = false,
                            isBuffering = false,
                            errorMessage = error.message ?: "\u64ad\u653e\u5668\u8fde\u63a5\u5931\u8d25"
                        )
                    }
                }
            }
        )
    }

    fun play(
        song: Song,
        source: PlaybackSource = PlaybackSource.Unknown
    ) {
        val controller = currentControllerOrNull()
        if (controller == null) {
            setPendingSingleSong(song, source)
            return
        }

        runCatching {
            val mediaItem = song.toMediaItem(source)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            updatePlaybackStarted(song, mediaId = mediaItem.mediaId)
        }.onFailure { error ->
            updatePlaybackFailed(song, error)
        }
    }

    fun playQueue(
        songs: List<Song>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.Unknown
    ) {
        if (songs.isEmpty() || startIndex !in songs.indices) {
            return
        }

        val controller = currentControllerOrNull()
        if (controller == null) {
            pendingPlaybackRequest = PendingPlaybackRequest.Queue(songs, startIndex, source)
            return
        }

        playLogicalQueue(
            items = songs.mapIndexed { index, song ->
                PlaybackQueueItem.local(song, source, index)
            },
            startIndex = startIndex
        )
    }

    fun playLogicalQueue(
        items: List<PlaybackQueueItem>,
        startIndex: Int,
        playWhenReady: Boolean = true
    ) {
        if (items.isEmpty() || startIndex !in items.indices) return
        val controller = currentControllerOrNull()
        if (controller == null) {
            pendingPlaybackRequest = PendingPlaybackRequest.LogicalQueue(
                items,
                startIndex,
                playWhenReady
            )
            val target = items[startIndex]
            updateCurrentSong(
                song = target.presentation,
                extensionArtwork = target.extensionArtwork,
                extensionLargeArtwork = target.extensionLargeArtwork,
                persistentTrack = target.persistentTrack
            )
            _playbackState.update {
                it.copy(
                    sourceQueue = items.sortedBy(PlaybackQueueItem::sourceIndex),
                    playbackQueue = items,
                    currentQueueIndex = startIndex,
                    playWhenReady = playWhenReady,
                    isBuffering = target.isOnline
                )
            }
            return
        }
        val mediaItems = items.map(PlaybackQueueMediaItemCodec::encode)
        val target = items[startIndex]
        runCatching {
            controller.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            controller.prepare()
            if (playWhenReady) controller.play() else controller.pause()
            updatePlaybackStarted(
                song = target.presentation,
                extensionArtwork = target.extensionArtwork,
                extensionLargeArtwork = target.extensionLargeArtwork,
                persistentTrack = target.persistentTrack,
                mediaId = target.queueId,
                playWhenReady = playWhenReady
            )
            syncLogicalQueueState(controller)
        }.onFailure { updatePlaybackFailed(target.presentation, it) }
    }

    /** ViewModel 开始了更新的切歌请求时，丢弃尚未连接 Controller 的旧请求。 */
    fun clearPendingPlaybackRequest() {
        pendingPlaybackRequest = null
    }

    /** 由 Host 已解析好的受控媒体项仍进入同一 MediaController，不创建第二个播放器。 */
    fun playResolvedMediaItem(
        song: Song,
        mediaItem: MediaItem,
        extensionArtwork: ExtensionImage? = null,
        extensionLargeArtwork: ExtensionImage? = null,
        persistentTrack: PersistentTrack? = null,
        playWhenReady: Boolean = true
    ) {
        val controller = currentControllerOrNull()
        if (controller == null) {
            pendingPlaybackRequest = PendingPlaybackRequest.ResolvedMediaItem(
                song = song,
                mediaItem = mediaItem,
                extensionArtwork = extensionArtwork,
                extensionLargeArtwork = extensionLargeArtwork,
                persistentTrack = persistentTrack,
                playWhenReady = playWhenReady
            )
            updateCurrentSong(song, extensionArtwork, extensionLargeArtwork, persistentTrack)
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    playWhenReady = playWhenReady,
                    isBuffering = false
                )
            }
            return
        }
        runCatching {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            if (playWhenReady) {
                controller.play()
            } else {
                controller.pause()
            }
            updatePlaybackStarted(
                song = song,
                extensionArtwork = extensionArtwork,
                extensionLargeArtwork = extensionLargeArtwork,
                persistentTrack = persistentTrack,
                mediaId = mediaItem.mediaId,
                playWhenReady = playWhenReady
            )
        }.onFailure { error -> updatePlaybackFailed(song, error) }
    }

    fun addSongsNext(
        songs: List<Song>,
        source: PlaybackSource = PlaybackSource.Unknown
    ): Boolean {
        if (songs.isEmpty()) return false
        val controller = currentControllerOrNull() ?: return false
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return false
        return runCatching {
            controller.addMediaItems(
                currentIndex + 1,
                songs.mapIndexed { offset, song ->
                    PlaybackQueueMediaItemCodec.encode(
                        PlaybackQueueItem.local(
                            song = song,
                            source = source,
                            sourceIndex = controller.mediaItemCount + offset
                        )
                    )
                }
            )
            true
        }.getOrDefault(false)
    }

    fun appendSongsToQueue(
        songs: List<Song>,
        source: PlaybackSource = PlaybackSource.Unknown
    ): Boolean {
        if (songs.isEmpty()) return false
        val controller = currentControllerOrNull() ?: return false
        return runCatching {
            controller.addMediaItems(songs.mapIndexed { offset, song ->
                PlaybackQueueMediaItemCodec.encode(
                    PlaybackQueueItem.local(
                        song = song,
                        source = source,
                        sourceIndex = controller.mediaItemCount + offset
                    )
                )
            })
            true
        }.getOrDefault(false)
    }

    fun updateCurrentSong(
        song: Song,
        extensionArtwork: ExtensionImage? = null,
        extensionLargeArtwork: ExtensionImage? = null,
        persistentTrack: PersistentTrack? = null
    ) {
        playbackStateMediaId = currentControllerOrNull()?.currentMediaItem?.mediaId
        _playbackState.update {
            it.copy(
                currentSong = song,
                currentTrack = persistentTrack
                    ?: song.takeIf { it.sourceType == SourceType.Local }?.toPersistentTrack(),
                extensionArtwork = extensionArtwork,
                extensionLargeArtwork = extensionLargeArtwork,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = song.durationMs.coerceAtLeast(0L)
            )
        }
    }

    fun updateProgress(
        positionMs: Long,
        bufferedPositionMs: Long,
        durationMs: Long
    ) {
        _playbackState.update {
            it.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                bufferedPositionMs = bufferedPositionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L)
            )
        }
    }

    fun updatePlaybackOrderMode(mode: PlaybackOrderMode) {
        logicalPlaybackOrderMode = mode
        _playbackState.update {
            it.copy(playbackOrderMode = mode)
        }
    }

    fun updateFromSnapshot(
        currentSong: Song,
        isPlaying: Boolean,
        playWhenReady: Boolean,
        isBuffering: Boolean,
        positionMs: Long,
        bufferedPositionMs: Long,
        durationMs: Long,
        playbackOrderMode: PlaybackOrderMode = getPlaybackOrderMode()
    ) {
        playbackStateMediaId = currentControllerOrNull()?.currentMediaItem?.mediaId
        _playbackState.update {
            it.copy(
                currentSong = currentSong,
                isPlaying = isPlaying,
                playWhenReady = playWhenReady,
                isBuffering = isBuffering,
                positionMs = positionMs.coerceAtLeast(0L),
                bufferedPositionMs = bufferedPositionMs.coerceAtLeast(0L),
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

    fun getCurrentPositionSnapshot(): PlaybackPositionSnapshot {
        val controller = currentControllerOrNull()
            ?: return PlaybackPositionSnapshot()
        return PlaybackPositionSnapshot(
            mediaId = controller.currentMediaItem?.mediaId,
            positionMs = controller.currentPosition.coerceAtLeast(0L)
        )
    }

    fun getDurationMs(): Long {
        val duration = currentControllerOrNull()?.duration ?: 0L
        return safeDuration(duration)
    }

    fun getBufferedPositionMs(): Long {
        val controller = currentControllerOrNull() ?: return 0L
        val expectedMediaId = playbackStateMediaId ?: return 0L
        if (controller.currentMediaItem?.mediaId != expectedMediaId) {
            return 0L
        }
        return controller.bufferedPosition.coerceAtLeast(0L)
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
            playWhenReady = controller.playWhenReady,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
            durationMs = safeDuration(controller.duration),
            playbackOrderMode = playbackOrderModeFromController(controller),
            logicalQueue = queueMediaItems.mapNotNull(PlaybackQueueMediaItemCodec::decode),
            sourceQueue = queueMediaItems.mapNotNull(PlaybackQueueMediaItemCodec::decode)
                .sortedBy(PlaybackQueueItem::sourceIndex)
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
                val shuffleOrderIndices = pendingShuffleOrderIndices
                pendingPlaybackOrderMode = null
                pendingShuffleOrderIndices = null
                applyPlaybackOrderMode(controller, pendingMode, shuffleOrderIndices)
                return pendingMode
            }
            return pendingMode
        }

        return controller?.let(::playbackOrderModeFromController)
            ?: logicalPlaybackOrderMode
    }

    fun setPlaybackOrderMode(
        mode: PlaybackOrderMode,
        shuffleOrderIndices: IntArray? = null
    ) {
        val controller = currentControllerOrNull()
        if (controller == null) {
            pendingPlaybackOrderMode = mode
            pendingShuffleOrderIndices = shuffleOrderIndices
            updatePlaybackOrderMode(mode)
            return
        }

        pendingPlaybackOrderMode = null
        pendingShuffleOrderIndices = null
        applyPlaybackOrderMode(controller, mode, shuffleOrderIndices)
        updatePlaybackOrderMode(mode)
    }

    fun togglePlaybackOrderMode() {
        setPlaybackOrderMode(nextPlaybackOrderMode(getPlaybackOrderMode()))
    }

    fun play() {
        resume()
    }

    fun resume() {
        currentControllerOrNull()?.play()
        pendingPlaybackRequest = (pendingPlaybackRequest as? PendingPlaybackRequest.ResolvedMediaItem)
            ?.copy(playWhenReady = true)
            ?: pendingPlaybackRequest
        _playbackState.update {
            it.copy(
                playWhenReady = true,
                errorMessage = null
            )
        }
    }

    fun pause() {
        currentControllerOrNull()?.pause()
        pendingPlaybackRequest = (pendingPlaybackRequest as? PendingPlaybackRequest.ResolvedMediaItem)
            ?.copy(playWhenReady = false)
            ?: pendingPlaybackRequest
        _playbackState.update {
            it.copy(isPlaying = false, playWhenReady = false)
        }
    }

    fun getCurrentMediaItemIndex(): Int? {
        return currentControllerOrNull()?.currentMediaItemIndex
    }

    fun getUpcomingMediaIdsInPlaybackOrder(limit: Int): List<String>? {
        val controller = currentControllerOrNull() ?: return null
        if (limit <= 0 || controller.currentMediaItemIndex == C.INDEX_UNSET) {
            return emptyList()
        }

        val timeline = controller.currentTimeline
        val mediaIds = ArrayList<String>(limit)
        val visitedIndices = mutableSetOf(controller.currentMediaItemIndex)
        var index = controller.currentMediaItemIndex
        for (ignored in 0 until limit) {
            index = timeline.getNextWindowIndex(
                index,
                Player.REPEAT_MODE_OFF,
                controller.shuffleModeEnabled
            )
            if (index == C.INDEX_UNSET || !visitedIndices.add(index)) {
                break
            }
            controller.getMediaItemAt(index).mediaId
                .takeIf(String::isNotBlank)
                ?.let(mediaIds::add)
        }
        return mediaIds
    }

    fun getPreviousMediaIdsInPlaybackOrder(limit: Int): List<String>? {
        val controller = currentControllerOrNull() ?: return null
        if (limit <= 0 || controller.currentMediaItemIndex == C.INDEX_UNSET) {
            return emptyList()
        }

        val timeline = controller.currentTimeline
        val mediaIds = ArrayList<String>(limit)
        val visitedIndices = mutableSetOf(controller.currentMediaItemIndex)
        var index = controller.currentMediaItemIndex
        for (ignored in 0 until limit) {
            index = timeline.getPreviousWindowIndex(
                index,
                Player.REPEAT_MODE_OFF,
                controller.shuffleModeEnabled
            )
            if (index == C.INDEX_UNSET || !visitedIndices.add(index)) {
                break
            }
            controller.getMediaItemAt(index).mediaId
                .takeIf(String::isNotBlank)
                ?.let(mediaIds::add)
        }
        return mediaIds
    }

    fun clearPlayback() {
        currentControllerOrNull()?.apply {
            pause()
            clearMediaItems()
        }
        playbackStateMediaId = null
        _playbackState.update { currentState ->
            currentState.copy(
                currentSong = null,
                isPlaying = false,
                playWhenReady = false,
                isBuffering = false,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = 0L
            )
        }
    }

    fun togglePlayPause() {
        val controller = currentControllerOrNull()
        val playWhenReady = controller?.playWhenReady ?: playbackState.value.playWhenReady
        if (playWhenReady) {
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
        pendingShuffleOrderIndices = null
        currentControllerOrNull()?.removeListener(listener)
        mediaControllerConnection.release()
    }

    private fun currentControllerOrNull(): MediaController? {
        return mediaControllerConnection.currentController
    }

    private fun setPendingSingleSong(
        song: Song,
        source: PlaybackSource
    ) {
        pendingPlaybackRequest = PendingPlaybackRequest.SingleSong(song, source)
        playbackStateMediaId = null
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = false,
                playWhenReady = true,
                isBuffering = false,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = song.durationMs.coerceAtLeast(0L),
                errorMessage = null
            )
        }
    }

    private fun playPendingRequest() {
        when (val request = pendingPlaybackRequest) {
            is PendingPlaybackRequest.Queue -> {
                pendingPlaybackRequest = null
                playQueue(request.songs, request.startIndex, request.source)
            }

            is PendingPlaybackRequest.SingleSong -> {
                pendingPlaybackRequest = null
                play(request.song, request.source)
            }

            is PendingPlaybackRequest.ResolvedMediaItem -> {
                pendingPlaybackRequest = null
                playResolvedMediaItem(
                    song = request.song,
                    mediaItem = request.mediaItem,
                    extensionArtwork = request.extensionArtwork,
                    extensionLargeArtwork = request.extensionLargeArtwork,
                    persistentTrack = request.persistentTrack,
                    playWhenReady = request.playWhenReady
                )
            }

            is PendingPlaybackRequest.LogicalQueue -> {
                pendingPlaybackRequest = null
                playLogicalQueue(
                    items = request.items,
                    startIndex = request.startIndex,
                    playWhenReady = request.playWhenReady
                )
            }

            null -> Unit
        }
    }

    private fun updatePlaybackStarted(
        song: Song,
        extensionArtwork: ExtensionImage? = null,
        extensionLargeArtwork: ExtensionImage? = null,
        persistentTrack: PersistentTrack? = null,
        mediaId: String? = currentControllerOrNull()?.currentMediaItem?.mediaId,
        playWhenReady: Boolean = true
    ) {
        playbackStateMediaId = mediaId
        _playbackState.update {
            it.copy(
                currentSong = song,
                currentTrack = persistentTrack
                    ?: song.takeIf { it.sourceType == SourceType.Local }?.toPersistentTrack(),
                extensionArtwork = extensionArtwork,
                extensionLargeArtwork = extensionLargeArtwork,
                isPlaying = currentControllerOrNull()?.isPlaying == true,
                playWhenReady = playWhenReady,
                isBuffering = currentControllerOrNull()?.playbackState == Player.STATE_BUFFERING,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = song.durationMs.coerceAtLeast(0L),
                errorMessage = null
            )
        }
    }

    private fun updatePlaybackFailed(song: Song, error: Throwable) {
        playbackStateMediaId = null
        _playbackState.update {
            it.copy(
                currentSong = song,
                isPlaying = false,
                playWhenReady = false,
                isBuffering = false,
                bufferedPositionMs = 0L,
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

    fun playAt(index: Int, playWhenReady: Boolean = true): Boolean {
        val controller = currentControllerOrNull() ?: return false
        if (index !in 0 until controller.mediaItemCount) return false
        return runCatching {
            controller.seekTo(index, C.TIME_UNSET)
            if (playWhenReady) controller.play()
            true
        }.getOrDefault(false)
    }

    private fun syncLogicalQueueState(
        controller: MediaController? = currentControllerOrNull()
    ) {
        controller ?: return
        val logicalQueue = (0 until controller.mediaItemCount).mapNotNull { index ->
            runCatching { controller.getMediaItemAt(index) }
                .getOrNull()
                ?.let(PlaybackQueueMediaItemCodec::decode)
        }
        if (logicalQueue.isEmpty()) return
        val currentIndex = controller.currentMediaItemIndex
        val current = logicalQueue.getOrNull(currentIndex)
        playbackStateMediaId = current?.queueId
        _playbackState.update { state ->
            state.copy(
                currentSong = current?.presentation ?: state.currentSong,
                currentTrack = current?.persistentTrack,
                extensionArtwork = current?.extensionArtwork,
                extensionLargeArtwork = current?.extensionLargeArtwork,
                sourceQueue = logicalQueue.sortedBy(PlaybackQueueItem::sourceIndex),
                playbackQueue = logicalQueue,
                currentQueueIndex = currentIndex,
                durationMs = controller.duration.safeDurationOr(
                    current?.presentation?.durationMs ?: state.durationMs
                )
            )
        }
    }

    private fun Long.safeDurationOr(fallback: Long): Long =
        if (this == C.TIME_UNSET || this < 0L) fallback.coerceAtLeast(0L) else this

    private fun applyPlaybackOrderMode(
        controller: MediaController,
        mode: PlaybackOrderMode,
        shuffleOrderIndices: IntArray? = null
    ) {
        val args = Bundle().apply {
            putString(EXTRA_PLAYBACK_ORDER_MODE, mode.name)
            if (shuffleOrderIndices != null) {
                putIntArray(EXTRA_SHUFFLE_ORDER_INDICES, shuffleOrderIndices)
            }
        }

        val commandResult = runCatching {
            controller.sendCustomCommand(setPlaybackOrderCommand, args)
        }
        if (commandResult.isFailure) {
            applyPlaybackOrderModeDirectly(controller, mode)
        }
    }

    private fun applyPlaybackOrderModeDirectly(
        controller: MediaController,
        mode: PlaybackOrderMode
    ) {
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
        data class SingleSong(
            val song: Song,
            val source: PlaybackSource
        ) : PendingPlaybackRequest

        data class Queue(
            val songs: List<Song>,
            val startIndex: Int,
            val source: PlaybackSource
        ) : PendingPlaybackRequest

        data class ResolvedMediaItem(
            val song: Song,
            val mediaItem: MediaItem,
            val extensionArtwork: ExtensionImage?,
            val extensionLargeArtwork: ExtensionImage?,
            val persistentTrack: PersistentTrack?,
            val playWhenReady: Boolean
        ) : PendingPlaybackRequest

        data class LogicalQueue(
            val items: List<PlaybackQueueItem>,
            val startIndex: Int,
            val playWhenReady: Boolean
        ) : PendingPlaybackRequest
    }
}
