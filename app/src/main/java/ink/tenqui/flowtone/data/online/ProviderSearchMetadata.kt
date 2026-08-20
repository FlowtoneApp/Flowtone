package ink.tenqui.flowtone.data.online

import java.util.Locale

/** Provider 为搜索结果提供的有序、可扩展 metadata 项。type 保持 String 以兼容未来扩展。 */
data class ProviderSearchMetadata(
    val type: String,
    val value: Long? = null,
    val text: String? = null
)

data class ProviderSearchMetadataLabels(
    val trackCountSuffix: String,
    val playCountSuffix: String
)

fun formatProviderSearchMetadata(
    metadata: ProviderSearchMetadata,
    labels: ProviderSearchMetadataLabels
): String? {
    val type = metadata.type.trim().lowercase()
    return when (type) {
        "track_count" -> metadata.value?.takeIf { it >= 0L }?.let { "$it${labels.trackCountSuffix}" }
        "play_count" -> metadata.value?.takeIf { it >= 0L }
            ?.let { "${formatChineseCompactNumber(it)}${labels.playCountSuffix}" }
        "creator", "text" -> metadata.text?.trim()?.takeIf(String::isNotEmpty)
        else -> metadata.text?.trim()?.takeIf(String::isNotEmpty)
    }
}

fun formatProviderSearchMetadataLine(
    metadata: List<ProviderSearchMetadata>,
    labels: ProviderSearchMetadataLabels
): String? = metadata.mapNotNull { formatProviderSearchMetadata(it, labels) }
    .takeIf { it.isNotEmpty() }
    ?.joinToString(" · ")

fun formatChineseCompactNumber(value: Long): String {
    require(value >= 0L) { "value must not be negative" }
    return when {
        value < 1_000L -> value.toString()
        value < 10_000L -> compactNumber(value, 1_000L, "千")
        value < 100_000_000L -> compactNumber(value, 10_000L, "万")
        else -> compactNumber(value, 100_000_000L, "亿")
    }
}

private fun compactNumber(value: Long, unit: Long, suffix: String): String {
    val scaled = value.toDouble() / unit.toDouble()
    val formatted = String.format(Locale.ROOT, "%.1f", scaled).trimEnd('0').trimEnd('.')
    return "$formatted$suffix"
}
