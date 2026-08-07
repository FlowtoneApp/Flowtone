package ink.tenqui.flowtone.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
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
        val candidates = findCandidates(song)
        coroutineContext.ensureActive()
        if (candidates.isEmpty()) {
            Log.d(TAG, "not found")
            return LyricsLoadResult.NotFound
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

    fun getLyricsFolders(): List<LyricsFolder> {
        return directoryStore.getTreeUris().map(::readLyricsFolder)
    }

    fun addLyricsDirectory(treeUri: Uri): Boolean {
        val currentUris = directoryStore.getTreeUris()
        if (currentUris.any { it.normalizeScheme() == treeUri.normalizeScheme() }) {
            return false
        }
        directoryStore.saveTreeUris(currentUris + treeUri)
        invalidateLyricsCache()
        return true
    }

    fun removeLyricsDirectory(treeUri: Uri) {
        directoryStore.saveTreeUris(
            directoryStore.getTreeUris().filterNot { it.normalizeScheme() == treeUri.normalizeScheme() }
        )
        invalidateLyricsCache()
    }

    private fun invalidateLyricsCache() {
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

    private fun findCandidates(song: Song): List<Uri> {
        val expectedName = expectedLyricsName(song)
        val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStoreCandidate(song)?.let(::add)
        }
            findLegacyFileCandidate(song)?.let(::add)
            if (expectedName != null) {
                directoryStore.getTreeUris().forEach { treeUri ->
                    findConfiguredFolderCandidate(treeUri, expectedName)?.let(::add)
                }
            }
        }.distinct()
        return candidates
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

    private fun findConfiguredFolderCandidate(treeUri: Uri, expectedName: String): Uri? {
        val permissionPersisted = contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
        if (!permissionPersisted) {
            return null
        }
        return try {
            val treeDocumentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val directoryUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryUri,
                treeDocumentId
            )
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val nameColumn = cursor.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val typeColumn = cursor.getColumnIndexOrThrow(
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                while (cursor.moveToNext()) {
                    val isDirectory = cursor.getString(typeColumn) ==
                        android.provider.DocumentsContract.Document.MIME_TYPE_DIR
                    if (!isDirectory && cursor.getString(nameColumn).equals(expectedName, ignoreCase = true)) {
                        return@use android.provider.DocumentsContract.buildDocumentUriUsingTree(
                            treeUri,
                            cursor.getString(idColumn)
                        )
                    }
                }
                null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.d(TAG, "configured folder lookup failed=${error::class.simpleName}")
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

    private fun readLyricsFolder(treeUri: Uri): LyricsFolder {
        val fallbackName = treeUri.lastPathSegment
            ?.substringAfterLast('%')
            ?.ifBlank { null }
            ?: "歌词文件夹"
        val hasPermission = contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
        if (!hasPermission) {
            return LyricsFolder(treeUri, fallbackName, treeUri.shortDescription(), false)
        }
        return try {
            val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val documentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            val name = contentResolver.query(
                documentUri,
                arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            LyricsFolder(
                uri = treeUri,
                displayName = name?.ifBlank { null } ?: fallbackName,
                location = treeUri.shortDescription(),
                isAccessible = true
            )
        } catch (_: Throwable) {
            LyricsFolder(treeUri, fallbackName, treeUri.shortDescription(), false)
        }
    }

    private companion object {
        const val TAG = "Lyrics"
        const val CACHE_SIZE = 24
    }
}

private fun Uri.shortDescription(): String =
    lastPathSegment?.takeLast(24)?.let { "…$it" } ?: "已选目录"

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
