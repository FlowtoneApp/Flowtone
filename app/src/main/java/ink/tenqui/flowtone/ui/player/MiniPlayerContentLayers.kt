package ink.tenqui.flowtone.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.playback.PlaybackOrderMode

@Composable
internal fun BoxScope.MiniPlayerBackgroundLayers(
    gestureModifier: Modifier,
    songSwipeModifier: Modifier,
    backgroundColor: Color,
    backgroundImageRequest: ImageRequest?,
    cloudColors: List<Color>,
    animationProgress: Float,
    isPlaying: Boolean,
    flowCloudSpeed: Float,
    waitForArtworkLoad: Boolean
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .then(gestureModifier)
            .then(songSwipeModifier)
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(backgroundColor)
    )
    BlurredArtworkBackground(
        imageRequest = backgroundImageRequest,
        alpha = lerpFloat(0.78f, 0f, animationProgress),
        waitForArtworkLoad = waitForArtworkLoad,
        modifier = Modifier.matchParentSize()
    )
    CrossfadeFlowCloudBackground(
        colors = cloudColors,
        progress = animationProgress,
        isPlaying = isPlaying,
        flowCloudSpeed = flowCloudSpeed,
        modifier = Modifier.matchParentSize()
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = lerpFloat(0.24f, 0.36f, animationProgress)))
    )
}

@Composable
internal fun BoxScope.MiniPlayerQueueSheetHost(
    showQueueSheet: Boolean,
    playbackQueue: List<Song>,
    sourceQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    playbackOrderMode: PlaybackOrderMode,
    displayOrder: QueueDisplayOrder,
    onDisplayOrderChange: (QueueDisplayOrder) -> Unit,
    backgroundImageRequest: ImageRequest?,
    cloudColors: List<Color>,
    backgroundProgress: Float,
    isPlaying: Boolean,
    flowCloudSpeed: Float,
    waitForArtworkLoad: Boolean,
    onSongClick: (Song) -> Unit,
    onDismissStart: () -> Unit,
    onDismiss: () -> Unit
) {
    if (showQueueSheet) {
        PlayerQueueBottomSheet(
            playbackQueue = playbackQueue,
            sourceQueue = sourceQueue,
            currentQueueIndex = currentQueueIndex,
            currentSong = currentSong,
            playbackOrderMode = playbackOrderMode,
            displayOrder = displayOrder,
            onDisplayOrderChange = onDisplayOrderChange,
            backgroundImageRequest = backgroundImageRequest,
            cloudColors = cloudColors,
            backgroundProgress = backgroundProgress,
            isPlaying = isPlaying,
            flowCloudSpeed = flowCloudSpeed,
            waitForArtworkLoad = waitForArtworkLoad,
            onSongClick = onSongClick,
            onDismissStart = onDismissStart,
            onDismiss = onDismiss,
            modifier = Modifier
                .matchParentSize()
                .zIndex(20f)
        )
    }
}
