package ink.tenqui.flowtone.data.search

import java.util.Locale

data class SearchQuery private constructor(
    val rawText: String,
    val text: String,
    val normalizedText: String
) {
    val isBlank: Boolean
        get() = normalizedText.isBlank()

    companion object {
        val Empty = from("")

        fun from(rawText: String): SearchQuery {
            val trimmed = rawText.trim()
            return SearchQuery(
                rawText = rawText,
                text = trimmed,
                normalizedText = trimmed.lowercase(Locale.ROOT)
            )
        }
    }
}
