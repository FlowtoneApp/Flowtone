package ink.tenqui.flowtone.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.text.normalizeMetadataText
import ink.tenqui.flowtone.core.model.SourceType

class AudioScanner(
    private val contentResolver: ContentResolver
) {
    fun scanSongs(): List<Song> {
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf(MIN_MUSIC_DURATION_MS.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val songs = mutableListOf<Song>()

        contentResolver.query(
            contentUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val artist = normalizeMetadataText(cursor.getString(artistColumn).orEmpty())
                    .ifBlank { "\u672a\u77e5\u827a\u672f\u5bb6" }
                val rawFilePath = if (dataColumn >= 0 && !cursor.isNull(dataColumn)) {
                    cursor.getString(dataColumn).orEmpty().ifBlank { null }
                } else {
                    null
                }
                val uri = ContentUris.withAppendedId(contentUri, id)
                val mediaStoreTitle = cursor.getString(titleColumn).orEmpty()
                val title = readId3Title(uri)
                    ?.takeIf { it.isNotBlank() }
                    ?: mediaStoreTitle
                val displayTitle = title.ifBlank { "\u672a\u77e5\u6b4c\u66F2" }
                val durationMs = cursor.getLong(durationColumn)
                val dateAddedSeconds = cursor.getLong(dateAddedColumn).coerceAtLeast(0L)
                val dateModifiedSeconds = cursor.getLong(dateModifiedColumn).coerceAtLeast(0L)
                val albumId = if (cursor.isNull(albumIdColumn)) {
                    null
                } else {
                    cursor.getLong(albumIdColumn)
                }
                val artworkUri = albumId?.let {
                    ContentUris.withAppendedId(ALBUM_ART_BASE_URI, it)
                }
                val filePath = rawFilePath
                val displayName = displayNameColumn.takeIf { it >= 0 }
                    ?.let(cursor::getString)
                    ?.ifBlank { null }
                val relativePath = relativePathColumn.takeIf { it >= 0 }
                    ?.let(cursor::getString)
                    ?.ifBlank { null }

                songs.add(
                    Song(
                        id = id,
                        sourceType = SourceType.Local,
                        title = displayTitle,
                        artist = artist,
                        durationMs = durationMs,
                        uri = uri,
                        albumId = albumId,
                        artworkUri = artworkUri,
                        filePath = filePath,
                        displayName = displayName,
                        relativePath = relativePath,
                        volumeName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.VOLUME_EXTERNAL
                        } else {
                            null
                        },
                        dateAddedSeconds = dateAddedSeconds,
                        dateModifiedSeconds = dateModifiedSeconds
                    )
                )
            }
        }

        return songs
    }

    private fun readId3Title(uri: Uri): String? = runCatching {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(ID3_READ_LIMIT)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read <= 0) break
                count += read
            }
            buffer.copyOf(count)
        } ?: return@runCatching null
        if (bytes.size < 10 || bytes.decodeAscii(0, 3) != "ID3") return@runCatching null
        val version = bytes[3].toInt() and 0xff
        if (version !in 3..4) return@runCatching null
        val tagEnd = minOf(bytes.size, 10 + bytes.syncSafeInt(6))
        var offset = 10
        while (offset + 10 <= tagEnd) {
            val frameId = bytes.decodeAscii(offset, offset + 4)
            if (frameId.all { it == '\u0000' }) break
            val frameSize = if (version == 4) bytes.syncSafeInt(offset + 4) else bytes.bigEndianInt(offset + 4)
            val dataStart = offset + 10
            val dataEnd = dataStart + frameSize
            if (frameSize <= 0 || dataEnd > tagEnd || dataEnd > bytes.size) break
            if (frameId == "TIT2") return@runCatching decodeId3Text(bytes, dataStart, dataEnd)
            offset = dataEnd
        }
        null
    }.getOrNull()

    private companion object {
        const val MIN_MUSIC_DURATION_MS = 30_000L
        const val ID3_READ_LIMIT = 256 * 1024
        val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

private fun ByteArray.syncSafeInt(offset: Int): Int =
    ((this[offset].toInt() and 0x7f) shl 21) or
        ((this[offset + 1].toInt() and 0x7f) shl 14) or
        ((this[offset + 2].toInt() and 0x7f) shl 7) or
        (this[offset + 3].toInt() and 0x7f)

private fun ByteArray.bigEndianInt(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 24) or
        ((this[offset + 1].toInt() and 0xff) shl 16) or
        ((this[offset + 2].toInt() and 0xff) shl 8) or
        (this[offset + 3].toInt() and 0xff)

private fun ByteArray.decodeAscii(start: Int, end: Int): String =
    copyOfRange(start, end).toString(Charsets.ISO_8859_1)

private fun decodeId3Text(bytes: ByteArray, start: Int, end: Int): String? {
    if (start >= end) return null
    val charset = when (bytes[start].toInt() and 0xff) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> return null
    }
    return bytes.copyOfRange(start + 1, end)
        .toString(charset)
        .trimEnd('\u0000')
        .takeIf { it.isNotBlank() }
}
