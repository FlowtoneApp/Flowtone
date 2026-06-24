package ink.tenqui.flowtone.ui.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.CorePalette
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score

internal enum class CloudColorPath(val logName: String) {
    MaterialYouSeeds("materialYouSeeds"),
    NeutralLowChroma("neutralLowChroma"),
    ThemeFallback("themeFallback")
}

internal data class MaterialYouSeedColorResult(
    val seedColors: List<Int>,
    val opaquePixelCount: Int,
    val quantizedColorCount: Int,
    val averageSaturation: Float,
    val averageLuminance: Float,
    val isLowChromaCover: Boolean,
    val colorPath: CloudColorPath,
    val usedFallback: Boolean,
    val fallbackReason: String?
)

@SuppressLint("RestrictedApi")
internal fun extractMaterialYouSeedColors(
    bitmap: Bitmap,
    fallbackColor: Int,
    count: Int = 3
): MaterialYouSeedColorResult {
    return runCatching {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val opaquePixels = pixels.filter { color ->
            AndroidColor.alpha(color) >= 0x80
        }.toIntArray()

        if (opaquePixels.isEmpty()) {
            MaterialYouSeedColorResult(
                seedColors = listOf(fallbackColor, fallbackColor, fallbackColor).take(count),
                opaquePixelCount = 0,
                quantizedColorCount = 0,
                averageSaturation = 0f,
                averageLuminance = 0f,
                isLowChromaCover = true,
                colorPath = CloudColorPath.ThemeFallback,
                usedFallback = true,
                fallbackReason = "no opaque pixels"
            )
        } else {
            val stats = calculateCoverColorStats(opaquePixels)
            val quantized = QuantizerCelebi.quantize(opaquePixels, 128)
            val isLowChromaCover = stats.averageSaturation < 0.12f ||
                (stats.averageSaturation < 0.16f && stats.averageLuminance < 0.35f)

            if (isLowChromaCover) {
                MaterialYouSeedColorResult(
                    seedColors = emptyList(),
                    opaquePixelCount = opaquePixels.size,
                    quantizedColorCount = quantized.size,
                    averageSaturation = stats.averageSaturation,
                    averageLuminance = stats.averageLuminance,
                    isLowChromaCover = true,
                    colorPath = CloudColorPath.NeutralLowChroma,
                    usedFallback = false,
                    fallbackReason = "low chroma cover"
                )
            } else {
                val rankedColors = Score.score(quantized)
                if (rankedColors.isEmpty()) {
                    MaterialYouSeedColorResult(
                        seedColors = emptyList(),
                        opaquePixelCount = opaquePixels.size,
                        quantizedColorCount = quantized.size,
                        averageSaturation = stats.averageSaturation,
                        averageLuminance = stats.averageLuminance,
                        isLowChromaCover = false,
                        colorPath = CloudColorPath.NeutralLowChroma,
                        usedFallback = false,
                        fallbackReason = "score returned no seed colors"
                    )
                } else {
                    MaterialYouSeedColorResult(
                        seedColors = normalizeSeedColors(
                            seedColors = rankedColors,
                            fallbackColor = fallbackColor,
                            count = count
                        ),
                        opaquePixelCount = opaquePixels.size,
                        quantizedColorCount = quantized.size,
                        averageSaturation = stats.averageSaturation,
                        averageLuminance = stats.averageLuminance,
                        isLowChromaCover = false,
                        colorPath = CloudColorPath.MaterialYouSeeds,
                        usedFallback = false,
                        fallbackReason = null
                    )
                }
            }
        }
    }.getOrDefault(
        MaterialYouSeedColorResult(
            seedColors = listOf(fallbackColor, fallbackColor, fallbackColor).take(count),
            opaquePixelCount = 0,
            quantizedColorCount = 0,
            averageSaturation = 0f,
            averageLuminance = 0f,
            isLowChromaCover = false,
            colorPath = CloudColorPath.ThemeFallback,
            usedFallback = true,
            fallbackReason = "seed extraction failed"
        )
    )
}

internal data class CoverColorStats(
    val averageSaturation: Float,
    val averageLuminance: Float
)

internal fun calculateCoverColorStats(pixels: IntArray): CoverColorStats {
    if (pixels.isEmpty()) {
        return CoverColorStats(
            averageSaturation = 0f,
            averageLuminance = 0f
        )
    }

    val hsv = FloatArray(3)
    var saturationSum = 0f
    var luminanceSum = 0f

    pixels.forEach { color ->
        AndroidColor.colorToHSV(color, hsv)
        saturationSum += hsv[1]
        luminanceSum += approximateLuminance(color)
    }

    return CoverColorStats(
        averageSaturation = saturationSum / pixels.size.toFloat(),
        averageLuminance = luminanceSum / pixels.size.toFloat()
    )
}

internal fun approximateLuminance(color: Int): Float {
    val red = AndroidColor.red(color) / 255f
    val green = AndroidColor.green(color) / 255f
    val blue = AndroidColor.blue(color) / 255f
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

internal fun normalizeSeedColors(
    seedColors: List<Int>,
    fallbackColor: Int,
    count: Int
): List<Int> {
    return if (seedColors.size >= count) {
        seedColors.take(count)
    } else {
        seedColors + List(count - seedColors.size) {
            seedColors.lastOrNull() ?: fallbackColor
        }
    }
}

internal fun neutralCloudColorsFromCover(
    averageLuminance: Float,
    isDarkTheme: Boolean
): List<Color> {
    val base = averageLuminance.coerceIn(0.08f, 0.82f)

    return if (isDarkTheme) {
        listOf(
            Color(
                red = (base * 0.72f).coerceIn(0f, 1f),
                green = (base * 0.76f).coerceIn(0f, 1f),
                blue = (base * 0.82f).coerceIn(0f, 1f)
            ),
            Color(
                red = (base * 0.92f).coerceIn(0f, 1f),
                green = (base * 0.94f).coerceIn(0f, 1f),
                blue = (base * 0.98f).coerceIn(0f, 1f)
            ),
            Color(
                red = (base + 0.18f).coerceAtMost(0.88f),
                green = (base + 0.19f).coerceAtMost(0.90f),
                blue = (base + 0.22f).coerceAtMost(0.96f)
            )
        )
    } else {
        listOf(
            Color(
                red = (base + 0.20f).coerceAtMost(0.86f),
                green = (base + 0.21f).coerceAtMost(0.88f),
                blue = (base + 0.23f).coerceAtMost(0.92f)
            ),
            Color(
                red = (base + 0.30f).coerceAtMost(0.92f),
                green = (base + 0.31f).coerceAtMost(0.94f),
                blue = (base + 0.33f).coerceAtMost(0.96f)
            ),
            Color(
                red = (base + 0.12f).coerceAtMost(0.80f),
                green = (base + 0.13f).coerceAtMost(0.82f),
                blue = (base + 0.15f).coerceAtMost(0.86f)
            )
        )
    }
}

internal fun Int.toArgbHex(): String {
    return "#${toUInt().toString(16).padStart(8, '0')}"
}

internal fun Color.toArgbHex(): String {
    return toArgb().toArgbHex()
}

@SuppressLint("RestrictedApi")
internal fun materialYouCloudColors(
    seedColors: List<Int>,
    isDarkTheme: Boolean
): List<Color> {
    val fallbackSeed = seedColors.lastOrNull() ?: AndroidColor.GRAY
    val normalizedSeeds = if (seedColors.size >= 3) {
        seedColors.take(3)
    } else {
        seedColors + List(3 - seedColors.size) { fallbackSeed }
    }
    val tones = if (isDarkTheme) {
        listOf(68, 78, 88)
    } else {
        listOf(62, 72, 82)
    }

    return normalizedSeeds.zip(tones).map { (seed, tone) ->
        Color(CorePalette.of(seed).a1.tone(tone))
    }
}

