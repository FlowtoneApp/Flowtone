package ink.tenqui.flowtone.viewmodel

import ink.tenqui.flowtone.data.local.PlaybackSettingsStore
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.data.online.toPresentationSong
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.toPersistentTrack
import ink.tenqui.flowtone.core.model.toPresentationSong
import ink.tenqui.flowtone.playback.PlaybackSource
import ink.tenqui.flowtone.playback.PlaybackController
import ink.tenqui.flowtone.playback.PlaybackOrderMode
import ink.tenqui.flowtone.playback.PlaybackQueueItem
import ink.tenqui.flowtone.playback.toPlaybackSource
import ink.tenqui.flowtone.playback.toSongOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/** 应用侧播放意图和队列展示映射；确认的播放事实始终来自 PlaybackController。 */
internal class MusicPlaybackSession(
    private val scope: CoroutineScope,
    private val playbackController: PlaybackController,
    private val playbackSettingsStore: PlaybackSettingsStore,
    private val localSongs: () -> List<Song>,
    private val onQueueChanged: () -> Unit
) {
    private val playbackState = playbackController.playbackState
    private val _queueState = MutableStateFlow(PlaybackQueuePresentation())
    val queueState: StateFlow<PlaybackQueuePresentation> = _queueState.asStateFlow()
    private var sourceQueue: List<Song> = emptyList()
    private var playbackQueue: List<Song> = emptyList()
    private var sourceTrackQueue: List<QueueTrackEntry> = emptyList()
    private var playbackTrackQueue: List<QueueTrackEntry> = emptyList()
    private var onlineQueueSongs: Map<String, ProviderSong> = emptyMap()
    private var currentQueueIndex = -1
    private var currentPlaybackSource: PlaybackSource = PlaybackSource.Unknown
    private var playbackOrderModeJob: Job? = null

    fun start() {
        observeControllerConnection()
        observeLogicalQueue()
    }

    fun preloadSnapshot(): PlaybackPreloadSnapshot = PlaybackPreloadSnapshot(
        sourceQueue, playbackQueue, playbackTrackQueue, currentQueueIndex
    )

    fun prepareLibraryQueue(songs: List<Song>, preservePlayback: Boolean) {
        if (!preservePlayback && playbackState.value.playbackQueue.isEmpty()) {
            sourceQueue = songs
            sourceTrackQueue = songs.map { song -> QueueTrackEntry(song.toPersistentTrack(), song) }
            rebuildPlaybackQueueForMode(
                mode = playbackState.value.playbackOrderMode,
                currentSong = playbackState.value.currentSong
            )
        }
        _queueState.update {
            it.copy(sourceQueue = sourceQueue, playbackQueue = playbackQueue, currentQueueIndex = currentQueueIndex)
        }
    }

    fun publishConfirmedQueueIndex() {
        _queueState.update { it.copy(currentQueueIndex = confirmedPlaybackQueueIndex()) }
    }

    fun applyHydratedMetadata(hydratedByIdentity: Map<String, ProviderSong>) {
        if (hydratedByIdentity.isEmpty()) return
        sourceTrackQueue = sourceTrackQueue.map { entry ->
            hydratedByIdentity[entry.stableIdentity]?.let { entry.copy(runtimeProviderSong = it) } ?: entry
        }
        playbackTrackQueue = playbackTrackQueue.map { entry ->
            hydratedByIdentity[entry.stableIdentity]?.let { entry.copy(runtimeProviderSong = it) } ?: entry
        }
        publishPlaybackQueue()
    }

    private fun beginPlaybackRequest() {
        playbackController.clearPendingPlaybackRequest()
    }

    private fun rebuildPlaybackQueueForMode(
        mode: PlaybackOrderMode,
        currentSong: Song?
    ) {
        playbackQueue = buildPlaybackQueueForMode(sourceQueue, mode, currentSong)
        playbackTrackQueue = playbackQueue.mapNotNull { queuedSong ->
            sourceTrackQueue.firstOrNull { isSameSong(it.presentation, queuedSong) }
        }
        currentQueueIndex = findSongIndex(playbackQueue, currentSong)
    }

    private fun buildPlaybackQueueForMode(
        queue: List<Song>,
        mode: PlaybackOrderMode,
        currentSong: Song?
    ): List<Song> {
        return when (mode) {
            PlaybackOrderMode.Shuffle -> buildShuffledPlaybackQueue(queue, currentSong)
            PlaybackOrderMode.Sequence,
            PlaybackOrderMode.RepeatOne -> queue
        }
    }

    private fun buildShuffledPlaybackQueue(
        queue: List<Song>,
        currentSong: Song?
    ): List<Song> {
        if (queue.isEmpty()) {
            return emptyList()
        }

        val officialCurrentSong = currentSong?.let { findSong(queue, it) }
        return if (officialCurrentSong == null) {
            queue.shuffled()
        } else {
            listOf(officialCurrentSong) + queue
                .filterNot { isSameSong(it, officialCurrentSong) }
                .shuffled()
        }
    }

    private fun buildPlaybackOrderIndices(
        orderedQueue: List<Song>,
        sourceQueue: List<Song>
    ): IntArray? {
        if (orderedQueue.size != sourceQueue.size) {
            return null
        }

        val indicesBySongId = sourceQueue
            .withIndex()
            .groupBy(
                keySelector = { it.value.id },
                valueTransform = { it.index }
            )
            .mapValues { (_, indices) -> indices.toMutableList() }
            .toMutableMap()
        val orderedIndices = IntArray(orderedQueue.size)

        orderedQueue.forEachIndexed { orderIndex, song ->
            val availableIndices = indicesBySongId[song.id] ?: return null
            if (availableIndices.isEmpty()) {
                return null
            }
            orderedIndices[orderIndex] = availableIndices.removeAt(0)
        }

        return orderedIndices
    }

    private fun findSong(queue: List<Song>, song: Song): Song? {
        return queue.firstOrNull { isSameSong(it, song) }
    }

    private fun findSongIndex(queue: List<Song>, song: Song?): Int {
        song ?: return -1
        return queue.indexOfFirst { isSameSong(it, song) }
    }

    private fun isSameSong(first: Song, second: Song): Boolean {
        return first.id == second.id || first.uri == second.uri
    }

    fun handleLocalSongsDeleted(deletedSongs: List<Song>) {
        if (deletedSongs.isEmpty()) {
            return
        }
        val currentSong = playbackState.value.currentSong
        if (currentSong != null && deletedSongs.any { deletedSong ->
                isSameSong(deletedSong, currentSong)
            }
        ) {
            playbackController.clearPlayback()
            sourceQueue = emptyList()
            sourceTrackQueue = emptyList()
            playbackQueue = emptyList()
            playbackTrackQueue = emptyList()
            currentQueueIndex = -1
            currentPlaybackSource = PlaybackSource.Unknown
            publishPlaybackQueue()
        }
    }

    fun playSong(
        song: Song,
        source: PlaybackSource = PlaybackSource.LocalLibrary
    ) {
        val queue = localSongs()
        val songIndex = queue.indexOfFirst { it.id == song.id || it.uri == song.uri }
        if (songIndex == -1) {
            sourceQueue = listOf(song)
            sourceTrackQueue = listOf(QueueTrackEntry(song.toPersistentTrack(), song))
            playbackQueue = listOf(song)
            playbackTrackQueue = sourceTrackQueue
            playSongAt(index = 0, source = source)
            return
        }

        sourceQueue = queue
        sourceTrackQueue = queue.map { QueueTrackEntry(it.toPersistentTrack(), it) }
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = song
        )
        val playbackIndex = findSongIndex(playbackQueue, song)
        playSongAt(index = playbackIndex, source = source)
    }

    fun playSongQueue(
        songs: List<Song>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.Unknown
    ) {
        if (songs.isEmpty() || startIndex !in songs.indices) {
            return
        }

        val startSong = songs[startIndex]
        sourceQueue = songs
        sourceTrackQueue = songs.map { QueueTrackEntry(it.toPersistentTrack(), it) }
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = startSong
        )
        val playbackIndex = findSongIndex(playbackQueue, startSong)
        playSongAt(index = playbackIndex, source = source)
    }

    fun playProviderSongQueue(
        queue: List<ProviderSong>,
        startIndex: Int,
        source: PlaybackSource
    ) {
        val selected = queue.getOrNull(startIndex) ?: return
        val displayQueue = queue.map(ProviderSong::toPresentationSong)
        val selectedDisplaySong = selected.toPresentationSong()
        sourceQueue = displayQueue
        sourceTrackQueue = queue.zip(displayQueue) { providerSong, presentation ->
            QueueTrackEntry(providerSong.toPersistentTrack(), presentation, providerSong)
        }
        onlineQueueSongs = queue.associateBy { providerSong ->
            providerSong.toPresentationSong().uri.toString()
        }
        rebuildPlaybackQueueForMode(
            mode = playbackState.value.playbackOrderMode,
            currentSong = selectedDisplaySong
        )
        val selectedIndex = findSongIndex(playbackQueue, selectedDisplaySong)
        if (selectedIndex == -1) return
        playSongAt(index = selectedIndex, source = source)
    }

    fun playQueueSong(song: Song) {
        val playbackIndex = findSongIndex(playbackQueue, song)
        if (playbackIndex != -1) {
            if (!playbackController.playAt(playbackIndex, playWhenReady = true)) {
                playSongAt(playbackIndex, source = currentPlaybackSource)
            }
        }
    }

    fun addSongsToNext(songs: List<Song>): Boolean {
        if (songs.isEmpty()) return false
        val currentSong = playbackState.value.currentSong
        if (currentSong == null) {
            playSongQueue(songs, 0, PlaybackSource.Unknown)
            return true
        }
        syncCurrentQueueIndex()
        val insertionIndex = (currentQueueIndex + 1).coerceIn(0, playbackQueue.size)
        if (!playbackController.addSongsNext(songs, currentPlaybackSource)) return false
        playbackQueue = playbackQueue.toMutableList().apply {
            addAll(insertionIndex, songs)
        }
        playbackTrackQueue = playbackTrackQueue.toMutableList().apply {
            addAll(insertionIndex, songs.map { QueueTrackEntry(it.toPersistentTrack(), it) })
        }
        sourceQueue = playbackQueue
        sourceTrackQueue = playbackTrackQueue
        publishPlaybackQueue()
        onQueueChanged()
        return true
    }

    fun appendSongsToQueue(songs: List<Song>): Boolean {
        if (songs.isEmpty()) return false
        if (playbackState.value.currentSong == null) {
            playSongQueue(songs, 0, PlaybackSource.Unknown)
            return true
        }
        if (!playbackController.appendSongsToQueue(songs, currentPlaybackSource)) return false
        playbackQueue = playbackQueue + songs
        playbackTrackQueue = playbackTrackQueue + songs.map {
            QueueTrackEntry(it.toPersistentTrack(), it)
        }
        sourceQueue = playbackQueue
        sourceTrackQueue = playbackTrackQueue
        publishPlaybackQueue()
        onQueueChanged()
        return true
    }

    private fun playSongAt(
        index: Int,
        source: PlaybackSource = currentPlaybackSource
    ) {
        if (playbackQueue.isEmpty() || index !in playbackQueue.indices) {
            currentQueueIndex = -1
            publishPlaybackQueue()
            return
        }

        val selectedEntry = playbackTrackQueue.getOrNull(index) ?: return
        beginPlaybackRequest()
        currentQueueIndex = index
        currentPlaybackSource = source
        val logicalItems = sourceTrackQueue.mapIndexed { sourceIndex, entry ->
            entry.toPlaybackQueueItem(source, sourceIndex)
        }
        val selectedSourceIndex = sourceTrackQueue.indexOfFirst { entry ->
            entry.stableIdentity == selectedEntry.stableIdentity
        }
        if (selectedSourceIndex == -1) return
        playbackController.playLogicalQueue(logicalItems, selectedSourceIndex, playWhenReady = true)
        publishPlaybackQueue()
        onQueueChanged()
    }

    private fun syncCurrentQueueIndex() {
        val currentSong = playbackState.value.currentSong
        currentQueueIndex = if (currentSong == null) {
            -1
        } else {
            findSongIndex(playbackQueue, currentSong)
        }
    }

    fun syncCurrentSongFromMediaId(mediaId: String) {
        val songIndex = playbackQueue.indexOfFirst { song ->
            song.id.toString() == mediaId || song.uri.toString() == mediaId
        }
        if (songIndex == -1) {
            return
        }

        currentQueueIndex = songIndex
        playbackController.updateCurrentSong(playbackQueue[songIndex])
        publishPlaybackQueue()
        onQueueChanged()
    }

    private fun observeControllerConnection() {
        scope.launch {
            playbackController.isConnected.collect { connected ->
                if (connected) {
                    restoreFromControllerIfPossible()
                }
            }
        }
    }

    fun restoreFromControllerIfPossible() {
        val snapshot = playbackController.getPlaybackSnapshot() ?: return
        if (snapshot.logicalQueue.isNotEmpty()) {
            sourceTrackQueue = snapshot.sourceQueue.map(PlaybackQueueItem::toQueueTrackEntry)
            playbackTrackQueue = snapshot.logicalQueue.map(PlaybackQueueItem::toQueueTrackEntry)
            sourceQueue = sourceTrackQueue.map(QueueTrackEntry::presentation)
            playbackQueue = playbackTrackQueue.map(QueueTrackEntry::presentation)
            currentQueueIndex = snapshot.currentMediaItemIndex
                .coerceIn(0, playbackQueue.lastIndex.coerceAtLeast(0))
            currentPlaybackSource = snapshot.logicalQueue
                .getOrNull(currentQueueIndex)
                ?.source
                ?: PlaybackSource.Unknown
            publishPlaybackQueue()
            onQueueChanged()
            return
        }
        val currentMediaItem = snapshot.currentMediaItem ?: return
        val scannedSongs = localSongs()
        val currentSong = currentMediaItem.toSongOrNull(scannedSongs) ?: return
        currentPlaybackSource = currentMediaItem.toPlaybackSource()

        val restoredQueue = if (snapshot.queueMediaItems.isNotEmpty()) {
            snapshot.queueMediaItems.mapNotNull { mediaItem ->
                mediaItem.toSongOrNull(scannedSongs)
            }
        } else {
            listOf(currentSong)
        }
        if (restoredQueue.isEmpty()) {
            return
        }

        sourceQueue = restoredQueue
        playbackQueue = buildPlaybackQueueForMode(
            queue = sourceQueue,
            mode = snapshot.playbackOrderMode,
            currentSong = currentSong
        )
        currentQueueIndex = findSongIndex(playbackQueue, currentSong)
            .takeIf { it != -1 } ?: 0

        val duration = when {
            snapshot.durationMs > 0L -> snapshot.durationMs
            currentSong.durationMs > 0L -> currentSong.durationMs
            else -> 0L
        }
        val position = if (duration > 0L) {
            snapshot.positionMs.coerceIn(0L, duration)
        } else {
            0L
        }

        playbackController.updateFromSnapshot(
            currentSong = currentSong,
            isPlaying = snapshot.isPlaying,
            playWhenReady = snapshot.playWhenReady,
            isBuffering = snapshot.isBuffering,
            positionMs = position,
            bufferedPositionMs = snapshot.bufferedPositionMs.coerceIn(0L, duration),
            durationMs = duration,
            playbackOrderMode = snapshot.playbackOrderMode
        )
        publishPlaybackQueue()
        onQueueChanged()
    }

    fun reconcileCurrentSongWithLibrary() {
        val scannedSongs = localSongs()
        val currentSong = playbackState.value.currentSong ?: return
        val officialSong = scannedSongs.firstOrNull {
            it.id == currentSong.id || it.uri == currentSong.uri
        } ?: return

        sourceQueue = sourceQueue.map { queuedSong ->
            scannedSongs.firstOrNull { it.id == queuedSong.id || it.uri == queuedSong.uri }
                ?: queuedSong
        }.ifEmpty {
            scannedSongs
        }
        playbackQueue = playbackQueue.map { queuedSong ->
            scannedSongs.firstOrNull { it.id == queuedSong.id || it.uri == queuedSong.uri }
                ?: queuedSong
        }
        currentQueueIndex = playbackQueue.indexOfFirst {
            it.id == officialSong.id || it.uri == officialSong.uri
        }
        playbackController.updateFromSnapshot(
            currentSong = officialSong,
            isPlaying = playbackState.value.isPlaying,
            playWhenReady = playbackState.value.playWhenReady,
            isBuffering = playbackState.value.isBuffering,
            positionMs = playbackState.value.positionMs,
            bufferedPositionMs = playbackState.value.bufferedPositionMs,
            durationMs = playbackState.value.durationMs.takeIf { it > 0L }
                ?: officialSong.durationMs.coerceAtLeast(0L),
            playbackOrderMode = playbackState.value.playbackOrderMode
        )
        publishPlaybackQueue()
        onQueueChanged()
    }

    private fun publishPlaybackQueue() {
        val hydratedByIdentity = (sourceTrackQueue + playbackTrackQueue)
            .mapNotNull { entry -> entry.runtimeProviderSong?.let { entry.stableIdentity to it } }
            .toMap()
        val controllerState = playbackState.value
        val sourceItems = controllerState.sourceQueue.takeIf { it.isNotEmpty() }
            ?.map { item ->
                hydratedByIdentity[item.stableIdentity]
                    ?.let { item.copy(runtimeProviderSong = it) }
                    ?: item
            }
            ?: sourceTrackQueue.mapIndexed { index, entry ->
                entry.toPlaybackQueueItem(currentPlaybackSource, index)
            }
        val playbackItems = controllerState.playbackQueue.takeIf { it.isNotEmpty() }
            ?.map { item ->
                hydratedByIdentity[item.stableIdentity]
                    ?.let { item.copy(runtimeProviderSong = it) }
                    ?: item
            }
            ?: playbackTrackQueue.mapIndexed { index, entry ->
                entry.toPlaybackQueueItem(currentPlaybackSource, index)
            }
        _queueState.update {
            it.copy(
                sourceQueue = sourceQueue,
                playbackQueue = playbackQueue,
                sourceQueueItems = sourceItems,
                playbackQueueItems = playbackItems,
                currentQueueIndex = currentQueueIndex
            )
        }
    }

    private fun observeLogicalQueue() {
        scope.launch {
            playbackState
                .map { state ->
                    LogicalQueueSnapshot(
                        sourceQueue = state.sourceQueue,
                        playbackQueue = state.playbackQueue,
                        currentIndex = state.currentQueueIndex
                    )
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    if (snapshot.playbackQueue.isEmpty()) return@collect
                    sourceTrackQueue = snapshot.sourceQueue.map(PlaybackQueueItem::toQueueTrackEntry)
                    playbackTrackQueue = snapshot.playbackQueue.map(PlaybackQueueItem::toQueueTrackEntry)
                    sourceQueue = sourceTrackQueue.map(QueueTrackEntry::presentation)
                    playbackQueue = playbackTrackQueue.map(QueueTrackEntry::presentation)
                    currentQueueIndex = snapshot.currentIndex
                    onlineQueueSongs = sourceTrackQueue.mapNotNull { entry ->
                        entry.runtimeProviderSong?.let { entry.presentation.uri.toString() to it }
                    }.toMap()
                    publishPlaybackQueue()
                    onQueueChanged()
                }
        }
    }

    private fun confirmedPlaybackQueueIndex(): Int {
        val confirmedSong = playbackState.value.currentSong ?: return -1
        return findSongIndex(playbackQueue, confirmedSong)
    }

    fun playPersistentTrackQueue(
        tracks: List<PersistentTrack>,
        startIndex: Int,
        source: PlaybackSource = PlaybackSource.Unknown
    ) {
        if (startIndex !in tracks.indices) return
        val entries = tracks.mapNotNull(::queueEntryForPersistentTrack)
        val selectedIdentity = tracks[startIndex].identityKey
        val selectedEntry = entries.firstOrNull {
            it.persistentTrack?.identityKey == selectedIdentity
        } ?: run {
            publishTrackPlaybackError("该本地歌曲已不可用")
            return
        }
        sourceTrackQueue = entries.toList()
        sourceQueue = entries.map(QueueTrackEntry::presentation)
        rebuildPlaybackQueueForMode(
            playbackState.value.playbackOrderMode,
            selectedEntry.presentation
        )
        val index = playbackTrackQueue.indexOfFirst {
            it.persistentTrack?.identityKey == selectedIdentity
        }
        playSongAt(index, source)
    }

    fun playPersistentTrack(track: PersistentTrack) = playPersistentTrackQueue(listOf(track), 0)

    private fun queueEntryForPersistentTrack(track: PersistentTrack): QueueTrackEntry? = when (track) {
        is PersistentTrack.Local -> localSongs()
            .firstOrNull { it.id.toString() == track.songId }
            ?.let { QueueTrackEntry(track, it) }
        is PersistentTrack.Online -> QueueTrackEntry(
            track,
            requireNotNull(track.toPresentationSong(emptyList()))
        )
    }

    private fun publishTrackPlaybackError(message: String) {
        val confirmedQueueIndex = confirmedPlaybackQueueIndex()
        _queueState.update { state ->
            state.copy(
                trackPlaybackErrorMessage = message,
                trackPlaybackErrorEventId = state.trackPlaybackErrorEventId + 1L,
                currentQueueIndex = confirmedQueueIndex
            )
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun togglePlaybackOrderMode() {
        val currentMode = playbackState.value.playbackOrderMode
        val nextMode = when (currentMode) {
            PlaybackOrderMode.Sequence -> PlaybackOrderMode.RepeatOne
            PlaybackOrderMode.RepeatOne -> PlaybackOrderMode.Shuffle
            PlaybackOrderMode.Shuffle -> PlaybackOrderMode.Sequence
        }
        playbackOrderModeJob?.cancel()
        playbackOrderModeJob = scope.launch {
            applyPlaybackOrderMode(nextMode)
        }
    }

    private suspend fun applyPlaybackOrderMode(mode: PlaybackOrderMode) {
        if (mode == playbackState.value.playbackOrderMode) {
            return
        }
        playbackController.setPlaybackOrderMode(mode)
        playbackSettingsStore.setPlaybackOrderMode(mode)
    }

    fun seekTo(positionMs: Long) {
        val durationMs = playbackState.value.durationMs
        val clampedPosition = if (durationMs > 0L) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            0L
        }

        playbackController.seekTo(clampedPosition)
        playbackController.updateProgress(
            positionMs = clampedPosition,
            bufferedPositionMs = playbackState.value.bufferedPositionMs,
            durationMs = durationMs
        )
    }

    fun playNext() {
        playNext(playWhenReady = true)
    }

    fun handlePlaybackEnded() {
        playNext(playWhenReady = false)
    }

    fun playPrevious() {
        if (playbackController.playPrevious(playWhenReady = true)) {
            return
        }

        syncCurrentQueueIndex()
        if (playbackQueue.isEmpty() || currentQueueIndex !in playbackQueue.indices) {
            return
        }

        val previousIndex = currentQueueIndex - 1
        if (previousIndex !in playbackQueue.indices) {
            return
        }

        playSongAt(index = previousIndex)
    }

    private fun playNext(playWhenReady: Boolean) {
        if (playbackController.playNext(playWhenReady = playWhenReady)) {
            return
        }

        syncCurrentQueueIndex()
        if (playbackQueue.isEmpty() || currentQueueIndex !in playbackQueue.indices) {
            return
        }

        val nextIndex = currentQueueIndex + 1
        if (nextIndex !in playbackQueue.indices) {
            return
        }

        playSongAt(index = nextIndex)
    }

    fun close() {
        playbackOrderModeJob?.cancel()
    }
}

internal data class QueueTrackEntry(
    val persistentTrack: PersistentTrack?,
    val presentation: Song,
    val runtimeProviderSong: ProviderSong? = null
) {
    val stableIdentity: String
        get() = persistentTrack?.identityKey
            ?: runtimeProviderSong?.trackRef?.let { "runtime:${it.extensionId}:${it.opaqueId}" }
            ?: "song:${presentation.sourceType}:${presentation.id}:${presentation.uri}"
}

internal data class PlaybackQueuePresentation(
    val sourceQueue: List<Song> = emptyList(),
    val playbackQueue: List<Song> = emptyList(),
    val sourceQueueItems: List<PlaybackQueueItem> = emptyList(),
    val playbackQueueItems: List<PlaybackQueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val trackPlaybackErrorMessage: String? = null,
    val trackPlaybackErrorEventId: Long = 0L
)

private data class LogicalQueueSnapshot(
    val sourceQueue: List<PlaybackQueueItem>,
    val playbackQueue: List<PlaybackQueueItem>,
    val currentIndex: Int
)

private fun QueueTrackEntry.toPlaybackQueueItem(
    source: PlaybackSource,
    sourceIndex: Int
): PlaybackQueueItem = when {
    runtimeProviderSong != null -> PlaybackQueueItem.provider(
        song = runtimeProviderSong,
        presentation = presentation,
        source = source,
        sourceIndex = sourceIndex
    )
    persistentTrack != null -> PlaybackQueueItem.persistent(
        track = persistentTrack,
        presentation = presentation,
        source = source,
        sourceIndex = sourceIndex
    )
    else -> PlaybackQueueItem.local(presentation, source, sourceIndex)
}

private fun PlaybackQueueItem.toQueueTrackEntry(): QueueTrackEntry = QueueTrackEntry(
    persistentTrack = persistentTrack,
    presentation = presentation,
    runtimeProviderSong = runtimeProviderSong
)

private fun ProviderSong.toPersistentTrack(): PersistentTrack.Online? {
    val identity = persistentTrackRef ?: return null
    return PersistentTrack.Online(
        sourceHost = identity.sourceHost,
        persistentId = identity.persistentId,
        cachedTitle = title,
        cachedArtist = artist,
        cachedDurationMs = durationMs
    )
}
