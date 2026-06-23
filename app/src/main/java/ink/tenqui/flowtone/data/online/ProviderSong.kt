package ink.tenqui.flowtone.data.online

data class ProviderSong(
    val providerId: String,
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val artworkUrl: String? = null
)
