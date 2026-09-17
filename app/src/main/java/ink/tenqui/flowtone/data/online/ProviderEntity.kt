package ink.tenqui.flowtone.data.online

import android.net.Uri
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.local.localArtistStableId
import ink.tenqui.flowtone.data.local.parseLocalArtistCandidates

enum class ProviderEntityCapability(val wireValue: String) {
    Song("song"),
    Album("album");

    companion object {
        fun fromWireValue(value: String): ProviderEntityCapability? = entries.firstOrNull {
            it.wireValue == value.trim().lowercase()
        }
    }
}

data class ProviderEntityIdentity(
    val providerId: String,
    val remoteId: String
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(remoteId.isNotBlank()) { "remoteId must not be blank" }
    }

    val stableKey: String
        get() = "${providerId.trim()}\u0000${remoteId.trim()}"
}

data class ProviderArtistRef(
    val remoteId: String? = null,
    val name: String
)

data class ProviderAlbumRef(
    val remoteId: String? = null,
    val title: String? = null
)

/** Describes the order already applied by a Provider to its Artist song collection. */
data class ArtistSongOrderInfo(
    val id: String? = null,
    val title: String
)

fun ArtistSongOrderInfo?.displayTitleOrNull(): String? =
    this?.title?.trim()?.takeIf(String::isNotEmpty)

sealed interface ProviderSearchItem {
    val identity: ProviderEntityIdentity
    val title: String
    val artist: String
    val artwork: ExtensionImage?
    val searchCategory: ProviderSearchCategory
    val metadata: List<ProviderSearchMetadata>?
    val providerId: String get() = identity.providerId
    val id: String get() = identity.remoteId
}

data class ProviderAlbum(
    override val identity: ProviderEntityIdentity,
    override val title: String,
    override val artist: String = "",
    val artists: List<ProviderArtistRef> = emptyList(),
    override val artwork: ExtensionImage? = null,
    val songCount: Int? = null,
    val releaseMetadata: String? = null,
    override val metadata: List<ProviderSearchMetadata>? = null
) : ProviderSearchItem {
    override val searchCategory: ProviderSearchCategory = ProviderSearchCategory.Album
}

data class ProviderArtist(
    override val identity: ProviderEntityIdentity,
    override val title: String,
    override val artist: String = "",
    override val artwork: ExtensionImage? = null,
    val largeArtwork: ExtensionImage? = null,
    override val metadata: List<ProviderSearchMetadata>? = null,
    val profileMetadata: ArtistMetadata? = null,
    val songOrder: ArtistSongOrderInfo? = null
) : ProviderSearchItem {
    override val searchCategory: ProviderSearchCategory = ProviderSearchCategory.User
}

/** Artist profile surfaces prefer the Provider's larger explicit image when one is available. */
fun ProviderArtist.preferredProfileArtwork(): ExtensionImage? = largeArtwork ?: artwork

data class ProviderPlaylistSearchItem(
    override val identity: ProviderEntityIdentity,
    override val title: String,
    override val artist: String,
    override val artwork: ExtensionImage? = null,
    override val metadata: List<ProviderSearchMetadata>? = null,
    /** 搜索结果可直接携带曲目；为空时 Host 会尝试可选的详情读取接口。 */
    val songs: List<ProviderSong> = emptyList()
) : ProviderSearchItem {
    override val searchCategory: ProviderSearchCategory = ProviderSearchCategory.Playlist
}

fun ProviderSong.resolvedArtists(): List<ProviderArtistRef> = artists
    .mapNotNull(::sanitizeProviderArtistRef)
    .ifEmpty {
        parseLocalArtistCandidates(artist).map { name -> ProviderArtistRef(name = name) }
    }

fun ProviderAlbum.resolvedArtists(): List<ProviderArtistRef> = artists
    .mapNotNull(::sanitizeProviderArtistRef)
    .ifEmpty {
        parseLocalArtistCandidates(artist).map { name -> ProviderArtistRef(name = name) }
    }

fun providerSongsForArtist(
    songs: List<ProviderSong>,
    providerId: String,
    artistId: String,
    artistName: String
): List<ProviderSong> = songs.filter { song ->
    song.providerId == providerId && providerArtistsMatch(
        artists = song.resolvedArtists(),
        artistId = artistId,
        artistName = artistName
    )
}

fun providerAlbumsForArtist(
    albums: List<ProviderAlbum>,
    providerId: String,
    artistId: String,
    artistName: String
): List<ProviderAlbum> = albums.filter { album ->
    album.providerId == providerId && providerArtistsMatch(
        artists = album.resolvedArtists(),
        artistId = artistId,
        artistName = artistName
    )
}

/**
 * Resolves Provider Artist profile metadata without crossing source or name-only identity bounds.
 * The navigation snapshot remains authoritative. Legacy metadata only fills missing fields when a
 * single-artist User payload proves ownership through both its entity identity and artist ref.
 */
fun resolveProviderArtistProfileMetadata(
    providerId: String,
    artistId: String,
    artistName: String,
    destinationMetadata: ArtistMetadata?,
    songs: List<ProviderSong>
): ArtistMetadata? {
    val normalizedProviderId = providerId.trim()
    val normalizedArtistId = artistId.trim()
    var resolved = destinationMetadata?.sanitizedFor(artistName)
    if (normalizedProviderId.isEmpty() || normalizedArtistId.isEmpty()) return resolved

    songs.asSequence()
        .filter { song ->
            song.legacyArtistMetadataBelongsTo(
                providerId = normalizedProviderId,
                artistId = normalizedArtistId
            )
        }
        .mapNotNull(ProviderSong::artistMetadata)
        .forEach { legacyMetadata ->
            resolved = mergeProviderArtistMetadata(
                artistName = artistName,
                preferred = resolved,
                fallback = legacyMetadata
            )
    }
    return resolved
}

fun providerSongsForAlbum(
    songs: List<ProviderSong>,
    album: ProviderAlbum
): List<ProviderSong> = songs.filter { song ->
    if (song.providerId != album.providerId) return@filter false
    val songAlbum = song.album ?: return@filter false
    val remoteAlbumId = songAlbum.remoteId?.trim().orEmpty()
    if (remoteAlbumId.isNotEmpty()) {
        remoteAlbumId == album.id
    } else {
        val albumTitle = songAlbum.title?.trim().orEmpty()
        albumTitle.isNotEmpty() && localArtistStableId(albumTitle) == localArtistStableId(album.title)
    }
}

fun dedupeProviderSongs(songs: List<ProviderSong>): List<ProviderSong> =
    songs.distinctBy { it.identity }

fun dedupeProviderAlbums(albums: List<ProviderAlbum>): List<ProviderAlbum> =
    albums.distinctBy { it.identity }

fun ProviderSong.toPresentationSong(): Song {
    val opaqueUri = Uri.Builder()
        .scheme("flowtone-extension")
        .authority("track")
        .appendPath(providerId)
        .appendPath(id)
        .build()
    return Song(
        id = -(("$providerId:$id").hashCode().toLong().let { kotlin.math.abs(it) + 1L }),
        sourceType = SourceType.Online,
        title = title,
        artist = artist,
        durationMs = durationMs ?: 0L,
        uri = opaqueUri,
        albumTitle = album?.title,
        displayName = title
    )
}

private fun providerArtistsMatch(
    artists: List<ProviderArtistRef>,
    artistId: String,
    artistName: String
): Boolean {
    val artistsWithId = artists.filter { !it.remoteId.isNullOrBlank() }
    if (artistsWithId.isNotEmpty()) {
        return artistsWithId.any { it.remoteId?.trim() == artistId.trim() }
    }
    val normalizedName = localArtistStableId(artistName)
    return normalizedName.isNotEmpty() && artists.any { artist ->
        localArtistStableId(artist.name) == normalizedName
    }
}

private fun ProviderSong.legacyArtistMetadataBelongsTo(
    providerId: String,
    artistId: String
): Boolean {
    if (this.providerId.trim() != providerId || searchCategory != ProviderSearchCategory.User) {
        return false
    }
    if (id.trim() != artistId) return false

    val sanitizedArtists = artists.mapNotNull(::sanitizeProviderArtistRef)
    return sanitizedArtists.size == 1 && sanitizedArtists.single().remoteId == artistId
}

private fun mergeProviderArtistMetadata(
    artistName: String,
    preferred: ArtistMetadata?,
    fallback: ArtistMetadata?
): ArtistMetadata? {
    val primary = preferred?.sanitizedFor(artistName)
    val secondary = fallback?.sanitizedFor(artistName)
    return ArtistMetadata(
        aliases = primary?.aliases.orEmpty() + secondary?.aliases.orEmpty(),
        biography = primary?.biography ?: secondary?.biography,
        songCount = primary?.songCount ?: secondary?.songCount,
        albumCount = primary?.albumCount ?: secondary?.albumCount,
        banner = primary?.banner ?: secondary?.banner
    ).sanitizedFor(artistName)
}

private fun sanitizeProviderArtistRef(artist: ProviderArtistRef): ProviderArtistRef? {
    val name = artist.name.trim().takeIf(String::isNotEmpty) ?: return null
    return ProviderArtistRef(
        remoteId = artist.remoteId?.trim()?.takeIf(String::isNotEmpty),
        name = name
    )
}
