package ink.tenqui.flowtone.data.online.capability

import ink.tenqui.flowtone.data.online.packageformat.ExtensionManifest

enum class AtomicCapabilityId(val value: String) {
    SearchSongPage("search.song.page"),
    SearchAlbumPage("search.album.page"),
    SearchPlaylistPage("search.playlist.page"),
    SearchArtistPage("search.artist.page"),
    SearchLandingGet("search.landing.get"),
    CatalogSongsList("catalog.songs.list"),
    CatalogAlbumsList("catalog.albums.list"),
    PlaylistSongsRead("playlist.songs.read"),
    SongPersistentResolve("song.persistent.resolve"),
    PlaybackResourceResolve("playback.resource.resolve"),
    ArtistAvatarLookup("artist.avatar.lookup"),
    ArtistMetadataLookup("artist.metadata.lookup");

    companion object {
        fun fromValue(value: String): AtomicCapabilityId? = entries.firstOrNull { it.value == value }
    }
}

enum class AtomicCapabilityGroup {
    SearchAndDiscovery,
    Catalog,
    Playlist,
    Playback,
    Artist
}

data class AtomicCapabilityDefinition(
    val id: AtomicCapabilityId,
    val group: AtomicCapabilityGroup,
    val label: String,
    val description: String? = null,
    val order: Int
)

object AtomicCapabilityDefinitions {
    val all: List<AtomicCapabilityDefinition> = listOf(
        AtomicCapabilityDefinition(
            AtomicCapabilityId.SearchSongPage,
            AtomicCapabilityGroup.SearchAndDiscovery,
            "搜索歌曲",
            order = 10
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.SearchAlbumPage,
            AtomicCapabilityGroup.SearchAndDiscovery,
            "搜索专辑",
            order = 20
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.SearchPlaylistPage,
            AtomicCapabilityGroup.SearchAndDiscovery,
            "搜索歌单",
            order = 30
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.SearchArtistPage,
            AtomicCapabilityGroup.SearchAndDiscovery,
            "搜索歌手",
            order = 40
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.SearchLandingGet,
            AtomicCapabilityGroup.SearchAndDiscovery,
            "获取搜索首页",
            order = 50
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.CatalogSongsList,
            AtomicCapabilityGroup.Catalog,
            "浏览歌曲目录",
            order = 60
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.CatalogAlbumsList,
            AtomicCapabilityGroup.Catalog,
            "浏览专辑目录",
            order = 70
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.PlaylistSongsRead,
            AtomicCapabilityGroup.Playlist,
            "读取歌单歌曲",
            order = 80
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.SongPersistentResolve,
            AtomicCapabilityGroup.Playback,
            "恢复已保存歌曲",
            order = 90
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.PlaybackResourceResolve,
            AtomicCapabilityGroup.Playback,
            "获取播放资源",
            order = 100
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.ArtistAvatarLookup,
            AtomicCapabilityGroup.Artist,
            "查找歌手头像",
            order = 110
        ),
        AtomicCapabilityDefinition(
            AtomicCapabilityId.ArtistMetadataLookup,
            AtomicCapabilityGroup.Artist,
            "查找歌手资料",
            order = 120
        )
    )

    private val byId = all.associateBy(AtomicCapabilityDefinition::id)

    fun get(id: AtomicCapabilityId): AtomicCapabilityDefinition = requireNotNull(byId[id])
}

data class CanonicalAtomicCapabilitySet(
    val values: Set<AtomicCapabilityId>
) {
    operator fun contains(id: AtomicCapabilityId): Boolean = id in values

    companion object {
        val Empty = CanonicalAtomicCapabilitySet(emptySet())

        fun of(vararg values: AtomicCapabilityId): CanonicalAtomicCapabilitySet =
            CanonicalAtomicCapabilitySet(values.toSet())
    }
}

object LegacyCapabilityCanonicalizer {
    fun canonicalize(manifest: ExtensionManifest): CanonicalAtomicCapabilitySet = canonicalize(
        legacyCapabilities = manifest.capabilities,
        musicSources = manifest.musicSources
    )

    fun canonicalize(
        legacyCapabilities: Collection<String>,
        musicSources: Collection<String> = emptyList()
    ): CanonicalAtomicCapabilitySet {
        val legacy = legacyCapabilities.toSet()
        val canonical = linkedSetOf<AtomicCapabilityId>()

        if ("artist_avatar" in legacy) canonical += AtomicCapabilityId.ArtistAvatarLookup
        if ("artist_metadata" in legacy) canonical += AtomicCapabilityId.ArtistMetadataLookup
        if ("music_provider" in legacy) {
            canonical += AtomicCapabilityId.SearchSongPage
            canonical += AtomicCapabilityId.PlaybackResourceResolve
            if (musicSources.isNotEmpty()) canonical += AtomicCapabilityId.SongPersistentResolve
            if ("song" in legacy) canonical += AtomicCapabilityId.CatalogSongsList
            if ("album" in legacy) canonical += AtomicCapabilityId.CatalogAlbumsList
        }

        return CanonicalAtomicCapabilitySet(canonical)
    }
}

enum class SummaryCapabilityId {
    SearchAndDiscovery,
    Playback,
    Artist,
    Album,
    Playlist
}

enum class SummaryCapabilityStatus {
    Supported,
    Partial,
    Unsupported
}

data class SummaryCapabilityDefinition(
    val id: SummaryCapabilityId,
    val label: String,
    val coreRequirements: List<Set<AtomicCapabilityId>>,
    val optionalCapabilities: Set<AtomicCapabilityId>
) {
    val relatedCapabilities: Set<AtomicCapabilityId> =
        coreRequirements.flatten().toSet() + optionalCapabilities
}

data class SummaryCapability(
    val id: SummaryCapabilityId,
    val label: String,
    val status: SummaryCapabilityStatus
)

object SummaryCapabilityDefinitions {
    val all: List<SummaryCapabilityDefinition> = listOf(
        SummaryCapabilityDefinition(
            id = SummaryCapabilityId.SearchAndDiscovery,
            label = "搜索与发现",
            coreRequirements = listOf(setOf(AtomicCapabilityId.SearchSongPage)),
            optionalCapabilities = setOf(
                AtomicCapabilityId.SearchAlbumPage,
                AtomicCapabilityId.SearchPlaylistPage,
                AtomicCapabilityId.SearchArtistPage,
                AtomicCapabilityId.SearchLandingGet
            )
        ),
        SummaryCapabilityDefinition(
            id = SummaryCapabilityId.Playback,
            label = "歌曲播放",
            coreRequirements = listOf(setOf(AtomicCapabilityId.PlaybackResourceResolve)),
            optionalCapabilities = setOf(
                AtomicCapabilityId.SongPersistentResolve,
                AtomicCapabilityId.CatalogSongsList
            )
        ),
        SummaryCapabilityDefinition(
            id = SummaryCapabilityId.Artist,
            label = "歌手",
            coreRequirements = listOf(
                setOf(
                    AtomicCapabilityId.ArtistAvatarLookup,
                    AtomicCapabilityId.ArtistMetadataLookup
                )
            ),
            optionalCapabilities = setOf(AtomicCapabilityId.SearchArtistPage)
        ),
        SummaryCapabilityDefinition(
            id = SummaryCapabilityId.Album,
            label = "专辑",
            coreRequirements = listOf(
                setOf(
                    AtomicCapabilityId.SearchAlbumPage,
                    AtomicCapabilityId.CatalogAlbumsList
                )
            ),
            optionalCapabilities = setOf(AtomicCapabilityId.CatalogSongsList)
        ),
        SummaryCapabilityDefinition(
            id = SummaryCapabilityId.Playlist,
            label = "歌单",
            coreRequirements = listOf(
                setOf(AtomicCapabilityId.SearchPlaylistPage),
                setOf(AtomicCapabilityId.PlaylistSongsRead)
            ),
            optionalCapabilities = emptySet()
        )
    )
}

object SummaryCapabilityAggregator {
    fun aggregate(capabilities: CanonicalAtomicCapabilitySet): List<SummaryCapability> =
        SummaryCapabilityDefinitions.all.map { definition ->
            val hasRelatedCapability = definition.relatedCapabilities.any { it in capabilities }
            val supportsEveryCoreGroup = definition.coreRequirements.all { alternatives ->
                alternatives.any { it in capabilities }
            }
            val status = when {
                !hasRelatedCapability -> SummaryCapabilityStatus.Unsupported
                supportsEveryCoreGroup -> SummaryCapabilityStatus.Supported
                else -> SummaryCapabilityStatus.Partial
            }
            SummaryCapability(definition.id, definition.label, status)
        }
}
