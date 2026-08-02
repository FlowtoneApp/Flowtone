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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class LocalLyricsRepository(
    context: Context
) {
    private val contentResolver = context.contentResolver
    private val directoryStore = LyricsDirectoryStore(context.applicationContext)
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestLock = Any()
    private val inFlightRequests = mutableMapOf<String, InFlightLyricsRequest>()
    private var directoryGeneration = 0
    private var preloadGeneration = 0L
    private var desiredPreloadUris: Set<String> = emptySet()
    private val cache = LinkedHashMap<String, List<LyricLine>>(CACHE_SIZE, 0.75f, true)
    private var pinnedCacheKeys: Set<String> = emptySet()

    fun getCachedLyrics(song: Song): List<LyricLine>? {
        return synchronized(requestLock) { cache[song.uri.toString()] }
    }

    fun updatePreloadWindow(
        generation: Long,
        currentSong: Song?,
        preloadSongs: List<Song>
    ) {
        synchronized(requestLock) {
            preloadGeneration = generation
            desiredPreloadUris = preloadSongs.mapTo(mutableSetOf()) { it.uri.toString() }
            inFlightRequests.forEach { (uri, request) ->
                if (uri in desiredPreloadUris && !request.hasForegroundRequester) {
                    request.preloadGeneration = generation
                }
            }
            pinnedCacheKeys = desiredPreloadUris.toMutableSet().apply {
                currentSong?.uri?.toString()?.let(::add)
            }
            trimCacheLocked()
        }
    }

    fun request(
        song: Song,
        role: LyricsRequestRole = LyricsRequestRole.Foreground
    ): LyricsLoadRequest = synchronized(requestLock) {
        val key = song.uri.toString()
        cache[key]?.let { lines ->
            Log.d(TAG, "cache hit for song=${song.id}")
            return LyricsLoadRequest(
                deferred = CompletableDeferred(LyricsLoadResult.Found(lines)),
                source = LyricsLoadSource.Cache
            )
        }
        inFlightRequests[key]?.let { inFlight ->
            if (role == LyricsRequestRole.Foreground) {
                inFlight.hasForegroundRequester = true
            }
            Log.d(TAG, "joining in-flight load for song=${song.id}")
            return LyricsLoadRequest(inFlight.deferred, LyricsLoadSource.InFlight)
        }

        val requestGeneration = directoryGeneration
        lateinit var deferred: Deferred<LyricsLoadResult>
        deferred = requestScope.async(start = CoroutineStart.LAZY) {
            loadUncached(song, key, requestGeneration, deferred)
        }
        val inFlight = InFlightLyricsRequest(
            deferred = deferred,
            hasForegroundRequester = role == LyricsRequestRole.Foreground,
            preloadGeneration = preloadGeneration.takeIf { role == LyricsRequestRole.Preload }
        )
        inFlightRequests[key] = inFlight
        deferred.invokeOnCompletion {
            synchronized(requestLock) {
                if (inFlightRequests[key] === inFlight) {
                    inFlightRequests.remove(key)
                }
            }
        }
        deferred.start()
        LyricsLoadRequest(deferred, LyricsLoadSource.NewRead)
    }

    private suspend fun loadUncached(
        song: Song,
        key: String,
        requestGeneration: Int,
        requestDeferred: Deferred<LyricsLoadResult>
    ): LyricsLoadResult {
        Log.d(TAG, "loading for song=${song.id}")
        val search = findCandidates(song)
        coroutineContext.ensureActive()
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
            coroutineContext.ensureActive()
            try {
                Log.d(TAG, "candidate found=$candidate")
                val text = contentResolver.openInputStream(candidate)?.use { input ->
                    input.readBytes().toString(StandardCharsets.UTF_8).removePrefix("\uFEFF")
                } ?: throw IllegalStateException("Unable to open lyrics file")
                coroutineContext.ensureActive()
                val lines = LrcParser.parse(text)
                coroutineContext.ensureActive()
                synchronized(requestLock) {
                    val activeRequest = inFlightRequests[key]
                    val belongsToCurrentWindow =
                        activeRequest?.preloadGeneration == preloadGeneration &&
                            key in desiredPreloadUris
                    val canWriteCache =
                        directoryGeneration == requestGeneration &&
                            activeRequest?.deferred === requestDeferred &&
                            (activeRequest.hasForegroundRequester || belongsToCurrentWindow)
                    if (canWriteCache) {
                        cache[key] = lines
                        trimCacheLocked()
                    }
                }
                Log.d(TAG, "parsed lines=${lines.size}")
                return LyricsLoadResult.Found(lines)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
                Log.d(TAG, "candidate failed=${error::class.simpleName}")
            }
        }
        return LyricsLoadResult.Failed(lastError ?: IllegalStateException("Unable to read lyrics file"))
    }

    fun saveLyricsDirectory(treeUri: Uri) {
        directoryStore.saveTreeUri(treeUri)
        val requestsToCancel = synchronized(requestLock) {
            directoryGeneration += 1
            preloadGeneration += 1
            desiredPreloadUris = emptySet()
            cache.clear()
            pinnedCacheKeys = emptySet()
            inFlightRequests.values.map { it.deferred }.also {
                inFlightRequests.clear()
            }
        }
        requestsToCancel.forEach { it.cancel() }
    }

    fun cancelPreload(key: String) {
        val requestToCancel = synchronized(requestLock) {
            val request = inFlightRequests[key]
            if (request == null || request.hasForegroundRequester) {
                null
            } else {
                inFlightRequests.remove(key)
                request.deferred
            }
        }
        requestToCancel?.cancel()
    }

    fun close() {
        requestScope.cancel()
    }

    private fun trimCacheLocked() {
        while (cache.size > CACHE_SIZE) {
            val removableKey = cache.keys.firstOrNull { it !in pinnedCacheKeys } ?: return
            cache.remove(removableKey)
        }
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

    suspend fun preload(song: Song) {
        coroutineContext.ensureActive()
        request(song, LyricsRequestRole.Preload).deferred.await()
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

data class LyricsLoadRequest(
    val deferred: Deferred<LyricsLoadResult>,
    val source: LyricsLoadSource
)

enum class LyricsLoadSource {
    Cache,
    InFlight,
    NewRead
}

enum class LyricsRequestRole {
    Foreground,
    Preload
}

private data class InFlightLyricsRequest(
    val deferred: Deferred<LyricsLoadResult>,
    var hasForegroundRequester: Boolean,
    var preloadGeneration: Long?
)

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
