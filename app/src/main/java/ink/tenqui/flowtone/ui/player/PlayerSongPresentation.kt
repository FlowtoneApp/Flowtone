package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil3.compose.asPainter
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CancellationException

internal data class PlayerSongPresentation(
    val key: Long?,
    val title: String,
    val artist: String,
    val imageRequest: ImageRequest?,
    val artworkPainter: Painter?
)

internal data class PlayerSongPresentationTransition(
    val previous: PlayerSongPresentation?,
    val current: PlayerSongPresentation,
    val progress: Float
)

private data class DesiredPlayerSongPresentation(
    val key: Long?,
    val title: String,
    val artist: String,
    val imageRequest: ImageRequest?
)

@Composable
internal fun rememberPlayerSongPresentationTransition(
    songKey: Long?,
    title: String,
    artist: String,
    imageRequest: ImageRequest?,
    imageLoader: ImageLoader
): PlayerSongPresentationTransition {
    val context = LocalContext.current
    val desired = DesiredPlayerSongPresentation(
        key = songKey,
        title = title,
        artist = artist,
        imageRequest = imageRequest
    )
    var current by remember {
        mutableStateOf(
            PlayerSongPresentation(
                key = songKey,
                title = title,
                artist = artist,
                imageRequest = imageRequest,
                artworkPainter = null
            )
        )
    }
    var previous by remember { mutableStateOf<PlayerSongPresentation?>(null) }
    val switchProgress = remember { Animatable(1f) }

    LaunchedEffect(desired) {
        val prepared = PlayerSongPresentation(
            key = desired.key,
            title = desired.title,
            artist = desired.artist,
            imageRequest = desired.imageRequest,
            artworkPainter = null
        )

        if (prepared.key == current.key) {
            // 同一首歌的封面完成加载时原位补全，不触发一次伪切歌。
            current = current.copy(
                title = prepared.title,
                artist = prepared.artist,
                imageRequest = prepared.imageRequest
            )
            return@LaunchedEffect
        }

        previous = current
        current = prepared
        switchProgress.snapTo(0f)
        switchProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = PlayerSongSwitchDurationMillis,
                easing = TrackSwitchProgressEasing
            )
        )
        previous = null
    }

    LaunchedEffect(desired.key, desired.imageRequest, imageLoader) {
        val painter = try {
            desired.imageRequest?.let { request ->
                (imageLoader.execute(request) as? SuccessResult)
                    ?.image
                    ?.asPainter(context)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }

        // 封面加载完成后只补齐当前展示项，不重新触发元信息切换。
        if (current.key == desired.key) {
            current = current.copy(
                imageRequest = desired.imageRequest,
                artworkPainter = painter
            )
        }
    }

    return PlayerSongPresentationTransition(
        previous = previous,
        current = current,
        progress = switchProgress.value.coerceIn(0f, 1f)
    )
}

@Composable
internal fun PlayerSongPresentationTransitionContent(
    transition: PlayerSongPresentationTransition,
    switchDirection: Int,
    switchDistancePx: Int,
    modifier: Modifier = Modifier,
    content: @Composable (PlayerSongPresentation, Float) -> Unit
) {
    val progress = transition.progress
    val direction = if (switchDirection < 0) -1 else 1
    val items = transition.previous?.let { oldPresentation ->
        listOf(oldPresentation to true, transition.current to false)
    } ?: listOf(transition.current to false)

    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        items.forEach { (presentation, isPrevious) ->
            val stableKey = presentation.key
                ?: presentation.imageRequest?.data
                ?: presentation.title
            key(stableKey) {
                val itemAlpha = if (isPrevious) 1f - progress else 1f.takeIf {
                    transition.previous == null
                } ?: progress
                val offsetX = if (isPrevious) {
                    -switchDistancePx * direction * progress
                } else {
                    switchDistancePx * direction * (1f - progress)
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        // 切歌位移只更新渲染图层，避免每个动画帧都触发布局，且保留子像素平滑度。
                        .graphicsLayer {
                            translationX = offsetX
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    content(presentation, itemAlpha)
                }
            }
        }
    }
}

internal const val PlayerSongSwitchDurationMillis = 320
