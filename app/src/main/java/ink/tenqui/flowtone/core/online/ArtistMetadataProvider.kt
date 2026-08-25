package ink.tenqui.flowtone.core.online

/**
 * Optional artist text metadata capability. It is intentionally separate from artwork lookup:
 * providers may support either capability without coupling image and textual data contracts.
 */
interface ArtistMetadataProvider {
    suspend fun findArtistMetadata(artistName: String): ArtistMetadata?
}
