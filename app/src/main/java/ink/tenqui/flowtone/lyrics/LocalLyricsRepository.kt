package ink.tenqui.flowtone.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import ink.tenqui.flowtone.core.model.Song
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

class LocalLyricsRepository(
    context: Context
) {
    private val contentResolver = context.contentResolver
    private val directoryStore = LyricsDirectoryStore(context.applicationContext)
    private val cache = object : LinkedHashMap<String, List<LyricLine>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LyricLine>>?): Boolean {
            return size > CACHE_SIZE
        }
    }

    fun load(song: Song): LyricsLoadResult {
        val key = song.uri.toString()
        synchronized(cache) { cache[key] }?.let { return LyricsLoadResult.Found(it) }

        Log.d(TAG, "loading for song=${song.id}")
        val search = findCandidates(song)
        val candidates = search.candidates
        if (candidates.isEmpty()) {
            Log.d(TAG, "not found")
            return when (search.treeResult) {
                TreeCandidateResult.DirectoryNotSelected ->
                    LyricsLoadResult.DirectoryNotSelected
                TreeCandidateResult.DirectoryPermissionLost ->
                    LyricsLoadResult.DirectoryPermissionLost
                TreeCandidateResult.OutsideSelectedDirectory ->
                    LyricsLoadResult.OutsideSelectedDirectory
                TreeCandidateResult.NotFound,
                is TreeCandidateResult.Found -> LyricsLoadResult.NotFound
            }
        }
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                Log.d(TAG, "candidate found=$candidate")
                val text = contentResolver.openInputStream(candidate)?.use { input ->
                    input.readBytes().toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
                } ?: throw IllegalStateException("Unable to open lyrics file")
                val lines = LrcParser.parse(text)
                synchronized(cache) { cache[key] = lines }
                Log.d(TAG, "parsed lines=${lines.size}")
                return LyricsLoadResult.Found(lines)
            } catch (error: Throwable) {
                lastError = error
                Log.d(TAG, "candidate failed=${error::class.simpleName}")
            }
        }
        return LyricsLoadResult.Failed(lastError ?: IllegalStateException("Unable to read lyrics file"))
    }

    fun saveLyricsDirectory(treeUri: Uri) {
        directoryStore.saveTreeUri(treeUri)
        synchronized(cache) { cache.clear() }
    }

    private fun findCandidates(song: Song): CandidateSearch {
        val treeResult = findTreeCandidate(song)
        val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStoreCandidate(song)?.let(::add)
        }
            if (treeResult is TreeCandidateResult.Found) {
                add(treeResult.uri)
            }
        findLegacyFileCandidate(song)?.let(::add)
        }.distinct()
        return CandidateSearch(candidates = candidates, treeResult = treeResult)
    }

    private fun expectedLyricsName(song: Song): String? {
        val displayName = song.displayName ?: song.filePath?.let(::File)?.name ?: return null
        return displayName.substringBeforeLast('.', displayName) + ".lrc"
    }

    private fun findMediaStoreCandidate(song: Song): Uri? {
        val expectedName = expectedLyricsName(song) ?: return null
        val relativePath = song.relativePath ?: return null
        val volume = song.volumeName ?: MediaStore.VOLUME_EXTERNAL
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        return runCatching {
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(relativePath),
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn).equals(expectedName, ignoreCase = true)) {
                        return@use ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    }
                }
                null
            }
        }.onFailure { Log.d(TAG, "MediaStore lookup failed=${it::class.simpleName}") }.getOrNull()
    }

    private fun findTreeCandidate(song: Song): TreeCandidateResult {
        val treeUri = directoryStore.getTreeUri()
            ?: return TreeCandidateResult.DirectoryNotSelected
        val permissionPersisted = contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
        if (!permissionPersisted) {
            return TreeCandidateResult.DirectoryPermissionLost
        }
        val expectedName = expectedLyricsName(song) ?: return TreeCandidateResult.NotFound
        val relativeSegments = relativeSegmentsWithinTree(treeUri, song.relativePath)
            ?: return TreeCandidateResult.OutsideSelectedDirectory
        return try {
            var directoryUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            for (segment in relativeSegments) {
                val child = findChild(directoryUri, segment, requireDirectory = true)
                    ?: return TreeCandidateResult.NotFound
                directoryUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child)
            }
            val childDocumentId = findChild(directoryUri, expectedName, requireDirectory = false)
                ?: return TreeCandidateResult.NotFound
            TreeCandidateResult.Found(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId)
            )
        } catch (_: SecurityException) {
            TreeCandidateResult.DirectoryPermissionLost
        }
    }

    private fun relativeSegmentsWithinTree(treeUri: Uri, songRelativePath: String?): List<String>? {
        val songPath = songRelativePath?.trim('/') ?: return emptyList()
        val treePath = DocumentsContract.getTreeDocumentId(treeUri)
            .substringAfter(':', missingDelimiterValue = "")
            .trim('/')
        val relativePath = when {
            treePath.isEmpty() -> songPath
            songPath == treePath -> ""
            songPath.startsWith("$treePath/") -> songPath.removePrefix("$treePath/")
            else -> return null
        }
        return relativePath.split('/').filter(String::isNotBlank)
    }

    private fun findChild(directoryUri: Uri, expectedName: String, requireDirectory: Boolean): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getDocumentId(directoryUri)
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val isDirectory = cursor.getString(typeColumn) == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDirectory == requireDirectory && cursor.getString(nameColumn).equals(expectedName, true)) {
                    return@use cursor.getString(idColumn)
                }
            }
            null
        }
    }

    private fun findLegacyFileCandidate(song: Song): Uri? {
        val audioFile = song.filePath?.let(::File) ?: return null
        val baseName = audioFile.name.substringBeforeLast('.', audioFile.name)
        val candidate = audioFile.parentFile?.listFiles()?.firstOrNull { file ->
            file.isFile && file.name.substringBeforeLast('.', file.name).equals(baseName, true) &&
                file.extension.equals("lrc", true)
        } ?: return null
        return Uri.fromFile(candidate)
    }

    private companion object {
        const val TAG = "Lyrics"
        const val CACHE_SIZE = 24
    }
}

sealed interface LyricsLoadResult {
    data object DirectoryNotSelected : LyricsLoadResult
    data object DirectoryPermissionLost : LyricsLoadResult
    data object OutsideSelectedDirectory : LyricsLoadResult
    data object NotFound : LyricsLoadResult
    data class Found(val lines: List<LyricLine>) : LyricsLoadResult
    data class Failed(val throwable: Throwable) : LyricsLoadResult
}

private data class CandidateSearch(
    val candidates: List<Uri>,
    val treeResult: TreeCandidateResult
)

private sealed interface TreeCandidateResult {
    data object DirectoryNotSelected : TreeCandidateResult
    data object DirectoryPermissionLost : TreeCandidateResult
    data object OutsideSelectedDirectory : TreeCandidateResult
    data object NotFound : TreeCandidateResult
    data class Found(val uri: Uri) : TreeCandidateResult
}
