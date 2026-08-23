package ink.tenqui.flowtone.data.repository

import ink.tenqui.flowtone.core.model.Playlist
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.core.model.PlaylistCardStyle
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LocalPlaylistCreatorName
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.toPersistentTrack
import ink.tenqui.flowtone.data.online.ProviderSong
import ink.tenqui.flowtone.core.model.playlistAppearanceColorKeyForStableId
import ink.tenqui.flowtone.core.model.randomPlaylistAppearanceColorKey
import ink.tenqui.flowtone.data.local.PlaylistStorage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaylistRepository(
    private val storage: PlaylistStorage
) {
    private val playlistMutex = Mutex()
    private val _playlists = MutableStateFlow(storage.loadPlaylists())
    private val _playlistSongEntries = MutableStateFlow(storage.loadPlaylistSongEntries())

    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()
    val playlistSongEntries: StateFlow<List<PlaylistSongEntry>> =
        _playlistSongEntries.asStateFlow()

    suspend fun syncLibraryPlaylistCards(
        cards: List<LibraryPlaylistCard>
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        val currentEntries = _playlistSongEntries.value
        val currentById = currentPlaylists.associateBy { playlist -> playlist.id }
        val now = System.currentTimeMillis()
        val nextPlaylists = cards
            .sortedBy { card -> card.order }
            .mapIndexed { index, card ->
                val existing = currentById[card.id]
                if (existing == null) {
                    Playlist(
                        id = card.id,
                        title = card.title,
                        subtitle = card.subtitle,
                        appearanceColorKey = card.appearanceColorKey
                            ?: playlistAppearanceColorKeyForStableId(card.id),
                        order = index,
                        createdAt = now,
                        updatedAt = now,
                        customArtworkUri = card.customArtworkUri?.toString(),
                        creatorName = card.creatorName ?: LocalPlaylistCreatorName,
                        description = card.description
                    )
                } else {
                    existing.copy(
                        title = card.title,
                        subtitle = card.subtitle,
                        appearanceColorKey = card.appearanceColorKey
                            ?: existing.appearanceColorKey,
                        order = index,
                        customArtworkUri = card.customArtworkUri?.toString()
                            ?: existing.customArtworkUri,
                        creatorName = card.creatorName ?: existing.creatorName,
                        description = card.description ?: existing.description,
                        updatedAt = if (
                            existing.title != card.title ||
                            existing.subtitle != card.subtitle ||
                            (
                                card.appearanceColorKey != null &&
                                    existing.appearanceColorKey != card.appearanceColorKey
                            ) ||
                            (card.creatorName != null &&
                                existing.creatorName != card.creatorName) ||
                            (card.description != null &&
                                existing.description != card.description) ||
                            existing.order != index
                        ) {
                            now
                        } else {
                            existing.updatedAt
                        }
                    )
                }
            }
        val nextPlaylistIds = nextPlaylists.mapTo(mutableSetOf()) { playlist -> playlist.id }
        val nextEntries = currentEntries.filter { entry -> entry.playlistId in nextPlaylistIds }

        if (nextPlaylists == currentPlaylists && nextEntries == currentEntries) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = currentEntries,
            nextPlaylists = normalizePlaylistOrder(nextPlaylists),
            nextEntries = nextEntries,
            successValue = Unit
        )
    }

    fun hasDuplicateTitle(title: String, excludingPlaylistId: String? = null): Boolean {
        val normalizedTitle = title.trim()
        return normalizedTitle.isNotEmpty() && _playlists.value.any { playlist ->
            playlist.id != excludingPlaylistId &&
                playlist.title.trim().equals(normalizedTitle, ignoreCase = true)
        }
    }

    suspend fun createPlaylist(title: String): PlaylistMutationResult<Playlist> =
        playlistMutex.withLock {
            val normalizedTitle = title.trim()
            val currentPlaylists = _playlists.value

            when {
                normalizedTitle.isEmpty() ->
                    return@withLock PlaylistMutationResult.Failure(
                        PlaylistMutationError.BlankTitle
                    )

                hasDuplicateTitle(normalizedTitle) ->
                    return@withLock PlaylistMutationResult.Failure(
                        PlaylistMutationError.DuplicateTitle
                    )
            }

            val now = System.currentTimeMillis()
            val mostRecentColorKey = currentPlaylists
                .maxByOrNull { item -> item.createdAt }
                ?.appearanceColorKey
            val playlist = Playlist(
                id = UUID.randomUUID().toString(),
                title = normalizedTitle,
                appearanceColorKey = randomPlaylistAppearanceColorKey(
                    avoiding = mostRecentColorKey
                ),
                order = (currentPlaylists.maxOfOrNull { item -> item.order } ?: -1) + 1,
                createdAt = now,
                updatedAt = now,
                creatorName = LocalPlaylistCreatorName
            )
            val nextPlaylists = normalizePlaylistOrder(currentPlaylists + playlist)

            commitMutation(
                previousPlaylists = currentPlaylists,
                previousEntries = _playlistSongEntries.value,
                nextPlaylists = nextPlaylists,
                nextEntries = _playlistSongEntries.value,
                successValue = nextPlaylists.first { item -> item.id == playlist.id }
            )
        }

    suspend fun renamePlaylist(
        id: String,
        newTitle: String
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val normalizedTitle = newTitle.trim()
        val currentPlaylists = _playlists.value

        when {
            normalizedTitle.isEmpty() ->
                return@withLock PlaylistMutationResult.Failure(
                    PlaylistMutationError.BlankTitle
                )

            currentPlaylists.none { playlist -> playlist.id == id } ->
                return@withLock PlaylistMutationResult.Failure(
                    PlaylistMutationError.NotFound
                )

            hasDuplicateTitle(normalizedTitle, excludingPlaylistId = id) ->
                return@withLock PlaylistMutationResult.Failure(
                    PlaylistMutationError.DuplicateTitle
                )
        }

        val now = System.currentTimeMillis()
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(
                    title = normalizedTitle,
                    updatedAt = now
                )
            } else {
                playlist
            }
        }

        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = _playlistSongEntries.value,
            nextPlaylists = nextPlaylists,
            nextEntries = _playlistSongEntries.value,
            successValue = Unit
        )
    }

    suspend fun deletePlaylist(id: String): PlaylistMutationResult<Unit> =
        playlistMutex.withLock {
            val currentPlaylists = _playlists.value
            if (currentPlaylists.none { playlist -> playlist.id == id }) {
                return@withLock PlaylistMutationResult.Failure(
                    PlaylistMutationError.NotFound
                )
            }

            commitMutation(
                previousPlaylists = currentPlaylists,
                previousEntries = _playlistSongEntries.value,
                nextPlaylists = normalizePlaylistOrder(
                    currentPlaylists.filterNot { playlist -> playlist.id == id }
                ),
                nextEntries = _playlistSongEntries.value.filterNot { entry ->
                    entry.playlistId == id
                },
                successValue = Unit
            )
        }

    suspend fun updatePlaylistCardStyle(
        id: String,
        style: PlaylistCardStyle
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        if (currentPlaylists.none { playlist -> playlist.id == id }) {
            return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.NotFound
            )
        }

        val now = System.currentTimeMillis()
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(
                    cardStyle = style,
                    updatedAt = now
                )
            } else {
                playlist
            }
        }

        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = _playlistSongEntries.value,
            nextPlaylists = nextPlaylists,
            nextEntries = _playlistSongEntries.value,
            successValue = Unit
        )
    }

    suspend fun updatePlaylistAppearanceColor(
        id: String,
        colorKey: PlaylistAppearanceColorKey
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        val currentPlaylist = currentPlaylists.firstOrNull { playlist -> playlist.id == id }
            ?: return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.NotFound
            )
        if (currentPlaylist.appearanceColorKey == colorKey) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val now = System.currentTimeMillis()
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(
                    appearanceColorKey = colorKey,
                    updatedAt = now
                )
            } else {
                playlist
            }
        }

        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = _playlistSongEntries.value,
            nextPlaylists = nextPlaylists,
            nextEntries = _playlistSongEntries.value,
            successValue = Unit
        )
    }

    suspend fun addSongToPlaylist(
        playlistId: String,
        song: Song
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        if (currentPlaylists.none { playlist -> playlist.id == playlistId }) {
            return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.NotFound
            )
        }

        val currentEntries = _playlistSongEntries.value
        val track = song.toPersistentTrack()
        val alreadyExists = currentEntries.any { entry ->
            entry.playlistId == playlistId && entry.track.identityKey == track.identityKey
        }
        if (alreadyExists) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val now = System.currentTimeMillis()
        val nextEntry = PlaylistSongEntry(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            track = track,
            addedAt = now
        )
        val nextEntries = currentEntries + nextEntry
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    updatedAt = now
                )
            } else {
                playlist
            }
        }

        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = currentEntries,
            nextPlaylists = nextPlaylists,
            nextEntries = nextEntries,
            successValue = Unit
        )
    }

    suspend fun addTrackToPlaylist(
        playlistId: String,
        track: PersistentTrack
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        if (currentPlaylists.none { it.id == playlistId }) {
            return@withLock PlaylistMutationResult.Failure(PlaylistMutationError.NotFound)
        }
        val currentEntries = _playlistSongEntries.value
        if (currentEntries.any {
                it.playlistId == playlistId && it.track.identityKey == track.identityKey
            }) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }
        val now = System.currentTimeMillis()
        val nextEntries = currentEntries + PlaylistSongEntry(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            track = track,
            addedAt = now
        )
        val nextPlaylists = currentPlaylists.map {
            if (it.id == playlistId) it.copy(updatedAt = now) else it
        }
        commitMutation(currentPlaylists, currentEntries, nextPlaylists, nextEntries, Unit)
    }

    /** 仅接受 Provider 明确给出的长期身份，绝不把 runtime opaqueId 当作 persistentId。 */
    suspend fun addProviderSongToPlaylist(
        playlistId: String,
        song: ProviderSong
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val identity = song.persistentTrackRef
            ?: return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.PersistentIdentityUnsupported
            )
        val currentPlaylists = _playlists.value
        if (currentPlaylists.none { it.id == playlistId }) {
            return@withLock PlaylistMutationResult.Failure(PlaylistMutationError.NotFound)
        }
        val currentEntries = _playlistSongEntries.value
        if (currentEntries.any { entry ->
                entry.playlistId == playlistId &&
                    entry.track.identityKey == "online:${identity.sourceHost}:${identity.persistentId}"
            }) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }
        val now = System.currentTimeMillis()
        val onlineSong = PersistentTrack.Online(
            sourceHost = identity.sourceHost,
            persistentId = identity.persistentId,
            cachedTitle = song.title,
            cachedArtist = song.artist,
            cachedDurationMs = song.durationMs
        )
        val nextEntries = currentEntries + PlaylistSongEntry(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            track = onlineSong,
            addedAt = now,
        )
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(updatedAt = now) else playlist
        }
        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = currentEntries,
            nextPlaylists = nextPlaylists,
            nextEntries = nextEntries,
            successValue = Unit
        )
    }

    suspend fun addSongToPlaylists(
        playlistIds: Set<String>,
        song: Song
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val normalizedPlaylistIds = playlistIds.filterTo(mutableSetOf()) { playlistId ->
            playlistId.isNotBlank()
        }
        if (normalizedPlaylistIds.isEmpty()) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val currentPlaylists = _playlists.value
        val existingPlaylistIds = currentPlaylists.mapTo(mutableSetOf()) { playlist -> playlist.id }
        val targetPlaylistIds = normalizedPlaylistIds.filterTo(mutableSetOf()) { playlistId ->
            playlistId in existingPlaylistIds
        }
        if (targetPlaylistIds.isEmpty()) {
            return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.NotFound
            )
        }

        val currentEntries = _playlistSongEntries.value
        val track = song.toPersistentTrack()
        val existingEntryKeys = currentEntries.mapTo(mutableSetOf()) { entry ->
            entry.playlistId to entry.track.identityKey
        }
        val now = System.currentTimeMillis()
        val newEntries = targetPlaylistIds
            .filterNot { playlistId -> playlistId to track.identityKey in existingEntryKeys }
            .map { playlistId ->
                PlaylistSongEntry(
                    id = UUID.randomUUID().toString(),
                    playlistId = playlistId,
                    track = track,
                    addedAt = now
                )
            }

        if (newEntries.isEmpty()) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val nextEntries = currentEntries + newEntries
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id in targetPlaylistIds) {
                playlist.copy(
                    updatedAt = now
                )
            } else {
                playlist
            }
        }

        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = currentEntries,
            nextPlaylists = nextPlaylists,
            nextEntries = nextEntries,
            successValue = Unit
        )
    }

    /**
     * 为本地创建的歌单保留自定义封面入口；Provider 可以在映射歌单时提供同一字段。
     */
    suspend fun updatePlaylistCustomArtworkUri(
        id: String,
        artworkUri: String?
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        val currentPlaylist = currentPlaylists.firstOrNull { playlist -> playlist.id == id }
            ?: return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.NotFound
            )
        val normalizedArtworkUri = artworkUri?.trim()?.ifBlank { null }
        if (currentPlaylist.customArtworkUri == normalizedArtworkUri) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val now = System.currentTimeMillis()
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(
                    customArtworkUri = normalizedArtworkUri,
                    updatedAt = now
                )
            } else {
                playlist
            }
        }
        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = _playlistSongEntries.value,
            nextPlaylists = nextPlaylists,
            nextEntries = _playlistSongEntries.value,
            successValue = Unit
        )
    }

    suspend fun updatePlaylistDescription(
        id: String,
        description: String?
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        val currentPlaylist = currentPlaylists.firstOrNull { playlist -> playlist.id == id }
            ?: return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.NotFound
            )
        val normalizedDescription = description?.trim()?.ifBlank { null }
        if (currentPlaylist.description == normalizedDescription) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val now = System.currentTimeMillis()
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == id) {
                playlist.copy(
                    description = normalizedDescription,
                    updatedAt = now
                )
            } else {
                playlist
            }
        }
        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = _playlistSongEntries.value,
            nextPlaylists = nextPlaylists,
            nextEntries = _playlistSongEntries.value,
            successValue = Unit
        )
    }

    suspend fun addSongsToPlaylists(
        playlistIds: Set<String>,
        songs: List<Song>
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        val existingPlaylistIds = currentPlaylists.mapTo(mutableSetOf()) { it.id }
        val targetPlaylistIds = playlistIds
            .filterTo(mutableSetOf()) { it.isNotBlank() && it in existingPlaylistIds }
        if (targetPlaylistIds.isEmpty() || songs.isEmpty()) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val currentEntries = _playlistSongEntries.value
        // A multi-selection may include repeated playlist entries. Keep only one
        // playlist entry per song and treat a repeat add as a recency/weight boost.
        val uniqueSongs = songs.distinctBy { song -> song.toPersistentTrack().identityKey }
        val now = System.currentTimeMillis()
        var sequence = 0L
        val nextEntries = currentEntries.toMutableList()
        targetPlaylistIds.forEach { playlistId ->
            uniqueSongs.forEach { song ->
                val track = song.toPersistentTrack()
                val matchingIndices = nextEntries.indices.filter { index ->
                    nextEntries[index].playlistId == playlistId &&
                        nextEntries[index].track.identityKey == track.identityKey
                }
                val weightedAddedAt = now + sequence++
                if (matchingIndices.isEmpty()) {
                    nextEntries += PlaylistSongEntry(
                        id = UUID.randomUUID().toString(),
                        playlistId = playlistId,
                        track = track,
                        addedAt = weightedAddedAt
                    )
                } else {
                    // Preserve the newest existing entry, remove historical duplicates,
                    // then update its timestamp so the song receives a higher recency weight.
                    val retainedIndex = matchingIndices.maxBy { index ->
                        nextEntries[index].addedAt
                    }
                    val retainedEntry = nextEntries[retainedIndex].copy(addedAt = weightedAddedAt)
                    matchingIndices
                        .filter { index -> index != retainedIndex }
                        .sortedDescending()
                        .forEach(nextEntries::removeAt)
                    val updatedIndex = nextEntries.indexOfFirst { entry ->
                        entry.id == retainedEntry.id
                    }
                    if (updatedIndex >= 0) {
                        nextEntries[updatedIndex] = retainedEntry
                    }
                }
            }
        }
        if (nextEntries == currentEntries) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id in targetPlaylistIds) playlist.copy(updatedAt = now) else playlist
        }
        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = currentEntries,
            nextPlaylists = nextPlaylists,
            nextEntries = nextEntries,
            successValue = Unit
        )
    }

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        songId: String
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        if (currentPlaylists.none { playlist -> playlist.id == playlistId }) {
            return@withLock PlaylistMutationResult.Failure(
                PlaylistMutationError.NotFound
            )
        }

        val normalizedSongId = songId.trim()
        if (normalizedSongId.isEmpty()) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val currentEntries = _playlistSongEntries.value
        val nextEntries = currentEntries.filterNot { entry ->
            entry.playlistId == playlistId &&
                (entry.track as? PersistentTrack.Local)?.songId == normalizedSongId
        }
        if (nextEntries.size == currentEntries.size) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val now = System.currentTimeMillis()
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(updatedAt = now)
            } else {
                playlist
            }
        }

        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = currentEntries,
            nextPlaylists = nextPlaylists,
            nextEntries = nextEntries,
            successValue = Unit
        )
    }

    suspend fun removeEntriesFromPlaylist(
        playlistId: String,
        entryIds: Set<String>
    ): PlaylistMutationResult<Unit> = playlistMutex.withLock {
        val currentPlaylists = _playlists.value
        if (currentPlaylists.none { it.id == playlistId }) {
            return@withLock PlaylistMutationResult.Failure(PlaylistMutationError.NotFound)
        }
        if (entryIds.isEmpty()) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }
        val currentEntries = _playlistSongEntries.value
        val nextEntries = currentEntries.filterNot {
            it.playlistId == playlistId && it.id in entryIds
        }
        if (nextEntries.size == currentEntries.size) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }
        val now = System.currentTimeMillis()
        val nextPlaylists = currentPlaylists.map {
            if (it.id == playlistId) it.copy(updatedAt = now) else it
        }
        commitMutation(
            previousPlaylists = currentPlaylists,
            previousEntries = currentEntries,
            nextPlaylists = nextPlaylists,
            nextEntries = nextEntries,
            successValue = Unit
        )
    }

    fun getPlaylistSongEntries(playlistId: String): List<PlaylistSongEntry> {
        return sortedEntriesForPlaylist(
            playlistId = playlistId,
            entries = _playlistSongEntries.value
        )
    }

    fun observePlaylistSongEntries(playlistId: String): Flow<List<PlaylistSongEntry>> {
        return playlistSongEntries.map { entries ->
            sortedEntriesForPlaylist(
                playlistId = playlistId,
                entries = entries
            )
        }
    }

    fun getPlaylistSongCount(playlistId: String): Int {
        return _playlistSongEntries.value.count { entry -> entry.playlistId == playlistId }
    }

    fun playlistSongCount(playlistId: String): Int {
        return getPlaylistSongCount(playlistId)
    }

    fun songsForPlaylist(
        playlistId: String,
        availableSongs: List<Song>
    ): List<Song> {
        val songsById = availableSongs.associateBy { song -> song.id.toString() }
        return _playlistSongEntries.value
            .filter { entry -> entry.playlistId == playlistId }
            .sortedBy { entry -> entry.addedAt }
            .mapNotNull { entry ->
                (entry.track as? PersistentTrack.Local)?.songId?.let(songsById::get)
            }
    }

    private suspend fun <T> commitMutation(
        previousPlaylists: List<Playlist>,
        previousEntries: List<PlaylistSongEntry>,
        nextPlaylists: List<Playlist>,
        nextEntries: List<PlaylistSongEntry>,
        successValue: T
    ): PlaylistMutationResult<T> {
        _playlists.value = nextPlaylists
        _playlistSongEntries.value = nextEntries
        val saved = runCatching {
            withContext(Dispatchers.IO) {
                storage.saveSnapshot(
                    playlists = nextPlaylists,
                    entries = nextEntries
                )
            }
        }.getOrDefault(false)

        return if (saved) {
            PlaylistMutationResult.Success(successValue)
        } else {
            _playlists.value = previousPlaylists
            _playlistSongEntries.value = previousEntries
            PlaylistMutationResult.Failure(PlaylistMutationError.SaveFailed)
        }
    }
}

sealed class PlaylistMutationResult<out T> {
    data class Success<T>(val value: T) : PlaylistMutationResult<T>()
    data class Failure(val error: PlaylistMutationError) : PlaylistMutationResult<Nothing>()
}

enum class PlaylistMutationError {
    BlankTitle,
    DuplicateTitle,
    NotFound,
    SaveFailed,
    PersistentIdentityUnsupported
}

private fun normalizePlaylistOrder(playlists: List<Playlist>): List<Playlist> {
    return playlists
        .sortedBy { playlist -> playlist.order }
        .mapIndexed { index, playlist -> playlist.copy(order = index) }
}

private fun sortedEntriesForPlaylist(
    playlistId: String,
    entries: List<PlaylistSongEntry>
): List<PlaylistSongEntry> {
    return entries
        .filter { entry -> entry.playlistId == playlistId }
        .sortedBy { entry -> entry.addedAt }
}
