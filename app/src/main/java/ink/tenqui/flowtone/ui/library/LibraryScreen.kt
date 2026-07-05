package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.LibraryPlaylistCardStore
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.LibraryCollectionCard
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.StaggeredPageElement
import ink.tenqui.flowtone.viewmodel.MusicUiState
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LibraryInfoCardHeight = 112.dp
private val CreatePlaylistPanelHeight = 236.dp
private val CreatePlaylistPanelMinWidth = 280.dp
private val CreatePlaylistPanelMaxWidth = 360.dp
private val CreatePlaylistPanelCornerRadius = 28.dp
private val CreatePlaylistShadowSafePadding = 48.dp
private val LibraryActionCardSpacing = 12.dp
private val LibraryPlaylistRowStartOffsetY = 20.dp
private val NewPlaylistRowStartOffsetY = 10.dp
private const val CreatePlaylistCardWidthFraction = 0.312f
private const val CreatePlaylistScrimMaxAlpha = 0.18f
private const val CreatePlaylistPanelStartScale = 0.96f
private const val CreatePlaylistPanelExitScale = 0.98f
private const val LibraryPlaylistRowDelayFraction = 0.06f
private const val LibraryPlaylistRowMaxDelayFraction = 0.18f
private const val LibraryPlaylistRowDurationFraction = 0.72f

internal sealed class CreatePlaylistState {
    object Idle : CreatePlaylistState()
    object Editing : CreatePlaylistState()
    object Closing : CreatePlaylistState()
}

internal class LibraryPlaylistController internal constructor(
    private val playlistStore: LibraryPlaylistCardStore,
    val listState: LazyListState,
    val panelProgress: Animatable<Float, AnimationVector1D>
) {
    var playlists by mutableStateOf(playlistStore.loadCards())
    var playlistName by mutableStateOf("")
    var createPlaylistState by mutableStateOf<CreatePlaylistState>(CreatePlaylistState.Idle)
    var shouldSavePlaylists by mutableStateOf(false)

    val duplicatePlaylistName: Boolean
        get() = hasDuplicatePlaylistTitle(playlistName, playlists)

    val canCreatePlaylist: Boolean
        get() = playlistName.trim().isNotEmpty() && !duplicatePlaylistName

    fun startEditing() {
        if (createPlaylistState == CreatePlaylistState.Idle) {
            playlistName = ""
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
        createPlaylistState = CreatePlaylistState.Idle
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
    onOpenLocalLibrary: () -> Unit,
    visible: Boolean,
    playlistController: LibraryPlaylistController,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val playlistRows = playlistController.playlists.chunked(2)
    val playlistRowStartOffsetYPx = with(density) {
        LibraryPlaylistRowStartOffsetY.toPx()
    }
    val newPlaylistRowStartOffsetYPx = with(density) {
        NewPlaylistRowStartOffsetY.toPx()
    }
    val libraryCardsProgress = remember {
        Animatable(if (visible) 1f else 0f)
    }
    val knownPlaylistRowKeys = remember {
        mutableStateListOf<String>().apply {
            addAll(playlistRows.map { rowPlaylists -> rowPlaylists.toLibraryPlaylistRowKey() })
        }
    }

    LaunchedEffect(visible) {
        libraryCardsProgress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = FlowtoneMotion.DurationMillis,
                easing = FlowtoneMotion.Easing
            )
        )
    }

    LazyColumn(
        state = playlistController.listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
                            onClick = onOpenLocalLibrary,
                            modifier = Modifier
                                .width(localLibraryWidth)
                                .height(LibraryInfoCardHeight)
                        )
                        CreatePlaylistEntryCard(
                            onClick = {
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
            val cardHeight = LibraryInfoCardHeight * (4f / 3f)
            val rowKey = rowPlaylists.toLibraryPlaylistRowKey()
            val isKnownRow = rowKey in knownPlaylistRowKeys
            val rowAppearProgress = remember(rowKey) {
                Animatable(if (isKnownRow) 1f else 0f)
            }

            LaunchedEffect(rowKey) {
                if (!isKnownRow && rowKey !in knownPlaylistRowKeys) {
                    knownPlaylistRowKeys.add(rowKey)
                    rowAppearProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = FlowtoneMotion.DurationMillis,
                            easing = FlowtoneMotion.Easing
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .libraryPlaylistRowMotion(
                        globalProgress = libraryCardsProgress.value,
                        rowIndex = rowIndex + 1,
                        pageStartOffsetYPx = playlistRowStartOffsetYPx,
                        rowAppearProgress = rowAppearProgress.value,
                        rowAppearStartOffsetYPx = newPlaylistRowStartOffsetYPx
                    ),
                horizontalArrangement = Arrangement.spacedBy(LibraryActionCardSpacing)
            ) {
                rowPlaylists.forEach { playlist ->
                    LibraryPlaylistTileCardView(
                        playlist = playlist,
                        modifier = Modifier
                            .weight(1f)
                            .height(cardHeight)
                    )
                }

                if (rowPlaylists.size == 1) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(cardHeight)
                    )
                }
            }
        }
    }
}

@Composable
internal fun CreatePlaylistOverlay(
    playlistController: LibraryPlaylistController,
    modifier: Modifier = Modifier
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val createPlaylistState = playlistController.createPlaylistState
    val scrimAlpha by animateFloatAsState(
        targetValue = if (createPlaylistState == CreatePlaylistState.Editing) {
            CreatePlaylistScrimMaxAlpha
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
                        onClick = playlistController::closeEditing
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
                        onPlaylistNameChange = { value ->
                            playlistController.playlistName = value
                        },
                        onCancel = {
                            playlistController.closeEditing()
                        },
                        onCreate = {
                            val title = playlistController.playlistName.trim()
                            if (
                                title.isNotEmpty() &&
                                !hasDuplicatePlaylistTitle(
                                    title,
                                    playlistController.playlists
                                )
                            ) {
                                val order = (
                                    playlistController.playlists.maxOfOrNull { playlist ->
                                        playlist.order
                                    } ?: -1
                                    ) + 1
                                val playlist = LibraryPlaylistCard(
                                    id = UUID.randomUUID().toString(),
                                    title = title,
                                    order = order
                                )
                                playlistController.playlists =
                                    playlistController.playlists + playlist
                                playlistController.shouldSavePlaylists = true
                                playlistController.closeEditing()
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

@Composable
private fun LibraryPlaylistTileCardView(
    playlist: LibraryPlaylistCard,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
}

@Composable
private fun CreatePlaylistPanel(
    playlistName: String,
    canCreate: Boolean,
    showDuplicateNameMessage: Boolean,
    onPlaylistNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(CreatePlaylistPanelCornerRadius)
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        modifier = modifier,
        shape = panelShape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "\u521b\u5efa\u6b4c\u5355",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = playlistName,
                onValueChange = onPlaylistNameChange,
                placeholder = {
                    Text(text = "\u6b4c\u5355\u540d")
                },
                singleLine = true,
                supportingText = if (showDuplicateNameMessage) {
                    {
                        Text(text = "\u5df2\u5b58\u5728\u540c\u540d\u6b4c\u5355")
                    }
                } else {
                    null
                },
                isError = showDuplicateNameMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = "\u53d6\u6d88")
                }
                Button(
                    onClick = onCreate,
                    enabled = canCreate,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor =
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor =
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(text = "\u521b\u5efa")
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
    pageStartOffsetYPx: Float,
    rowAppearProgress: Float,
    rowAppearStartOffsetYPx: Float
): Modifier {
    val rowProgress = libraryPlaylistRowProgress(
        globalProgress = globalProgress,
        rowIndex = rowIndex
    )
    val easedProgress = FlowtoneMotion.Easing.transform(rowProgress)
    val easedAppearProgress = FlowtoneMotion.Easing.transform(rowAppearProgress.coerceIn(0f, 1f))
    return graphicsLayer {
        alpha = easedProgress * easedAppearProgress
        translationY = pageStartOffsetYPx * (1f - easedProgress) +
            rowAppearStartOffsetYPx * (1f - easedAppearProgress)
    }
}

private fun List<LibraryPlaylistCard>.toLibraryPlaylistRowKey(): String {
    return joinToString(separator = "_") { playlist -> playlist.id }
}

private fun libraryPlaylistRowProgress(
    globalProgress: Float,
    rowIndex: Int
): Float {
    val delay = (rowIndex.coerceAtLeast(0) * LibraryPlaylistRowDelayFraction)
        .coerceAtMost(LibraryPlaylistRowMaxDelayFraction)
    return ((globalProgress.coerceIn(0f, 1f) - delay) / LibraryPlaylistRowDurationFraction)
        .coerceIn(0f, 1f)
}

private fun hasDuplicatePlaylistTitle(
    playlistName: String,
    playlists: List<LibraryPlaylistCard>
): Boolean {
    val normalizedName = playlistName.trim()
    return normalizedName.isNotEmpty() && playlists.any { playlist ->
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
