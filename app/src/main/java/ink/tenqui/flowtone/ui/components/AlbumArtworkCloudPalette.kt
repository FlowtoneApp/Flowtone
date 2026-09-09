package ink.tenqui.flowtone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.ui.player.CloudColorPath
import ink.tenqui.flowtone.ui.player.extractMaterialYouSeedColors
import ink.tenqui.flowtone.ui.player.materialYouCloudColors
import ink.tenqui.flowtone.ui.player.neutralCloudColorsFromCover
import ink.tenqui.flowtone.ui.player.normalizeBackdropColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ArtworkColorCacheLimit = 12

internal data class ArtworkPaletteCacheKey(
    val artworkUri: String,
    val isDarkTheme: Boolean
)

internal fun artworkPaletteCacheIdentity(artworkData: Any): String = when (artworkData) {
    is ExtensionImage -> "extension-image:${artworkData.extensionId}:${artworkData.url}"
    else -> artworkData.toString()
}

internal object ArtworkPaletteMemoryCache {
    private val colors = LinkedHashMap<ArtworkPaletteCacheKey, Color>(16, 0.75f, true)

    @Synchronized fun color(key: ArtworkPaletteCacheKey): Color? = colors[key]

    @Synchronized fun putColor(key: ArtworkPaletteCacheKey, value: Color) {
        colors[key] = value
        trim(colors)
    }

    @Synchronized internal fun resolveColorForTest(
        artworkKey: String,
        isDarkTheme: Boolean,
        resolver: () -> Color
    ): Color {
        val key = ArtworkPaletteCacheKey(artworkKey, isDarkTheme)
        return colors[key] ?: resolver().also { putColor(key, it) }
    }

    @Synchronized internal fun clearForTest() {
        colors.clear()
    }

    private fun <T> trim(values: LinkedHashMap<ArtworkPaletteCacheKey, T>) {
        while (values.size > ArtworkColorCacheLimit) values.remove(values.keys.first())
    }
}

@Composable
internal fun rememberAlbumArtworkCloudColor(
    artworkData: Any?,
    fallbackColor: Color,
    isDarkTheme: Boolean
): Color {
    val context = LocalContext.current
    val imageLoader = if (artworkData is ExtensionImage) {
        remember(context) { ExtensionManager.get(context).extensionImageLoader }
    } else {
        context.imageLoader
    }
    return resolvedAlbumArtworkCloudColor(
        resolvedColor = rememberArtworkBackgroundColor(
            artworkData = artworkData,
            imageLoader = imageLoader,
            fallbackColor = fallbackColor,
            isDarkTheme = isDarkTheme
        ),
        fallbackColor = fallbackColor
    )
}

internal fun resolvedAlbumArtworkCloudColor(
    resolvedColor: Color?,
    fallbackColor: Color
): Color = resolvedColor ?: fallbackColor

@Composable
internal fun rememberArtworkBackgroundColor(
    artworkData: Any?,
    imageLoader: ImageLoader,
    fallbackColor: Color,
    isDarkTheme: Boolean
): Color? {
    val context = LocalContext.current
    val cacheKey = remember(artworkData, isDarkTheme) {
        artworkData?.let {
            ArtworkPaletteCacheKey(artworkPaletteCacheIdentity(it), isDarkTheme)
        }
    }
    var resolvedColor by remember(cacheKey) {
        mutableStateOf(cacheKey?.let(ArtworkPaletteMemoryCache::color))
    }

    LaunchedEffect(cacheKey) {
        val key = cacheKey ?: run {
            resolvedColor = null
            return@LaunchedEffect
        }
        ArtworkPaletteMemoryCache.color(key)?.let { cached ->
            resolvedColor = cached
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(artworkData)
            .size(96, 96)
            .allowHardware(false)
            .crossfade(false)
            .build()
        val color = try {
            withContext(Dispatchers.Default) {
                val bitmap = (imageLoader.execute(request) as? SuccessResult)
                    ?.image?.toBitmap(96, 96) ?: return@withContext null
                val seedResult = extractMaterialYouSeedColors(
                    bitmap = bitmap,
                    fallbackColor = fallbackColor.toArgb(),
                    count = 1
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
                normalizeBackdropColors(colors, isDarkTheme).firstOrNull()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        resolvedColor = color
        if (color != null) ArtworkPaletteMemoryCache.putColor(key, color)
    }
    return resolvedColor
}
