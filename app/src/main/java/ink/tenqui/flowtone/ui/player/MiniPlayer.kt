package ink.tenqui.flowtone.ui.player

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    val artworkUri = playerUiState.artworkUri
    val useLocalArtworkLoading = currentSong?.sourceType == SourceType.Local
    val durationMs = playerUiState.durationMs
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val fallbackSeedColor = MaterialTheme.colorScheme.primary.toArgb()
    val state = rememberMiniPlayerState(
        fullscreen = fullscreen,
        expanded = expanded,
        hasCurrentSong = hasCurrentSong,
        initialIsPlaying = playerUiState.isPlaying,
        currentSong = currentSong,
        title = title,
        artist = artist,
        artworkUri = artworkUri,
        fallbackSeedColor = fallbackSeedColor,
        isDarkTheme = isDarkTheme,
        context = context,
        allSongs = allSongs
    )
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
    val fullscreenProgress by animateFloatAsState(
        targetValue = if (fullscreen && expanded && hasCurrentSong) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = MiniPlayerEasing
        ),
        label = "MiniPlayerFullscreenProgress",
        finishedListener = { finalValue ->
            state.isFullscreenPlayer =
                finalValue == 1f && fullscreen && expanded && hasCurrentSong
        }
    )
    val fullscreenInteractionActive = isFullscreenInteractionActive(
        fullscreen = fullscreen,
        fullscreenProgress = fullscreenProgress
    )
    val fullscreenContentExitProgress by animateFloatAsState(
        targetValue = if (
            shouldShowFullscreenContentExit(
                fullscreenContentMode = state.fullscreenContentMode,
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
                fullscreenContentMode = state.fullscreenContentMode,
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
                fullscreenContentMode = state.fullscreenContentMode,
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
        fullscreenContentMode = state.fullscreenContentMode,
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
                state.fullscreenContentMode != FullscreenContentMode.ArtistPlaceholder
            ) {
                state.artistPlaceholderArtists = emptyList()
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
    MiniPlayerCoverImageEffects(
        coverImageRequest = state.coverImageRequest,
        context = context
    )
    MiniPlayerBackdropEffects(
        currentSong = currentSong,
        title = title,
        artworkUri = artworkUri,
        fallbackSeedColor = fallbackSeedColor,
        isDarkTheme = isDarkTheme,
        paletteImageRequest = state.paletteImageRequest,
        backgroundImageRequest = state.backgroundImageRequest,
        coverImageRequest = state.coverImageRequest,
        fallbackSeedColors = state.fallbackSeedColors,
        fallbackCloudColors = state.fallbackCloudColors,
        fallbackBackdrop = state.fallbackBackdrop,
        lastStableBackdrop = state.lastStableBackdrop,
        usingFallbackCloudColors = state.usingFallbackCloudColors,
        context = context,
        onLastStableBackdropChange = { state.lastStableBackdrop = it },
        onUsingFallbackCloudColorsChange = {
            state.usingFallbackCloudColors = it
        }
    )
    MiniPlayerAddToPlaylistDialogEffects(
        addToPlaylistDialogBackgroundColor =
            state.addToPlaylistDialogBackgroundColor,
        onAddToPlaylistDialogBackgroundColorChange = onAddToPlaylistDialogBackgroundColorChange
    )
    val titleColor = Color.White
    val artistColor = Color.White
    val controlIconColor = Color.White
    val progressTrackColor = Color(0xFF9E9E9E)
    val progressColor = Color.White
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
        state.artistPlaceholderArtists = emptyList()
        state.expandedMoreMenu = false
        state.fullscreenContentMode = FullscreenContentMode.AddToPlaylist
    }
    fun enterSongInfoMode() {
        if (
            state.fullscreenContentMode != FullscreenContentMode.Playback ||
            !state.isFullscreenPlayer ||
            fullscreenContentExitProgress > 0.01f ||
            artistPlaceholderProgress > 0.01f
        ) {
            return
        }
        state.artistPlaceholderArtists = emptyList()
        state.expandedMoreMenu = false
        state.fullscreenContentMode = FullscreenContentMode.SongInfo
    }
    fun enterArtistPlaceholderMode(rawArtist: String) {
        if (
            state.fullscreenContentMode != FullscreenContentMode.Playback ||
            !state.isFullscreenPlayer ||
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

        state.expandedMoreMenu = false
        state.artistPlaceholderArtists = artists
        state.fullscreenContentMode = FullscreenContentMode.ArtistPlaceholder
    }
    fun exitArtistPlaceholderWithAnimation() {
        state.expandedMoreMenu = false
        if (state.fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder) {
            state.fullscreenContentMode = FullscreenContentMode.Playback
        }
    }
    fun exitFullscreenContentMode() {
        when (state.fullscreenContentMode) {
            FullscreenContentMode.ArtistPlaceholder -> exitArtistPlaceholderWithAnimation()
            FullscreenContentMode.Playback -> {
                state.expandedMoreMenu = false
            }
            else -> {
                state.expandedMoreMenu = false
                state.artistPlaceholderArtists = emptyList()
                state.fullscreenContentMode = FullscreenContentMode.Playback
            }
        }
    }
    fun resetFullscreenContentMode() {
        state.expandedMoreMenu = false
        state.artistPlaceholderArtists = emptyList()
        if (state.fullscreenContentMode != FullscreenContentMode.Playback) {
            state.fullscreenContentMode = FullscreenContentMode.Playback
        }
    }
    fun exitFullscreenContentModeForSongChange() {
        val artistPlaceholderExitInProgress = isArtistPlaceholderExitInProgress(
            fullscreenContentMode = state.fullscreenContentMode,
            artistPlaceholderArtists = state.artistPlaceholderArtists,
            artistPlaceholderProgress = artistPlaceholderProgress
        )
        if (state.fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder) {
            exitArtistPlaceholderWithAnimation()
        } else if (!artistPlaceholderExitInProgress) {
            resetFullscreenContentMode()
        }
    }
    fun exitAddToPlaylistMode() {
        exitFullscreenContentMode()
    }
    BackHandler(
        enabled = state.fullscreenContentMode == FullscreenContentMode.AddToPlaylist &&
            fullscreenInteractionActive
    ) {
        exitAddToPlaylistMode()
    }
    BackHandler(
        enabled = state.fullscreenContentMode == FullscreenContentMode.SongInfo &&
            fullscreenInteractionActive
    ) {
        exitFullscreenContentMode()
    }
    BackHandler(
        enabled = state.fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder &&
            fullscreenInteractionActive
    ) {
        exitFullscreenContentMode()
    }
    MiniPlayerFullscreenContentEffects(
        fullscreen = fullscreen,
        expanded = expanded,
        hasCurrentSong = hasCurrentSong,
        currentSong = currentSong,
        onFullscreenPlayerChange = { state.isFullscreenPlayer = it },
        resetFullscreenContentMode = ::resetFullscreenContentMode,
        exitFullscreenContentModeForSongChange = ::exitFullscreenContentModeForSongChange
    )
    val artistClickEnabled = isArtistClickEnabled(
        isFullscreenPlayer = state.isFullscreenPlayer,
        fullscreenContentMode = state.fullscreenContentMode,
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
        onProgressScrubbingChange = { state.isProgressScrubbing = it }
    )
    val visualIsPlaying = if (
        state.isProgressScrubbing ||
        state.keepPlayPauseVisualLockedAfterSeek
    ) {
        state.lockedIsPlayingDuringScrub
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
        state.lockedIsPlayingDuringScrub = isPlayingToLock
        state.keepPlayPauseVisualLockedAfterSeek = true
        state.playPauseVisualLockToken += 1
    }
    fun playPreviousFromMiniPlayer() {
        if (hasCurrentSong) {
            state.collapsedMetadataSwitchDirection = -1
            lockPlayPauseVisual(true)
            callbacks.onPlayPrevious()
        }
    }
    fun playNextFromMiniPlayer() {
        if (hasCurrentSong) {
            state.collapsedMetadataSwitchDirection = 1
            lockPlayPauseVisual(true)
            callbacks.onPlayNext()
        }
    }
    MiniPlayerPlayPauseVisualLockEffects(
        playPauseVisualLockToken = state.playPauseVisualLockToken,
        keepPlayPauseVisualLockedAfterSeek = state.keepPlayPauseVisualLockedAfterSeek,
        onKeepPlayPauseVisualLockedAfterSeekChange = {
            state.keepPlayPauseVisualLockedAfterSeek = it
        }
    )
    val playbackGesturesEnabled = isPlaybackGestureContent(state.fullscreenContentMode)
    val fullscreenContentBackGesturesEnabled =
        isFullscreenContentBackGestureContent(state.fullscreenContentMode)
    val playerGesturesEnabled = isPlayerGesturesEnabled(
        playbackGesturesEnabled = playbackGesturesEnabled,
        fullscreenContentBackGesturesEnabled = fullscreenContentBackGesturesEnabled
    )
    val gestureModifier = if (playerGesturesEnabled) {
        Modifier.pointerInput(
            hasCurrentSong,
            expanded,
            fullscreenInteractionActive,
            state.fullscreenContentMode,
            minimized,
            swipeThresholdPx,
            fullscreenSwipeThresholdPx,
            allowFullscreenFromCollapsed,
            allowFullscreenFromExpanded
        ) {
            detectVerticalDragGestures(
                onDragStart = {
                    state.accumulatedDragY = 0f
                },
                onVerticalDrag = { _, dragAmount ->
                    if (hasCurrentSong) {
                        state.accumulatedDragY += dragAmount
                    }
                },
                onDragEnd = {
                    if (!hasCurrentSong) {
                        return@detectVerticalDragGestures
                    }
                    if (fullscreenContentBackGesturesEnabled) {
                        if (state.accumulatedDragY >= fullscreenSwipeThresholdPx) {
                            exitFullscreenContentMode()
                        }
                        return@detectVerticalDragGestures
                    }
                    when {
                        state.accumulatedDragY <= -swipeThresholdPx && minimized -> {
                            onMinimizedChange(false)
                        }
                        state.accumulatedDragY <= -fullscreenSwipeThresholdPx &&
                            !expanded &&
                            !fullscreenInteractionActive &&
                            allowFullscreenFromCollapsed -> {
                            onMinimizedChange(false)
                            onFullscreenChange(true)
                            onExpandedChange(true)
                        }
                        state.accumulatedDragY <= -fullscreenSwipeThresholdPx &&
                            expanded &&
                            !fullscreenInteractionActive &&
                            allowFullscreenFromExpanded -> {
                            onFullscreenChange(true)
                        }
                        state.accumulatedDragY <= -swipeThresholdPx && !expanded -> {
                            onExpandedChange(true)
                        }
                        state.accumulatedDragY >= fullscreenSwipeThresholdPx &&
                            fullscreenInteractionActive -> {
                            onFullscreenChange(false)
                        }
                        state.accumulatedDragY >= swipeThresholdPx &&
                            expanded &&
                            !fullscreenInteractionActive -> {
                            onExpandedChange(false)
                        }
                        state.accumulatedDragY >= swipeThresholdPx &&
                            !expanded &&
                            !fullscreenInteractionActive &&
                            !minimized -> {
                            onMinimizedChange(true)
                        }
                    }
                },
                onDragCancel = {
                    state.accumulatedDragY = 0f
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
        enabled = isAddToPlaylistBackGestureEnabled(state.fullscreenContentMode),
        thresholdPx = addToPlaylistBackSwipeThresholdPx,
        onBack = ::exitAddToPlaylistMode
    )
    val addToPlaylistPullDownThresholdPx = with(density) {
        64.dp.toPx()
    }
    val addToPlaylistPullDownBackModifier = Modifier.addToPlaylistPullDownBackGesture(
        enabled = isAddToPlaylistBackGestureEnabled(state.fullscreenContentMode),
        listState = state.addToPlaylistListState,
        thresholdPx = addToPlaylistPullDownThresholdPx,
        onBack = ::exitAddToPlaylistMode
    )
    MiniPlayerQueueEffects(
        queueSheetBackgroundBlurred = state.queueSheetBackgroundBlurred,
        queueSheetBackgroundBlurProgress = state.queueSheetBackgroundBlurProgress
    )
    val queueSheetBackgroundBlurRadius =
        12.dp * state.queueSheetBackgroundBlurProgress.value
    val miniPlayerScene = miniPlayerScene(
        expanded = expanded,
        fullscreen = fullscreen,
        hasCurrentSong = hasCurrentSong,
        fullscreenContentMode = state.fullscreenContentMode,
        artistPlaceholderActive = artistPlaceholderActive,
        artistPlaceholderProgress = artistPlaceholderProgress,
        showQueueSheet = state.showQueueSheet
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
        interactionSource = state.noRippleInteractionSource,
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
                    .pointerInput(
                        state.expandedMoreMenu,
                        state.fullscreenContentMode
                    ) {
                        if (
                            !state.expandedMoreMenu ||
                            state.fullscreenContentMode != FullscreenContentMode.Playback
                        ) {
                            return@pointerInput
                        }

                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                state.expandedMoreMenu = false
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
                        backgroundColor = coverTintDialogBackgroundColor(
                            state.lastStableBackdrop.colors
                        ),
                        backgroundImageRequest =
                            state.lastStableBackdrop.backgroundImageRequest,
                        cloudColors = state.lastStableBackdrop.colors,
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
                        imageRequest = state.coverImageRequest,
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
                        collapsedMetadataSwitchDirection =
                            state.collapsedMetadataSwitchDirection,
                        artistClickEnabled = artistClickEnabled,
                        fullscreenContentMode = state.fullscreenContentMode,
                        libraryPlaylists = libraryPlaylists,
                        playlistIdsContainingCurrentSong = playlistIdsContainingCurrentSong,
                        newlyCreatedPlaylistId = newlyCreatedPlaylistId,
                        addToPlaylistListState = state.addToPlaylistListState,
                        isCurrentSongLiked = isCurrentSongLiked,
                        expandedMoreMenu = state.expandedMoreMenu,
                        lyricsHostCanAttach = lyricsHostCanAttach,
                        fullscreen = fullscreen,
                        expanded = expanded,
                        artistPlaceholderArtists = state.artistPlaceholderArtists,
                        artistPlaceholderLocalSongs =
                            state.artistPlaceholderLocalSongs,
                        artistPlaceholderActive = artistPlaceholderActive,
                        artistPlaceholderProgress = artistPlaceholderProgress,
                        fullscreenSwipeThresholdPx = fullscreenSwipeThresholdPx,
                        songInfoProgress = songInfoProgress,
                        callbacks = callbacks,
                        collapseInteractionSource = state.noRippleInteractionSource,
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
                            state.isProgressScrubbing = scrubbing
                        },
                        onPlayPrevious = {
                            state.expandedMoreMenu = false
                            playPreviousFromMiniPlayer()
                        },
                        onTogglePlayPause = {
                            state.expandedMoreMenu = false
                            if (hasCurrentSong) {
                                state.isProgressScrubbing = false
                                state.keepPlayPauseVisualLockedAfterSeek = false
                                callbacks.onTogglePlayPause()
                            }
                        },
                        onPlayNext = {
                            state.expandedMoreMenu = false
                            playNextFromMiniPlayer()
                        },
                        onMoreMenuExpandedChange = { expanded ->
                            state.expandedMoreMenu = expanded
                        },
                        onToggleLiked = onToggleCurrentSongLiked,
                        onAddToPlaylist = {
                            enterAddToPlaylistMode()
                        },
                        onOpenSongInfo = {
                            enterSongInfoMode()
                        },
                        onOpenQueue = {
                            state.queueSheetBackgroundBlurred = true
                            state.showQueueSheet = true
                        },
                        onArtistHostBack = ::exitFullscreenContentMode,
                        onArtistHostArtistClick = ::openArtistDetailFromPlaceholder,
                        onCollapseClick = {
                            if (
                                state.fullscreenContentMode !=
                                FullscreenContentMode.Playback
                            ) {
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
            showQueueSheet = state.showQueueSheet,
            playbackQueue = playbackQueue,
            sourceQueue = sourceQueue,
            currentQueueIndex = currentQueueIndex,
            currentSong = currentSong,
            displayOrder = queueDisplayOrder,
            onDisplayOrderChange = onQueueDisplayOrderChange,
            backgroundImageRequest = state.lastStableBackdrop.backgroundImageRequest,
            cloudColors = state.lastStableBackdrop.colors,
            backgroundProgress = animationProgress,
            isPlaying = playerUiState.isPlaying,
            waitForArtworkLoad = useLocalArtworkLoading,
            onSongClick = callbacks.onPlayQueueSong,
            onDismissStart = {
                state.queueSheetBackgroundBlurred = false
            },
            onDismiss = {
                state.showQueueSheet = false
            }
            )
        }
    )
}
