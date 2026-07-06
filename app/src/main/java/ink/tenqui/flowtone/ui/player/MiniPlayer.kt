package ink.tenqui.flowtone.ui.player

import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.SongListItem
import ink.tenqui.flowtone.ui.components.pullToDismissAtTop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal val MiniPlayerCollapsedHeight = 92.dp
internal val MiniPlayerMinimizedHeight = 52.dp
internal val MiniPlayerDragHotZoneHeight = 20.dp
private const val PAUSED_ARTWORK_SCALE = 0.965f
private const val PAUSE_ARTWORK_SCALE_DURATION_MS = 300
private const val PAUSED_ARTWORK_ROTATION_DEGREES = 3f
private const val PAUSE_ARTWORK_ROTATION_DURATION_MS = 300
private const val FLOWTONE_FAVORITE_BUTTON_TAG = "FlowtoneFavoriteButton"
private val AddToPlaylistCardHeight = 148.dp
private val AddToPlaylistCardSpacing = 12.dp
private val ArtistPlaceholderAvatarSize = 84.dp
private val ArtistPlaceholderAvatarGap = 14.dp
private val ArtistPlaceholderNameHeight = 48.dp
private val ArtistPlaceholderItemWidth = 104.dp
private val ArtistPlaceholderListTopGap = 28.dp
private val ArtistPlaceholderHintHeight = 24.dp
private val ArtistPlaceholderHintBottomGap = 18.dp
private const val ArtistPlaceholderNameYFraction = 0.312f

private enum class FullscreenContentMode {
    Playback,
    AddToPlaylist,
    SongInfo,
    ArtistPlaceholder
}

private sealed interface PlayerBackdropState {
    val key: String
    val colors: List<Color>
    val backgroundImageRequest: ImageRequest?
    val coverImageRequest: ImageRequest?

    data class Artwork(
        override val key: String,
        override val colors: List<Color>,
        override val backgroundImageRequest: ImageRequest,
        override val coverImageRequest: ImageRequest
    ) : PlayerBackdropState

    data class Fallback(
        override val key: String,
        override val colors: List<Color>
    ) : PlayerBackdropState {
        override val backgroundImageRequest: ImageRequest? = null
        override val coverImageRequest: ImageRequest? = null
    }
}

private sealed class AddToPlaylistCardItem {
    data class Playlist(val playlist: LibraryPlaylistCard) : AddToPlaylistCardItem()
    object CreatePlaylist : AddToPlaylistCardItem()
}

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
    val fullscreenInteractionActive = fullscreen || fullscreenProgress > 0.01f
    val fullscreenContentExitProgress by animateFloatAsState(
        targetValue = if (
            (
                fullscreenContentMode == FullscreenContentMode.AddToPlaylist ||
                    fullscreenContentMode == FullscreenContentMode.SongInfo
            ) &&
                fullscreen &&
                expanded &&
                hasCurrentSong
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
            fullscreenContentMode == FullscreenContentMode.AddToPlaylist &&
            fullscreen &&
            expanded &&
            hasCurrentSong
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
            fullscreenContentMode == FullscreenContentMode.SongInfo &&
            fullscreen &&
            expanded &&
            hasCurrentSong
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
    val artistPlaceholderActive =
        fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder &&
            fullscreen &&
            expanded &&
            hasCurrentSong
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

    val slideInEasing = CubicBezierEasing(0.05f, 0.85f, 0.18f, 1.0f)

    val miniPlayerSlideOffsetY by animateDpAsState(
        targetValue = if (hasCurrentSong) 0.dp else hiddenOffsetDp,
        animationSpec = tween(
            durationMillis = 360,
            easing = if (hasCurrentSong) {
                slideInEasing
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
    LaunchedEffect(coverImageRequest) {
        coverImageRequest?.let { request ->
            context.imageLoader.enqueue(request)
        }
    }
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

    LaunchedEffect(currentSong?.id, currentSong?.uri, artworkUri, fallbackSeedColor, isDarkTheme) {
        Log.d(
            FLOWTONE_CLOUD_COLORS_TAG,
            "start songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                "requestData=${paletteImageRequest?.data}"
        )

        if (artworkUri == null || paletteImageRequest == null) {
            lastStableBackdrop = fallbackBackdrop
            usingFallbackCloudColors = true
            Log.d(
                FLOWTONE_CLOUD_COLORS_TAG,
                "fallback used for songId=${currentSong?.id}, song=${title}, reason=artworkUri is null, " +
                    "path=songFallback, " +
                    "colors=${fallbackCloudColors.joinToString { it.toArgbHex() }}"
            )
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.Default) {
                val result = context.imageLoader.execute(paletteImageRequest)
                Log.d(
                    FLOWTONE_CLOUD_COLORS_TAG,
                    "coil result songId=${currentSong?.id}, song=${title}, success=${result is SuccessResult}"
                )

                val bitmap = (result as? SuccessResult)?.image?.toBitmap(96, 96)
                    ?: error("Coil did not return a bitmap image")
                val seedResult = extractMaterialYouSeedColors(
                    bitmap = bitmap,
                    fallbackColor = fallbackSeedColors.first(),
                    count = 3
                )
                val colors = normalizeBackdropColors(
                    colors = when (seedResult.colorPath) {
                        CloudColorPath.MaterialYouSeeds -> materialYouCloudColors(
                            seedColors = seedResult.seedColors,
                            isDarkTheme = isDarkTheme
                        )

                        CloudColorPath.NeutralLowChroma -> neutralCloudColorsFromCover(
                            averageLuminance = seedResult.averageLuminance,
                            isDarkTheme = isDarkTheme
                        )

                        CloudColorPath.ThemeFallback -> fallbackCloudColors
                    },
                    isDarkTheme = isDarkTheme
                )
                val usedFallback = seedResult.usedFallback ||
                    seedResult.colorPath == CloudColorPath.ThemeFallback
                Log.d(
                    FLOWTONE_CLOUD_COLORS_TAG,
                    "success songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                        "requestData=${paletteImageRequest.data}, bitmap=${bitmap.width}x${bitmap.height}, " +
                        "opaque=${seedResult.opaquePixelCount}, quantized=${seedResult.quantizedColorCount}, " +
                        "sat=${seedResult.averageSaturation}, lum=${seedResult.averageLuminance}, " +
                        "lowChroma=${seedResult.isLowChromaCover}, path=${seedResult.colorPath.logName}, " +
                        "seeds=${seedResult.seedColors.joinToString { it.toArgbHex() }}, " +
                        "colors=${colors.joinToString { it.toArgbHex() }}, " +
                        "fallback=${usedFallback}, reason=${seedResult.fallbackReason.orEmpty()}"
                )
                colors to usedFallback
            }
        }.onSuccess { (colors, usedFallback) ->
            val nextBackgroundRequest = backgroundImageRequest
            val nextCoverRequest = coverImageRequest
            lastStableBackdrop = if (nextBackgroundRequest != null && nextCoverRequest != null) {
                PlayerBackdropState.Artwork(
                    key = currentSong.toBackdropKey(),
                    colors = colors,
                    backgroundImageRequest = nextBackgroundRequest,
                    coverImageRequest = nextCoverRequest
                )
            } else {
                fallbackBackdrop
            }
            usingFallbackCloudColors = usedFallback
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            lastStableBackdrop = fallbackBackdrop
            usingFallbackCloudColors = true
            Log.w(
                FLOWTONE_CLOUD_COLORS_TAG,
                "fallback used for songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                    "requestData=${paletteImageRequest.data}, reason=${throwable.message}, " +
                    "path=songFallback, " +
                    "colors=${fallbackCloudColors.joinToString { it.toArgbHex() }}",
                throwable
            )
        }
    }
    LaunchedEffect(currentSong?.id, artworkUri, lastStableBackdrop) {
        val backdropName = when (lastStableBackdrop) {
            is PlayerBackdropState.Artwork -> "Artwork"
            is PlayerBackdropState.Fallback -> "Fallback"
        }
        Log.d(
            FLOWTONE_CLOUD_COLORS_TAG,
            "render songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                "backdrop=$backdropName, " +
                "colors=${lastStableBackdrop.colors.joinToString { it.toArgbHex() }}, " +
                "usingFallback=$usingFallbackCloudColors"
        )
    }
    val addToPlaylistDialogBackgroundColor = remember(lastStableBackdrop.colors) {
        coverTintDialogBackgroundColor(lastStableBackdrop.colors)
    }
    LaunchedEffect(addToPlaylistDialogBackgroundColor) {
        onAddToPlaylistDialogBackgroundColorChange(addToPlaylistDialogBackgroundColor)
    }
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
            onToggleSongLiked(song)
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
        val artistPlaceholderExitInProgress =
            fullscreenContentMode == FullscreenContentMode.Playback &&
                artistPlaceholderArtists.isNotEmpty() &&
                artistPlaceholderProgress > 0.001f
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
    LaunchedEffect(fullscreen, expanded, hasCurrentSong) {
        if (!fullscreen || !expanded) {
            isFullscreenPlayer = false
            resetFullscreenContentMode()
        } else if (!hasCurrentSong) {
            isFullscreenPlayer = false
            exitFullscreenContentModeForSongChange()
        }
    }
    LaunchedEffect(fullscreen, hasCurrentSong, currentSong?.id) {
        if (!fullscreen) {
            resetFullscreenContentMode()
        } else if (!hasCurrentSong) {
            exitFullscreenContentModeForSongChange()
        } else if (currentSong?.id != null) {
            exitFullscreenContentModeForSongChange()
        }
    }
    val artistClickEnabled =
        isFullscreenPlayer &&
            fullscreenContentMode == FullscreenContentMode.Playback &&
            fullscreenContentExitProgress <= 0.01f &&
            artistPlaceholderProgress <= 0.01f
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
        onOpenArtistRootPage(selectedArtistName)
    }
    LaunchedEffect(currentSong?.id) {
        isProgressScrubbing = false
    }
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
            onPlayPrevious()
        }
    }
    fun playNextFromMiniPlayer() {
        if (hasCurrentSong) {
            collapsedMetadataSwitchDirection = 1
            lockPlayPauseVisual(true)
            onPlayNext()
        }
    }
    LaunchedEffect(playPauseVisualLockToken) {
        val token = playPauseVisualLockToken
        if (keepPlayPauseVisualLockedAfterSeek) {
            delay(650L)
            if (playPauseVisualLockToken == token) {
                keepPlayPauseVisualLockedAfterSeek = false
            }
        }
    }
    var accumulatedDragY by remember { mutableStateOf(0f) }
    val playbackGesturesEnabled =
        fullscreenContentMode == FullscreenContentMode.Playback
    val fullscreenContentBackGesturesEnabled =
        fullscreenContentMode == FullscreenContentMode.SongInfo ||
            fullscreenContentMode == FullscreenContentMode.ArtistPlaceholder
    val playerGesturesEnabled =
        playbackGesturesEnabled ||
            fullscreenContentBackGesturesEnabled
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
        enabled = fullscreenContentMode == FullscreenContentMode.AddToPlaylist,
        thresholdPx = addToPlaylistBackSwipeThresholdPx,
        onBack = ::exitAddToPlaylistMode
    )
    val addToPlaylistPullDownThresholdPx = with(density) {
        64.dp.toPx()
    }
    val addToPlaylistListState = rememberLazyListState()
    val addToPlaylistPullDownBackModifier = Modifier.addToPlaylistPullDownBackGesture(
        enabled = fullscreenContentMode == FullscreenContentMode.AddToPlaylist,
        listState = addToPlaylistListState,
        thresholdPx = addToPlaylistPullDownThresholdPx,
        onBack = ::exitAddToPlaylistMode
    )
    val queueSheetBackgroundBlurProgress = remember { Animatable(0f) }
    LaunchedEffect(queueSheetBackgroundBlurred) {
        if (queueSheetBackgroundBlurred) {
            queueSheetBackgroundBlurProgress.snapTo(0f)
        }
        queueSheetBackgroundBlurProgress.animateTo(
            targetValue = if (queueSheetBackgroundBlurred) 1f else 0f,
            animationSpec = tween(
                durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                easing = LinearEasing
            )
        )
    }
    val queueSheetBackgroundBlurRadius = 12.dp * queueSheetBackgroundBlurProgress.value
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hostHeight)
            .graphicsLayer {
                translationY = miniPlayerSlideOffsetY.toPx()
                alpha = visibleProgress
                clip = fullscreenProgress > 0.01f
            }
    ) {
        val playerShape = RoundedCornerShape(
            topStart = lerpDp(24.dp, 0.dp, fullscreenProgress),
            topEnd = lerpDp(24.dp, 0.dp, fullscreenProgress),
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        val playerShadowElevation = lerpDp(0.dp, 18.dp, animationProgress)
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(queueSheetBackgroundBlurRadius)
        ) {
            PlayerDragHandle(
                animationProgress = animationProgress,
                hasCurrentSong = hasCurrentSong,
                expanded = expanded,
                interactionSource = noRippleInteractionSource,
                onActivate = {
                    if (minimized) {
                        onMinimizedChange(false)
                    } else {
                        onExpandedChange(true)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dragHotZoneHeight)
                    .graphicsLayer {
                        translationY = handleOffsetY.toPx()
                    }
                    .align(Alignment.TopCenter)
                    .then(gestureModifier)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = visualPanelTop)
                    .fillMaxWidth()
                    .height(visualPanelHeight)
                    .shadow(
                        elevation = playerShadowElevation,
                        shape = playerShape,
                        clip = false
                    )
                    .clickable(
                        enabled = hasCurrentSong && !expanded,
                        interactionSource = noRippleInteractionSource,
                        indication = null
                    ) {
                        if (minimized) {
                            onMinimizedChange(false)
                        } else {
                            onExpandedChange(true)
                        }
                    }
            ) {
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
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .then(gestureModifier)
                            .then(songSwipeModifier)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(coverTintDialogBackgroundColor(lastStableBackdrop.colors))
                    )
                    BlurredArtworkBackground(
                        imageRequest = lastStableBackdrop.backgroundImageRequest,
                        alpha = lerpFloat(0.78f, 0f, animationProgress),
                        waitForArtworkLoad = useLocalArtworkLoading,
                        modifier = Modifier.matchParentSize()
                    )
                    CrossfadeFlowCloudBackground(
                        colors = lastStableBackdrop.colors,
                        progress = animationProgress,
                        isPlaying = playerUiState.isPlaying,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = lerpFloat(0.24f, 0.36f, animationProgress)))
                    )
                    val fullscreenProgressTrackWidth = playerWidth * 0.76f
                    val fullscreenArtworkX = (playerWidth - fullscreenProgressTrackWidth) / 2f
                    val fullscreenMetadataTop =
                        fullscreenCoverCenterY + fullscreenProgressTrackWidth / 2f + 14.dp
                    val addToPlaylistArtworkSize = 56.dp
                    val addToPlaylistArtworkLeft = 20.dp
                    val addToPlaylistArtworkTop = with(density) {
                        WindowInsets.statusBars.getTop(this).toDp()
                    } + 72.dp
                    val addToPlaylistTextStart =
                        addToPlaylistArtworkLeft + addToPlaylistArtworkSize + 12.dp
                    val addToPlaylistTextWidth =
                        (playerWidth - addToPlaylistTextStart - 20.dp).coerceAtLeast(80.dp)
                    val addToPlaylistCardsTop =
                        addToPlaylistArtworkTop + addToPlaylistArtworkSize + 24.dp
                    val addToPlaylistCardsHeight =
                        (visualPanelHeight - addToPlaylistCardsTop - 20.dp)
                            .coerceAtLeast(AddToPlaylistCardHeight)
                    val artistExitProgress = artistPlaceholderProgress.coerceIn(0f, 1f)
                    val fullscreenContentExitSharedProgress = maxOf(
                        fullscreenContentExitProgress.coerceIn(0f, 1f),
                        artistExitProgress
                    )
                    val artworkContentExitProgress =
                        fullscreenContentExitProgress.coerceIn(0f, 1f)
                    val addToPlaylistSharedProgress = addToPlaylistProgress.coerceIn(0f, 1f)
                    val playbackContentAlpha = 1f - fullscreenContentExitSharedProgress
                    val playbackContentOffsetY = 32.dp * fullscreenContentExitSharedProgress
                    MorphArtworkLayer(
                        imageRequest = coverImageRequest,
                        waitForArtworkLoad = useLocalArtworkLoading,
                        progress = artworkAnimationProgress,
                        scaleProgress = artworkScaleProgress,
                        currentHeight = currentHeight,
                        viewportHeight = currentHeight,
                        collapsedHeight = collapsedHeight,
                        playerWidth = playerWidth,
                        expandedArtworkSize = expandedArtworkSize,
                        expandedArtworkTop = expandedArtworkTop,
                        fullscreenProgress = fullscreenProgress,
                        fullscreenArtworkSize = fullscreenProgressTrackWidth,
                        fullscreenArtworkCenterY = fullscreenCoverCenterY,
                        contentExitProgress = artworkContentExitProgress,
                        addToPlaylistArtworkSize = addToPlaylistArtworkSize,
                        addToPlaylistArtworkX = addToPlaylistArtworkLeft,
                        addToPlaylistArtworkTop = addToPlaylistArtworkTop,
                        playbackScale = artworkPlaybackScale,
                        playbackRotationDegrees = artworkPlaybackRotationDegrees,
                        layerAlpha = 1f - artistExitProgress,
                        layerTranslationY =
                            16.dp *
                                (1f - minimizedProgress) *
                                (1f - fullscreenProgress) -
                                24.dp * artistExitProgress,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(visualPanelHeight)
                            .align(Alignment.TopCenter)
                    ) {
                    SharedSongInfo(
                        title = title,
                        artist = artist,
                        currentSongKey = currentSong?.id,
                        progress = animationProgress,
                        titleColor = titleColor,
                        artistColor = artistColor,
                        playerWidth = playerWidth,
                        minimizedProgress = minimizedProgress,
                        minimizedHeight = minimizedHeight,
                        collapsedHeight = collapsedHeight,
                        expandedTop = expandedMetadataTop,
                        fullscreenProgress = fullscreenProgress,
                        fullscreenX = fullscreenArtworkX,
                        fullscreenTop = fullscreenMetadataTop,
                        contentExitProgress = fullscreenContentExitSharedProgress,
                        switchDirection = collapsedMetadataSwitchDirection,
                        artistClickEnabled = artistClickEnabled,
                        onArtistClick = ::handleArtistClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                    )
                    AddToPlaylistItemSongInfo(
                        title = title,
                        artist = artist,
                        progress = addToPlaylistSharedProgress,
                        titleColor = titleColor,
                        artistColor = artistColor,
                        width = addToPlaylistTextWidth,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = addToPlaylistTextStart,
                                y = addToPlaylistArtworkTop
                            )
                    )
                    if (
                        fullscreenContentMode == FullscreenContentMode.AddToPlaylist ||
                        addToPlaylistSharedProgress > 0.001f
                    ) {
                        AddToPlaylistPlaylistGrid(
                            playlists = libraryPlaylists,
                            playlistIdsContainingCurrentSong = playlistIdsContainingCurrentSong,
                            newlyCreatedPlaylistId = newlyCreatedPlaylistId,
                            onNewPlaylistCreateAnimationFinished =
                                onNewPlaylistCreateAnimationFinished,
                            listState = addToPlaylistListState,
                            progress = addToPlaylistSharedProgress,
                            screenWidth = playerWidth,
                            pullToDismissEnabled =
                                fullscreenContentMode == FullscreenContentMode.AddToPlaylist,
                            onDismissAtTop = ::exitAddToPlaylistMode,
                            onCreatePlaylistClick = onCreatePlaylistClick,
                            onPlaylistClick = { playlist ->
                                onAddSongToPlaylist(playlist, ::exitAddToPlaylistMode)
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(y = addToPlaylistCardsTop)
                                .fillMaxWidth()
                                .height(addToPlaylistCardsHeight)
                                .padding(horizontal = 20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                alpha = playbackContentAlpha
                                translationY =
                                    (fullscreenStationaryControlsOffsetY -
                                        fullscreenControlsLiftY +
                                        playbackContentOffsetY).toPx()
                            }
                    ) {
                        ExpandedOnlyContent(
                            progress = animationProgress,
                            positionMs = playerUiState.positionMs,
                            durationMs = durationMs,
                            isPlaying = playerUiState.isPlaying,
                            isPlayingForVisualLock = visualIsPlaying,
                            currentSongKey = currentSong?.id,
                            hasCurrentSong = hasCurrentSong,
                            progressTrackColor = progressTrackColor,
                            progressColor = progressColor,
                            fullscreenProgress = fullscreenProgress,
                            onSeekTo = onSeekTo,
                            onLockPlayPauseVisual = ::lockPlayPauseVisual,
                            onScrubbingChange = { scrubbing ->
                                isProgressScrubbing = scrubbing
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = expandedProgressTop)
                        )
                        SharedPlaybackControls(
                            progress = animationProgress,
                            isPlaying = visualIsPlaying,
                            iconColor = controlIconColor,
                            screenWidth = playerWidth,
                            minimizedProgress = minimizedProgress,
                            minimizedHeight = minimizedHeight,
                            collapsedHeight = collapsedHeight,
                            expandedTop = expandedControlsTop,
                            fullscreenProgress = fullscreenProgress,
                            controlsExitProgress = fullscreenContentExitSharedProgress,
                            onPlayPrevious = {
                                expandedMoreMenu = false
                                playPreviousFromMiniPlayer()
                            },
                            onTogglePlayPause = {
                                expandedMoreMenu = false
                                if (hasCurrentSong) {
                                    isProgressScrubbing = false
                                    keepPlayPauseVisualLockedAfterSeek = false
                                    onTogglePlayPause()
                                }
                            },
                            onPlayNext = {
                                expandedMoreMenu = false
                                playNextFromMiniPlayer()
                            },
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                        SideButtonsOverlay(
                            progress = animationProgress,
                            playerWidth = playerWidth,
                            currentHeight = currentHeight,
                            expandedHeight = expandedHeight,
                            expandedProgressTop = expandedProgressTop,
                            expandedControlsTop = expandedControlsTop,
                            hasCurrentSong = hasCurrentSong,
                            isCurrentSongLiked = isCurrentSongLiked,
                            playbackOrderMode = playerUiState.playbackOrderMode,
                            iconColor = controlIconColor,
                            fullscreenProgress = fullscreenProgress,
                            controlsExitProgress = fullscreenContentExitSharedProgress,
                            moreMenuExpanded = expandedMoreMenu,
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
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxSize()
                                .zIndex(2f),
                            onTogglePlaybackOrderMode = onTogglePlaybackOrderMode
                        )
                    }
                    if (
                        artistPlaceholderArtists.isNotEmpty() &&
                        (artistPlaceholderActive || artistPlaceholderProgress > 0.001f)
                    ) {
                        ArtistPlaceholderOverlay(
                            artists = artistPlaceholderArtists,
                            artistSongs = artistPlaceholderLocalSongs,
                            currentSong = currentSong,
                            progress = artistPlaceholderProgress,
                            backGestureThresholdPx = fullscreenSwipeThresholdPx,
                            backGestureEnabled = artistPlaceholderActive &&
                                artistPlaceholderProgress > 0.5f,
                            onBack = ::exitFullscreenContentMode,
                            onArtistClick = ::openArtistDetailFromPlaceholder,
                            onSongClick = onPlayArtistSongQueue,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(visualPanelHeight)
                                .zIndex(6f)
                        )
                    }
                    if (
                        fullscreenContentMode == FullscreenContentMode.SongInfo ||
                        songInfoProgress > 0.001f
                    ) {
                        FullscreenSongInfoOverlay(
                            song = currentSong,
                            progress = songInfoProgress,
                            topPadding = addToPlaylistCardsTop,
                            backGestureThresholdPx = fullscreenSwipeThresholdPx,
                            onBack = ::exitFullscreenContentMode,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(visualPanelHeight)
                                .zIndex(6f)
                        )
                    }
                }
                FullscreenCollapseArrow(
                    progress = fullscreenProgress,
                    interactionSource = noRippleInteractionSource,
                    onClick = {
                        if (fullscreenContentMode != FullscreenContentMode.Playback) {
                            exitFullscreenContentMode()
                        } else if (allowFullscreenFromCollapsed) {
                            onFullscreenChange(false)
                            onExpandedChange(false)
                        } else {
                            onFullscreenChange(false)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(10f)
                )
            }
        }
        }
        if (showQueueSheet) {
            PlayerQueueBottomSheet(
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
                onSongClick = onPlayQueueSong,
                onDismissStart = {
                    queueSheetBackgroundBlurred = false
                },
                onDismiss = {
                    showQueueSheet = false
                },
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(20f)
            )
        }
    }
}

private fun Modifier.addToPlaylistBackSwipeGesture(
    enabled: Boolean,
    thresholdPx: Float,
    onBack: () -> Unit
): Modifier {
    if (!enabled) {
        return this
    }

    return pointerInput(thresholdPx) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )

            var dragX = 0f
            var dragY = 0f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break

                if (!event.changes.any { pointer -> pointer.pressed }) {
                    break
                }

                val delta = change.position - change.previousPosition
                dragX += delta.x
                dragY += delta.y

                val absDragX = abs(dragX)
                val absDragY = abs(dragY)
                val isClearRightSwipe =
                    dragX > thresholdPx && absDragX > absDragY * 1.5f

                if (isClearRightSwipe) {
                    change.consume()
                    onBack()
                    break
                }

                val verticalGestureDominates =
                    absDragY > thresholdPx && absDragY >= absDragX
                val leftSwipePassedThreshold = dragX < -thresholdPx
                if (verticalGestureDominates || leftSwipePassedThreshold) {
                    break
                }
            }
        }
    }
}

private fun Modifier.addToPlaylistPullDownBackGesture(
    enabled: Boolean,
    listState: LazyListState,
    thresholdPx: Float,
    onBack: () -> Unit
): Modifier {
    if (!enabled) {
        return this
    }

    return pointerInput(listState, thresholdPx) {
        awaitEachGesture {
            awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial
            )

            var dragX = 0f
            var dragY = 0f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break

                if (!event.changes.any { pointer -> pointer.pressed }) {
                    break
                }

                val listAtTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (!listAtTop) {
                    break
                }

                val delta = change.position - change.previousPosition
                dragX += delta.x
                dragY += delta.y

                val absDragX = abs(dragX)
                val absDragY = abs(dragY)
                val isClearPullDown =
                    dragY > thresholdPx && absDragY > absDragX * 1.25f

                if (isClearPullDown) {
                    change.consume()
                    onBack()
                    break
                }

                val upwardPassedThreshold = dragY < -thresholdPx
                val horizontalGestureDominates =
                    absDragX > thresholdPx && absDragX >= absDragY
                if (upwardPassedThreshold || horizontalGestureDominates) {
                    break
                }
            }
        }
    }
}

@Composable
private fun ArtistPlaceholderOverlay(
    artists: List<String>,
    artistSongs: List<Song>,
    currentSong: Song?,
    progress: Float,
    backGestureThresholdPx: Float,
    backGestureEnabled: Boolean,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) {
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .fullscreenContentBackGesture(
                enabled = backGestureEnabled,
                thresholdPx = backGestureThresholdPx,
                onBack = onBack
            )
    ) {
        val overlayProgress = progress.coerceIn(0f, 1f)
        val singleArtist = artists.size == 1
        val contentHeight =
            ArtistPlaceholderAvatarSize + ArtistPlaceholderAvatarGap + ArtistPlaceholderNameHeight
        val multiArtistContentHeight =
            ArtistPlaceholderHintHeight + ArtistPlaceholderHintBottomGap + contentHeight
        val contentTop = if (!singleArtist) {
            (maxHeight - multiArtistContentHeight) / 2f
        } else {
            val nameCenterY = maxHeight * ArtistPlaceholderNameYFraction
            val nameTop = (nameCenterY - ArtistPlaceholderNameHeight / 2f)
                .coerceAtLeast(0.dp)
            (nameTop - ArtistPlaceholderAvatarGap - ArtistPlaceholderAvatarSize)
                .coerceAtLeast(0.dp)
        }

        if (singleArtist) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = contentTop)
                    .fillMaxWidth()
                    .height(contentHeight)
                    .graphicsLayer {
                        alpha = overlayProgress
                        translationY = 18.dp.toPx() * (1f - overlayProgress)
                    },
                contentPadding = PaddingValues(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 20.dp,
                    alignment = Alignment.CenterHorizontally
                )
            ) {
                itemsIndexed(
                    items = artists,
                    key = { index, artist -> "$index:$artist" }
                ) { _, artist ->
                    ArtistPlaceholderItem(
                        artist = artist,
                        onArtistClick = onArtistClick
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = contentTop)
                    .fillMaxWidth()
                    .height(multiArtistContentHeight)
                    .graphicsLayer {
                        alpha = overlayProgress
                        translationY = 18.dp.toPx() * (1f - overlayProgress)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择一位艺人以查看详情",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ArtistPlaceholderHintHeight)
                        .padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(ArtistPlaceholderHintBottomGap))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentHeight),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 20.dp,
                        alignment = Alignment.CenterHorizontally
                    )
                ) {
                    itemsIndexed(
                        items = artists,
                        key = { index, artist -> "$index:$artist" }
                    ) { _, artist ->
                        ArtistPlaceholderItem(
                            artist = artist,
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }

        if (singleArtist) {
            val listTop = contentTop + contentHeight + ArtistPlaceholderListTopGap
            val listHeight = (maxHeight - listTop - 28.dp).coerceAtLeast(156.dp)
            ArtistPlaceholderLocalSongList(
                artistName = artists.first(),
                songs = artistSongs,
                currentSong = currentSong,
                onSongClick = onSongClick,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = listTop)
                    .fillMaxWidth()
                    .height(listHeight)
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        alpha = overlayProgress
                        translationY = 18.dp.toPx() * (1f - overlayProgress)
                    }
            )
        }
    }
}

@Composable
private fun ArtistPlaceholderLocalSongList(
    artistName: String,
    songs: List<Song>,
    currentSong: Song?,
    onSongClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 12.dp)
    ) {
        Text(
            text = "本地音乐中的 $artistName",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有找到该艺术家的本地歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {
                itemsIndexed(
                    items = songs,
                    key = { index, song -> "${song.id}-${song.uri}-$index" }
                ) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrentSong = currentSong?.id == song.id || currentSong?.uri == song.uri,
                        onClick = {
                            onSongClick(songs, index)
                        },
                        titleColor = Color.White,
                        artistColor = Color.White.copy(alpha = 0.76f),
                        durationColor = Color.White.copy(alpha = 0.72f),
                        currentSongBackgroundColor = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistPlaceholderItem(
    artist: String,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.width(ArtistPlaceholderItemWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(ArtistPlaceholderAvatarSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xFFB8B8B8).copy(alpha = 0.72f))
                .clickable(
                    interactionSource = noRippleInteractionSource,
                    indication = null,
                    onClick = { onArtistClick(artist) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(ArtistPlaceholderAvatarSize * 0.54f)
            )
        }
        Text(
            text = artist,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(ArtistPlaceholderNameHeight)
                .clickable(
                    interactionSource = noRippleInteractionSource,
                    indication = null,
                    onClick = { onArtistClick(artist) }
                )
                .padding(top = 8.dp)
        )
    }
}
@Composable
private fun AddToPlaylistItemSongInfo(
    title: String,
    artist: String,
    progress: Float,
    titleColor: Color,
    artistColor: Color,
    width: Dp,
    modifier: Modifier = Modifier
) {
    val itemProgress = progress.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .width(width)
            .height(56.dp)
            .graphicsLayer {
                alpha = itemProgress
                translationY = (16.dp * (1f - itemProgress)).toPx()
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = artistColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun AddToPlaylistPlaylistGrid(
    playlists: List<LibraryPlaylistCard>,
    playlistIdsContainingCurrentSong: Set<String>,
    newlyCreatedPlaylistId: String?,
    onNewPlaylistCreateAnimationFinished: (String) -> Unit,
    listState: LazyListState,
    progress: Float,
    screenWidth: Dp,
    pullToDismissEnabled: Boolean,
    onDismissAtTop: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(playlists) {
        playlists
            .sortedBy { playlist -> playlist.order }
            .map<LibraryPlaylistCard, AddToPlaylistCardItem> { playlist ->
                AddToPlaylistCardItem.Playlist(playlist)
            } + AddToPlaylistCardItem.CreatePlaylist
    }
    val rows = remember(items) {
        items.chunked(2)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.pullToDismissAtTop(
            listState = listState,
            enabled = pullToDismissEnabled,
            threshold = 64.dp,
            onDismiss = onDismissAtTop
        ),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(AddToPlaylistCardSpacing)
    ) {
        itemsIndexed(
            items = rows,
            key = { _, rowItems -> rowItems.toAddToPlaylistRowKey() }
        ) { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AddToPlaylistCardSpacing)
            ) {
                rowItems.forEachIndexed { columnIndex, item ->
                    val itemIndex = rowIndex * 2 + columnIndex
                    AddToPlaylistCard(
                        item = item,
                        progress = progress,
                        screenWidth = screenWidth,
                        isLeftColumn = itemIndex % 2 == 0,
                        playCreateAnimation = when (item) {
                            is AddToPlaylistCardItem.Playlist ->
                                item.playlist.id == newlyCreatedPlaylistId
                            AddToPlaylistCardItem.CreatePlaylist -> false
                        },
                        alreadyContainsSong = when (item) {
                            is AddToPlaylistCardItem.Playlist ->
                                item.playlist.id in playlistIdsContainingCurrentSong
                            AddToPlaylistCardItem.CreatePlaylist -> false
                        },
                        onCreateAnimationFinished = onNewPlaylistCreateAnimationFinished,
                        onCreatePlaylistClick = onCreatePlaylistClick,
                        onPlaylistClick = onPlaylistClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(AddToPlaylistCardHeight)
                    )
                }

                if (rowItems.size == 1) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(AddToPlaylistCardHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistCard(
    item: AddToPlaylistCardItem,
    progress: Float,
    screenWidth: Dp,
    isLeftColumn: Boolean,
    playCreateAnimation: Boolean,
    alreadyContainsSong: Boolean,
    onCreateAnimationFinished: (String) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (LibraryPlaylistCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardProgress = progress.coerceIn(0f, 1f)
    val itemKey = when (item) {
        is AddToPlaylistCardItem.Playlist -> item.playlist.id
        AddToPlaylistCardItem.CreatePlaylist -> "create_playlist"
    }
    val createProgress = remember(itemKey) {
        Animatable(if (playCreateAnimation) 0f else 1f)
    }
    LaunchedEffect(playCreateAnimation, itemKey) {
        if (playCreateAnimation) {
            createProgress.snapTo(0f)
            createProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FlowtoneMotion.DurationMillis,
                    easing = FlowtoneMotion.Easing
                )
            )
            if (item is AddToPlaylistCardItem.Playlist) {
                onCreateAnimationFinished(item.playlist.id)
            }
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
    val createEasedProgress = FlowtoneMotion.Easing.transform(
        createProgress.value.coerceIn(0f, 1f)
    )
    val startOffsetX = with(density) {
        if (isLeftColumn) {
            -screenWidth.toPx()
        } else {
            screenWidth.toPx()
        }
    }
    val cardClickEnabled =
        cardProgress > 0.99f && createEasedProgress > 0.99f && !alreadyContainsSong
    val disabledColor = Color.White.copy(alpha = 0.38f)
    val primaryContentColor = if (alreadyContainsSong) disabledColor else Color.White
    val secondaryContentColor = if (alreadyContainsSong) {
        disabledColor
    } else {
        Color.White.copy(alpha = 0.78f)
    }
    val onClick = when (item) {
        is AddToPlaylistCardItem.Playlist -> {
            { onPlaylistClick(item.playlist) }
        }
        AddToPlaylistCardItem.CreatePlaylist -> {
            onCreatePlaylistClick
        }
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                alpha = cardProgress * createEasedProgress
                translationX = lerpFloat(startOffsetX, 0f, cardProgress)
                translationY = 18.dp.toPx() * (1f - createEasedProgress)
            }
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = primaryContentColor,
                shape = RoundedCornerShape(24.dp)
            )
            .background(Color.Transparent)
            .clickable(
                enabled = cardClickEnabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        when (item) {
            is AddToPlaylistCardItem.Playlist -> {
                Text(
                    text = item.playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (alreadyContainsSong) {
                        "\u6b64\u6b4c\u66f2\u5df2\u5b58\u5728"
                    } else {
                        item.playlist.subtitle
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            AddToPlaylistCardItem.CreatePlaylist -> {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "创建歌单",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "创建歌单",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    text = "新歌单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun List<AddToPlaylistCardItem>.toAddToPlaylistRowKey(): String {
    return joinToString(separator = "_") { item ->
        when (item) {
            is AddToPlaylistCardItem.Playlist -> item.playlist.id
            AddToPlaylistCardItem.CreatePlaylist -> "create_playlist"
        }
    }
}

private fun Song?.toBackdropKey(): String {
    return this?.let { song ->
        "${song.id}|${song.title}|${song.artist}|${song.uri}"
    } ?: "empty_backdrop"
}

private fun normalizeBackdropColors(
    colors: List<Color>,
    isDarkTheme: Boolean
): List<Color> {
    val fallbackColors = if (isDarkTheme) {
        listOf(
            Color(0xFF5D6C8F),
            Color(0xFF77658E),
            Color(0xFF4E7A73)
        )
    } else {
        listOf(
            Color(0xFF7185B7),
            Color(0xFF9B7EB3),
            Color(0xFF72A79C)
        )
    }
    val sourceColors = colors.ifEmpty { fallbackColors }
    val lastColor = sourceColors.lastOrNull() ?: fallbackColors.last()

    return List(3) { index ->
        sourceColors.getOrElse(index) { lastColor }.copy(alpha = 1f)
    }
}

private fun coverTintDialogBackgroundColor(colors: List<Color>): Color {
    val seedColor = colors.firstOrNull() ?: Color(0xFF24212B)
    val darkened = mixWithBlack(seedColor, amount = 0.62f)
    return if (darkened.luminance() <= 0.24f) {
        darkened
    } else {
        mixWithBlack(darkened, amount = 0.45f)
    }
}

private fun mixWithBlack(color: Color, amount: Float): Color {
    val blackAmount = amount.coerceIn(0f, 1f)
    val colorAmount = 1f - blackAmount
    return Color(
        red = color.red * colorAmount,
        green = color.green * colorAmount,
        blue = color.blue * colorAmount,
        alpha = 1f
    )
}

@Composable
private fun FullscreenCollapseArrow(
    progress: Float,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleProgress = SoftElementEasing.transform(progress.coerceIn(0f, 1f))
    val density = LocalDensity.current
    val safeTopPadding = with(density) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val arrowOffsetY = lerpDp((-32).dp, safeTopPadding + 18.dp, visibleProgress)

    Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = "\u6536\u8d77\u5168\u5c4f\u64ad\u653e\u5668",
        tint = Color.White,
        modifier = modifier
            .offset(y = arrowOffsetY)
            .size(width = 42.dp, height = 30.dp)
            .clickable(
                enabled = progress > 0.72f,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .graphicsLayer {
                alpha = visibleProgress
                scaleX = lerpFloat(0.72f, 1f, visibleProgress)
                scaleY = lerpFloat(0.72f, 1f, visibleProgress)
            }
    )
}

private fun songFallbackCloudSeedColors(
    song: Song?,
    fallbackColor: Int
): List<Int> {
    song ?: return listOf(fallbackColor, fallbackColor, fallbackColor)

    val baseHash = "${song.id}|${song.title}|${song.artist}|${song.uri}".hashCode()
    return List(3) { index ->
        val hue = Math.floorMod(baseHash + index * 47, 360).toFloat()
        AndroidColor.HSVToColor(floatArrayOf(hue, 0.62f, 0.78f))
    }
}
