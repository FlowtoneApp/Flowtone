package ink.tenqui.flowtone.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.text.normalizeMetadataText
import ink.tenqui.flowtone.core.text.titleWithFilenameFallback
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
                val title = titleWithFilenameFallback(
                    metadataTitle = normalizeMetadataText(cursor.getString(titleColumn).orEmpty()),
                    filePath = rawFilePath,
                    artist = artist
                ).ifBlank { "\u672a\u77e5\u6b4c\u66f2" }
                val durationMs = cursor.getLong(durationColumn)
                val dateAddedSeconds = cursor.getLong(dateAddedColumn).coerceAtLeast(0L)
                val dateModifiedSeconds = cursor.getLong(dateModifiedColumn).coerceAtLeast(0L)
                val uri = ContentUris.withAppendedId(contentUri, id)
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
                        title = title,
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

    private companion object {
        const val MIN_MUSIC_DURATION_MS = 30_000L
        val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
