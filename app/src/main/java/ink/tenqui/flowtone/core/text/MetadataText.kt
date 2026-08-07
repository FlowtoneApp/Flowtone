package ink.tenqui.flowtone.core.text

import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder

/**
 * Repairs the common UTF-8-as-Latin-1 mojibake produced by a few tag readers.
 * Strings without a strong mojibake marker are returned unchanged.
 */
internal fun normalizeMetadataText(value: String): String {
    if (value.isEmpty() || !looksLikeMojibake(value)) return value

    val windows1252 = Charset.forName("windows-1252")
    val latin1 = Charset.forName("ISO-8859-1")
    var candidate = value
    repeat(2) {
        val charset = if (candidate.any { it.code in 0x80..0x9F }) latin1 else windows1252
        val encoder: CharsetEncoder = charset.newEncoder()
        if (!encoder.canEncode(candidate)) return@repeat
        val repaired = candidate.toByteArray(charset).toString(Charsets.UTF_8)
        if (repaired == candidate || repaired.contains('\uFFFD')) return@repeat
        candidate = repaired
        if (!looksLikeMojibake(candidate)) return candidate
    }
    return candidate
}

private fun looksLikeMojibake(value: String): Boolean {
    return value.any { character ->
        character == '\u00C7' || // Ç
        character == '\u00C3' || // Ã
        character == '\u00C2' || // Â
            character == '\u00E5' || // å
        character == '\u00E2' || // â
        character == '\u00E3' || // ã
        character == '\u00EF' || // ï
            character == '\u00F0' || // ð
            character == '\u00A4' // ¤
    }
}
