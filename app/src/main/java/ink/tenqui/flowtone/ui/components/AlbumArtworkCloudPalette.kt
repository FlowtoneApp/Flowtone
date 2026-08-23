package ink.tenqui.flowtone.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import ink.tenqui.flowtone.ui.player.CloudColorPath
import ink.tenqui.flowtone.ui.player.extractMaterialYouSeedColors
import ink.tenqui.flowtone.ui.player.materialYouCloudColors
import ink.tenqui.flowtone.ui.player.neutralCloudColorsFromCover
import ink.tenqui.flowtone.ui.player.normalizeBackdropColors
import ink.tenqui.flowtone.ui.theme.FlowtoneCloudPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AlbumArtworkPaletteCacheLimit = 12

private data class AlbumArtworkPaletteCacheKey(
    val artworkUri: String,
    val isDarkTheme: Boolean
)

@Composable
internal fun rememberAlbumArtworkCloudPalette(
    artworkUri: Uri?,
    fallbackPalette: FlowtoneCloudPalette,
    isDarkTheme: Boolean
): FlowtoneCloudPalette {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val cache = remember {
        LinkedHashMap<AlbumArtworkPaletteCacheKey, FlowtoneCloudPalette>()
    }
    val cacheKey = remember(artworkUri, isDarkTheme) {
        artworkUri?.let { uri ->
            AlbumArtworkPaletteCacheKey(
                artworkUri = uri.toString(),
                isDarkTheme = isDarkTheme
            )
        }
    }
    var resolvedPalette by remember(cacheKey) {
        mutableStateOf(cacheKey?.let(cache::get))
    }

    LaunchedEffect(cacheKey) {
        val key = cacheKey ?: run {
            resolvedPalette = null
            return@LaunchedEffect
        }
        cache[key]?.let { cached ->
            resolvedPalette = cached
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(artworkUri)
            .size(96, 96)
            .allowHardware(false)
            .crossfade(false)
            .build()
        val palette = try {
            withContext(Dispatchers.Default) {
                val bitmap = (imageLoader.execute(request) as? SuccessResult)
                    ?.image
                    ?.toBitmap(96, 96)
                    ?: return@withContext null
                val seedResult = extractMaterialYouSeedColors(
                    bitmap = bitmap,
                    fallbackColor = fallbackPalette.primary.toArgb(),
                    count = 3
                )
                val colors = when (seedResult.colorPath) {
                    CloudColorPath.MaterialYouSeeds -> materialYouCloudColors(
                        seedColors = seedResult.seedColors,
                        isDarkTheme = isDarkTheme
                    )

                    CloudColorPath.NeutralLowChroma -> neutralCloudColorsFromCover(
                        averageLuminance = seedResult.averageLuminance,
                        isDarkTheme = isDarkTheme
                    )

                    CloudColorPath.ThemeFallback -> return@withContext null
                }
                normalizeBackdropColors(colors, isDarkTheme).let { normalized ->
                    FlowtoneCloudPalette(
                        primary = normalized[0],
                        secondary = normalized[1],
                        tertiary = normalized[2]
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }

        resolvedPalette = palette
        if (palette != null) {
            cache[key] = palette
            while (cache.size > AlbumArtworkPaletteCacheLimit) {
                cache.remove(cache.keys.first())
            }
        }
    }

    return resolvedPalette ?: fallbackPalette
}
