package ink.tenqui.flowtone.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType

class AudioScanner(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val fileMetadataReader: SongFileMetadataReader =
        AndroidSongFileMetadataReader(contentResolver)
    private val metadataCache = SongFileMetadataCache(appContext)

    fun scanSongs(): List<Song> {
        val startedAtMs = SystemClock.elapsedRealtime()
        val contentUri = audioContentUri()
        val indexedSongs = queryIndexedSongs(contentUri) ?: run {
            Log.w(TAG, "scan failed because MediaStore returned no cursor")
            return emptyList()
        }
        val validUris = indexedSongs.mapTo(mutableSetOf()) { it.uri.toString() }
        val changedCacheEntries = mutableMapOf<String, SongFileMetadataCacheEntry>()
        var cacheHits = 0
        var fileReads = 0
        var fileTagHits = 0
        var fileTagOverrides = 0
        var fileReadFailures = 0

        val songs = indexedSongs.map { indexedSong ->
            val cached = metadataCache.find(
                uri = indexedSong.uri,
                dateModifiedSeconds = indexedSong.dateModifiedSeconds,
                sizeBytes = indexedSong.sizeBytes
            )
            val fileMetadata = if (cached != null) {
                cacheHits++
                cached.metadata
            } else {
                fileReads++
                fileMetadataReader.read(indexedSong.uri).also { metadata ->
                    if (metadata == null) fileReadFailures++
                    changedCacheEntries[indexedSong.uri.toString()] = SongFileMetadataCacheEntry(
                        dateModifiedSeconds = indexedSong.dateModifiedSeconds,
                        sizeBytes = indexedSong.sizeBytes,
                        metadata = metadata
                    )
                }
            }
            if (fileMetadata.hasUsableTag()) fileTagHits++
            if (indexedSong.isOverriddenBy(fileMetadata)) fileTagOverrides++
            indexedSong.toSong(fileMetadata)
        }

        metadataCache.update(
            validUris = validUris,
            changedEntries = changedCacheEntries
        )
        Log.i(
            TAG,
            "scan complete songs=${songs.size}, elapsedMs=" +
                "${SystemClock.elapsedRealtime() - startedAtMs}, cacheHits=$cacheHits, " +
                "fileReads=$fileReads, fileTagHits=$fileTagHits, " +
                "fileTagOverrides=$fileTagOverrides, " +
                "fileReadFailures=$fileReadFailures"
        )
        return songs
    }

    private fun queryIndexedSongs(contentUri: Uri): List<IndexedSong>? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val cursor = contentResolver.query(
            contentUri,
            projection,
            selection,
            arrayOf(MIN_MUSIC_DURATION_MS.toString()),
            "${MediaStore.Audio.Media.TITLE} ASC"
        ) ?: return null

        return cursor.use { queryCursor ->
            buildList {
                while (queryCursor.moveToNext()) {
                    add(queryCursor.toIndexedSong(contentUri))
                }
            }
        }
    }

    private fun Cursor.toIndexedSong(contentUri: Uri): IndexedSong {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val albumIdColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        return IndexedSong(
            id = id,
            uri = ContentUris.withAppendedId(contentUri, id),
            mediaStoreTitle = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)),
            mediaStoreArtist = getString(getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)),
            durationMs = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
            albumId = if (isNull(albumIdColumn)) null else getLong(albumIdColumn),
            filePath = nullableText(MediaStore.Audio.Media.DATA),
            displayName = nullableText(MediaStore.MediaColumns.DISPLAY_NAME),
            relativePath = nullableText(MediaStore.MediaColumns.RELATIVE_PATH),
            dateAddedSeconds = getLong(
                getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            ).coerceAtLeast(0L),
            dateModifiedSeconds = getLong(
                getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            ).coerceAtLeast(0L),
            sizeBytes = getLong(
                getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            ).coerceAtLeast(0L)
        )
    }

    private fun Cursor.nullableText(columnName: String): String? {
        val column = getColumnIndex(columnName)
        if (column < 0 || isNull(column)) return null
        return getString(column).orEmpty().ifBlank { null }
    }

    private fun audioContentUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private data class IndexedSong(
        val id: Long,
        val uri: Uri,
        val mediaStoreTitle: String?,
        val mediaStoreArtist: String?,
        val durationMs: Long,
        val albumId: Long?,
        val filePath: String?,
        val displayName: String?,
        val relativePath: String?,
        val dateAddedSeconds: Long,
        val dateModifiedSeconds: Long,
        val sizeBytes: Long
    ) {
        fun isOverriddenBy(fileMetadata: SongFileMetadata?): Boolean {
            if (fileMetadata == null) return false
            val fileTitle = usableMetadataText(fileMetadata.title)
            val fileArtist = usableMetadataText(fileMetadata.artist)
            return (fileTitle != null && fileTitle != usableMetadataText(mediaStoreTitle)) ||
                (fileArtist != null && fileArtist != usableMetadataText(mediaStoreArtist))
        }

        fun toSong(fileMetadata: SongFileMetadata?): Song {
            val metadata = resolveSongMetadata(
                fileMetadata = fileMetadata,
                mediaStoreTitle = mediaStoreTitle,
                mediaStoreArtist = mediaStoreArtist,
                mediaStoreDurationMs = durationMs
            )
            return Song(
                id = id,
                sourceType = SourceType.Local,
                title = metadata.title,
                artist = metadata.artist,
                durationMs = metadata.durationMs,
                uri = uri,
                albumId = albumId,
                artworkUri = albumId?.let {
                    ContentUris.withAppendedId(ALBUM_ART_BASE_URI, it)
                },
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
        }
    }

    private companion object {
        const val TAG = "AudioScanner"
        const val MIN_MUSIC_DURATION_MS = 30_000L
        val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}

private fun SongFileMetadata?.hasUsableTag(): Boolean {
    return this != null &&
        (usableMetadataText(title) != null || usableMetadataText(artist) != null)
}
