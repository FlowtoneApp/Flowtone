package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LibraryInfoCardHeight = 112.dp
private val PlaylistCardHeight = 88.dp
private val CreatePlaylistPanelHeight = 236.dp
private val CreatePlaylistPanelMinWidth = 280.dp
private val CreatePlaylistPanelMaxWidth = 360.dp
private val LibraryActionCardSpacing = 12.dp
private const val CreatePlaylistCardWidthFraction = 0.312f
private const val CreatePlaylistScrimMaxAlpha = 0.18f
private const val CreatePlaylistPanelStartScale = 0.96f
private const val CreatePlaylistPanelExitScale = 0.98f

private sealed class CreatePlaylistState {
    object Idle : CreatePlaylistState()
    object Editing : CreatePlaylistState()
    object Closing : CreatePlaylistState()
    data class AnimatingWindowToCard(
        val playlist: LibraryPlaylistCard
    ) : CreatePlaylistState()
}

@Composable
fun LibraryScreen(
    songCount: Int,
    onOpenLocalLibrary: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val playlistStore = remember(context) {
        LibraryPlaylistCardStore(context.applicationContext)
    }
    var playlists by remember(playlistStore) {
        mutableStateOf(playlistStore.loadCards())
    }
    var playlistName by rememberSaveable {
        mutableStateOf("")
    }
    var createPlaylistState by remember {
        mutableStateOf<CreatePlaylistState>(CreatePlaylistState.Idle)
    }
    var libraryRootBounds by remember {
        mutableStateOf<Rect?>(null)
    }
    var createWindowBounds by remember {
        mutableStateOf<Rect?>(null)
    }
    var targetPlaylistCardBounds by remember {
        mutableStateOf<Rect?>(null)
    }
    val listState = rememberLazyListState()
    val panelProgress = remember {
        Animatable(0f)
    }
    val flyingProgress = remember {
        Animatable(0f)
    }
    var revealTargetCardBehindFlying by remember {
        mutableStateOf(false)
    }
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
    val animatingState = createPlaylistState as? CreatePlaylistState.AnimatingWindowToCard
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val duplicatePlaylistName = hasDuplicatePlaylistTitle(playlistName, playlists)
    val canCreatePlaylist = playlistName.trim().isNotEmpty() && !duplicatePlaylistName

    LaunchedEffect(createPlaylistState) {
        when (val state = createPlaylistState) {
            CreatePlaylistState.Editing -> {
                panelProgress.snapTo(0f)
                panelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtoneMotion.Easing
                    )
                )
            }

            CreatePlaylistState.Closing -> {
                panelProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis / 2,
                        easing = FlowtoneMotion.Easing
                    )
                )
                if (createPlaylistState == CreatePlaylistState.Closing) {
                    playlistName = ""
                    createPlaylistState = CreatePlaylistState.Idle
                    createWindowBounds = null
                    panelProgress.snapTo(0f)
                }
            }

            is CreatePlaylistState.AnimatingWindowToCard -> {
                panelProgress.snapTo(1f)
                flyingProgress.snapTo(0f)
                revealTargetCardBehindFlying = false
                if (playlists.none { playlist -> playlist.id == state.playlist.id }) {
                    playlists = playlists + state.playlist
                    withFrameNanos { }
                }
                val targetIndex = playlists
                    .indexOfFirst { playlist -> playlist.id == state.playlist.id }
                    .takeIf { index -> index >= 0 }
                    ?: playlists.lastIndex.coerceAtLeast(0)
                listState.animateScrollToItem(index = 1 + targetIndex)

                var remainingFrames = 60
                while (
                    (createWindowBounds == null || targetPlaylistCardBounds == null) &&
                    remainingFrames > 0
                ) {
                    withFrameNanos { }
                    remainingFrames -= 1
                }

                val startBounds = createWindowBounds
                val targetBounds = targetPlaylistCardBounds
                if (startBounds == null || targetBounds == null) {
                    flyingProgress.snapTo(1f)
                    revealTargetCardBehindFlying = true
                    withFrameNanos { }
                    playlistName = ""
                    createPlaylistState = CreatePlaylistState.Idle
                    flyingProgress.snapTo(0f)
                    revealTargetCardBehindFlying = false
                    panelProgress.snapTo(0f)
                    val playlistsToPersist = playlists
                    withContext(Dispatchers.IO) {
                        playlistStore.saveCards(playlistsToPersist)
                    }
                    return@LaunchedEffect
                }

                flyingProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtoneMotion.Easing
                    )
                )
                revealTargetCardBehindFlying = true
                withFrameNanos { }

                val currentAnimatingState =
                    createPlaylistState as? CreatePlaylistState.AnimatingWindowToCard
                if (currentAnimatingState?.playlist?.id == state.playlist.id) {
                    playlistName = ""
                    createPlaylistState = CreatePlaylistState.Idle
                    createWindowBounds = null
                    targetPlaylistCardBounds = null
                }
                flyingProgress.snapTo(0f)
                revealTargetCardBehindFlying = false
                panelProgress.snapTo(0f)
                val playlistsToPersist = playlists
                withContext(Dispatchers.IO) {
                    playlistStore.saveCards(playlistsToPersist)
                }
            }

            CreatePlaylistState.Idle -> Unit
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { coordinates ->
                libraryRootBounds = coordinates.boundsInRoot()
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 8.dp,
                end = 20.dp,
                bottom = 24.dp
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
                                    if (createPlaylistState == CreatePlaylistState.Idle) {
                                        playlistName = ""
                                        targetPlaylistCardBounds = null
                                        createPlaylistState = CreatePlaylistState.Editing
                                    }
                                },
                                modifier = Modifier
                                    .width(createCardWidth)
                                    .height(LibraryInfoCardHeight)
                            )
                        }
                    }
                }
            }

            items(
                items = playlists,
                key = { playlist -> playlist.id }
            ) { playlist ->
                val isAnimatingTarget = animatingState?.playlist?.id == playlist.id
                Box(modifier = Modifier.fillMaxWidth()) {
                    LibraryPlaylistCardView(
                        playlist = playlist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PlaylistCardHeight)
                            .alpha(
                                if (isAnimatingTarget && !revealTargetCardBehindFlying) {
                                    0f
                                } else {
                                    1f
                                }
                            )
                            .then(
                                if (isAnimatingTarget) {
                                    Modifier.onGloballyPositioned { coordinates ->
                                        targetPlaylistCardBounds = coordinates.boundsInRoot()
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }

        if (scrimAlpha > 0.001f || createPlaylistState != CreatePlaylistState.Idle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = {
                            if (createPlaylistState == CreatePlaylistState.Editing) {
                                createPlaylistState = CreatePlaylistState.Closing
                            }
                        }
                    )
            )
        }

        if (
            createPlaylistState == CreatePlaylistState.Editing ||
            createPlaylistState == CreatePlaylistState.Closing
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = maxWidth - 56.dp
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
                    fraction = panelProgress.value.coerceIn(0f, 1f)
                )

                CreatePlaylistPanel(
                    playlistName = playlistName,
                    canCreate = canCreatePlaylist,
                    showDuplicateNameMessage = duplicatePlaylistName,
                    onPlaylistNameChange = { value ->
                        playlistName = value
                    },
                    onCancel = {
                        createPlaylistState = CreatePlaylistState.Closing
                    },
                    onCreate = {
                        val title = playlistName.trim()
                        if (title.isNotEmpty() && !hasDuplicatePlaylistTitle(title, playlists)) {
                            val order = (playlists.maxOfOrNull { playlist ->
                                playlist.order
                            } ?: -1) + 1
                            val playlist = LibraryPlaylistCard(
                                id = UUID.randomUUID().toString(),
                                title = title,
                                order = order
                            )
                            targetPlaylistCardBounds = null
                            playlists = playlists + playlist
                            createPlaylistState =
                                CreatePlaylistState.AnimatingWindowToCard(playlist)
                        }
                    },
                    modifier = Modifier
                        .width(panelWidth)
                        .height(CreatePlaylistPanelHeight)
                        .onGloballyPositioned { coordinates ->
                            createWindowBounds = coordinates.boundsInRoot()
                        }
                        .graphicsLayer {
                            alpha = panelProgress.value.coerceIn(0f, 1f)
                            scaleX = panelScale
                            scaleY = panelScale
                        }
                )
            }
        }

        if (animatingState != null && libraryRootBounds != null && createWindowBounds != null) {
            val rootBounds = libraryRootBounds
            val startBounds = createWindowBounds
            if (rootBounds != null && startBounds != null) {
                val targetBounds = targetPlaylistCardBounds ?: startBounds
                val progress = flyingProgress.value.coerceIn(-0.05f, 1.05f)
                val startRect = startBounds.relativeTo(rootBounds)
                val targetRect = targetBounds.relativeTo(rootBounds)
                val flyingLeft = lerpFloat(startRect.left, targetRect.left, progress)
                val flyingTop = lerpFloat(startRect.top, targetRect.top, progress)
                val flyingWidth = lerpFloat(startRect.width, targetRect.width, progress)
                val flyingHeight = lerpFloat(startRect.height, targetRect.height, progress)

                if (flyingWidth > 1f && flyingHeight > 1f) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = flyingLeft.roundToInt(),
                                    y = flyingTop.roundToInt()
                                )
                            }
                            .size(
                                width = with(density) { flyingWidth.toDp() },
                                height = with(density) { flyingHeight.toDp() }
                            )
                    ) {
                        FlyingPlaylistCreationCard(
                            title = animatingState.playlist.title,
                            progress = flyingProgress.value,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
private fun LibraryPlaylistCardView(
    playlist: LibraryPlaylistCard,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
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

@Composable
private fun FlyingPlaylistCreationCard(
    title: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val boundedProgress = progress.coerceIn(0f, 1f)
    val formAlpha = 1f - ((boundedProgress - 0.22f) / 0.36f).coerceIn(0f, 1f)
    val cardAlpha = ((boundedProgress - 0.52f) / 0.32f).coerceIn(0f, 1f)
    val cornerRadius = lerpDp(28.dp, 24.dp, boundedProgress)
    val containerColor = lerp(
        start = MaterialTheme.colorScheme.surfaceContainerHigh,
        stop = MaterialTheme.colorScheme.surfaceContainer,
        fraction = boundedProgress
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        tonalElevation = lerpDp(4.dp, 0.dp, boundedProgress),
        shadowElevation = lerpDp(18.dp, 0.dp, boundedProgress)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(formAlpha)
                    .padding(24.dp)
            ) {
                Text(
                    text = "\u521b\u5efa\u6b4c\u5355",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(top = 18.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u53d6\u6d88",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u521b\u5efa",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(cardAlpha)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "0 \u9996\u6b4c\u66f2",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun Rect.relativeTo(root: Rect): Rect {
    return Rect(
        left = left - root.left,
        top = top - root.top,
        right = right - root.left,
        bottom = bottom - root.top
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
    return start + (stop - start) * fraction
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
