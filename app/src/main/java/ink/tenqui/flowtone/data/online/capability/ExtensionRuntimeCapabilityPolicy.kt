package ink.tenqui.flowtone.data.online.capability

import ink.tenqui.flowtone.data.online.ProviderSearchCategory

data class ExtensionRuntimeRequirements(
    val artistAvatar: Boolean,
    val artistMetadata: Boolean,
    val musicProvider: Boolean
) {
    val requiresRuntime: Boolean get() = artistAvatar || artistMetadata || musicProvider
}

/** Host-owned runtime routing rules derived only from canonical Atomic Capabilities. */
object ExtensionRuntimeCapabilityPolicy {
    val musicProviderCapabilities: Set<AtomicCapabilityId> = setOf(
        AtomicCapabilityId.SearchSongPage,
        AtomicCapabilityId.SearchAlbumPage,
        AtomicCapabilityId.SearchPlaylistPage,
        AtomicCapabilityId.SearchArtistPage,
        AtomicCapabilityId.SearchLandingGet,
        AtomicCapabilityId.CatalogSongsList,
        AtomicCapabilityId.CatalogAlbumsList,
        AtomicCapabilityId.PlaylistSongsRead,
        AtomicCapabilityId.SongPersistentResolve,
        AtomicCapabilityId.PlaybackResourceResolve
    )

    fun requiresMusicProviderRuntime(capabilities: CanonicalAtomicCapabilitySet): Boolean =
        capabilities.values.any { it in musicProviderCapabilities }

    fun registrationRequirements(
        capabilities: CanonicalAtomicCapabilitySet
    ): ExtensionRuntimeRequirements = ExtensionRuntimeRequirements(
        artistAvatar = AtomicCapabilityId.ArtistAvatarLookup in capabilities,
        artistMetadata = AtomicCapabilityId.ArtistMetadataLookup in capabilities,
        musicProvider = requiresMusicProviderRuntime(capabilities)
    )

    fun searchCapability(category: ProviderSearchCategory): AtomicCapabilityId = when (category) {
        ProviderSearchCategory.Single -> AtomicCapabilityId.SearchSongPage
        ProviderSearchCategory.Album -> AtomicCapabilityId.SearchAlbumPage
        ProviderSearchCategory.Playlist -> AtomicCapabilityId.SearchPlaylistPage
        ProviderSearchCategory.User -> AtomicCapabilityId.SearchArtistPage
    }

    fun supportsSearch(
        capabilities: CanonicalAtomicCapabilitySet,
        category: ProviderSearchCategory
    ): Boolean = searchCapability(category) in capabilities
}
