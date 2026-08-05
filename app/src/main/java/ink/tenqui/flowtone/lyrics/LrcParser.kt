package ink.tenqui.flowtone.lyrics

object LrcParser {
    private val timestampTag = Regex("\\[(\\d+):(\\d{1,2})(?:\\.(\\d{1,3}))?]")

    fun parse(source: String): List<LyricLine> {
        return buildList {
            source.lineSequence().forEach { sourceLine ->
                val matches = timestampTag.findAll(sourceLine).toList()
                if (matches.isEmpty()) return@forEach

                val text = normalizeLyricText(
                    sourceLine.substring(matches.last().range.last + 1)
                )
                matches.forEach { match ->
                    parseTimestamp(match)?.let { timestampMs ->
                        add(LyricLine(timestampMs = timestampMs, text = text))
                    }
                }
            }
        }.sortedBy { it.timestampMs }
    }

    private fun parseTimestamp(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        if (seconds !in 0..59) return null
        val fraction = match.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }

    private fun normalizeLyricText(text: String): String =
        text.trimStart { character ->
            character.isWhitespace() ||
                character == '\uFEFF' ||
                character == '\u200B'
        }
}
