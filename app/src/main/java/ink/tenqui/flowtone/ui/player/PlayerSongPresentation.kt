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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
    val artist: String,
    val imageRequest: ImageRequest?,
    val artworkRequestIdentity: PlayerArtworkRequestIdentity?,
    val artworkPainter: Painter?,
    val artworkPainterIdentity: PlayerArtworkRequestIdentity?,
    val artworkLoadState: PlayerArtworkLoadState,
    val previousArtworkPainter: Painter? = null,
    val previousArtworkPainterIdentity: PlayerArtworkRequestIdentity? = null,
    val largeArtworkRequestIdentity: PlayerArtworkRequestIdentity? = null,
    val largeArtworkPainter: Painter? = null,
    val largeArtworkPainterIdentity: PlayerArtworkRequestIdentity? = null
)

internal data class PlayerSongPresentationTransition(
    val previous: PlayerSongPresentation?,
    val current: PlayerSongPresentation,
    val progress: Float,
    val artworkCrossfadeProgress: Float
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
)

internal data class RetainedPlayerArtwork(
    val painter: Painter,
    val identity: PlayerArtworkRequestIdentity?
)

internal fun retainedPlayerArtworkForNextTarget(
    presentation: PlayerSongPresentation,
    crossfadeProgress: Float
): RetainedPlayerArtwork? {
    val currentArtwork = presentation.artworkPainter?.let { painter ->
        RetainedPlayerArtwork(painter, presentation.artworkPainterIdentity)
    }
    val previousArtwork = presentation.previousArtworkPainter?.let { painter ->
        RetainedPlayerArtwork(painter, presentation.previousArtworkPainterIdentity)
    }
    return when {
        currentArtwork == null -> previousArtwork
        previousArtwork == null -> currentArtwork
        crossfadeProgress >= 0.5f -> currentArtwork
        else -> previousArtwork
    }
}

internal fun shouldShowMissingArtworkPlaceholder(
    presentation: PlayerSongPresentation
): Boolean = when (presentation.artworkLoadState) {
    PlayerArtworkLoadState.Missing,
    PlayerArtworkLoadState.Failed -> true

    PlayerArtworkLoadState.Loading ->
        presentation.artworkPainter == null && presentation.previousArtworkPainter == null

    PlayerArtworkLoadState.Ready ->
        presentation.artworkPainter == null && presentation.previousArtworkPainter == null
}

internal fun isRetainingOutgoingArtworkDuringSongSwitch(
    transition: PlayerSongPresentationTransition,
    presentation: PlayerSongPresentation
): Boolean {
    val outgoing = transition.previous ?: return false
    if (presentation !== transition.current) return false
    val painter = presentation.artworkPainter ?: return false
    return presentation.artworkLoadState == PlayerArtworkLoadState.Loading &&
        presentation.artworkPainterIdentity != presentation.artworkRequestIdentity &&
        outgoing.artworkPainter === painter
}

internal fun playerArtworkSwitchAlpha(
    transition: PlayerSongPresentationTransition,
    presentation: PlayerSongPresentation,
    switchAlpha: Float
): Float = if (isRetainingOutgoingArtworkDuringSongSwitch(transition, presentation)) {
    1f
} else {
    switchAlpha
}

internal fun isCurrentPlayerArtworkRequest(
    current: PlayerSongPresentation,
    requestedSongKey: String?,
    requestedIdentity: PlayerArtworkRequestIdentity?,
    latestSongKey: String?,
    latestRequestIdentity: PlayerArtworkRequestIdentity?
): Boolean =
    current.key == requestedSongKey &&
        current.artworkRequestIdentity == requestedIdentity &&
        current.artworkLoadState == PlayerArtworkLoadState.Loading &&
        latestSongKey == requestedSongKey &&
        latestRequestIdentity == requestedIdentity

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
        mutableStateOf(
            PlayerSongPresentation(
                key = songKey,
                lyricsSongId = lyricsSongId,
                title = title,
                artist = artist,
                imageRequest = imageRequest,
                artworkRequestIdentity = desired.artworkRequestIdentity,
                artworkPainter = null,
                artworkPainterIdentity = null,
                artworkLoadState = if (imageRequest == null) {
                    PlayerArtworkLoadState.Missing
                } else {
                    PlayerArtworkLoadState.Loading
                },
                largeArtworkRequestIdentity = desired.largeArtworkRequestIdentity
            )
        )
    }
    var previous by remember { mutableStateOf<PlayerSongPresentation?>(null) }
    val switchProgress = remember { Animatable(1f) }
    val artworkCrossfadeProgress = remember { Animatable(1f) }

    // 歌曲和封面请求变化时先更新展示目标，并将当前实际可见的 Painter 留在渲染状态里。
    LaunchedEffect(desired.key, desired.artworkRequestIdentity, imageLoader) {
        val songChanged = current.key != desired.key
        val requestChanged = current.artworkRequestIdentity != desired.artworkRequestIdentity
        val needsInitialLoad = desired.imageRequest != null &&
            current.artworkLoadState == PlayerArtworkLoadState.Loading &&
            current.artworkPainterIdentity != desired.artworkRequestIdentity
        if (!songChanged && !requestChanged && !needsInitialLoad) {
            return@LaunchedEffect
        }

        val retainedArtwork = retainedPlayerArtworkForNextTarget(
            presentation = current,
            crossfadeProgress = artworkCrossfadeProgress.value
        )
        val reusesArtwork = desired.imageRequest != null &&
            retainedArtwork?.identity == desired.artworkRequestIdentity
        val missingArtworkWithSameSong = desired.imageRequest == null &&
            !songChanged &&
            retainedArtwork != null

        if (songChanged) {
            val oldPresentation = current
            val matchingLargePainter = oldPresentation.largeArtworkPainter?.takeIf {
                retainedArtwork != null &&
                    oldPresentation.largeArtworkPainterIdentity?.data == retainedArtwork.identity?.data
            }
            previous = oldPresentation.copy(
                artworkPainter = retainedArtwork?.painter,
                artworkPainterIdentity = retainedArtwork?.identity,
                previousArtworkPainter = null,
                previousArtworkPainterIdentity = null,
                largeArtworkPainter = matchingLargePainter,
                largeArtworkPainterIdentity = if (matchingLargePainter != null) {
                    oldPresentation.largeArtworkPainterIdentity
                } else {
                    null
                }
            )
            switchProgress.snapTo(0f)
        }

        val matchingLargePainter = current.largeArtworkPainter?.takeIf {
            desired.largeArtworkRequestIdentity != null &&
                current.largeArtworkPainterIdentity == desired.largeArtworkRequestIdentity
        }
        current = PlayerSongPresentation(
            key = desired.key,
            lyricsSongId = desired.lyricsSongId,
            title = desired.title,
            artist = desired.artist,
            imageRequest = desired.imageRequest,
            artworkRequestIdentity = desired.artworkRequestIdentity,
            artworkPainter = if (desired.imageRequest == null) {
                null
            } else {
                retainedArtwork?.painter
            },
            artworkPainterIdentity = if (desired.imageRequest == null) {
                null
            } else {
                retainedArtwork?.identity
            },
            artworkLoadState = when {
                desired.imageRequest == null -> PlayerArtworkLoadState.Missing
                reusesArtwork -> PlayerArtworkLoadState.Ready
                else -> PlayerArtworkLoadState.Loading
            },
            previousArtworkPainter = retainedArtwork?.painter.takeIf {
                missingArtworkWithSameSong
            },
            previousArtworkPainterIdentity = retainedArtwork?.identity.takeIf {
                missingArtworkWithSameSong
            },
            largeArtworkRequestIdentity = desired.largeArtworkRequestIdentity,
            largeArtworkPainter = matchingLargePainter,
            largeArtworkPainterIdentity = matchingLargePainter?.let {
                current.largeArtworkPainterIdentity
            }
        )
        artworkCrossfadeProgress.snapTo(if (missingArtworkWithSameSong) 0f else 1f)

        coroutineScope {
            if (previous != null && switchProgress.value < 1f) {
                launch {
                    val remainingDuration = (
                        (1f - switchProgress.value) * PlayerSongSwitchDurationMillis
                    ).toInt().coerceAtLeast(1)
                    switchProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = remainingDuration,
                            easing = TrackSwitchProgressEasing
                        )
                    )
                    if (
                        current.key == desired.key &&
                        current.artworkRequestIdentity == desired.artworkRequestIdentity &&
                        latestDesired.key == desired.key &&
                        latestDesired.artworkRequestIdentity == desired.artworkRequestIdentity
                    ) {
                        previous = null
                    }
                }
            }

            when {
                desired.imageRequest == null -> {
                    if (missingArtworkWithSameSong) {
                        launch {
                            artworkCrossfadeProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = PlayerSongSwitchDurationMillis,
                                    easing = TrackSwitchProgressEasing
                                )
                            )
                            if (
                                current.key == desired.key &&
                                current.artworkRequestIdentity == null &&
                                latestDesired.key == desired.key &&
                                latestDesired.artworkRequestIdentity == null
                            ) {
                                current = current.copy(
                                    previousArtworkPainter = null,
                                    previousArtworkPainterIdentity = null
                                )
                            }
                        }
                    }
                }

                reusesArtwork -> Unit

                else -> launch {
                    val targetPainter = try {
                        (imageLoader.execute(requireNotNull(desired.imageRequest)) as? SuccessResult)
                            ?.image
                            ?.asPainter(context)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        null
                    }

                    if (
                        !isCurrentPlayerArtworkRequest(
                            current = current,
                            requestedSongKey = desired.key,
                            requestedIdentity = desired.artworkRequestIdentity,
                            latestSongKey = latestDesired.key,
                            latestRequestIdentity = latestDesired.artworkRequestIdentity
                        )
                    ) {
                        return@launch
                    }

                    if (targetPainter == null) {
                        val fallback = current.artworkPainter?.let { painter ->
                            RetainedPlayerArtwork(painter, current.artworkPainterIdentity)
                        }
                        if (fallback == null) {
                            current = current.copy(
                                artworkPainter = null,
                                artworkPainterIdentity = null,
                                previousArtworkPainter = null,
                                previousArtworkPainterIdentity = null,
                                artworkLoadState = PlayerArtworkLoadState.Failed
                            )
                            artworkCrossfadeProgress.snapTo(1f)
                        } else {
                            current = current.copy(
                                artworkPainter = null,
                                artworkPainterIdentity = null,
                                previousArtworkPainter = fallback.painter,
                                previousArtworkPainterIdentity = fallback.identity,
                                artworkLoadState = PlayerArtworkLoadState.Failed
                            )
                            artworkCrossfadeProgress.snapTo(0f)
                            artworkCrossfadeProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = PlayerSongSwitchDurationMillis,
                                    easing = TrackSwitchProgressEasing
                                )
                            )
                            if (
                                current.key == desired.key &&
                                current.artworkRequestIdentity == desired.artworkRequestIdentity &&
                                current.artworkLoadState == PlayerArtworkLoadState.Failed &&
                                latestDesired.key == desired.key &&
                                latestDesired.artworkRequestIdentity == desired.artworkRequestIdentity
                            ) {
                                current = current.copy(
                                    previousArtworkPainter = null,
                                    previousArtworkPainterIdentity = null
                                )
                            }
                        }
                        return@launch
                    }

                    val trackSwitchActive = previous != null && switchProgress.value < 1f
                    val fallback = current.artworkPainter?.let { painter ->
                        RetainedPlayerArtwork(painter, current.artworkPainterIdentity)
                    }?.takeIf { artwork ->
                        artwork.identity != desired.artworkRequestIdentity
                    }
                    current = current.copy(
                        artworkPainter = targetPainter,
                        artworkPainterIdentity = desired.artworkRequestIdentity,
                        previousArtworkPainter = fallback?.painter.takeUnless {
                            trackSwitchActive
                        },
                        previousArtworkPainterIdentity = fallback?.identity.takeUnless {
                            trackSwitchActive
                        },
                        artworkLoadState = PlayerArtworkLoadState.Ready
                    )

                    if (trackSwitchActive || fallback == null) {
                        artworkCrossfadeProgress.snapTo(1f)
                    } else {
                        artworkCrossfadeProgress.snapTo(0f)
                        artworkCrossfadeProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = PlayerSongSwitchDurationMillis,
                                easing = TrackSwitchProgressEasing
                            )
                        )
                        if (
                            current.key == desired.key &&
                            current.artworkRequestIdentity == desired.artworkRequestIdentity &&
                            current.artworkPainter === targetPainter &&
                            current.artworkLoadState == PlayerArtworkLoadState.Ready &&
                            latestDesired.key == desired.key &&
                            latestDesired.artworkRequestIdentity == desired.artworkRequestIdentity
                        ) {
                            current = current.copy(
                                previousArtworkPainter = null,
                                previousArtworkPainterIdentity = null
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(desired.key, desired.lyricsSongId, desired.title, desired.artist, desired.imageRequest) {
        if (current.key == desired.key) {
            current = current.copy(
                lyricsSongId = desired.lyricsSongId,
                title = desired.title,
                artist = desired.artist,
                imageRequest = desired.imageRequest
            )
        }
    }

    LaunchedEffect(desired.key, desired.largeArtworkRequestIdentity) {
        if (current.key == desired.key &&
            current.largeArtworkRequestIdentity != desired.largeArtworkRequestIdentity
        ) {
            val matchingPainter = current.largeArtworkPainter?.takeIf {
                current.largeArtworkPainterIdentity == desired.largeArtworkRequestIdentity
            }
            current = current.copy(
                largeArtworkRequestIdentity = desired.largeArtworkRequestIdentity,
                largeArtworkPainter = matchingPainter,
                largeArtworkPainterIdentity = matchingPainter?.let {
                    current.largeArtworkPainterIdentity
                }
            )
        }
    }

    // 大图只增强已就绪的缩略封面；缩略图仍是稳定的绘制基线。
    LaunchedEffect(
        desired.key,
        desired.largeArtworkRequestIdentity,
        current.artworkRequestIdentity,
        current.artworkPainterIdentity,
        current.artworkLoadState,
        imageLoader
    ) {
        val request = desired.largeArtworkRequest
        if (request == null) {
            if (current.key == desired.key && current.largeArtworkPainter != null) {
                current = current.copy(
                    largeArtworkPainter = null,
                    largeArtworkPainterIdentity = null
                )
            }
            return@LaunchedEffect
        }
        if (
            current.key != desired.key ||
            current.artworkLoadState != PlayerArtworkLoadState.Ready ||
            current.artworkPainter == null ||
            current.artworkPainterIdentity != current.artworkRequestIdentity
        ) {
            return@LaunchedEffect
        }
        if (
            current.largeArtworkPainter != null &&
            current.largeArtworkPainterIdentity == desired.largeArtworkRequestIdentity
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
            current.key == desired.key &&
            current.artworkRequestIdentity == desired.artworkRequestIdentity &&
            current.artworkLoadState == PlayerArtworkLoadState.Ready &&
            latestDesired.key == desired.key &&
            latestDesired.largeArtworkRequestIdentity == desired.largeArtworkRequestIdentity
        ) {
            current = current.copy(
                largeArtworkPainter = painter,
                largeArtworkPainterIdentity = desired.largeArtworkRequestIdentity
            )
        }
    }

    return PlayerSongPresentationTransition(
        previous = previous,
        current = current,
        progress = switchProgress.value.coerceIn(0f, 1f),
        artworkCrossfadeProgress = artworkCrossfadeProgress.value.coerceIn(0f, 1f)
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
