package ink.tenqui.flowtone.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import ink.tenqui.flowtone.core.model.PlaylistSongEntry
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.isSongLiked
import ink.tenqui.flowtone.ui.player.approximateLuminance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
internal data class PlaylistCardVisualState(
    val baseColorArgb: Int? = null,
    val signature: String = ""
) {
    companion object {
        val Empty = PlaylistCardVisualState()
    }
}

@Composable
internal fun rememberPlaylistCardVisualStates(
    playlists: List<LibraryPlaylistCard>,
    songs: List<Song>,
    playlistSongEntries: List<PlaylistSongEntry>,
    likedSongKeys: List<String>
): Map<String, PlaylistCardVisualState> {
    val context = LocalContext.current.applicationContext
    val cache = remember { PlaylistArtworkColorCache() }
    val specs = remember(playlists, songs, playlistSongEntries, likedSongKeys) {
        buildPlaylistArtworkSpecs(
            playlists = playlists,
            songs = songs,
            playlistSongEntries = playlistSongEntries,
            likedSongKeys = likedSongKeys
        )
    }

    LaunchedEffect(context, specs) {
        cache.update(
            context = context,
            specs = specs
        )
    }

    return cache.visualStates
}

internal data class PlaylistArtworkSpec(
    val playlistId: String,
    val signature: String,
    val samples: List<PlaylistArtworkSample>
)

internal data class PlaylistArtworkSample(
    val artworkKey: String,
    val artworkUri: Uri
)

internal fun buildPlaylistArtworkSpecs(
    playlists: List<LibraryPlaylistCard>,
    songs: List<Song>,
    playlistSongEntries: List<PlaylistSongEntry>,
    likedSongKeys: List<String>
): List<PlaylistArtworkSpec> {
    val songsById = songs.associateBy { song -> song.id.toString() }
    val entriesByPlaylist = playlistSongEntries
        .groupBy { entry -> entry.playlistId }
        .mapValues { (_, entries) -> entries.sortedBy { entry -> entry.addedAt } }

    return playlists
        .distinctBy { playlist -> playlist.id }
        .map { playlist ->
            val playlistSongs = if (playlist.id == LikedSongsPlaylistId && playlist.isSystem) {
                songs.filter { song -> isSongLiked(song, likedSongKeys) }
            } else {
                entriesByPlaylist[playlist.id]
                    .orEmpty()
                    .mapNotNull { entry -> songsById[entry.songId] }
            }
            val samples = playlistSongs
                .asSequence()
                .mapNotNull { song -> song.artworkUri }
                .map { artworkUri ->
                    PlaylistArtworkSample(
                        artworkKey = artworkUri.toString(),
                        artworkUri = artworkUri
                    )
                }
                .distinctBy { sample -> sample.artworkKey }
                .take(MaxPlaylistArtworkSamples)
                .toList()
            PlaylistArtworkSpec(
                playlistId = playlist.id,
                signature = playlistArtworkSignature(
                    songCount = playlistSongs.size,
                    samples = samples
                ),
                samples = samples
            )
        }
}

private fun playlistArtworkSignature(
    songCount: Int,
    samples: List<PlaylistArtworkSample>
): String {
    return buildString {
        append(songCount)
        samples.forEach { sample ->
            append('|')
            append(sample.artworkKey)
        }
    }
}

private class PlaylistArtworkColorCache {
    val visualStates = mutableStateMapOf<String, PlaylistCardVisualState>()

    private val playlistColorCache = mutableMapOf<PlaylistArtworkCacheKey, Int?>()
    private val artworkColorCache = mutableMapOf<String, ArtworkRepresentativeColor?>()

    suspend fun update(
        context: Context,
        specs: List<PlaylistArtworkSpec>
    ) {
        val activePlaylistIds = specs.mapTo(mutableSetOf()) { spec -> spec.playlistId }
        visualStates.keys
            .filterNot { playlistId -> playlistId in activePlaylistIds }
            .forEach { playlistId -> visualStates.remove(playlistId) }

        specs.forEach { spec ->
            if (visualStates[spec.playlistId] == null) {
                visualStates[spec.playlistId] = PlaylistCardVisualState(
                    signature = spec.signature
                )
            }
        }

        specs.forEach { spec ->
            val cacheKey = PlaylistArtworkCacheKey(
                playlistId = spec.playlistId,
                signature = spec.signature
            )
            if (playlistColorCache.containsKey(cacheKey)) {
                publish(
                    spec = spec,
                    baseColorArgb = playlistColorCache[cacheKey]
                )
                return@forEach
            }

            val baseColor = calculatePlaylistBaseColor(
                context = context,
                samples = spec.samples
            )
            playlistColorCache[cacheKey] = baseColor
            publish(
                spec = spec,
                baseColorArgb = baseColor
            )
        }
    }

    private fun publish(
        spec: PlaylistArtworkSpec,
        baseColorArgb: Int?
    ) {
        visualStates[spec.playlistId] = PlaylistCardVisualState(
            baseColorArgb = baseColorArgb,
            signature = spec.signature
        )
    }

    private suspend fun calculatePlaylistBaseColor(
        context: Context,
        samples: List<PlaylistArtworkSample>
    ): Int? {
        if (samples.isEmpty()) {
            return null
        }

        val artworkColors = samples.mapNotNull { sample ->
            if (artworkColorCache.containsKey(sample.artworkKey)) {
                artworkColorCache[sample.artworkKey]
            } else {
                val color = extractArtworkRepresentativeColor(
                    context = context,
                    sample = sample
                )
                artworkColorCache[sample.artworkKey] = color
                color
            }
        }

        return mergeArtworkRepresentativeColors(artworkColors)
    }
}

private data class PlaylistArtworkCacheKey(
    val playlistId: String,
    val signature: String
)

private data class ArtworkRepresentativeColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val luminance: Float,
    val weight: Float
)

private suspend fun extractArtworkRepresentativeColor(
    context: Context,
    sample: PlaylistArtworkSample
): ArtworkRepresentativeColor? {
    return runCatching {
        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(sample.artworkUri)
                .size(ArtworkSampleSizePx, ArtworkSampleSizePx)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap = (result as? SuccessResult)?.image
                ?.toBitmap(ArtworkSampleSizePx, ArtworkSampleSizePx)
                ?: return@withContext null

            bitmap.extractRepresentativeColor()
        }
    }.getOrElse { throwable ->
        if (throwable is CancellationException) {
            throw throwable
        }
        null
    }
}

private fun Bitmap.extractRepresentativeColor(): ArtworkRepresentativeColor? {
    if (width <= 0 || height <= 0) {
        return null
    }

    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)

    val hsv = FloatArray(3)
    var hueX = 0.0
    var hueY = 0.0
    var saturationSum = 0.0
    var valueSum = 0.0
    var luminanceSum = 0.0
    var weightSum = 0.0
    var validPixelCount = 0

    pixels.forEach { color ->
        val alpha = AndroidColor.alpha(color)
        if (alpha < MinOpaqueAlpha) {
            return@forEach
        }

        AndroidColor.colorToHSV(color, hsv)
        val saturation = hsv[1]
        val value = hsv[2]
        val luminance = approximateLuminance(color)
        val extremePenalty = when {
            luminance < 0.04f || luminance > 0.96f -> 0.08f
            value < 0.06f || (value > 0.98f && saturation < 0.08f) -> 0.12f
            else -> 1f
        }
        val chromaWeight = 0.28f + saturation * 0.72f
        val luminanceWeight = (1f - abs(luminance - 0.5f) * 1.35f)
            .coerceIn(0.18f, 1f)
        val weight = (alpha / 255f) * chromaWeight * luminanceWeight * extremePenalty
        if (weight <= 0f) {
            return@forEach
        }

        val radians = hsv[0] * PI / 180.0
        val hueWeight = weight * saturation.coerceAtLeast(0.05f)
        hueX += cos(radians) * hueWeight
        hueY += sin(radians) * hueWeight
        saturationSum += saturation * weight
        valueSum += value * weight
        luminanceSum += luminance * weight
        weightSum += weight
        validPixelCount += 1
    }

    if (validPixelCount == 0 || weightSum <= 0.0) {
        return null
    }

    val averageSaturation = (saturationSum / weightSum).toFloat()
    val averageValue = (valueSum / weightSum).toFloat()
    val averageLuminance = (luminanceSum / weightSum).toFloat()
    val hue = circularHueOrFallback(
        hueX = hueX,
        hueY = hueY,
        fallbackHue = DefaultPlaylistFallbackHue
    )
    val coverWeight = ((0.35f + averageSaturation * 0.65f) *
        (1f - abs(averageLuminance - 0.5f) * 0.80f).coerceIn(0.35f, 1f))
        .coerceIn(0.20f, 1f)

    return ArtworkRepresentativeColor(
        hue = hue,
        saturation = averageSaturation,
        value = averageValue,
        luminance = averageLuminance,
        weight = coverWeight
    )
}

private fun mergeArtworkRepresentativeColors(
    colors: List<ArtworkRepresentativeColor>
): Int? {
    if (colors.isEmpty()) {
        return null
    }

    var hueX = 0.0
    var hueY = 0.0
    var saturationSum = 0.0
    var valueSum = 0.0
    var luminanceSum = 0.0
    var weightSum = 0.0

    colors.forEach { color ->
        val radians = color.hue * PI / 180.0
        val hueWeight = color.weight * color.saturation.coerceAtLeast(0.12f)
        hueX += cos(radians) * hueWeight
        hueY += sin(radians) * hueWeight
        saturationSum += color.saturation * color.weight
        valueSum += color.value * color.weight
        luminanceSum += color.luminance * color.weight
        weightSum += color.weight
    }

    if (weightSum <= 0.0) {
        return null
    }

    val hue = circularHueOrFallback(
        hueX = hueX,
        hueY = hueY,
        fallbackHue = DefaultPlaylistFallbackHue
    )
    val averageSaturation = (saturationSum / weightSum).toFloat()
    val averageValue = (valueSum / weightSum).toFloat()
    val averageLuminance = (luminanceSum / weightSum).toFloat()
    val safeSaturation = when {
        averageSaturation < 0.10f -> 0.16f
        else -> averageSaturation.coerceIn(0.16f, 0.62f)
    }
    val safeValue = ((averageValue + averageLuminance) / 2f)
        .coerceIn(0.34f, 0.78f)

    return AndroidColor.HSVToColor(
        floatArrayOf(
            hue,
            safeSaturation,
            safeValue
        )
    )
}

private fun circularHueOrFallback(
    hueX: Double,
    hueY: Double,
    fallbackHue: Float
): Float {
    if (abs(hueX) < 0.0001 && abs(hueY) < 0.0001) {
        return fallbackHue
    }

    val degrees = atan2(hueY, hueX) * 180.0 / PI
    return ((degrees + 360.0) % 360.0).toFloat()
}

private const val MaxPlaylistArtworkSamples = 8
private const val ArtworkSampleSizePx = 48
private const val MinOpaqueAlpha = 0x80
private const val DefaultPlaylistFallbackHue = 258f
