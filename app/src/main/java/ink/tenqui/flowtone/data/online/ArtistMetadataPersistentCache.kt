package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.data.local.localArtistStableId
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Host-owned successful artist_metadata cache; provider-private data remains in its own cache. */
class ArtistMetadataPersistentCache(
    private val root: File
) {
    private val namespaces = mutableMapOf<String, MutableMap<String, ArtistMetadata>>()

    @Synchronized
    fun get(extensionId: String, artistName: String): ArtistMetadata? {
        validateExtensionId(extensionId)
        return namespace(extensionId)[cacheKey(extensionId, artistName)]
    }

    @Synchronized
    fun put(extensionId: String, artistName: String, metadata: ArtistMetadata) {
        validateExtensionId(extensionId)
        namespace(extensionId)[cacheKey(extensionId, artistName)] = metadata
        persist(extensionId, namespace(extensionId))
    }

    @Synchronized
    fun clear(extensionId: String) {
        validateExtensionId(extensionId)
        namespaces.remove(extensionId)
        File(root, extensionId).resolve(CacheDirectoryName).deleteRecursively()
    }

    private fun namespace(extensionId: String): MutableMap<String, ArtistMetadata> =
        namespaces.getOrPut(extensionId) { load(extensionId) }

    private fun load(extensionId: String): MutableMap<String, ArtistMetadata> {
        val file = cacheFile(extensionId)
        if (!file.isFile) return linkedMapOf()
        return runCatching {
            val rootJson = JSONObject(file.readText(Charsets.UTF_8))
            require(rootJson.optInt("format", -1) in 1..FormatVersion)
            require(rootJson.optString("extensionId") == extensionId)
            linkedMapOf<String, ArtistMetadata>().apply {
                val entries = rootJson.optJSONArray("entries") ?: JSONArray()
                repeat(entries.length()) { index ->
                    val entry = entries.optJSONObject(index) ?: return@repeat
                    val key = entry.optString("cacheKey")
                    val aliases = entry.optJSONArray("aliases")?.let { aliasesArray ->
                        List(aliasesArray.length()) { aliasesArray.optString(it) }
                    }.orEmpty()
                    val biography = (entry.opt("biography") as? String)
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                    val songCount = entry.optNonNegativeInt("songCount")
                    val albumCount = entry.optNonNegativeInt("albumCount")
                    if (key.isNotBlank()) {
                        ArtistMetadata(aliases, biography, songCount, albumCount)
                            .sanitized()
                            ?.let { put(key, it) }
                    }
                }
            }
        }.getOrElse { linkedMapOf() }
    }

    private fun persist(extensionId: String, entries: Map<String, ArtistMetadata>) {
        val file = cacheFile(extensionId)
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("format", FormatVersion)
            .put("extensionId", extensionId)
            .put("entries", JSONArray().apply {
                entries.forEach { (key, metadata) ->
                    put(
                        JSONObject()
                            .put("cacheKey", key)
                            .put("aliases", JSONArray(metadata.aliases))
                            .put("biography", metadata.biography ?: JSONObject.NULL)
                            .put("songCount", metadata.songCount ?: JSONObject.NULL)
                            .put("albumCount", metadata.albumCount ?: JSONObject.NULL)
                    )
                }
            })
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.toString(), Charsets.UTF_8)
        runCatching {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun cacheFile(extensionId: String): File =
        File(root, extensionId).resolve(CacheDirectoryName).resolve(CacheFileName)

    private fun validateExtensionId(extensionId: String) {
        require(SafeExtensionId.matches(extensionId) && ".." !in extensionId)
    }

    companion object {
        private const val FormatVersion = 2
        private const val CacheDirectoryName = "artist-metadata-results"
        private const val CacheFileName = "entries.json"
        private val SafeExtensionId = Regex("[a-zA-Z0-9._-]+")

        internal fun cacheKey(extensionId: String, artistName: String): String =
            "$extensionId\nartist_metadata\n${artistName.trim().lowercase(Locale.ROOT)}"
    }
}

private fun JSONObject.optNonNegativeInt(name: String): Int? {
    val value = opt(name) as? Number ?: return null
    val number = value.toDouble()
    return number
        .takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it == it.toInt().toDouble() }
        ?.toInt()
}

internal fun ArtistMetadata.sanitized(): ArtistMetadata? {
    val normalizedAliases = aliases.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(::localArtistStableId)
        .toList()
    val normalizedBiography = biography?.trim()?.takeIf(String::isNotEmpty)
    val normalizedSongCount = songCount?.takeIf { it >= 0 }
    val normalizedAlbumCount = albumCount?.takeIf { it >= 0 }
    return ArtistMetadata(normalizedAliases, normalizedBiography, normalizedSongCount, normalizedAlbumCount)
        .takeIf {
            it.aliases.isNotEmpty() || it.biography != null ||
                it.songCount != null || it.albumCount != null
        }
}

internal fun ArtistMetadata.sanitizedFor(artistName: String): ArtistMetadata? {
    val canonicalArtistId = artistName.trim().takeIf(String::isNotEmpty)?.let(::localArtistStableId)
    val metadata = sanitized() ?: return null
    val aliases = metadata.aliases.filter { alias ->
        canonicalArtistId == null || localArtistStableId(alias) != canonicalArtistId
    }
    return ArtistMetadata(aliases, metadata.biography, metadata.songCount, metadata.albumCount)
        .takeIf {
            it.aliases.isNotEmpty() || it.biography != null ||
                it.songCount != null || it.albumCount != null
        }
}
