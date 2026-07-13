package ink.tenqui.flowtone.data.repository

import ink.tenqui.flowtone.core.model.Playlist
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.core.model.PlaylistCardStyle
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Song
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
                        updatedAt = now
                    )
                } else {
                    existing.copy(
                        title = card.title,
                        subtitle = card.subtitle,
                        appearanceColorKey = card.appearanceColorKey
                            ?: existing.appearanceColorKey,
                        order = index,
                        updatedAt = if (
                            existing.title != card.title ||
                            existing.subtitle != card.subtitle ||
                            (
                                card.appearanceColorKey != null &&
                                    existing.appearanceColorKey != card.appearanceColorKey
                            ) ||
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
                updatedAt = now
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
        val songId = song.stablePlaylistSongId()
        val alreadyExists = currentEntries.any { entry ->
            entry.playlistId == playlistId && entry.songId == songId
        }
        if (alreadyExists) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val now = System.currentTimeMillis()
        val nextEntry = PlaylistSongEntry(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            songId = songId,
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
        val songId = song.stablePlaylistSongId()
        val existingEntryKeys = currentEntries.mapTo(mutableSetOf()) { entry ->
            entry.playlistId to entry.songId
        }
        val now = System.currentTimeMillis()
        val newEntries = targetPlaylistIds
            .filterNot { playlistId -> playlistId to songId in existingEntryKeys }
            .map { playlistId ->
                PlaylistSongEntry(
                    id = UUID.randomUUID().toString(),
                    playlistId = playlistId,
                    songId = songId,
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
            entry.playlistId == playlistId && entry.songId == normalizedSongId
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
        val songsById = availableSongs.associateBy { song -> song.stablePlaylistSongId() }
        return _playlistSongEntries.value
            .filter { entry -> entry.playlistId == playlistId }
            .sortedBy { entry -> entry.addedAt }
            .mapNotNull { entry -> songsById[entry.songId] }
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
    SaveFailed
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

private fun Song.stablePlaylistSongId(): String {
    return id.toString()
}
