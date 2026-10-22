package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CancellationException

internal enum class PlayerArtworkLoadState {
    Missing,
    Loading,
    Ready,
    Failed
}

internal data class PlayerArtworkRequestIdentity(
    val data: Any,
    val sizePx: Int
)

internal data class PlayerSongPresentation(
    val key: String?,
    val lyricsSongId: Long?,
    val title: String,
    val artist: String
)

/**
 * 封面有独立于歌曲文字的状态与进度。
 *
 * basePainter 在目标图片准备期间保持完整可见；targetPainter 就绪后叠加渐变。
 * transitionId 只用于取消过期的封面动画，不参与歌曲文字切换。
 */
internal data class PlayerArtworkPresentation(
    val imageRequest: ImageRequest?,
    val requestIdentity: PlayerArtworkRequestIdentity? = null,
    val basePainter: Painter? = null,
    val baseIdentity: PlayerArtworkRequestIdentity? = null,
    val targetPainter: Painter? = null,
    val targetIdentity: PlayerArtworkRequestIdentity? = null,
    val targetIsPlaceholder: Boolean = false,
    val loadState: PlayerArtworkLoadState = PlayerArtworkLoadState.Missing,
    val largeArtworkPainter: Painter? = null,
    val largeArtworkRequestIdentity: PlayerArtworkRequestIdentity? = null,
    val progress: Float = 1f,
    val transitionId: Int = 0,
    val isTransitioning: Boolean = false
)

internal data class PlayerSongPresentationTransition(
    val previous: PlayerSongPresentation?,
    val current: PlayerSongPresentation,
    val progress: Float,
    val artwork: PlayerArtworkPresentation
)

private data class DesiredPlayerSongPresentation(
    val key: String?,
    val lyricsSongId: Long?,
    val title: String,
    val artist: String,
    val imageRequest: ImageRequest?,
    val artworkRequestIdentity: PlayerArtworkRequestIdentity?,
    val largeArtworkRequest: ImageRequest?,
    val largeArtworkRequestIdentity: PlayerArtworkRequestIdentity?
) {
    fun songPresentation() = PlayerSongPresentation(
        key = key,
        lyricsSongId = lyricsSongId,
        title = title,
        artist = artist
    )
}

internal data class RetainedPlayerArtwork(
    val painter: Painter,
    val identity: PlayerArtworkRequestIdentity?
)

/**
 * 快速连续切歌时，保留当前过渡中占比更大的封面作为下一次过渡起点。
 */
internal fun retainedPlayerArtworkForNextTarget(
    artwork: PlayerArtworkPresentation,
    crossfadeProgress: Float
): RetainedPlayerArtwork? {
    val progress = crossfadeProgress.coerceIn(0f, 1f)
    if (artwork.targetIsPlaceholder) {
        if (progress >= 0.5f || artwork.basePainter == null) return null
        return RetainedPlayerArtwork(artwork.basePainter, artwork.baseIdentity)
    }

    val target = artwork.targetPainter?.let { painter ->
        RetainedPlayerArtwork(painter, artwork.targetIdentity)
    }
    val base = artwork.basePainter?.let { painter ->
        RetainedPlayerArtwork(painter, artwork.baseIdentity)
    }
    return when {
        target == null -> base
        base == null -> target
        progress >= 0.5f -> target
        else -> base
    }
}

internal fun preparePlayerArtworkTarget(
    current: PlayerArtworkPresentation,
    imageRequest: ImageRequest?,
    requestIdentity: PlayerArtworkRequestIdentity?,
    largeArtworkRequestIdentity: PlayerArtworkRequestIdentity?,
    crossfadeProgress: Float
): PlayerArtworkPresentation {
    val retained = retainedPlayerArtworkForNextTarget(current, crossfadeProgress)
    val transitionId = current.transitionId + 1
    val reusesArtwork = requestIdentity != null && retained?.identity == requestIdentity
    val retainedLargePainter = current.largeArtworkPainter?.takeIf {
        reusesArtwork &&
            current.largeArtworkRequestIdentity == largeArtworkRequestIdentity
    }

    if (reusesArtwork) {
        return PlayerArtworkPresentation(
            imageRequest = imageRequest,
            requestIdentity = requestIdentity,
            basePainter = retained?.painter,
            baseIdentity = requestIdentity,
            loadState = PlayerArtworkLoadState.Ready,
            largeArtworkPainter = retainedLargePainter,
            largeArtworkRequestIdentity = largeArtworkRequestIdentity.takeIf {
                retainedLargePainter != null
            },
            progress = 1f,
            transitionId = transitionId
        )
    }

    if (requestIdentity == null && retained != null) {
        return PlayerArtworkPresentation(
            imageRequest = null,
            basePainter = retained.painter,
            baseIdentity = retained.identity,
            targetIsPlaceholder = true,
            loadState = PlayerArtworkLoadState.Missing,
            progress = 0f,
            transitionId = transitionId,
            isTransitioning = true
        )
    }

    return PlayerArtworkPresentation(
        imageRequest = imageRequest,
        requestIdentity = requestIdentity,
        basePainter = retained?.painter,
        baseIdentity = retained?.identity,
        loadState = if (requestIdentity == null) {
            PlayerArtworkLoadState.Missing
        } else {
            PlayerArtworkLoadState.Loading
        },
        largeArtworkRequestIdentity = largeArtworkRequestIdentity,
        progress = 1f,
        transitionId = transitionId
    )
}

internal fun playerArtworkReadyForTarget(
    current: PlayerArtworkPresentation,
    requestedIdentity: PlayerArtworkRequestIdentity,
    painter: Painter
): PlayerArtworkPresentation? {
    if (
        current.requestIdentity != requestedIdentity ||
        current.loadState != PlayerArtworkLoadState.Loading
    ) {
        return null
    }

    if (current.basePainter != null && current.baseIdentity == requestedIdentity) {
        return current.copy(
            targetPainter = null,
            targetIdentity = null,
            targetIsPlaceholder = false,
            loadState = PlayerArtworkLoadState.Ready,
            progress = 1f,
            transitionId = current.transitionId + 1,
            isTransitioning = false
        )
    }

    return current.copy(
        targetPainter = painter,
        targetIdentity = requestedIdentity,
        targetIsPlaceholder = false,
        loadState = PlayerArtworkLoadState.Ready,
        progress = 0f,
        transitionId = current.transitionId + 1,
        isTransitioning = true
    )
}

internal fun playerArtworkFailedForTarget(
    current: PlayerArtworkPresentation,
    requestedIdentity: PlayerArtworkRequestIdentity
): PlayerArtworkPresentation? {
    if (
        current.requestIdentity != requestedIdentity ||
        current.loadState != PlayerArtworkLoadState.Loading
    ) {
        return null
    }

    return if (current.basePainter != null) {
        current.copy(
            targetPainter = null,
            targetIdentity = null,
            targetIsPlaceholder = true,
            loadState = PlayerArtworkLoadState.Failed,
            largeArtworkPainter = null,
            largeArtworkRequestIdentity = null,
            progress = 0f,
            transitionId = current.transitionId + 1,
            isTransitioning = true
        )
    } else {
        current.copy(
            targetPainter = null,
            targetIdentity = null,
            targetIsPlaceholder = false,
            loadState = PlayerArtworkLoadState.Failed,
            largeArtworkPainter = null,
            largeArtworkRequestIdentity = null,
            progress = 1f,
            transitionId = current.transitionId + 1,
            isTransitioning = false
        )
    }
}

internal fun completePlayerArtworkTransition(
    current: PlayerArtworkPresentation
): PlayerArtworkPresentation = when {
    current.targetIsPlaceholder -> current.copy(
        basePainter = null,
        baseIdentity = null,
        targetPainter = null,
        targetIdentity = null,
        targetIsPlaceholder = false,
        largeArtworkPainter = null,
        largeArtworkRequestIdentity = null,
        progress = 1f,
        isTransitioning = false
    )

    current.targetPainter != null -> current.copy(
        basePainter = current.targetPainter,
        baseIdentity = current.targetIdentity,
        targetPainter = null,
        targetIdentity = null,
        targetIsPlaceholder = false,
        largeArtworkRequestIdentity = current.largeArtworkRequestIdentity,
        progress = 1f,
        isTransitioning = false
    )

    else -> current.copy(progress = 1f, isTransitioning = false)
}

internal fun playerArtworkPlaceholderAlpha(
    artwork: PlayerArtworkPresentation
): Float = when {
    artwork.targetIsPlaceholder -> artwork.progress.coerceIn(0f, 1f)
    artwork.basePainter == null && artwork.targetPainter != null ->
        1f - artwork.progress.coerceIn(0f, 1f)
    artwork.basePainter == null -> 1f
    else -> 0f
}

internal fun shouldShowMissingArtworkPlaceholder(
    artwork: PlayerArtworkPresentation
): Boolean = playerArtworkPlaceholderAlpha(artwork) > 0.001f

internal fun isCurrentPlayerArtworkRequest(
    current: PlayerArtworkPresentation,
    requestedIdentity: PlayerArtworkRequestIdentity?,
    latestRequestIdentity: PlayerArtworkRequestIdentity?
): Boolean =
    requestedIdentity != null &&
        current.requestIdentity == requestedIdentity &&
        current.loadState == PlayerArtworkLoadState.Loading &&
        latestRequestIdentity == requestedIdentity

internal suspend fun publishPlayerArtworkPresentation(
    presentation: PlayerArtworkPresentation,
    progress: Animatable<Float, AnimationVector1D>,
    publish: (PlayerArtworkPresentation) -> Unit
) {
    // 先重置共享进度再发布新 Painter，避免首帧用上一次已完成的进度绘制目标封面。
    progress.snapTo(presentation.progress)
    publish(presentation)
}

private fun artworkRequestIdentity(
    request: ImageRequest?,
    sizePx: Int
): PlayerArtworkRequestIdentity? = request?.let { imageRequest ->
    PlayerArtworkRequestIdentity(
        data = imageRequest.data,
        sizePx = sizePx
    )
}

@Composable
internal fun rememberPlayerSongPresentationTransition(
    songKey: String?,
    lyricsSongId: Long?,
    title: String,
    artist: String,
    imageRequest: ImageRequest?,
    largeArtworkRequest: ImageRequest?,
    imageLoader: ImageLoader
): PlayerSongPresentationTransition {
    val context = LocalContext.current
    val desired = DesiredPlayerSongPresentation(
        key = songKey,
        lyricsSongId = lyricsSongId,
        title = title,
        artist = artist,
        imageRequest = imageRequest,
        artworkRequestIdentity = artworkRequestIdentity(imageRequest, 768),
        largeArtworkRequest = largeArtworkRequest,
        largeArtworkRequestIdentity = artworkRequestIdentity(largeArtworkRequest, 1_536)
    )
    val latestDesired by rememberUpdatedState(desired)
    var current by remember {
        mutableStateOf(desired.songPresentation())
    }
    var previous by remember { mutableStateOf<PlayerSongPresentation?>(null) }
    val switchProgress = remember { Animatable(1f) }

    var artwork by remember {
        mutableStateOf(
            PlayerArtworkPresentation(
                imageRequest = imageRequest,
                loadState = if (imageRequest == null) {
                    PlayerArtworkLoadState.Missing
                } else {
                    PlayerArtworkLoadState.Loading
                }
            )
        )
    }
    val artworkProgress = remember { Animatable(1f) }

    LaunchedEffect(desired.key) {
        val next = latestDesired.songPresentation()
        if (current.key == desired.key) {
            current = next
            return@LaunchedEffect
        }

        previous = current
        current = next
        switchProgress.snapTo(0f)
        switchProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = PlayerSongSwitchDurationMillis,
                easing = TrackSwitchProgressEasing
            )
        )
        if (current.key == desired.key && latestDesired.key == desired.key) {
            previous = null
        }
    }

    LaunchedEffect(desired.key, desired.lyricsSongId, desired.title, desired.artist) {
        if (current.key == desired.key) {
            current = desired.songPresentation()
        }
    }

    // 加载任务只由图片身份驱动。切换到同一封面的歌曲不会取消并重启图片请求。
    LaunchedEffect(desired.artworkRequestIdentity, imageLoader) {
        val requestedIdentity = desired.artworkRequestIdentity
        if (artwork.requestIdentity == requestedIdentity) {
            return@LaunchedEffect
        }

        val nextArtwork = preparePlayerArtworkTarget(
            current = artwork,
            imageRequest = desired.imageRequest,
            requestIdentity = requestedIdentity,
            largeArtworkRequestIdentity = desired.largeArtworkRequestIdentity,
            crossfadeProgress = artworkProgress.value
        )
        publishPlayerArtworkPresentation(nextArtwork, artworkProgress) {
            artwork = it
        }

        if (requestedIdentity == null) {
            return@LaunchedEffect
        }

        val request = requireNotNull(desired.imageRequest)
        val targetPainter = try {
            (imageLoader.execute(request) as? SuccessResult)
                ?.image
                ?.asPainter(context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

        if (
            !isCurrentPlayerArtworkRequest(
                current = artwork,
                requestedIdentity = requestedIdentity,
                latestRequestIdentity = latestDesired.artworkRequestIdentity
            )
        ) {
            return@LaunchedEffect
        }

        val result = if (targetPainter != null) {
            playerArtworkReadyForTarget(artwork, requestedIdentity, targetPainter)
        } else {
            playerArtworkFailedForTarget(artwork, requestedIdentity)
        } ?: return@LaunchedEffect

        publishPlayerArtworkPresentation(result, artworkProgress) {
            artwork = it
        }
    }

    // 每次封面目标变化只运行一条几何一致的进度动画。
    LaunchedEffect(artwork.transitionId) {
        val transition = artwork
        if (!transition.isTransitioning) {
            return@LaunchedEffect
        }

        artworkProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = PlayerSongSwitchDurationMillis,
                easing = TrackSwitchProgressEasing
            )
        )
        if (artwork.transitionId == transition.transitionId) {
            artwork = completePlayerArtworkTransition(artwork)
        }
    }

    val artworkReadyForLargeRequest =
        artwork.requestIdentity == desired.artworkRequestIdentity &&
            artwork.loadState == PlayerArtworkLoadState.Ready &&
            (artwork.baseIdentity == desired.artworkRequestIdentity ||
                artwork.targetIdentity == desired.artworkRequestIdentity)

    LaunchedEffect(
        desired.artworkRequestIdentity,
        desired.largeArtworkRequestIdentity,
        artworkReadyForLargeRequest,
        imageLoader
    ) {
        val request = desired.largeArtworkRequest
        val expectedIdentity = desired.largeArtworkRequestIdentity
        if (artwork.largeArtworkRequestIdentity != expectedIdentity) {
            artwork = artwork.copy(
                largeArtworkPainter = null,
                largeArtworkRequestIdentity = null
            )
        }
        if (
            request == null ||
            expectedIdentity == null ||
            !artworkReadyForLargeRequest
        ) {
            return@LaunchedEffect
        }
        if (
            artwork.largeArtworkPainter != null &&
            artwork.largeArtworkRequestIdentity == expectedIdentity
        ) {
            return@LaunchedEffect
        }

        val painter = try {
            (imageLoader.execute(request) as? SuccessResult)?.image?.asPainter(context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        if (
            painter != null &&
            artwork.requestIdentity == desired.artworkRequestIdentity &&
            artworkReadyForLargeRequest &&
            latestDesired.artworkRequestIdentity == desired.artworkRequestIdentity &&
            latestDesired.largeArtworkRequestIdentity == expectedIdentity
        ) {
            artwork = artwork.copy(
                largeArtworkPainter = painter,
                largeArtworkRequestIdentity = expectedIdentity
            )
        }
    }

    val renderedArtwork = artwork.copy(
        imageRequest = desired.imageRequest,
        largeArtworkPainter = artwork.largeArtworkPainter.takeIf {
            artwork.largeArtworkRequestIdentity == desired.largeArtworkRequestIdentity
        },
        progress = artworkProgress.value.coerceIn(0f, 1f)
    )

    return PlayerSongPresentationTransition(
        previous = previous,
        current = current,
        progress = switchProgress.value.coerceIn(0f, 1f),
        artwork = renderedArtwork
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
            val stableKey = presentation.key ?: presentation.lyricsSongId ?: presentation.title
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
