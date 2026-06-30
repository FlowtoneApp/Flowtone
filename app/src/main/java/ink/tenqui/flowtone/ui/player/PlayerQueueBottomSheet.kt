package ink.tenqui.flowtone.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.ui.components.SongListItem
import kotlinx.coroutines.delay

internal enum class QueueDisplayOrder(val label: String) {
    PlaybackOrder("\u64ad\u653e\u987a\u5e8f"),
    ListOrder("\u5217\u8868\u987a\u5e8f")
}

@Composable
internal fun PlayerQueueBottomSheet(
    playbackQueue: List<Song>,
    sourceQueue: List<Song>,
    currentQueueIndex: Int,
    currentSong: Song?,
    backgroundImageRequest: ImageRequest?,
    cloudColors: List<Color>,
    backgroundProgress: Float,
    isPlaying: Boolean,
    waitForArtworkLoad: Boolean,
    onSongClick: (Song) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayOrder by rememberSaveable {
        mutableStateOf(QueueDisplayOrder.PlaybackOrder)
    }
    val displayedQueue = when (displayOrder) {
        QueueDisplayOrder.PlaybackOrder -> playbackQueue
        QueueDisplayOrder.ListOrder -> sourceQueue.ifEmpty { playbackQueue }
    }
    val sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    var sheetVisible by remember { mutableStateOf(false) }
    var dismissStarted by remember { mutableStateOf(false) }
    val noRippleInteractionSource = remember { MutableInteractionSource() }

    fun requestDismiss() {
        if (!dismissStarted) {
            dismissStarted = true
            sheetVisible = false
        }
    }

    BackHandler(enabled = true) {
        requestDismiss()
    }

    LaunchedEffect(Unit) {
        sheetVisible = true
    }
    LaunchedEffect(sheetVisible, dismissStarted) {
        if (dismissStarted && !sheetVisible) {
            delay(220L)
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = { requestDismiss() }
            )
    ) {
        AnimatedVisibility(
            visible = sheetVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                initialAlpha = 0f
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                initialOffsetY = { fullHeight -> fullHeight + 80 }
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            ) + slideOutVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                targetOffsetY = { fullHeight -> fullHeight + 80 }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .clip(sheetShape)
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = {}
                    )
            ) {
                PlayerQueueGlassBackground(
                    imageRequest = backgroundImageRequest,
                    cloudColors = cloudColors,
                    progress = backgroundProgress,
                    isPlaying = isPlaying,
                    waitForArtworkLoad = waitForArtworkLoad,
                    shape = sheetShape,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .matchParentSize()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, top = 20.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u64ad\u653e\u961f\u5217",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        QueueDisplayOrderSelector(
                            selectedOrder = displayOrder,
                            onOrderSelected = { displayOrder = it }
                        )
                    }

                    if (displayedQueue.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "\u6682\u65e0\u64ad\u653e\u961f\u5217",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            itemsIndexed(
                                items = displayedQueue,
                                key = { index, song -> "${song.id}-${song.uri}-$index-${displayOrder.name}" }
                            ) { index, song ->
                                val isCurrentSong = when {
                                    currentSong != null -> song.id == currentSong.id || song.uri == currentSong.uri
                                    displayOrder == QueueDisplayOrder.PlaybackOrder &&
                                        currentQueueIndex in playbackQueue.indices -> {
                                        index == currentQueueIndex
                                    }
                                    else -> false
                                }

                                SongListItem(
                                    song = song,
                                    isCurrentSong = isCurrentSong,
                                    onClick = onSongClick,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerQueueGlassBackground(
    imageRequest: ImageRequest?,
    cloudColors: List<Color>,
    progress: Float,
    isPlaying: Boolean,
    waitForArtworkLoad: Boolean,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .blur(44.dp)
        ) {
            BlurredArtworkBackground(
                imageRequest = imageRequest,
                alpha = 0.92f,
                waitForArtworkLoad = waitForArtworkLoad,
                modifier = Modifier.matchParentSize()
            )
            CrossfadeFlowCloudBackground(
                colors = cloudColors,
                progress = progress,
                isPlaying = isPlaying,
                alpha = 0.92f,
                modifier = Modifier.matchParentSize()
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.36f))
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.10f))
        )
    }
}

@Composable
private fun QueueDisplayOrderSelector(
    selectedOrder: QueueDisplayOrder,
    onOrderSelected: (QueueDisplayOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        QueueDisplayOrder.entries.forEach { order ->
            val selected = selectedOrder == order
            Text(
                text = order.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    .clickable { onOrderSelected(order) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
