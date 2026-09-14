package ink.tenqui.flowtone.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.PersistentSongResolution
import ink.tenqui.flowtone.data.online.ProviderSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MediaSession-facing player. Its timeline is the logical queue while [engine] only contains the
 * currently resolved resource.
 */
@OptIn(UnstableApi::class)
internal class SessionPlaybackPlayer(
    private val engine: ExoPlayer,
    private val scope: CoroutineScope,
    private val extensionManager: ExtensionManager,
    private val appPreferences: AppPreferences,
    private val artworkLoader: SessionArtworkLoader,
    private val onLogicalTargetChanged: (PlaybackQueueItem?) -> Unit,
    private val onPlaybackOrderChanged: (PlaybackOrderMode) -> Unit,
    private val onResolveError: (PlaybackQueueItem, String) -> Unit
) : ForwardingSimpleBasePlayer(engine) {
    private val queue = SessionPlaybackQueue()
    private var desiredPlayWhenReady = false
    private var resolving = false
    private var resolvingQueueId: String? = null
    private var logicalError: PlaybackException? = null
    private val requestGate = PlaybackRequestGate()
    private var resolveJob: Job? = null
    private var artworkJob: Job? = null
    private var preloadJob: Job? = null
    private var resolvedQueueId: String? = null
    private var currentArtworkData: ByteArray? = null
    private val preloadedMediaItems = mutableMapOf<String, PreloadedOnlineItem>()

    val currentLogicalItem: PlaybackQueueItem?
        get() = queue.currentItem

    val playbackOrderMode: PlaybackOrderMode
        get() = queue.orderMode

    val sourceItems: List<PlaybackQueueItem>
        get() = queue.sourceItems

    private val engineListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED || resolving || !desiredPlayWhenReady) return
            if (queue.orderMode == PlaybackOrderMode.RepeatOne) {
                engine.seekTo(0L)
                engine.prepare()
                engine.playWhenReady = desiredPlayWhenReady
                return
            }
            val next = queue.nextIndex
            if (next == null) {
                desiredPlayWhenReady = false
                invalidateState()
            } else {
                queue.select(next)
                activateCurrentTarget("engine-ended")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = queue.currentItem ?: return
            if (resolvedQueueId != item.queueId || engine.currentMediaItem?.mediaId != item.queueId) {
                return
            }
            desiredPlayWhenReady = false
            onResolveError(item, error.message ?: "播放失败")
            invalidateState()
        }
    }

    init {
        engine.addListener(engineListener)
    }

    override fun getState(): State {
        val base = super.getState()
        val playlist = queue.playbackItems.mapIndexed { index, item ->
            val metadataItem = PlaybackQueueMediaItemCodec.encode(
                item = item,
                artworkData = currentArtworkData.takeIf { index == queue.currentIndex }
            )
            SimpleBasePlayer.MediaItemData.Builder(item.queueId)
                .setMediaItem(metadataItem)
                .setMediaMetadata(metadataItem.mediaMetadata)
                .setDurationUs(item.presentation.durationMs.toDurationUs())
                .setIsSeekable(index == queue.currentIndex && resolvedQueueId == item.queueId)
                .setIsPlaceholder(resolvedQueueId != item.queueId)
                .build()
        }
        val commands = Player.Commands.Builder()
            .addAll(base.availableCommands)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SET_REPEAT_MODE)
            .add(Player.COMMAND_SET_SHUFFLE_MODE)
            .build()
        val builder = base.buildUpon()
            .setAvailableCommands(commands)
            .setPlaylist(playlist)
            .setPlayWhenReady(
                desiredPlayWhenReady,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setRepeatMode(
                if (queue.orderMode == PlaybackOrderMode.RepeatOne) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
            )
            .setShuffleModeEnabled(queue.orderMode == PlaybackOrderMode.Shuffle)
            .setPlayerError(logicalError ?: base.playerError)
        if (queue.currentIndex in playlist.indices) {
            builder.setCurrentMediaItemIndex(queue.currentIndex)
        }
        if (resolving) {
            builder
                .setPlaybackState(Player.STATE_BUFFERING)
                .setIsLoading(true)
        }
        return builder.build()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        val logicalItems = decodeLogicalItems(mediaItems, "set")
            ?: return Futures.immediateFailedFuture<Void>(
                IllegalArgumentException("MediaSession queue contains an unknown item format")
            )
        queue.replace(
            items = logicalItems,
            selectedIndex = startIndex.coerceIn(0, logicalItems.lastIndex.coerceAtLeast(0))
        )
        desiredPlayWhenReady = false
        logicalError = null
        logLogicalState("handleSetMediaItems.afterReplace")
        activateCurrentTarget("set-media-items")
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        val currentQueueId = queue.currentItem?.queueId
        if (resolvedQueueId == currentQueueId) {
            engine.prepare()
        } else if (shouldActivateForPrepare(currentQueueId, resolvingQueueId, resolvedQueueId)) {
            activateCurrentTarget("prepare")
        } else {
            Log.d(LOG_TAG, "handlePrepare skip current=$currentQueueId resolving=$resolvingQueueId")
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<*> {
        val logicalItems = decodeLogicalItems(mediaItems, "add")
            ?: return Futures.immediateFailedFuture<Void>(
                IllegalArgumentException("MediaSession queue contains an unknown item format")
            )
        if (index == queue.currentIndex + 1) {
            queue.addNext(logicalItems)
        } else {
            queue.append(logicalItems)
        }
        invalidateState()
        schedulePreload()
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val oldTargetId = queue.currentItem?.queueId
        queue.removePlaybackRange(fromIndex, toIndex)
        if (queue.currentItem?.queueId != oldTargetId) {
            activateCurrentTarget("remove-current")
        } else {
            invalidateState()
            schedulePreload()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        desiredPlayWhenReady = playWhenReady
        engine.playWhenReady = playWhenReady
        if (playWhenReady && resolvedQueueId != queue.currentItem?.queueId && !resolving) {
            activateCurrentTarget("play-when-ready")
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex != queue.currentIndex) {
            if (queue.select(mediaItemIndex) != null) {
                activateCurrentTarget("seek")
            }
        } else if (resolvedQueueId == queue.currentItem?.queueId) {
            engine.seekTo(positionMs.coerceAtLeast(0L))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        desiredPlayWhenReady = false
        engine.stop()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        val mode = if (repeatMode == Player.REPEAT_MODE_ONE) {
            PlaybackOrderMode.RepeatOne
        } else if (queue.orderMode == PlaybackOrderMode.Shuffle) {
            PlaybackOrderMode.Shuffle
        } else {
            PlaybackOrderMode.Sequence
        }
        setPlaybackOrderMode(mode)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        setPlaybackOrderMode(
            if (shuffleModeEnabled) PlaybackOrderMode.Shuffle else PlaybackOrderMode.Sequence
        )
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        cancelPendingWork()
        engine.removeListener(engineListener)
        engine.release()
        return Futures.immediateVoidFuture()
    }

    fun setPlaybackOrderMode(mode: PlaybackOrderMode) {
        queue.setOrderMode(mode)
        onPlaybackOrderChanged(mode)
        invalidateState()
        schedulePreload()
    }

    fun clearLogicalQueue() {
        cancelPendingWork()
        queue.clear()
        resolvedQueueId = null
        currentArtworkData = null
        desiredPlayWhenReady = false
        engine.clearMediaItems()
        onLogicalTargetChanged(null)
        invalidateState()
    }

    private fun activateCurrentTarget(reason: String) {
        val item = queue.currentItem ?: run {
            clearLogicalQueue()
            return
        }
        val requestToken = requestGate.begin(item.queueId)
        resolveJob?.cancel()
        artworkJob?.cancel()
        resolving = item.isOnline
        resolvingQueueId = item.queueId.takeIf { item.isOnline }
        logicalError = null
        resolvedQueueId = null
        currentArtworkData = null
        engine.playWhenReady = false
        engine.stop()
        engine.clearMediaItems()
        Log.d(
            LOG_TAG,
            "activate reason=$reason generation=${requestToken.generation} queueId=${item.queueId} " +
                "stable=${item.stableIdentity} index=${queue.currentIndex} size=${queue.playbackItems.size} " +
                "source=${item.presentation.sourceType} online=${item.isOnline} " +
                "runtime=${item.runtimeProviderSong != null} persistent=${item.persistentTrack != null} " +
                "desired=$desiredPlayWhenReady rawCount=${engine.mediaItemCount}"
        )
        onLogicalTargetChanged(item)
        invalidateState()
        loadCurrentArtwork(item, requestToken)

        if (!item.isOnline) {
            val mediaItem = MediaItemMapper.toMediaItem(item.presentation, item.source)
                .buildUpon()
                .setMediaId(item.queueId)
                .setMediaMetadata(PlaybackQueueMediaItemCodec.encode(item).mediaMetadata)
                .build()
            commitResolvedItem(item, mediaItem, requestToken)
            return
        }

        preloadedMediaItems.remove(item.queueId)?.let { preloaded ->
            commitResolvedItem(
                item = item.copy(runtimeProviderSong = preloaded.providerSong),
                mediaItem = preloaded.mediaItem,
                requestToken = requestToken
            )
            return
        }
        resolveJob = scope.launch {
            try {
                extensionManager.initialize()
                Log.d(LOG_TAG, "resolve.provider generation=${requestToken.generation} queueId=${item.queueId}")
                val providerSong = resolveProviderSong(item) ?: return@launch resolveFailed(
                    item,
                    requestToken,
                    "该在线歌曲暂时无法恢复"
                )
                val resource = extensionManager.resolvePlaybackResource(providerSong)
                    ?: return@launch resolveFailed(item, requestToken, "无法解析在线播放资源")
                Log.d(LOG_TAG, "resolve.resource generation=${requestToken.generation} queueId=${item.queueId}")
                val mediaItem = extensionManager.createPlaybackMediaItem(providerSong, resource)
                    ?: return@launch resolveFailed(item, requestToken, "无法创建在线播放资源")
                commitResolvedItem(
                    item = item.copy(runtimeProviderSong = providerSong),
                    mediaItem = mediaItem,
                    requestToken = requestToken
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                resolveFailed(item, requestToken, error.message ?: "在线播放失败")
            }
        }
    }

    private suspend fun resolveProviderSong(item: PlaybackQueueItem): ProviderSong? {
        item.runtimeProviderSong?.let { return it }
        val persistent = item.persistentTrack as? PersistentTrack.Online ?: return null
        return when (val result = extensionManager.resolvePersistentPlaylistSong(persistent)) {
            is PersistentSongResolution.Resolved -> result.song
            is PersistentSongResolution.ProviderMissing,
            is PersistentSongResolution.Unresolved -> null
        }
    }

    private fun commitResolvedItem(
        item: PlaybackQueueItem,
        mediaItem: MediaItem,
        requestToken: PlaybackRequestToken
    ) {
        if (!isCurrent(requestToken)) return
        queue.updateItem(item)
        resolving = false
        resolvingQueueId = null
        resolvedQueueId = item.queueId
        val logicalMetadata = PlaybackQueueMediaItemCodec.encode(item, currentArtworkData).mediaMetadata
        if (currentArtworkData == null) {
            loadCurrentArtwork(item, requestToken)
        }
        val resolved = mediaItem.buildUpon()
            .setMediaId(item.queueId)
            .setMediaMetadata(logicalMetadata)
            .build()
        engine.setMediaItem(resolved)
        engine.prepare()
        engine.playWhenReady = desiredPlayWhenReady
        logLogicalState("commitResolvedItem generation=${requestToken.generation}")
        invalidateState()
        schedulePreload()
    }

    private fun resolveFailed(
        item: PlaybackQueueItem,
        requestToken: PlaybackRequestToken,
        message: String
    ) {
        if (!isCurrent(requestToken)) return
        resolving = false
        resolvingQueueId = null
        desiredPlayWhenReady = false
        resolvedQueueId = null
        logicalError = PlaybackException(
            message,
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED
        )
        onResolveError(item, message)
        invalidateState()
    }

    private fun loadCurrentArtwork(item: PlaybackQueueItem, requestToken: PlaybackRequestToken) {
        val image = item.extensionLargeArtwork ?: item.extensionArtwork ?: return
        artworkJob = scope.launch {
            val data = runCatching { artworkLoader.load(image) }.getOrNull() ?: return@launch
            if (!isCurrent(requestToken)) return@launch
            currentArtworkData = data
            invalidateState()
        }
    }

    private fun schedulePreload() {
        preloadJob?.cancel()
        val start = queue.currentIndex + 1
        val targets = queue.playbackItems
            .drop(start.coerceAtLeast(0))
            .filter(PlaybackQueueItem::isOnline)
            .take(appPreferences.getOnlinePlaybackPreloadCount().coerceAtLeast(0))
        val validIds = targets.mapTo(mutableSetOf(), PlaybackQueueItem::queueId)
        preloadedMediaItems.keys.retainAll(validIds)
        if (targets.isEmpty()) return
        preloadJob = scope.launch {
            extensionManager.initialize()
            for (target in targets) {
                if (target.queueId in preloadedMediaItems) continue
                try {
                    val providerSong = resolveProviderSong(target) ?: continue
                    val resource = extensionManager.resolvePlaybackResource(providerSong) ?: continue
                    val mediaItem = extensionManager.createPlaybackMediaItem(providerSong, resource) ?: continue
                    preloadedMediaItems[target.queueId] = PreloadedOnlineItem(providerSong, mediaItem)
                    extensionManager.preloadPlaybackContent(
                        mediaItem = mediaItem,
                        resource = resource,
                        percentage = appPreferences.getOnlinePlaybackPreloadPercentage()
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    // Preload failure must never fail the active target.
                }
            }
        }
    }

    private fun cancelPendingWork() {
        requestGate.invalidate()
        resolveJob?.cancel()
        artworkJob?.cancel()
        preloadJob?.cancel()
        resolveJob = null
        artworkJob = null
        preloadJob = null
        preloadedMediaItems.clear()
        resolving = false
        resolvingQueueId = null
    }

    private fun isCurrent(requestToken: PlaybackRequestToken): Boolean =
        requestGate.isCurrent(requestToken, queue.currentItem?.queueId)

    private fun decodeLogicalItems(
        mediaItems: List<MediaItem>,
        operation: String
    ): List<PlaybackQueueItem>? {
        val decoded = ArrayList<PlaybackQueueItem>(mediaItems.size)
        mediaItems.forEachIndexed { index, mediaItem ->
            val item = PlaybackQueueMediaItemCodec.decode(mediaItem)
            if (item == null) {
                val keys = mediaItem.mediaMetadata.extras?.keySet()?.sorted().orEmpty()
                Log.w(
                    LOG_TAG,
                    "decodeFailed operation=$operation index=$index mediaId=${mediaItem.mediaId} " +
                        "hasExtras=${mediaItem.mediaMetadata.extras != null} extrasKeys=$keys"
                )
                return null
            }
            decoded += item
        }
        return decoded
    }

    private fun logLogicalState(event: String) {
        Log.d(
            LOG_TAG,
            "$event playlistSize=${queue.playbackItems.size} currentIndex=${queue.currentIndex} " +
                "current=${queue.currentItem?.queueId} playbackState=$playbackState " +
                "playWhenReady=$desiredPlayWhenReady resolving=$resolving " +
                "resolvingQueueId=$resolvingQueueId resolvedQueueId=$resolvedQueueId " +
                "rawCount=${engine.mediaItemCount}"
        )
    }

    private fun Long.toDurationUs(): Long = when {
        this <= 0L -> C.TIME_UNSET
        this > Long.MAX_VALUE / 1_000L -> C.TIME_UNSET
        else -> this * 1_000L
    }

    private data class PreloadedOnlineItem(
        val providerSong: ProviderSong,
        val mediaItem: MediaItem
    )

    private companion object {
        const val LOG_TAG = "FlowtoneLogicalQueue"
    }
}
