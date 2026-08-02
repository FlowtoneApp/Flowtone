package ink.tenqui.flowtone.ui.player

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.player.lyrics.FullscreenPlaybackContentMode

@Stable
internal class MiniPlayerState internal constructor(
    fullscreenContentModeState: MutableState<FullscreenContentMode>,
    fullscreenPlaybackContentModeState: MutableState<FullscreenPlaybackContentMode>,
    addToPlaylistEnteredFromLyricsState: MutableState<Boolean>,
    artistPlaceholderArtistsState: MutableState<List<String>>,
    collapsedMetadataSwitchDirectionState: MutableState<Int>,
    isFullscreenPlayerState: MutableState<Boolean>,
    lastStableBackdropState: MutableState<PlayerBackdropState>,
    usingFallbackCloudColorsState: MutableState<Boolean>,
    isProgressScrubbingState: MutableState<Boolean>,
    lockedIsPlayingDuringScrubState: MutableState<Boolean>,
    keepPlayPauseVisualLockedAfterSeekState: MutableState<Boolean>,
    playPauseVisualLockTokenState: MutableState<Int>,
    showQueueSheetState: MutableState<Boolean>,
    expandedMoreMenuState: MutableState<Boolean>,
    queueSheetBackgroundBlurredState: MutableState<Boolean>,
    accumulatedDragYState: MutableState<Float>,
    val noRippleInteractionSource: MutableInteractionSource,
    val addToPlaylistListState: LazyListState,
    val queueSheetBackgroundBlurProgress: Animatable<Float, AnimationVector1D>,
    val backgroundImageRequest: ImageRequest?,
    val coverImageRequest: ImageRequest?,
    val paletteImageRequest: ImageRequest?,
    val fallbackSeedColors: List<Int>,
    val fallbackCloudColors: List<Color>,
    val fallbackBackdrop: PlayerBackdropState,
    val addToPlaylistDialogBackgroundColor: Color,
    val artistPlaceholderLocalSongs: List<Song>
) {
    var fullscreenContentMode by fullscreenContentModeState
    var fullscreenPlaybackContentMode by fullscreenPlaybackContentModeState
    var addToPlaylistEnteredFromLyrics by addToPlaylistEnteredFromLyricsState
    var artistPlaceholderArtists by artistPlaceholderArtistsState
    var collapsedMetadataSwitchDirection by collapsedMetadataSwitchDirectionState
    var isFullscreenPlayer by isFullscreenPlayerState
    var lastStableBackdrop by lastStableBackdropState
    var usingFallbackCloudColors by usingFallbackCloudColorsState
    var isProgressScrubbing by isProgressScrubbingState
    var lockedIsPlayingDuringScrub by lockedIsPlayingDuringScrubState
    var keepPlayPauseVisualLockedAfterSeek by keepPlayPauseVisualLockedAfterSeekState
    var playPauseVisualLockToken by playPauseVisualLockTokenState
    var showQueueSheet by showQueueSheetState
    var expandedMoreMenu by expandedMoreMenuState
    var queueSheetBackgroundBlurred by queueSheetBackgroundBlurredState
    var accumulatedDragY by accumulatedDragYState
}

@Composable
internal fun rememberMiniPlayerState(
    fullscreen: Boolean,
    expanded: Boolean,
    hasCurrentSong: Boolean,
    initialIsPlaying: Boolean,
    currentSong: Song?,
    title: String,
    artist: String,
    artworkUri: Uri?,
    fallbackSeedColor: Int,
    isDarkTheme: Boolean,
    context: Context,
    allSongs: List<Song>
): MiniPlayerState {
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
    val fullscreenContentModeState = rememberSaveable {
        mutableStateOf(FullscreenContentMode.Playback)
    }
    val fullscreenPlaybackContentModeState = rememberSaveable {
        mutableStateOf(FullscreenPlaybackContentMode.Artwork)
    }
    val addToPlaylistEnteredFromLyricsState = remember { mutableStateOf(false) }
    val artistPlaceholderArtistsState = rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    val collapsedMetadataSwitchDirectionState = remember { mutableStateOf(1) }
    val isFullscreenPlayerState = remember {
        mutableStateOf(fullscreen && expanded && hasCurrentSong)
    }
    val lastStableBackdropState = remember {
        mutableStateOf<PlayerBackdropState>(fallbackBackdrop)
    }
    val usingFallbackCloudColorsState = remember {
        mutableStateOf(true)
    }
    val isProgressScrubbingState = remember { mutableStateOf(false) }
    val lockedIsPlayingDuringScrubState = remember { mutableStateOf(initialIsPlaying) }
    val keepPlayPauseVisualLockedAfterSeekState = remember { mutableStateOf(false) }
    val playPauseVisualLockTokenState = remember { mutableStateOf(0) }
    val showQueueSheetState = rememberSaveable { mutableStateOf(false) }
    val expandedMoreMenuState = remember { mutableStateOf(false) }
    val queueSheetBackgroundBlurredState = remember { mutableStateOf(false) }
    val accumulatedDragYState = remember { mutableStateOf(0f) }
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val addToPlaylistListState = rememberLazyListState()
    val queueSheetBackgroundBlurProgress = remember { Animatable(0f) }
    val addToPlaylistDialogBackgroundColor = remember(lastStableBackdropState.value.colors) {
        coverTintDialogBackgroundColor(lastStableBackdropState.value.colors)
    }
    val artistPlaceholderLocalSongs = remember(artistPlaceholderArtistsState.value, allSongs) {
        if (artistPlaceholderArtistsState.value.size == 1) {
            localSongsForArtist(allSongs, artistPlaceholderArtistsState.value.first())
        } else {
            emptyList()
        }
    }

    return MiniPlayerState(
        fullscreenContentModeState = fullscreenContentModeState,
        fullscreenPlaybackContentModeState = fullscreenPlaybackContentModeState,
        addToPlaylistEnteredFromLyricsState = addToPlaylistEnteredFromLyricsState,
        artistPlaceholderArtistsState = artistPlaceholderArtistsState,
        collapsedMetadataSwitchDirectionState = collapsedMetadataSwitchDirectionState,
        isFullscreenPlayerState = isFullscreenPlayerState,
        lastStableBackdropState = lastStableBackdropState,
        usingFallbackCloudColorsState = usingFallbackCloudColorsState,
        isProgressScrubbingState = isProgressScrubbingState,
        lockedIsPlayingDuringScrubState = lockedIsPlayingDuringScrubState,
        keepPlayPauseVisualLockedAfterSeekState = keepPlayPauseVisualLockedAfterSeekState,
        playPauseVisualLockTokenState = playPauseVisualLockTokenState,
        showQueueSheetState = showQueueSheetState,
        expandedMoreMenuState = expandedMoreMenuState,
        queueSheetBackgroundBlurredState = queueSheetBackgroundBlurredState,
        accumulatedDragYState = accumulatedDragYState,
        noRippleInteractionSource = noRippleInteractionSource,
        addToPlaylistListState = addToPlaylistListState,
        queueSheetBackgroundBlurProgress = queueSheetBackgroundBlurProgress,
        backgroundImageRequest = backgroundImageRequest,
        coverImageRequest = coverImageRequest,
        paletteImageRequest = paletteImageRequest,
        fallbackSeedColors = fallbackSeedColors,
        fallbackCloudColors = fallbackCloudColors,
        fallbackBackdrop = fallbackBackdrop,
        addToPlaylistDialogBackgroundColor = addToPlaylistDialogBackgroundColor,
        artistPlaceholderLocalSongs = artistPlaceholderLocalSongs
    )
}
