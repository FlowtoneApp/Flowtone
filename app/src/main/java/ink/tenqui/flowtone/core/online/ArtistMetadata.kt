package ink.tenqui.flowtone.core.online

/** Text metadata supplied by an online extension for a single artist identity. */
data class ArtistMetadata(
    val aliases: List<String> = emptyList(),
    val biography: String? = null,
    /** null means the provider did not declare a count; zero is a valid declared value. */
    val songCount: Int? = null,
    /** null means the provider did not declare a count; zero is a valid declared value. */
    val albumCount: Int? = null
)
