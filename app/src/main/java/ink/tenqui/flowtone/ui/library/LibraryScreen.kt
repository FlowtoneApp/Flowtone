package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Playlist
import ink.tenqui.flowtone.core.model.PlaylistAppearanceColorKey
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.likedSongsPlaylistCard
import ink.tenqui.flowtone.data.local.LibraryPlaylistCardStore
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedEndPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedStartPadding
import ink.tenqui.flowtone.ui.components.FlowtonePageHeaderExpandedTopPadding
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.viewmodel.MusicUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed class CreatePlaylistState {
    object Idle : CreatePlaylistState()
    object Editing : CreatePlaylistState()
    object Closing : CreatePlaylistState()
}

internal enum class PlaylistDialogMode {
    Create,
    Rename,
    Delete
}

internal enum class PlaylistDialogVisualStyle {
    Library,
    AddToPlaylist
}

internal class LibraryPlaylistController internal constructor(
    private val playlistStore: LibraryPlaylistCardStore,
    val listState: LazyListState,
    val panelProgress: Animatable<Float, AnimationVector1D>
) {
    private val editablePlaylistBounds = mutableMapOf<String, Rect>()

    var playlists by mutableStateOf(playlistStore.loadCards())
    var playlistName by mutableStateOf("")
    var createPlaylistState by mutableStateOf<CreatePlaylistState>(CreatePlaylistState.Idle)
    var playlistDialogMode by mutableStateOf(PlaylistDialogMode.Create)
    var playlistDialogVisualStyle by mutableStateOf(PlaylistDialogVisualStyle.Library)
    var dialogPlaylist by mutableStateOf<LibraryPlaylistCard?>(null)
    var dialogLocked by mutableStateOf(false)
    var editingPlaylistId by mutableStateOf<String?>(null)
        private set
    var editingPlaylistBounds by mutableStateOf<Rect?>(null)
        private set
    var libraryViewportBounds by mutableStateOf<Rect?>(null)
        private set
    var newlyCreatedPlaylistId by mutableStateOf<String?>(null)
    var shouldSavePlaylists by mutableStateOf(false)

    val duplicatePlaylistName: Boolean
        get() = if (dialogLocked) {
            false
        } else when (playlistDialogMode) {
            PlaylistDialogMode.Create -> hasDuplicatePlaylistTitle(playlistName, playlists)
            PlaylistDialogMode.Rename -> hasDuplicatePlaylistTitle(
                playlistName = playlistName,
                playlists = playlists,
                excludingPlaylistId = dialogPlaylist?.id
            )
            PlaylistDialogMode.Delete -> false
        }

    val canCreatePlaylist: Boolean
        get() = !dialogLocked &&
            when (playlistDialogMode) {
                PlaylistDialogMode.Create,
                PlaylistDialogMode.Rename ->
                    playlistName.trim().isNotEmpty() && !duplicatePlaylistName
                PlaylistDialogMode.Delete -> dialogPlaylist != null
            }

    fun startEditing(
        visualStyle: PlaylistDialogVisualStyle = PlaylistDialogVisualStyle.Library
    ) {
        if (createPlaylistState == CreatePlaylistState.Idle) {
            clearPlaylistEditing()
            playlistName = ""
            dialogPlaylist = null
            dialogLocked = false
            playlistDialogMode = PlaylistDialogMode.Create
            playlistDialogVisualStyle = visualStyle
            createPlaylistState = CreatePlaylistState.Editing
        }
    }

    fun startRenamePlaylist(playlist: LibraryPlaylistCard) {
        val editablePlaylist = playlists.firstOrNull { candidate ->
            candidate.id == playlist.id && !candidate.isSystem
        } ?: return
        if (createPlaylistState == CreatePlaylistState.Idle) {
            playlistName = editablePlaylist.title
            dialogPlaylist = editablePlaylist
            dialogLocked = false
            playlistDialogMode = PlaylistDialogMode.Rename
            playlistDialogVisualStyle = PlaylistDialogVisualStyle.Library
            createPlaylistState = CreatePlaylistState.Editing
        }
    }

    fun startDeletePlaylist(playlist: LibraryPlaylistCard) {
        val editablePlaylist = playlists.firstOrNull { candidate ->
            candidate.id == playlist.id && !candidate.isSystem
        } ?: return
        if (createPlaylistState == CreatePlaylistState.Idle) {
            playlistName = editablePlaylist.title
            dialogPlaylist = editablePlaylist
            dialogLocked = false
            playlistDialogMode = PlaylistDialogMode.Delete
            playlistDialogVisualStyle = PlaylistDialogVisualStyle.Library
            createPlaylistState = CreatePlaylistState.Editing
        }
    }

    fun closeEditing() {
        if (createPlaylistState == CreatePlaylistState.Editing) {
            createPlaylistState = CreatePlaylistState.Closing
        }
    }

    fun resetCreateState() {
        playlistName = ""
        dialogPlaylist = null
        dialogLocked = false
        playlistDialogMode = PlaylistDialogMode.Create
        playlistDialogVisualStyle = PlaylistDialogVisualStyle.Library
        createPlaylistState = CreatePlaylistState.Idle
    }

    fun lockDialog() {
        dialogLocked = true
    }

    fun unlockDialog() {
        dialogLocked = false
    }

    fun startPlaylistEditing(playlist: LibraryPlaylistCard) {
        if (createPlaylistState != CreatePlaylistState.Idle) {
            return
        }
        val editablePlaylist = playlists.firstOrNull { candidate ->
            candidate.id == playlist.id && !candidate.isSystem
        } ?: return
        editingPlaylistId = editablePlaylist.id
        editingPlaylistBounds = editablePlaylistBounds[editablePlaylist.id]
    }

    fun updateEditingPlaylistBounds(
        playlistId: String,
        bounds: Rect
    ) {
        editablePlaylistBounds[playlistId] = bounds
        if (editingPlaylistId == playlistId && editingPlaylistBounds != bounds) {
            editingPlaylistBounds = bounds
        }
    }

    fun removePlaylistBounds(playlistId: String) {
        editablePlaylistBounds.remove(playlistId)
    }

    fun startPlaylistEditingAt(positionInRoot: Offset): Boolean {
        val viewportBounds = libraryViewportBounds ?: return false
        if (
            positionInRoot.x !in viewportBounds.left..viewportBounds.right ||
            positionInRoot.y !in viewportBounds.top..viewportBounds.bottom
        ) {
            return false
        }
        val targetId = playlistIdAtPosition(
            boundsByPlaylistId = editablePlaylistBounds,
            excludedPlaylistId = editingPlaylistId,
            position = positionInRoot
        )
            ?: return false
        val playlist = playlists.firstOrNull { candidate ->
            candidate.id == targetId && !candidate.isSystem
        } ?: return false
        startPlaylistEditing(playlist)
        return editingPlaylistId == playlist.id
    }

    fun clearPlaylistEditing() {
        editingPlaylistId = null
        editingPlaylistBounds = null
    }

    fun updateLibraryViewportBounds(bounds: Rect) {
        if (libraryViewportBounds != bounds) {
            libraryViewportBounds = bounds
        }
    }

    suspend fun savePlaylists() {
        val playlistsToPersist = playlists
        withContext(Dispatchers.IO) {
            playlistStore.saveCards(playlistsToPersist)
        }
    }

    suspend fun savePlaylistsIfRequested() {
        if (!shouldSavePlaylists) {
            return
        }
        shouldSavePlaylists = false
        savePlaylists()
    }

    fun applySongCounts(entries: List<PlaylistSongEntry>) {
        val counts = entries.groupingBy { entry -> entry.playlistId }.eachCount()
        val updatedPlaylists = playlists.map { playlist ->
            val songCount = counts[playlist.id] ?: 0
            val subtitle = "$songCount \u9996\u6b4c\u66f2"
            if (playlist.subtitle == subtitle) {
                playlist
            } else {
                playlist.copy(subtitle = subtitle)
            }
        }
        if (updatedPlaylists != playlists) {
            playlists = updatedPlaylists
            shouldSavePlaylists = true
        }
    }

    fun applyRepositoryPlaylists(
        repositoryPlaylists: List<Playlist>,
        entries: List<PlaylistSongEntry>,
        createdPlaylistId: String? = null
    ) {
        val counts = entries.groupingBy { entry -> entry.playlistId }.eachCount()
        playlists = repositoryPlaylists
            .sortedBy { playlist -> playlist.order }
            .map { playlist ->
                val songCount = counts[playlist.id] ?: 0
                LibraryPlaylistCard(
                    id = playlist.id,
                    title = playlist.title,
                    subtitle = "$songCount \u9996\u6b4c\u66f2",
                    order = playlist.order,
                    appearanceColorKey = playlist.appearanceColorKey
                )
            }
        val activePlaylistIds = playlists.mapTo(mutableSetOf()) { playlist -> playlist.id }
        editablePlaylistBounds.keys.retainAll(activePlaylistIds)
        editingPlaylistId = editingPlaylistId?.takeIf { editingId ->
            playlists.any { playlist -> playlist.id == editingId && !playlist.isSystem }
        }
        if (editingPlaylistId == null) {
            editingPlaylistBounds = null
        }
        val dialogTargetStillExists = dialogPlaylist?.id?.let { dialogPlaylistId ->
            playlists.any { playlist -> playlist.id == dialogPlaylistId && !playlist.isSystem }
        } ?: true
        if (!dialogTargetStillExists && playlistDialogMode != PlaylistDialogMode.Create) {
            clearPlaylistEditing()
            closeEditing()
        }
        if (createdPlaylistId != null) {
            newlyCreatedPlaylistId = createdPlaylistId
        }
    }

    fun previewPlaylistAppearanceColor(
        playlistId: String,
        colorKey: PlaylistAppearanceColorKey
    ) {
        playlists = playlists.map { playlist ->
            if (playlist.id == playlistId && !playlist.isSystem) {
                playlist.copy(appearanceColorKey = colorKey)
            } else {
                playlist
            }
        }
    }

    fun consumeNewlyCreatedPlaylistAnimation(playlistId: String) {
        if (newlyCreatedPlaylistId == playlistId) {
            newlyCreatedPlaylistId = null
        }
    }
}

internal fun playlistIdAtPosition(
    boundsByPlaylistId: Map<String, Rect>,
    excludedPlaylistId: String?,
    position: Offset
): String? {
    return boundsByPlaylistId.entries
        .firstOrNull { (playlistId, bounds) ->
            playlistId != excludedPlaylistId &&
                position.x in bounds.left..bounds.right &&
                position.y in bounds.top..bounds.bottom
        }
        ?.key
}

@Composable
internal fun rememberLibraryPlaylistController(): LibraryPlaylistController {
    val context = LocalContext.current
    val playlistStore = remember(context) {
        LibraryPlaylistCardStore(context.applicationContext)
    }
    val listState = rememberLazyListState()
    val panelProgress = remember {
        Animatable(0f)
    }

    return remember(playlistStore, listState, panelProgress) {
        LibraryPlaylistController(
            playlistStore = playlistStore,
            listState = listState,
            panelProgress = panelProgress
        )
    }
}

@Composable
internal fun LibraryScreen(
    songCount: Int,
    likedSongCount: Int,
    onOpenLocalLibrary: () -> Unit,
    onOpenPlaylist: (LibraryPlaylistCard) -> Unit,
    visible: Boolean,
    flowCloudSpeed: Float,
    isFlowCloudPlaying: Boolean,
    playlistController: LibraryPlaylistController,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val likedPlaylist = remember(likedSongCount) {
        likedSongsPlaylistCard(likedSongCount)
    }
    val playlistRows = playlistController.playlists.chunked(2)
    val playlistCardHeight = LibraryInfoCardHeight * (4f / 3f)
    val playlistRowItemOffsetYPx = with(density) {
        playlistCardHeight.toPx() / LibraryItemSlideOffsetDivisor
    }
    val libraryCardsProgress = remember {
        Animatable(if (visible) 1f else 0f)
    }

    LaunchedEffect(visible) {
        if (!visible) {
            playlistController.clearPlaylistEditing()
        }
        libraryCardsProgress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = LinearEasing
            )
        )
    }

    LibraryHomeContent(
        songCount = songCount,
        visible = visible,
        likedPlaylist = likedPlaylist,
        playlistRows = playlistRows,
        playlistCardHeight = playlistCardHeight,
        libraryCardsProgress = libraryCardsProgress.value,
        playlistRowItemOffsetYPx = playlistRowItemOffsetYPx,
        flowCloudSpeed = flowCloudSpeed,
        isFlowCloudPlaying = isFlowCloudPlaying,
        listState = playlistController.listState,
        editingPlaylistId = playlistController.editingPlaylistId,
        newlyCreatedPlaylistId = playlistController.newlyCreatedPlaylistId,
        onOpenLocalLibrary = {
            playlistController.clearPlaylistEditing()
            onOpenLocalLibrary()
        },
        onCreatePlaylist = {
            playlistController.clearPlaylistEditing()
            playlistController.startEditing()
        },
        onCreateAnimationFinished = { playlist ->
            playlistController.consumeNewlyCreatedPlaylistAnimation(playlist.id)
        },
        onOpenPlaylist = { playlist ->
            playlistController.clearPlaylistEditing()
            onOpenPlaylist(playlist)
        },
        onStartPlaylistEditing = playlistController::startPlaylistEditing,
        onEditingPlaylistBoundsChanged = playlistController::updateEditingPlaylistBounds,
        onEditingPlaylistBoundsRemoved = playlistController::removePlaylistBounds,
        onLibraryViewportBoundsChanged = playlistController::updateLibraryViewportBounds,
        modifier = modifier
    )
}

private fun hasDuplicatePlaylistTitle(
    playlistName: String,
    playlists: List<LibraryPlaylistCard>,
    excludingPlaylistId: String? = null
): Boolean {
    val normalizedName = playlistName.trim()
    return normalizedName.isNotEmpty() && playlists.any { playlist ->
        playlist.id != excludingPlaylistId &&
            playlist.title.trim().equals(normalizedName, ignoreCase = true)
    }
}

@Composable
fun LocalLibraryScreen(
    title: String,
    uiState: MusicUiState,
    currentSong: Song?,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val showsSongList = uiState.hasPermission &&
        !uiState.isLoading &&
        uiState.errorMessage == null &&
        uiState.hasScanned &&
        uiState.songs.isNotEmpty()

    PlaylistDetailCollapsingHeaderScaffold(
        title = title,
        listState = listState.takeIf { showsSongList },
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        contentModifier = contentModifier,
        modifier = modifier
    ) {
        when {
            !uiState.hasPermission -> PermissionContent(
                permissionDenied = permissionDenied,
                onRequestPermission = onRequestPermission,
                modifier = Modifier.fillMaxSize()
            )

            uiState.isLoading -> CenterMessage(
                title = "\u6b63\u5728\u626b\u63cf\u672c\u5730\u97f3\u4e50",
                subtitle = "\u6211\u4eec\u6b63\u5728\u67e5\u627e\u8bbe\u5907\u4e2d\u7684\u97f3\u4e50\u6587\u4ef6",
                modifier = Modifier.fillMaxSize(),
                showProgress = true
            )

            uiState.errorMessage != null -> CenterMessage(
                title = uiState.errorMessage,
                modifier = Modifier.fillMaxSize()
            )

            !uiState.hasScanned -> CenterMessage(
                title = "\u51c6\u5907\u626b\u63cf\u672c\u5730\u97f3\u4e50",
                subtitle = "\u6388\u6743\u540e\u5c06\u81ea\u52a8\u663e\u793a\u53ef\u64ad\u653e\u7684\u6b4c\u66f2",
                modifier = Modifier.fillMaxSize()
            )

            uiState.songs.isEmpty() -> CenterMessage(
                title = "\u6ca1\u6709\u627e\u5230\u672c\u5730\u97f3\u4e50",
                subtitle = "\u8bf7\u786e\u8ba4\u8bbe\u5907\u4e2d\u5df2\u4fdd\u5b58\u97f3\u4e50\u6587\u4ef6",
                modifier = Modifier.fillMaxSize()
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = FlowtonePageHeaderExpandedTopPadding,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "local-library-header") {
                    PlaylistDetailHeaderListItem(
                        modifier = Modifier.padding(
                            start = FlowtonePageHeaderExpandedStartPadding,
                            end = FlowtonePageHeaderExpandedEndPadding
                        )
                    )
                }
                itemsIndexed(
                    items = uiState.songs,
                    key = { _, song -> song.id }
                ) { index, song ->
                    val firstVisibleSongIndex = (listState.firstVisibleItemIndex - 1)
                        .coerceAtLeast(0)
                    val visibleAnimationIndex = (index - firstVisibleSongIndex)
                        .coerceIn(0, 10)
                    SongListItem(
                        song = song,
                        isCurrentSong = currentSong?.id == song.id,
                        onClick = onSongClick,
                        modifier = itemModifier(visibleAnimationIndex)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String?,
    playlistTitle: String,
    allSongs: List<Song>,
    playlistSongEntries: List<PlaylistSongEntry>,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
    onCollapseProgressStateChange: (State<Float>?) -> Unit = {},
    headerModifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    suppressEmptyState: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = remember(playlistId) { LazyListState() }
    val playlistSongs = remember(playlistId, allSongs, playlistSongEntries) {
        if (playlistId == null) {
            emptyList()
        } else {
            val songsById = allSongs.associateBy { song -> song.id.toString() }
            playlistSongEntries
                .filter { entry -> entry.playlistId == playlistId }
                .sortedBy { entry -> entry.addedAt }
                .mapNotNull { entry -> songsById[entry.songId] }
        }
    }

    if (playlistSongs.isEmpty()) {
        PlaylistDetailCollapsingHeaderScaffold(
            title = playlistTitle,
            listState = null,
            onCollapseProgressStateChange = onCollapseProgressStateChange,
            headerModifier = headerModifier,
            contentModifier = contentModifier,
            modifier = modifier
        ) {
            EmptyPlaylistState(
                visible = !suppressEmptyState,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    PlaylistDetailCollapsingHeaderScaffold(
        title = playlistTitle,
        listState = listState,
        onCollapseProgressStateChange = onCollapseProgressStateChange,
        headerModifier = headerModifier,
        contentModifier = contentModifier,
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = FlowtonePageHeaderExpandedTopPadding,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "playlist-detail-header") {
                PlaylistDetailHeaderListItem(
                    modifier = Modifier.padding(
                        start = FlowtonePageHeaderExpandedStartPadding,
                        end = FlowtonePageHeaderExpandedEndPadding
                    )
                )
            }
            itemsIndexed(
                items = playlistSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                val firstVisibleSongIndex = (listState.firstVisibleItemIndex - 1)
                    .coerceAtLeast(0)
                val visibleAnimationIndex = (index - firstVisibleSongIndex)
                    .coerceIn(0, 10)
                SongListItem(
                    song = song,
                    isCurrentSong = currentSong?.id == song.id,
                    onClick = {
                        onSongClick(playlistSongs, index)
                    },
                    modifier = itemModifier(visibleAnimationIndex)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}
