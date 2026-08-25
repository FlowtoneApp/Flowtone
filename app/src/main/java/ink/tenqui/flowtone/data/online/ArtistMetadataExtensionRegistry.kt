package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ArtistMetadataExtension
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/** Ordered registry for the optional artist_metadata extension capability. */
class ArtistMetadataExtensionRegistry(
    private val logger: ExtensionCoreLogger = ExtensionCoreLogger { event, details ->
        android.util.Log.d("FlowtoneExtension", "$event $details")
    },
    private val persistentCache: ArtistMetadataPersistentCache? = null
) {
    private val extensions = mutableListOf<ArtistMetadataExtension>()
    private val memoryCache = mutableMapOf<String, ArtistMetadata>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ArtistMetadata?>>()

    @Synchronized
    fun install(extension: ArtistMetadataExtension) {
        extensions.removeAll { it.id == extension.id }
        extensions += extension
    }

    @Synchronized
    fun uninstall(extensionId: String, clearPersistentCache: Boolean = false) {
        extensions.removeAll { it.id == extensionId }
        memoryCache.keys.removeAll { it.startsWith("$extensionId\n") }
        inFlight.entries.removeIf { (key, deferred) ->
            if (key.startsWith("$extensionId\n")) {
                deferred.cancel()
                true
            } else {
                false
            }
        }
        if (clearPersistentCache) persistentCache?.clear(extensionId)
    }

    @Synchronized
    fun installedExtensions(): List<ArtistMetadataExtension> = extensions.toList()

    suspend fun findArtistMetadata(artistName: String): ArtistMetadata? {
        val artist = artistName.trim()
        if (artist.isEmpty()) return null
        val aliases = linkedMapOf<String, String>()
        var biography: String? = null
        installedExtensions().forEach { extension ->
            val key = ArtistMetadataPersistentCache.cacheKey(extension.id, artist)
            val metadata = synchronized(this) { memoryCache[key] }
                ?: persistentCache?.get(extension.id, artist)?.also { cached ->
                    synchronized(this) { memoryCache[key] = cached }
                }
                ?: try {
                    findInFlight(extension, artist)?.sanitizedFor(artist)?.also { resolved ->
                        synchronized(this) { memoryCache[key] = resolved }
                        runCatching { persistentCache?.put(extension.id, artist, resolved) }
                            .onFailure { error ->
                                logger.log(
                                    "extension.artist_metadata.cache.store_failed",
                                    "extension=${extension.id} exception=${error.javaClass.simpleName}"
                                )
                            }
                    }
                } catch (error: Exception) {
                    logger.log(
                        "extension.invoke.failed",
                        "extension=${extension.id} capability=artist_metadata exception=${error.javaClass.simpleName}"
                    )
                    null
                }

            metadata?.sanitizedFor(artist)?.let { resolved ->
                resolved.aliases.forEach { alias ->
                    aliases.putIfAbsent(alias.stableAliasKey(), alias)
                }
                if (biography == null) biography = resolved.biography
            }
        }
        return ArtistMetadata(aliases.values.toList(), biography)
            .takeIf { it.aliases.isNotEmpty() || it.biography != null }
    }

    private suspend fun findInFlight(
        extension: ArtistMetadataExtension,
        artistName: String
    ): ArtistMetadata? {
        val key = ArtistMetadataPersistentCache.cacheKey(extension.id, artistName)
        val ours = CompletableDeferred<ArtistMetadata?>()
        val existing = inFlight.putIfAbsent(key, ours)
        if (existing != null) return existing.await()
        try {
            val result = extension.findArtistMetadata(artistName)
            ours.complete(result)
            return result
        } catch (error: Throwable) {
            ours.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, ours)
        }
    }

    private fun String.stableAliasKey(): String =
        ink.tenqui.flowtone.data.local.localArtistStableId(this)
}
