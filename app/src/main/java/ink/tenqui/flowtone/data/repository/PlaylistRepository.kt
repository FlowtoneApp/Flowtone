package ink.tenqui.flowtone.data.repository

import ink.tenqui.flowtone.core.model.Playlist
import ink.tenqui.flowtone.core.model.PlaylistCardStyle
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.PlaylistStorage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            val playlist = Playlist(
                id = UUID.randomUUID().toString(),
                title = normalizedTitle,
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
        val alreadyExists = currentEntries.any { entry ->
            entry.playlistId == playlistId && entry.songId == song.id
        }
        if (alreadyExists) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val now = System.currentTimeMillis()
        val nextEntry = PlaylistSongEntry(
            playlistId = playlistId,
            songId = song.id,
            addedAt = now,
            order = currentEntries.count { entry -> entry.playlistId == playlistId },
            titleSnapshot = song.title,
            artistSnapshot = song.artist,
            artworkUriSnapshot = song.artworkUri?.toString()
        )
        val nextEntries = currentEntries + nextEntry
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    subtitle = "${nextEntries.count { entry -> entry.playlistId == playlistId }} 首歌曲",
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
        val existingEntryKeys = currentEntries.mapTo(mutableSetOf()) { entry ->
            entry.playlistId to entry.songId
        }
        val now = System.currentTimeMillis()
        val newEntries = targetPlaylistIds
            .filterNot { playlistId -> playlistId to song.id in existingEntryKeys }
            .map { playlistId ->
                PlaylistSongEntry(
                    playlistId = playlistId,
                    songId = song.id,
                    addedAt = now,
                    order = currentEntries.count { entry -> entry.playlistId == playlistId },
                    titleSnapshot = song.title,
                    artistSnapshot = song.artist,
                    artworkUriSnapshot = song.artworkUri?.toString()
                )
            }

        if (newEntries.isEmpty()) {
            return@withLock PlaylistMutationResult.Success(Unit)
        }

        val nextEntries = currentEntries + newEntries
        val nextPlaylists = currentPlaylists.map { playlist ->
            if (playlist.id in targetPlaylistIds) {
                playlist.copy(
                    subtitle = "${nextEntries.count { entry -> entry.playlistId == playlist.id }} 首歌曲",
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

    fun playlistSongCount(playlistId: String): Int {
        return _playlistSongEntries.value.count { entry -> entry.playlistId == playlistId }
    }

    fun songsForPlaylist(
        playlistId: String,
        availableSongs: List<Song>
    ): List<Song> {
        val songsById = availableSongs.associateBy { song -> song.id }
        return _playlistSongEntries.value
            .filter { entry -> entry.playlistId == playlistId }
            .sortedWith(compareBy<PlaylistSongEntry> { entry -> entry.addedAt }
                .thenBy { entry -> entry.order })
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
