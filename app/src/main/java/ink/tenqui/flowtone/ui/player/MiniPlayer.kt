package ink.tenqui.flowtone.ui.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

private const val FLOWTONE_FAVORITE_BUTTON_TAG = "FlowtoneFavoriteButton"

@Composable
fun MiniPlayer(
    playerUiState: PlayerUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    fullscreenHeight: Dp,
    allowFullscreenFromCollapsed: Boolean = false,
    allowFullscreenFromExpanded: Boolean = true,
    disablePausedArtworkTilt: Boolean = false,
    minimized: Boolean,
    onMinimizedChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlaybackOrderMode: () -> Unit,
    libraryPlaylists: List<LibraryPlaylistCard> = emptyList(),
    playlistIdsContainingCurrentSong: Set<String> = emptySet(),
    newlyCreatedPlaylistId: String? = null,
    onNewPlaylistCreateAnimationFinished: (String) -> Unit = {},
    onAddToPlaylistDialogBackgroundColorChange: (Color) -> Unit = {},
    onCreatePlaylistClick: () -> Unit = {},
    onAddSongToPlaylist: (LibraryPlaylistCard, () -> Unit) -> Unit = { _, onAdded ->
        onAdded()
    },
    sourceQueue: List<Song> = emptyList(),
    playbackQueue: List<Song> = emptyList(),
    allSongs: List<Song> = emptyList(),
    currentQueueIndex: Int = -1,
    queueDisplayOrder: QueueDisplayOrder = QueueDisplayOrder.PlaybackOrder,
    onQueueDisplayOrderChange: (QueueDisplayOrder) -> Unit = {},
    onPlayQueueSong: (Song) -> Unit = {},
    onPlayArtistSongQueue: (List<Song>, Int) -> Unit = { _, _ -> },
    likedSongKeys: List<String> = emptyList(),
    onToggleSongLiked: (Song) -> Unit = {},
    onOpenArtistRootPage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentSong = playerUiState.currentSong
    val hasCurrentSong = playerUiState.hasCurrentSong
    val title = currentSong?.title.orEmpty()
    val artist = currentSong?.artist.orEmpty()
    val callbacks = MiniPlayerCallbacks(
        onTogglePlayPause = onTogglePlayPause,
        onPlayPrevious = onPlayPrevious,
        onPlayNext = onPlayNext,
        onSeekTo = onSeekTo,
        onTogglePlaybackOrderMode = onTogglePlaybackOrderMode,
        onPlayQueueSong = onPlayQueueSong,
        onPlayArtistSongQueue = onPlayArtistSongQueue,
        onToggleSongLiked = onToggleSongLiked,
        onOpenArtistRootPage = onOpenArtistRootPage
    )
    var fullscreenContentMode by rememberSaveable {
        mutableStateOf(FullscreenContentMode.Playback)
    }
    var artistPlaceholderArtists by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var collapsedMetadataSwitchDirection by remember { mutableStateOf(1) }
    val artworkUri = playerUiState.artworkUri
    val useLocalArtworkLoading = currentSong?.sourceType == SourceType.Local
    val durationMs = playerUiState.durationMs
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val collapsedHeight = MiniPlayerCollapsedHeight
    val minimizedHeight = MiniPlayerMinimizedHeight
    val dragHotZoneHeight = MiniPlayerDragHotZoneHeight
    val swipeThresholdPx = with(density) { 40.dp.toPx() }
    val fullscreenSwipeThresholdPx = with(density) { 72.dp.toPx() }
    val songSwipeThresholdPx = with(density) { 64.dp.toPx() }
    val targetExpandedHeight = configuration.screenHeightDp.dp * 0.618f
    val widthBasedArtworkSize = if (configuration.screenWidthDp.dp * 0.76f < 340.dp) {
        configuration.screenWidthDp.dp * 0.76f
    } else {
        340.dp
    }
    val expandedHeight = if (targetExpandedHeight > collapsedHeight) {
        targetExpandedHeight
    } else {
        collapsedHeight
    }
    val fullscreenTargetHeight = if (fullscreenHeight > expandedHeight) {
        fullscreenHeight
    } else {
        expandedHeight
    }
    val heightLimitedArtworkSize = expandedHeight * 0.52f
    val expandedArtworkSize = if (widthBasedArtworkSize < heightLimitedArtworkSize) {
        widthBasedArtworkSize
    } else {
        heightLimitedArtworkSize
    }
    val expandedArtworkTop = 24.dp
    val expandedMetadataTop = expandedArtworkTop + expandedArtworkSize + 14.dp
    val expandedProgressTop = expandedMetadataTop + 76.dp
    val expandedControlsTop = expandedProgressTop + 58.dp
    val animationProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "MiniPlayerProgress"
    )
    val minimizedProgress by animateFloatAsState(
        targetValue = if (minimized) 0f else 1f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_MINIMIZE_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerMinimizedProgress"
    )
    val artworkAnimationProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = ARTWORK_ANIMATION_DURATION_MS,
            easing = ArtworkEasing
        ),
        label = "MiniPlayerArtworkProgress"
    )
    val artworkScaleProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = ARTWORK_ANIMATION_DURATION_MS,
            easing = if (expanded) {
                ArtworkScaleShrinkEasing
            } else {
                ArtworkScaleEasing
            }
        ),
        label = "MiniPlayerArtworkScaleProgress"
    )
    val baseHeight = lerpDp(minimizedHeight, collapsedHeight, minimizedProgress)
    val currentHeight = baseHeight + (expandedHeight - collapsedHeight) * animationProgress
    var isFullscreenPlayer by remember {
        mutableStateOf(fullscreen && expanded && hasCurrentSong)
    }
    val fullscreenProgress by animateFloatAsState(
        targetValue = if (fullscreen && expanded && hasCurrentSong) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "MiniPlayerFullscreenProgress",
        finishedListener = { finalValue ->
            isFullscreenPlayer = finalValue == 1f && fullscreen && expanded && hasCurrentSong
        }
    )
    val fullscreenInteractionActive = isFullscreenInteractionActive(
        fullscreen = fullscreen,
        fullscreenProgress = fullscreenProgress
    )
    val fullscreenContentExitProgress by animateFloatAsState(
        targetValue = if (
            shouldShowFullscreenContentExit(
                fullscreenContentMode = fullscreenContentMode,
                fullscreen = fullscreen,
                expanded = expanded,
                hasCurrentSong = hasCurrentSong
            )
        ) {
            1f
        } else {
            0f
        },
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "FullscreenContentExitProgress"
    )
    val addToPlaylistProgress by animateFloatAsState(
        targetValue = if (
            shouldShowAddToPlaylistContent(
                fullscreenContentMode = fullscreenContentMode,
                fullscreen = fullscreen,
                expanded = expanded,
                hasCurrentSong = hasCurrentSong
            )
        ) {
            1f
        } else {
            0f
        },
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "AddToPlaylistProgress"
    )
    val songInfoProgress by animateFloatAsState(
        targetValue = if (
            shouldShowSongInfoContent(
                fullscreenContentMode = fullscreenContentMode,
                fullscreen = fullscreen,
                expanded = expanded,
                hasCurrentSong = hasCurrentSong
            )
        ) {
            1f
        } else {
            0f
        },
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "FullscreenSongInfoProgress"
    )
    val artistPlaceholderActive = isArtistPlaceholderActive(
        fullscreenContentMode = fullscreenContentMode,
        fullscreen = fullscreen,
        expanded = expanded,
        hasCurrentSong = hasCurrentSong
    )
    val artistPlaceholderProgress by animateFloatAsState(
        targetValue = if (artistPlaceholderActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "ArtistPlaceholderProgress",
        finishedListener = { finalValue ->
            if (
                finalValue == 0f &&
                fullscreenContentMode != FullscreenContentMode.ArtistPlaceholder
            ) {
                artistPlaceholderArtists = emptyList()
            }
        }
    )
    val hostHeight = lerpDp(
        currentHeight + dragHotZoneHeight,
        fullscreenTargetHeight,
        fullscreenProgress
    )
    val visualPanelHeight = lerpDp(currentHeight, fullscreenTargetHeight, fullscreenProgress)
    val visualPanelTop = hostHeight - visualPanelHeight
    val handleOffsetY = (visualPanelHeight + dragHotZoneHeight) * fullscreenProgress
    val fullscreenCoverCenterY = fullscreenTargetHeight * 0.4f
    val fullscreenStationaryControlsOffsetY =
        (fullscreenTargetHeight - currentHeight) * fullscreenProgress
    val fullscreenControlsLiftY = 50.dp * fullscreenProgress
    val visibleProgress by animateFloatAsState(
        targetValue = if (hasCurrentSong) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "MiniPlayerVisibleProgress"
    )
    val hiddenOffsetDp = currentHeight + dragHotZoneHeight + 32.dp

    val miniPlayerSlideOffsetY by animateDpAsState(
        targetValue = if (hasCurrentSong) 0.dp else hiddenOffsetDp,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_SLIDE_ANIMATION_DURATION_MS,
            easing = if (hasCurrentSong) {
                MiniPlayerSlideInEasing
            } else {
                FastOutSlowInEasing
            }
        ),
        label = "MiniPlayerSlideOffsetY"
    )
    val backgroundImageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(256, 256)
                .crossfade(false)
                .build()
        }
    }
    val coverImageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(768, 768)
                .crossfade(false)
                .build()
        }
    }
    val paletteImageRequest: ImageRequest? = remember(artworkUri, context) {
        artworkUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .size(96, 96)
                .allowHardware(false)
                .crossfade(false)
                .build()
        }
    }
    MiniPlayerCoverImageEffects(
        coverImageRequest = coverImageRequest,
        context = context
    )
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val fallbackSeedColor = MaterialTheme.colorScheme.primary.toArgb()
    val fallbackSeedColors = remember(currentSong?.id, title, artist, artworkUri, fallbackSeedColor) {
        songFallbackCloudSeedColors(
            song = currentSong,
            fallbackColor = fallbackSeedColor
        )
    }
    val fallbackCloudColors = materialYouCloudColors(
        seedColors = fallbackSeedColors,
        isDarkTheme = isDarkTheme
    )
    val fallbackBackdrop = remember(
        currentSong?.id,
        title,
        artist,
        currentSong?.uri,
        fallbackSeedColor,
        isDarkTheme
    ) {
        PlayerBackdropState.Fallback(
            key = currentSong.toBackdropKey(),
            colors = normalizeBackdropColors(
                colors = fallbackCloudColors,
                isDarkTheme = isDarkTheme
            )
        )
    }
    var lastStableBackdrop by remember {
        mutableStateOf<PlayerBackdropState>(fallbackBackdrop)
    }
    var usingFallbackCloudColors by remember {
        mutableStateOf(true)
    }

    MiniPlayerBackdropEffects(
        currentSong = currentSong,
        title = title,
        artworkUri = artworkUri,
        fallbackSeedColor = fallbackSeedColor,
        isDarkTheme = isDarkTheme,
        paletteImageRequest = paletteImageRequest,
        backgroundImageRequest = backgroundImageRequest,
        coverImageRequest = coverImageRequest,
        fallbackSeedColors = fallbackSeedColors,
        fallbackCloudColors = fallbackCloudColors,
        fallbackBackdrop = fallbackBackdrop,
        lastStableBackdrop = lastStableBackdrop,
        usingFallbackCloudColors = usingFallbackCloudColors,
        context = context,
        onLastStableBackdropChange = { lastStableBackdrop = it },
        onUsingFallbackCloudColorsChange = { usingFallbackCloudColors = it }
    )
    val addToPlaylistDialogBackgroundColor = remember(lastStableBackdrop.colors) {
        coverTintDialogBackgroundColor(lastStableBackdrop.colors)
    }
    MiniPlayerAddToPlaylistDialogEffects(
        addToPlaylistDialogBackgroundColor = addToPlaylistDialogBackgroundColor,
        onAddToPlaylistDialogBackgroundColorChange = onAddToPlaylistDialogBackgroundColorChange
    )
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val titleColor = Color.White
    val artistColor = Color.White
    val controlIconColor = Color.White
    val progressTrackColor = Color(0xFF9E9E9E)
    val progressColor = Color.White
    var isProgressScrubbing by remember { mutableStateOf(false) }
    var lockedIsPlayingDuringScrub by remember { mutableStateOf(playerUiState.isPlaying) }
    var keepPlayPauseVisualLockedAfterSeek by remember { mutableStateOf(false) }
    var playPauseVisualLockToken by remember { mutableStateOf(0) }
    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var expandedMoreMenu by remember { mutableStateOf(false) }
    var queueSheetBackgroundBlurred by remember { mutableStateOf(false) }
    val artistPlaceholderLocalSongs = remember(artistPlaceholderArtists, allSongs) {
        if (artistPlaceholderArtists.size == 1) {
            localSongsForArtist(allSongs, artistPlaceholderArtists.first())
        } else {
            emptyList()
        }
    }
    val isCurrentSongLiked = currentSong?.let { song ->
        isSongLiked(song, likedSongKeys)
    } ?: false
    val onToggleCurrentSongLiked: () -> Unit = {
        currentSong?.let { song ->
            Log.d(
                FLOWTONE_FAVORITE_BUTTON_TAG,
                "favorite click songId=${song.id}, expanded=$expanded, fullscreen=$fullscreen, " +
                    "minimized=$minimized"
            )
            callbacks.onToggleSongLiked(song)
        }
        Unit
    }
    fun enterAddToPlaylistMode() {
        artistPlaceholderArtists = emptyList()
        expandedMoreMenu = false
        fullscreenContentMode = FullscreenContentMode.AddToPlaylist
    }
    fun enterSongInfoMode() {
        if (
            fullscreenContentMode != FullscreenContentMode.Playback ||
            !isFullscreenPlayer ||
            fullscreenContentExitProgress > 0.01f ||
            artistPlaceholderProgress > 0.01f
        ) {
            return
        }
        artistPlaceholderArtists = emptyList()
        expandedMoreMenu = false
        fullscreenContentMode = FullscreenContentMode.SongInfo
    }
    fun enterArtistPlaceholderMode(rawArtist: String) {
        if (
            fullscreenContentMode != FullscreenContentMode.Playback ||
            !isFullscreenPlayer ||
            fullscreenContentExitProgress > 0.01f ||
            artistPlaceholderProgress > 0.01f
        ) {
            return
        }
        val displayArtist = rawArtist.trim()
        if (!isSelectableArtist(displayArtist)) {
            return
        }
        val artists = parseArtistCandidates(displayArtist)
        if (artists.isEmpty()) {
            return
        }

        expandedMoreMenu = false
        artistPlaceholderArtists = artists
        fullscreenContentMode = FullscreenContentMode.ArtistPlaceholder
    }
    fun exitArtistPlaceholderWithAnimation() {
        expandedMoreMenu = false
        if (fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder) {
            fullscreenContentMode = FullscreenContentMode.Playback
        }
    }
    fun exitFullscreenContentMode() {
        when (fullscreenContentMode) {
            FullscreenContentMode.ArtistPlaceholder -> exitArtistPlaceholderWithAnimation()
            FullscreenContentMode.Playback -> {
                expandedMoreMenu = false
            }
            else -> {
                expandedMoreMenu = false
                artistPlaceholderArtists = emptyList()
                fullscreenContentMode = FullscreenContentMode.Playback
            }
        }
    }
    fun resetFullscreenContentMode() {
        expandedMoreMenu = false
        artistPlaceholderArtists = emptyList()
        if (fullscreenContentMode != FullscreenContentMode.Playback) {
            fullscreenContentMode = FullscreenContentMode.Playback
        }
    }
    fun exitFullscreenContentModeForSongChange() {
        val artistPlaceholderExitInProgress = isArtistPlaceholderExitInProgress(
            fullscreenContentMode = fullscreenContentMode,
            artistPlaceholderArtists = artistPlaceholderArtists,
            artistPlaceholderProgress = artistPlaceholderProgress
        )
        if (fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder) {
            exitArtistPlaceholderWithAnimation()
        } else if (!artistPlaceholderExitInProgress) {
            resetFullscreenContentMode()
        }
    }
    fun exitAddToPlaylistMode() {
        exitFullscreenContentMode()
    }
    BackHandler(
        enabled = fullscreenContentMode == FullscreenContentMode.AddToPlaylist &&
            fullscreenInteractionActive
    ) {
        exitAddToPlaylistMode()
    }
    BackHandler(
        enabled = fullscreenContentMode == FullscreenContentMode.SongInfo &&
            fullscreenInteractionActive
    ) {
        exitFullscreenContentMode()
    }
    BackHandler(
        enabled = fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder &&
            fullscreenInteractionActive
    ) {
        exitFullscreenContentMode()
    }
    MiniPlayerFullscreenContentEffects(
        fullscreen = fullscreen,
        expanded = expanded,
        hasCurrentSong = hasCurrentSong,
        currentSong = currentSong,
        onFullscreenPlayerChange = { isFullscreenPlayer = it },
        resetFullscreenContentMode = ::resetFullscreenContentMode,
        exitFullscreenContentModeForSongChange = ::exitFullscreenContentModeForSongChange
    )
    val artistClickEnabled = isArtistClickEnabled(
        isFullscreenPlayer = isFullscreenPlayer,
        fullscreenContentMode = fullscreenContentMode,
        fullscreenContentExitProgress = fullscreenContentExitProgress,
        artistPlaceholderProgress = artistPlaceholderProgress
    )
    fun handleArtistClick(rawArtist: String) {
        if (!artistClickEnabled) {
            return
        }
        enterArtistPlaceholderMode(rawArtist)
    }
    fun openArtistDetailFromPlaceholder(artistName: String) {
        val selectedArtistName = artistName.trim()
        if (!artistPlaceholderActive || selectedArtistName.isBlank()) {
            return
        }
        resetFullscreenContentMode()
        callbacks.onOpenArtistRootPage(selectedArtistName)
    }
    MiniPlayerSongTransitionEffects(
        currentSong = currentSong,
        onProgressScrubbingChange = { isProgressScrubbing = it }
    )
    val visualIsPlaying = if (isProgressScrubbing || keepPlayPauseVisualLockedAfterSeek) {
        lockedIsPlayingDuringScrub
    } else {
        playerUiState.isPlaying
    }
    val artworkPlaybackScale by animateFloatAsState(
        targetValue = if (visualIsPlaying || !hasCurrentSong) {
            1f
        } else {
            PAUSED_ARTWORK_SCALE
        },
        animationSpec = tween(
            durationMillis = PAUSE_ARTWORK_SCALE_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "ArtworkPlaybackScale"
    )
    val artworkPlaybackRotationDegrees by animateFloatAsState(
        targetValue = if (
            visualIsPlaying ||
            !hasCurrentSong ||
            disablePausedArtworkTilt
        ) {
            0f
        } else {
            PAUSED_ARTWORK_ROTATION_DEGREES
        },
        animationSpec = tween(
            durationMillis = PAUSE_ARTWORK_ROTATION_DURATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "ArtworkPlaybackRotation"
    )
    fun lockPlayPauseVisual(isPlayingToLock: Boolean) {
        lockedIsPlayingDuringScrub = isPlayingToLock
        keepPlayPauseVisualLockedAfterSeek = true
        playPauseVisualLockToken += 1
    }
    fun playPreviousFromMiniPlayer() {
        if (hasCurrentSong) {
            collapsedMetadataSwitchDirection = -1
            lockPlayPauseVisual(true)
            callbacks.onPlayPrevious()
        }
    }
    fun playNextFromMiniPlayer() {
        if (hasCurrentSong) {
            collapsedMetadataSwitchDirection = 1
            lockPlayPauseVisual(true)
            callbacks.onPlayNext()
        }
    }
    MiniPlayerPlayPauseVisualLockEffects(
        playPauseVisualLockToken = playPauseVisualLockToken,
        keepPlayPauseVisualLockedAfterSeek = keepPlayPauseVisualLockedAfterSeek,
        onKeepPlayPauseVisualLockedAfterSeekChange = {
            keepPlayPauseVisualLockedAfterSeek = it
        }
    )
    var accumulatedDragY by remember { mutableStateOf(0f) }
    val playbackGesturesEnabled = isPlaybackGestureContent(fullscreenContentMode)
    val fullscreenContentBackGesturesEnabled =
        isFullscreenContentBackGestureContent(fullscreenContentMode)
    val playerGesturesEnabled = isPlayerGesturesEnabled(
        playbackGesturesEnabled = playbackGesturesEnabled,
        fullscreenContentBackGesturesEnabled = fullscreenContentBackGesturesEnabled
    )
    val gestureModifier = if (playerGesturesEnabled) {
        Modifier.pointerInput(
            hasCurrentSong,
            expanded,
            fullscreenInteractionActive,
            fullscreenContentMode,
            minimized,
            swipeThresholdPx,
            fullscreenSwipeThresholdPx,
            allowFullscreenFromCollapsed,
            allowFullscreenFromExpanded
        ) {
            detectVerticalDragGestures(
                onDragStart = {
                    accumulatedDragY = 0f
                },
                onVerticalDrag = { _, dragAmount ->
                    if (hasCurrentSong) {
                        accumulatedDragY += dragAmount
                    }
                },
                onDragEnd = {
                    if (!hasCurrentSong) {
                        return@detectVerticalDragGestures
                    }
                    if (fullscreenContentBackGesturesEnabled) {
                        if (accumulatedDragY >= fullscreenSwipeThresholdPx) {
                            exitFullscreenContentMode()
                        }
                        return@detectVerticalDragGestures
                    }
                    when {
                        accumulatedDragY <= -swipeThresholdPx && minimized -> {
                            onMinimizedChange(false)
                        }
                        accumulatedDragY <= -fullscreenSwipeThresholdPx &&
                            !expanded &&
                            !fullscreenInteractionActive &&
                            allowFullscreenFromCollapsed -> {
                            onMinimizedChange(false)
                            onFullscreenChange(true)
                            onExpandedChange(true)
                        }
                        accumulatedDragY <= -fullscreenSwipeThresholdPx &&
                            expanded &&
                            !fullscreenInteractionActive &&
                            allowFullscreenFromExpanded -> {
                            onFullscreenChange(true)
                        }
                        accumulatedDragY <= -swipeThresholdPx && !expanded -> {
                            onExpandedChange(true)
                        }
                        accumulatedDragY >= fullscreenSwipeThresholdPx && fullscreenInteractionActive -> {
                            onFullscreenChange(false)
                        }
                        accumulatedDragY >= swipeThresholdPx && expanded && !fullscreenInteractionActive -> {
                            onExpandedChange(false)
                        }
                        accumulatedDragY >= swipeThresholdPx &&
                            !expanded &&
                            !fullscreenInteractionActive &&
                            !minimized -> {
                            onMinimizedChange(true)
                        }
                    }
                },
                onDragCancel = {
                    accumulatedDragY = 0f
                }
            )
        }
    } else {
        Modifier
    }
    val progressGestureStartY =
        expandedProgressTop + fullscreenStationaryControlsOffsetY - fullscreenControlsLiftY
    val progressGestureEndY = progressGestureStartY + 64.dp
    val progressGestureIgnoreRangePx = with(density) {
        progressGestureStartY.toPx()..progressGestureEndY.toPx()
    }
    val songSwipeModifier = Modifier.swipeToChangeSong(
        enabled = hasCurrentSong && playbackGesturesEnabled,
        thresholdPx = songSwipeThresholdPx,
        ignoredStartYRangePx = progressGestureIgnoreRangePx,
        onSwipeLeft = ::playNextFromMiniPlayer,
        onSwipeRight = ::playPreviousFromMiniPlayer
    )
    val addToPlaylistBackSwipeThresholdPx = with(density) {
        72.dp.toPx()
    }
    val addToPlaylistBackSwipeModifier = Modifier.addToPlaylistBackSwipeGesture(
        enabled = isAddToPlaylistBackGestureEnabled(fullscreenContentMode),
        thresholdPx = addToPlaylistBackSwipeThresholdPx,
        onBack = ::exitAddToPlaylistMode
    )
    val addToPlaylistPullDownThresholdPx = with(density) {
        64.dp.toPx()
    }
    val addToPlaylistListState = rememberLazyListState()
    val addToPlaylistPullDownBackModifier = Modifier.addToPlaylistPullDownBackGesture(
        enabled = isAddToPlaylistBackGestureEnabled(fullscreenContentMode),
        listState = addToPlaylistListState,
        thresholdPx = addToPlaylistPullDownThresholdPx,
        onBack = ::exitAddToPlaylistMode
    )
    val queueSheetBackgroundBlurProgress = remember { Animatable(0f) }
    MiniPlayerQueueEffects(
        queueSheetBackgroundBlurred = queueSheetBackgroundBlurred,
        queueSheetBackgroundBlurProgress = queueSheetBackgroundBlurProgress
    )
    val queueSheetBackgroundBlurRadius = 12.dp * queueSheetBackgroundBlurProgress.value
    val miniPlayerScene = miniPlayerScene(
        expanded = expanded,
        fullscreen = fullscreen,
        hasCurrentSong = hasCurrentSong,
        fullscreenContentMode = fullscreenContentMode,
        artistPlaceholderActive = artistPlaceholderActive,
        artistPlaceholderProgress = artistPlaceholderProgress,
        showQueueSheet = showQueueSheet
    )
    val lyricsHostCanAttach = canAttachMiniPlayerLyricsHost(
        scene = miniPlayerScene,
        fullscreenContentExitProgress = fullscreenContentExitProgress,
        artistPlaceholderProgress = artistPlaceholderProgress
    )
    MiniPlayerVisualSurface(
        hostHeight = hostHeight,
        miniPlayerSlideOffsetY = miniPlayerSlideOffsetY,
        visibleProgress = visibleProgress,
        fullscreenProgress = fullscreenProgress,
        queueSheetBackgroundBlurRadius = queueSheetBackgroundBlurRadius,
        animationProgress = animationProgress,
        visualPanelTop = visualPanelTop,
        visualPanelHeight = visualPanelHeight,
        dragHotZoneHeight = dragHotZoneHeight,
        handleOffsetY = handleOffsetY,
        hasCurrentSong = hasCurrentSong,
        expanded = expanded,
        interactionSource = noRippleInteractionSource,
        gestureModifier = gestureModifier,
        onActivate = {
            if (minimized) {
                onMinimizedChange(false)
            } else {
                onExpandedChange(true)
            }
        },
        modifier = modifier,
        panelContent = { playerShape ->
                BoxWithConstraints(
                    modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(visualPanelHeight)
                    .pointerInput(expandedMoreMenu, fullscreenContentMode) {
                        if (
                            !expandedMoreMenu ||
                            fullscreenContentMode != FullscreenContentMode.Playback
                        ) {
                            return@pointerInput
                        }

                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                expandedMoreMenu = false
                            }
                        }
                    }
                    .graphicsLayer {
                        shape = playerShape
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .then(addToPlaylistBackSwipeModifier)
                    .then(addToPlaylistPullDownBackModifier)
                ) {
                    val playerWidth = maxWidth
                    MiniPlayerBackgroundLayers(
                        gestureModifier = gestureModifier,
                        songSwipeModifier = songSwipeModifier,
                        backgroundColor = coverTintDialogBackgroundColor(lastStableBackdrop.colors),
                        backgroundImageRequest = lastStableBackdrop.backgroundImageRequest,
                        cloudColors = lastStableBackdrop.colors,
                        animationProgress = animationProgress,
                        isPlaying = playerUiState.isPlaying,
                        waitForArtworkLoad = useLocalArtworkLoading,
                    )
                    val addToPlaylistStatusBarsTop = with(density) {
                        WindowInsets.statusBars.getTop(this).toDp()
                    }
                    val fullscreenLayoutMetrics = miniPlayerFullscreenLayoutMetrics(
                        playerWidth = playerWidth,
                        visualPanelHeight = visualPanelHeight,
                        fullscreenCoverCenterY = fullscreenCoverCenterY,
                        addToPlaylistStatusBarsTop = addToPlaylistStatusBarsTop,
                        fullscreenContentExitProgress = fullscreenContentExitProgress,
                        artistPlaceholderProgress = artistPlaceholderProgress,
                        addToPlaylistProgress = addToPlaylistProgress
                    )
                    MiniPlayerFullscreenLayout(
                        imageRequest = coverImageRequest,
                        waitForArtworkLoad = useLocalArtworkLoading,
                        playerUiState = playerUiState,
                        title = title,
                        artist = artist,
                        hasCurrentSong = hasCurrentSong,
                        visualIsPlaying = visualIsPlaying,
                        currentHeight = currentHeight,
                        visualPanelHeight = visualPanelHeight,
                        collapsedHeight = collapsedHeight,
                        minimizedHeight = minimizedHeight,
                        expandedHeight = expandedHeight,
                        expandedArtworkSize = expandedArtworkSize,
                        expandedArtworkTop = expandedArtworkTop,
                        expandedMetadataTop = expandedMetadataTop,
                        expandedProgressTop = expandedProgressTop,
                        expandedControlsTop = expandedControlsTop,
                        playerWidth = playerWidth,
                        fullscreenProgress = fullscreenProgress,
                        animationProgress = animationProgress,
                        artworkAnimationProgress = artworkAnimationProgress,
                        artworkScaleProgress = artworkScaleProgress,
                        minimizedProgress = minimizedProgress,
                        fullscreenCoverCenterY = fullscreenCoverCenterY,
                        fullscreenStationaryControlsOffsetY = fullscreenStationaryControlsOffsetY,
                        fullscreenControlsLiftY = fullscreenControlsLiftY,
                        layoutMetrics = fullscreenLayoutMetrics,
                        artworkPlaybackScale = artworkPlaybackScale,
                        artworkPlaybackRotationDegrees = artworkPlaybackRotationDegrees,
                        titleColor = titleColor,
                        artistColor = artistColor,
                        controlIconColor = controlIconColor,
                        progressTrackColor = progressTrackColor,
                        progressColor = progressColor,
                        collapsedMetadataSwitchDirection = collapsedMetadataSwitchDirection,
                        artistClickEnabled = artistClickEnabled,
                        fullscreenContentMode = fullscreenContentMode,
                        libraryPlaylists = libraryPlaylists,
                        playlistIdsContainingCurrentSong = playlistIdsContainingCurrentSong,
                        newlyCreatedPlaylistId = newlyCreatedPlaylistId,
                        addToPlaylistListState = addToPlaylistListState,
                        isCurrentSongLiked = isCurrentSongLiked,
                        expandedMoreMenu = expandedMoreMenu,
                        lyricsHostCanAttach = lyricsHostCanAttach,
                        fullscreen = fullscreen,
                        expanded = expanded,
                        artistPlaceholderArtists = artistPlaceholderArtists,
                        artistPlaceholderLocalSongs = artistPlaceholderLocalSongs,
                        artistPlaceholderActive = artistPlaceholderActive,
                        artistPlaceholderProgress = artistPlaceholderProgress,
                        fullscreenSwipeThresholdPx = fullscreenSwipeThresholdPx,
                        songInfoProgress = songInfoProgress,
                        callbacks = callbacks,
                        collapseInteractionSource = noRippleInteractionSource,
                        onArtistClick = ::handleArtistClick,
                        onNewPlaylistCreateAnimationFinished =
                            onNewPlaylistCreateAnimationFinished,
                        onDismissAddToPlaylistAtTop = ::exitAddToPlaylistMode,
                        onCreatePlaylistClick = onCreatePlaylistClick,
                        onPlaylistClick = { playlist ->
                            onAddSongToPlaylist(playlist, ::exitAddToPlaylistMode)
                        },
                        onLockPlayPauseVisual = ::lockPlayPauseVisual,
                        onScrubbingChange = { scrubbing ->
                            isProgressScrubbing = scrubbing
                        },
                        onPlayPrevious = {
                            expandedMoreMenu = false
                            playPreviousFromMiniPlayer()
                        },
                        onTogglePlayPause = {
                            expandedMoreMenu = false
                            if (hasCurrentSong) {
                                isProgressScrubbing = false
                                keepPlayPauseVisualLockedAfterSeek = false
                                callbacks.onTogglePlayPause()
                            }
                        },
                        onPlayNext = {
                            expandedMoreMenu = false
                            playNextFromMiniPlayer()
                        },
                        onMoreMenuExpandedChange = { expanded ->
                            expandedMoreMenu = expanded
                        },
                        onToggleLiked = onToggleCurrentSongLiked,
                        onAddToPlaylist = {
                            enterAddToPlaylistMode()
                        },
                        onOpenSongInfo = {
                            enterSongInfoMode()
                        },
                        onOpenQueue = {
                            queueSheetBackgroundBlurred = true
                            showQueueSheet = true
                        },
                        onArtistHostBack = ::exitFullscreenContentMode,
                        onArtistHostArtistClick = ::openArtistDetailFromPlaceholder,
                        onCollapseClick = {
                            if (fullscreenContentMode != FullscreenContentMode.Playback) {
                                exitFullscreenContentMode()
                            } else if (allowFullscreenFromCollapsed) {
                                onFullscreenChange(false)
                                onExpandedChange(false)
                            } else {
                                onFullscreenChange(false)
                            }
                        }
                    )
            }
        },
        overlayContent = {
            MiniPlayerQueueSheetHost(
            showQueueSheet = showQueueSheet,
            playbackQueue = playbackQueue,
            sourceQueue = sourceQueue,
            currentQueueIndex = currentQueueIndex,
            currentSong = currentSong,
            displayOrder = queueDisplayOrder,
            onDisplayOrderChange = onQueueDisplayOrderChange,
            backgroundImageRequest = lastStableBackdrop.backgroundImageRequest,
            cloudColors = lastStableBackdrop.colors,
            backgroundProgress = animationProgress,
            isPlaying = playerUiState.isPlaying,
            waitForArtworkLoad = useLocalArtworkLoading,
            onSongClick = callbacks.onPlayQueueSong,
            onDismissStart = {
                queueSheetBackgroundBlurred = false
            },
            onDismiss = {
                showQueueSheet = false
            }
            )
        }
    )
}
