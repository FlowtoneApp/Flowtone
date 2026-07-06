package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Playlist
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.likedSongsPlaylistCard
import ink.tenqui.flowtone.data.local.LibraryPlaylistCardStore
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.LibraryCollectionCard
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.StaggeredPageElement
import ink.tenqui.flowtone.viewmodel.MusicUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LibraryInfoCardHeight = 112.dp
private val CreatePlaylistPanelHeight = 236.dp
private val CreatePlaylistPanelMinWidth = 280.dp
private val CreatePlaylistPanelMaxWidth = 360.dp
private val CreatePlaylistPanelCornerRadius = 28.dp
private val CreatePlaylistShadowSafePadding = 48.dp
private val LibraryActionCardSpacing = 12.dp
private const val CreatePlaylistCardWidthFraction = 0.312f
private const val CreatePlaylistScrimMaxAlpha = 0.18f
private const val CreatePlaylistPanelStartScale = 0.96f
private const val CreatePlaylistPanelExitScale = 0.98f
private const val LibraryItemSlideOffsetDivisor = 6f

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
    var playlists by mutableStateOf(playlistStore.loadCards())
    var playlistName by mutableStateOf("")
    var createPlaylistState by mutableStateOf<CreatePlaylistState>(CreatePlaylistState.Idle)
    var playlistDialogMode by mutableStateOf(PlaylistDialogMode.Create)
    var playlistDialogVisualStyle by mutableStateOf(PlaylistDialogVisualStyle.Library)
    var dialogPlaylist by mutableStateOf<LibraryPlaylistCard?>(null)
    var dialogLocked by mutableStateOf(false)
    var activePlaylistActionId by mutableStateOf<String?>(null)
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
            activePlaylistActionId = null
            playlistName = ""
            dialogPlaylist = null
            dialogLocked = false
            playlistDialogMode = PlaylistDialogMode.Create
            playlistDialogVisualStyle = visualStyle
            createPlaylistState = CreatePlaylistState.Editing
        }
    }

    fun startRenamePlaylist(playlist: LibraryPlaylistCard) {
        if (createPlaylistState == CreatePlaylistState.Idle) {
            playlistName = playlist.title
            dialogPlaylist = playlist
            dialogLocked = false
            playlistDialogMode = PlaylistDialogMode.Rename
            playlistDialogVisualStyle = PlaylistDialogVisualStyle.Library
            createPlaylistState = CreatePlaylistState.Editing
        }
    }

    fun startDeletePlaylist(playlist: LibraryPlaylistCard) {
        if (createPlaylistState == CreatePlaylistState.Idle) {
            playlistName = playlist.title
            dialogPlaylist = playlist
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

    fun showPlaylistActions(playlistId: String) {
        activePlaylistActionId = playlistId
    }

    fun clearPlaylistActions() {
        activePlaylistActionId = null
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
                    order = playlist.order
                )
            }
        activePlaylistActionId = activePlaylistActionId?.takeIf { activeId ->
            playlists.any { playlist -> playlist.id == activeId }
        }
        if (createdPlaylistId != null) {
            newlyCreatedPlaylistId = createdPlaylistId
        }
    }

    fun consumeNewlyCreatedPlaylistAnimation(playlistId: String) {
        if (newlyCreatedPlaylistId == playlistId) {
            newlyCreatedPlaylistId = null
        }
    }
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
    playlistController: LibraryPlaylistController,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val playlists = remember(playlistController.playlists, likedSongCount) {
        listOf(likedSongsPlaylistCard(likedSongCount)) + playlistController.playlists
    }
    val playlistRows = playlists.chunked(2)
    val playlistCardHeight = LibraryInfoCardHeight * (4f / 3f)
    val playlistRowItemOffsetYPx = with(density) {
        playlistCardHeight.toPx() / LibraryItemSlideOffsetDivisor
    }
    val libraryCardsProgress = remember {
        Animatable(if (visible) 1f else 0f)
    }

    LaunchedEffect(visible) {
        if (!visible) {
            playlistController.clearPlaylistActions()
        }
        libraryCardsProgress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = LinearEasing
            )
        )
    }

    LazyColumn(
        state = playlistController.listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = playlistController::clearPlaylistActions
            ),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 8.dp,
            end = 20.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "library-actions") {
            StaggeredPageElement(
                visible = visible,
                animationIndex = 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val createCardWidth = maxWidth * CreatePlaylistCardWidthFraction
                    val localLibraryWidth =
                        maxWidth - createCardWidth - LibraryActionCardSpacing

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(LibraryActionCardSpacing)
                    ) {
                        LibraryCollectionCard(
                            title = "\u672c\u5730\u66f2\u5e93",
                            subtitle = "$songCount \u9996\u6b4c\u66f2",
                            onClick = {
                                playlistController.clearPlaylistActions()
                                onOpenLocalLibrary()
                            },
                            modifier = Modifier
                                .width(localLibraryWidth)
                                .height(LibraryInfoCardHeight)
                        )
                        CreatePlaylistEntryCard(
                            onClick = {
                                playlistController.clearPlaylistActions()
                                playlistController.startEditing()
                            },
                            modifier = Modifier
                                .width(createCardWidth)
                                .height(LibraryInfoCardHeight)
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = playlistRows,
            key = { _, rowPlaylists ->
                rowPlaylists.toLibraryPlaylistRowKey()
            }
        ) { rowIndex, rowPlaylists ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .libraryPlaylistRowMotion(
                        globalProgress = libraryCardsProgress.value,
                        rowIndex = rowIndex + 1,
                        rowAppearProgress = 1f,
                        itemOffsetYPx = playlistRowItemOffsetYPx
                    ),
                horizontalArrangement = Arrangement.spacedBy(LibraryActionCardSpacing)
            ) {
                rowPlaylists.forEach { playlist ->
                    val showActions = playlistController.activePlaylistActionId == playlist.id
                    val editable = !playlist.isSystem
                    LibraryPlaylistTileCardView(
                        playlist = playlist,
                        cardHeight = playlistCardHeight,
                        showActions = editable && showActions,
                        playCreateAnimation =
                            playlistController.newlyCreatedPlaylistId == playlist.id,
                        onCreateAnimationFinished = {
                            playlistController.consumeNewlyCreatedPlaylistAnimation(
                                playlist.id
                            )
                        },
                        onClick = {
                            playlistController.clearPlaylistActions()
                            onOpenPlaylist(playlist)
                        },
                        onLongClick = {
                            if (editable) {
                                playlistController.showPlaylistActions(playlist.id)
                            }
                        },
                        onEdit = {
                            playlistController.startRenamePlaylist(playlist)
                        },
                        onDelete = {
                            playlistController.startDeletePlaylist(playlist)
                        },
                        modifier = Modifier
                            .weight(1f)
                    )
                }

                if (rowPlaylists.size == 1) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(playlistCardHeight)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CreatePlaylistOverlay(
    playlistController: LibraryPlaylistController,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    addToPlaylistDialogBackgroundColor: Color = Color(0xFF1B1B20),
    modifier: Modifier = Modifier
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val createPlaylistState = playlistController.createPlaylistState
    val scrimMaxAlpha = if (
        playlistController.playlistDialogVisualStyle == PlaylistDialogVisualStyle.AddToPlaylist
    ) {
        0.34f
    } else {
        CreatePlaylistScrimMaxAlpha
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (createPlaylistState == CreatePlaylistState.Editing) {
            scrimMaxAlpha
        } else {
            0f
        },
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "CreatePlaylistScrim"
    )

    LaunchedEffect(createPlaylistState) {
        when (createPlaylistState) {
            CreatePlaylistState.Editing -> {
                playlistController.panelProgress.snapTo(0f)
                playlistController.panelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtoneMotion.Easing
                    )
                )
            }

            CreatePlaylistState.Closing -> {
                playlistController.panelProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis / 2,
                        easing = FlowtoneMotion.Easing
                    )
                )
                if (playlistController.createPlaylistState == CreatePlaylistState.Closing) {
                    playlistController.resetCreateState()
                    playlistController.panelProgress.snapTo(0f)
                    playlistController.savePlaylistsIfRequested()
                }
            }

            CreatePlaylistState.Idle -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        if (scrimAlpha > 0.001f || createPlaylistState != CreatePlaylistState.Idle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = {
                            if (!playlistController.dialogLocked) {
                                playlistController.closeEditing()
                            }
                        }
                    )
            )
        }

        if (
            createPlaylistState == CreatePlaylistState.Editing ||
            createPlaylistState == CreatePlaylistState.Closing
        ) {
            val availableWidth = maxWidth - CreatePlaylistShadowSafePadding -
                CreatePlaylistShadowSafePadding
            val panelWidth = when {
                availableWidth < CreatePlaylistPanelMinWidth -> availableWidth
                availableWidth > CreatePlaylistPanelMaxWidth -> CreatePlaylistPanelMaxWidth
                else -> availableWidth
            }

            val panelMinScale = if (createPlaylistState == CreatePlaylistState.Closing) {
                CreatePlaylistPanelExitScale
            } else {
                CreatePlaylistPanelStartScale
            }
            val panelScale = lerpFloat(
                start = panelMinScale,
                stop = 1f,
                fraction = playlistController.panelProgress.value.coerceIn(0f, 1f)
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = playlistController.panelProgress.value.coerceIn(0f, 1f)
                            scaleX = panelScale
                            scaleY = panelScale
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        }
                        .padding(CreatePlaylistShadowSafePadding),
                    contentAlignment = Alignment.Center
                ) {
                    CreatePlaylistPanel(
                        playlistName = playlistController.playlistName,
                        canCreate = playlistController.canCreatePlaylist,
                        showDuplicateNameMessage = playlistController.duplicatePlaylistName,
                        dialogLocked = playlistController.dialogLocked,
                        mode = playlistController.playlistDialogMode,
                        visualStyle = playlistController.playlistDialogVisualStyle,
                        addToPlaylistDialogBackgroundColor =
                            addToPlaylistDialogBackgroundColor,
                        onPlaylistNameChange = { value ->
                            playlistController.playlistName = value
                        },
                        onCancel = {
                            playlistController.closeEditing()
                        },
                        onCreate = {
                            when (playlistController.playlistDialogMode) {
                                PlaylistDialogMode.Create -> {
                                    val title = playlistController.playlistName.trim()
                                    if (playlistController.canCreatePlaylist) {
                                        playlistController.lockDialog()
                                        onCreatePlaylist(title)
                                    }
                                }
                                PlaylistDialogMode.Rename -> {
                                    val playlist = playlistController.dialogPlaylist
                                    val title = playlistController.playlistName.trim()
                                    if (
                                        playlist != null &&
                                        playlistController.canCreatePlaylist
                                    ) {
                                        playlistController.lockDialog()
                                        onRenamePlaylist(playlist.id, title)
                                    }
                                }
                                PlaylistDialogMode.Delete -> {
                                    if (playlistController.canCreatePlaylist) {
                                        playlistController.lockDialog()
                                    }
                                    playlistController.dialogPlaylist?.let { playlist ->
                                        onDeletePlaylist(playlist.id)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .width(panelWidth)
                            .height(CreatePlaylistPanelHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = "\u521b\u5efa\u6b4c\u5355",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = "\u521b\u5efa\u6b4c\u5355",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPlaylistTileCardView(
    playlist: LibraryPlaylistCard,
    cardHeight: androidx.compose.ui.unit.Dp,
    showActions: Boolean,
    playCreateAnimation: Boolean,
    onCreateAnimationFinished: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionProgress by animateFloatAsState(
        targetValue = if (showActions) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis / 2,
            easing = FlowtoneMotion.Easing
        ),
        label = "LibraryPlaylistActionButtons"
    )
    val createProgress = remember(playlist.id) {
        Animatable(if (playCreateAnimation) 0f else 1f)
    }
    LaunchedEffect(playCreateAnimation, playlist.id) {
        if (playCreateAnimation) {
            createProgress.snapTo(0f)
            createProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
            onCreateAnimationFinished()
        } else if (createProgress.value < 1f) {
            createProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (
                        FlowtoneMotion.DurationMillis * (1f - createProgress.value)
                        ).toInt().coerceAtLeast(1),
                    easing = FlowtoneMotion.Easing
                )
            )
        }
    }
    val actionButtonColor = MaterialTheme.colorScheme.onSurface
    val editActionIconSize = 24.dp
    val editActionTouchSize = 36.dp

    Box(
        modifier = modifier.height(cardHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .graphicsLayer {
                    val eased = FlowtoneMotion.Easing.transform(
                        createProgress.value.coerceIn(0f, 1f)
                    )
                    alpha = eased
                    translationY = 18.dp.toPx() * (1f - eased)
                }
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(end = 44.dp),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlist.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (showActions || actionProgress > 0.001f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .graphicsLayer {
                            val eased = FlowtoneMotion.Easing.transform(
                                actionProgress.coerceIn(0f, 1f)
                            )
                            alpha = eased
                            translationX = 18.dp.toPx() * (1f - eased)
                            scaleX = 0.96f + 0.04f * eased
                            scaleY = 0.96f + 0.04f * eased
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(editActionTouchSize)
                            .clickable(onClick = onEdit),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "\u7f16\u8f91\u6b4c\u5355",
                            tint = actionButtonColor,
                            modifier = Modifier.size(editActionIconSize)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(editActionTouchSize)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "\u5220\u9664\u6b4c\u5355",
                            tint = actionButtonColor,
                            modifier = Modifier.size(editActionIconSize)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistPanel(
    playlistName: String,
    canCreate: Boolean,
    showDuplicateNameMessage: Boolean,
    dialogLocked: Boolean,
    mode: PlaylistDialogMode,
    visualStyle: PlaylistDialogVisualStyle,
    addToPlaylistDialogBackgroundColor: Color,
    onPlaylistNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(CreatePlaylistPanelCornerRadius)
    val addToPlaylistStyle = visualStyle == PlaylistDialogVisualStyle.AddToPlaylist
    val containerColor = if (addToPlaylistStyle) {
        addToPlaylistDialogBackgroundColor.copy(alpha = 1f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (addToPlaylistStyle) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryContentColor = if (addToPlaylistStyle) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val panelBorder = if (addToPlaylistStyle) {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
    } else {
        null
    }
    val shadowElevation = if (addToPlaylistStyle) 0.dp else 18.dp
    val titleText = when (mode) {
        PlaylistDialogMode.Create -> "\u521b\u5efa\u6b4c\u5355"
        PlaylistDialogMode.Rename -> "\u7f16\u8f91\u6b4c\u5355"
        PlaylistDialogMode.Delete -> "\u5220\u9664\u6b4c\u5355"
    }
    val confirmText = when (mode) {
        PlaylistDialogMode.Create -> "\u521b\u5efa"
        PlaylistDialogMode.Rename -> "\u4fdd\u5b58"
        PlaylistDialogMode.Delete -> "\u5220\u9664"
    }
    val showTitleError = showDuplicateNameMessage && !dialogLocked

    Surface(
        modifier = modifier,
        shape = panelShape,
        color = containerColor,
        border = panelBorder,
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(panelShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                if (mode == PlaylistDialogMode.Delete) {
                    Text(
                        text = "\u786e\u5b9a\u8981\u5220\u9664\u8fd9\u4e2a\u6b4c\u5355\u5417\uff1f",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryContentColor,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = onPlaylistNameChange,
                        enabled = !dialogLocked,
                        placeholder = {
                            Text(
                                text = "\u6b4c\u5355\u540d\u79f0",
                                color = secondaryContentColor
                            )
                        },
                        singleLine = true,
                        textStyle = if (addToPlaylistStyle) {
                            LocalTextStyle.current.copy(color = contentColor)
                        } else {
                            LocalTextStyle.current
                        },
                        supportingText = if (showTitleError) {
                            {
                                Text(
                                    text = "\u5df2\u5b58\u5728\u540c\u540d\u6b4c\u5355",
                                    color = if (addToPlaylistStyle) {
                                        secondaryContentColor
                                    } else {
                                        Color.Unspecified
                                    }
                                )
                            }
                        } else {
                            null
                        },
                        isError = showTitleError,
                        colors = if (addToPlaylistStyle) {
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = contentColor,
                                unfocusedTextColor = contentColor,
                                disabledTextColor = contentColor.copy(alpha = 0.72f),
                                errorTextColor = contentColor,
                                focusedBorderColor = contentColor,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.58f),
                                disabledBorderColor = Color.White.copy(alpha = 0.34f),
                                errorBorderColor = contentColor,
                                focusedPlaceholderColor = secondaryContentColor,
                                unfocusedPlaceholderColor = secondaryContentColor,
                                disabledPlaceholderColor = secondaryContentColor,
                                errorPlaceholderColor = secondaryContentColor,
                                cursorColor = contentColor,
                                errorCursorColor = contentColor,
                                focusedLabelColor = contentColor,
                                unfocusedLabelColor = secondaryContentColor,
                                disabledLabelColor = secondaryContentColor,
                                errorLabelColor = contentColor
                            )
                        } else {
                            OutlinedTextFieldDefaults.colors()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = !dialogLocked
                    ) {
                        Text(
                            text = "\u53d6\u6d88",
                            color = if (addToPlaylistStyle) contentColor else Color.Unspecified
                        )
                    }
                    Button(
                        onClick = onCreate,
                        enabled = canCreate && !dialogLocked,
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor =
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(text = confirmText)
                    }
                }
            }
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun Modifier.libraryPlaylistRowMotion(
    globalProgress: Float,
    rowIndex: Int,
    rowAppearProgress: Float,
    itemOffsetYPx: Float
): Modifier {
    val rowProgress = libraryPlaylistRowProgress(
        globalProgress = globalProgress,
        rowIndex = rowIndex
    )
    val easedProgress = FlowtoneMotion.Easing.transform(rowProgress)
    val easedAppearProgress = FlowtoneMotion.Easing.transform(rowAppearProgress.coerceIn(0f, 1f))
    val itemProgress = easedProgress * easedAppearProgress
    return graphicsLayer {
        alpha = itemProgress
        translationY = itemOffsetYPx * (1f - itemProgress)
    }
}

private fun List<LibraryPlaylistCard>.toLibraryPlaylistRowKey(): String {
    return joinToString(separator = "_") { playlist -> playlist.id }
}

private fun libraryPlaylistRowProgress(
    globalProgress: Float,
    rowIndex: Int
): Float {
    val delayMillis = FlowtoneMotion.staggerDelayMillis(rowIndex).toFloat()
    val durationMillis = FlowtoneMotion.staggerDurationMillis(rowIndex)
        .coerceAtLeast(1)
        .toFloat()
    val elapsedMillis = globalProgress.coerceIn(0f, 1f) * FlowtoneMotion.DurationMillis
    return ((elapsedMillis - delayMillis) / durationMillis).coerceIn(0f, 1f)
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
    uiState: MusicUiState,
    currentSong: Song?,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSongClick: (Song) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    when {
        !uiState.hasPermission -> PermissionContent(
            permissionDenied = permissionDenied,
            onRequestPermission = onRequestPermission,
            modifier = modifier
        )

        uiState.isLoading -> CenterMessage(
            title = "\u6b63\u5728\u626b\u63cf\u672c\u5730\u97f3\u4e50",
            subtitle = "\u6211\u4eec\u6b63\u5728\u67e5\u627e\u8bbe\u5907\u4e2d\u7684\u97f3\u4e50\u6587\u4ef6",
            modifier = modifier,
            showProgress = true
        )

        uiState.errorMessage != null -> CenterMessage(
            title = uiState.errorMessage,
            modifier = modifier
        )

        !uiState.hasScanned -> CenterMessage(
            title = "\u51c6\u5907\u626b\u63cf\u672c\u5730\u97f3\u4e50",
            subtitle = "\u6388\u6743\u540e\u5c06\u81ea\u52a8\u663e\u793a\u53ef\u64ad\u653e\u7684\u6b4c\u66f2",
            modifier = modifier
        )

        uiState.songs.isEmpty() -> CenterMessage(
            title = "\u6ca1\u6709\u627e\u5230\u672c\u5730\u97f3\u4e50",
            subtitle = "\u8bf7\u786e\u8ba4\u8bbe\u5907\u4e2d\u5df2\u4fdd\u5b58\u97f3\u4e50\u6587\u4ef6",
            modifier = modifier
        )

        else -> LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(
                items = uiState.songs,
                key = { _, song -> song.id }
            ) { index, song ->
                val visibleAnimationIndex = (
                    index - listState.firstVisibleItemIndex
                    ).coerceIn(0, 10)
                SongListItem(
                    song = song,
                    isCurrentSong = currentSong?.id == song.id,
                    onClick = onSongClick,
                    modifier = itemModifier(visibleAnimationIndex)
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String?,
    allSongs: List<Song>,
    playlistSongEntries: List<PlaylistSongEntry>,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    itemModifier: (Int) -> Modifier = { Modifier },
    suppressEmptyState: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
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
        EmptyPlaylistState(
            visible = !suppressEmptyState,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(
            items = playlistSongs,
            key = { _, song -> song.id }
        ) { index, song ->
            val visibleAnimationIndex = (
                index - listState.firstVisibleItemIndex
                ).coerceIn(0, 10)
            SongListItem(
                song = song,
                isCurrentSong = currentSong?.id == song.id,
                onClick = {
                    onSongClick(playlistSongs, index)
                },
                modifier = itemModifier(visibleAnimationIndex)
            )
        }
    }
}

@Composable
private fun EmptyPlaylistState(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var messageVisible by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        messageVisible = visible
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = messageVisible,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                ),
                initialOffsetY = { with(density) { 12.dp.roundToPx() } }
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                ),
                targetOffsetY = { with(density) { 12.dp.roundToPx() } }
            )
        ) {
            Text(
                text = "\u6b64\u6b4c\u5355\u4e2d\u6682\u65e0\u6b4c\u66f2",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionContent(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (permissionDenied) {
                "\u65e0\u6cd5\u8bbf\u95ee\u672c\u5730\u97f3\u4e50"
            } else {
                "\u9700\u8981\u97f3\u9891\u6743\u9650"
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (permissionDenied) {
                "\u6743\u9650\u88ab\u62d2\u7edd\uff0c\u53ef\u4ee5\u518d\u6b21\u6388\u6743\u540e\u7ee7\u7eed\u626b\u63cf"
            } else {
                "\u6388\u6743\u540e\uff0cFlowtone \u624d\u80fd\u626b\u63cf\u5e76\u64ad\u653e\u672c\u5730\u97f3\u4e50"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            modifier = Modifier.padding(top = 24.dp),
            onClick = onRequestPermission
        ) {
            Text(text = "\u6388\u4e88\u6743\u9650")
        }
    }
}

@Composable
private fun CenterMessage(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
