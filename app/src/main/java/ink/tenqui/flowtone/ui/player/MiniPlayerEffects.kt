package ink.tenqui.flowtone.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import ink.tenqui.flowtone.core.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun shouldKeepScreenOnForLyricsPage(
    lyricsModeActive: Boolean,
    allowScreenOffOnLyricsPage: Boolean
): Boolean = lyricsModeActive && !allowScreenOffOnLyricsPage

@Composable
internal fun MiniPlayerLyricsKeepScreenOnEffect(keepScreenOn: Boolean) {
    val view = LocalView.current
    if (keepScreenOn) {
        DisposableEffect(view) {
            val previousKeepScreenOn = view.keepScreenOn
            view.keepScreenOn = true
            onDispose {
                view.keepScreenOn = previousKeepScreenOn
            }
        }
    }
}

@Composable
internal fun MiniPlayerBackdropEffects(
    currentSong: Song?,
    title: String,
    artworkUri: Uri?,
    fallbackSeedColor: Int,
    isDarkTheme: Boolean,
    paletteImageRequest: ImageRequest?,
    backgroundImageRequest: ImageRequest?,
    coverImageRequest: ImageRequest?,
    fallbackSeedColors: List<Int>,
    fallbackCloudColors: List<Color>,
    fallbackBackdrop: PlayerBackdropState,
    lastStableBackdrop: PlayerBackdropState,
    usingFallbackCloudColors: Boolean,
    context: Context,
    onLastStableBackdropChange: (PlayerBackdropState) -> Unit,
    onUsingFallbackCloudColorsChange: (Boolean) -> Unit
) {
    LaunchedEffect(currentSong?.id, currentSong?.uri, artworkUri, fallbackSeedColor, isDarkTheme) {
        Log.d(
            FLOWTONE_CLOUD_COLORS_TAG,
            "start songId=${currentSong?.id}, song=${title}, artworkUri=$artworkUri, " +
                "requestData=${paletteImageRequest?.data}"
        )

        if (artworkUri == null || paletteImageRequest == null) {
            onLastStableBackdropChange(fallbackBackdrop)
            onUsingFallbackCloudColorsChange(true)
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
            onLastStableBackdropChange(
                if (nextBackgroundRequest != null && nextCoverRequest != null) {
                    PlayerBackdropState.Artwork(
                        key = currentSong.toBackdropKey(),
                        colors = colors,
                        backgroundImageRequest = nextBackgroundRequest,
                        coverImageRequest = nextCoverRequest
                    )
                } else {
                    fallbackBackdrop
                }
            )
            onUsingFallbackCloudColorsChange(usedFallback)
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            onLastStableBackdropChange(fallbackBackdrop)
            onUsingFallbackCloudColorsChange(true)
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
}

@Composable
internal fun MiniPlayerAddToPlaylistDialogEffects(
    addToPlaylistDialogBackgroundColor: Color,
    onAddToPlaylistDialogBackgroundColorChange: (Color) -> Unit
) {
    LaunchedEffect(addToPlaylistDialogBackgroundColor) {
        onAddToPlaylistDialogBackgroundColorChange(addToPlaylistDialogBackgroundColor)
    }
}

@Composable
internal fun MiniPlayerFullscreenContentEffects(
    fullscreen: Boolean,
    expanded: Boolean,
    hasCurrentSong: Boolean,
    currentSong: Song?,
    onFullscreenPlayerChange: (Boolean) -> Unit,
    resetFullscreenContentMode: () -> Unit,
    resetFullscreenPlaybackContentMode: () -> Unit,
    exitFullscreenContentModeForSongChange: () -> Unit
) {
    LaunchedEffect(fullscreen, expanded, hasCurrentSong) {
        if (!fullscreen || !expanded) {
            onFullscreenPlayerChange(false)
            resetFullscreenContentMode()
            resetFullscreenPlaybackContentMode()
        } else if (!hasCurrentSong) {
            onFullscreenPlayerChange(false)
            exitFullscreenContentModeForSongChange()
            resetFullscreenPlaybackContentMode()
        }
    }
    LaunchedEffect(fullscreen, hasCurrentSong, currentSong?.id) {
        if (!fullscreen) {
            resetFullscreenContentMode()
            resetFullscreenPlaybackContentMode()
        } else if (!hasCurrentSong) {
            exitFullscreenContentModeForSongChange()
            resetFullscreenPlaybackContentMode()
        } else if (currentSong?.id != null) {
            exitFullscreenContentModeForSongChange()
        }
    }
}

@Composable
internal fun MiniPlayerSongTransitionEffects(
    currentSong: Song?,
    onProgressScrubbingChange: (Boolean) -> Unit
) {
    LaunchedEffect(currentSong?.id) {
        onProgressScrubbingChange(false)
    }
}

@Composable
internal fun MiniPlayerPlayPauseVisualLockEffects(
    playPauseVisualLockToken: Int,
    keepPlayPauseVisualLockedAfterSeek: Boolean,
    onKeepPlayPauseVisualLockedAfterSeekChange: (Boolean) -> Unit
) {
    LaunchedEffect(playPauseVisualLockToken) {
        val token = playPauseVisualLockToken
        if (keepPlayPauseVisualLockedAfterSeek) {
            delay(650L)
            if (playPauseVisualLockToken == token) {
                onKeepPlayPauseVisualLockedAfterSeekChange(false)
            }
        }
    }
}

@Composable
internal fun MiniPlayerQueueEffects(
    queueSheetBackgroundBlurred: Boolean,
    queueSheetBackgroundBlurProgress: Animatable<Float, AnimationVector1D>
) {
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
}
