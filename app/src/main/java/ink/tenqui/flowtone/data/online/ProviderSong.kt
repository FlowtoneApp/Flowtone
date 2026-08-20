package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.core.online.PersistentProviderTrackRef

/** Provider 搜索项在 Host 搜索页中的有限分类，不代表 Provider 导航能力。 */
enum class ProviderSearchCategory {
    Single,
    Playlist,
    Album,
    User
}

internal fun providerSearchCategoryFromWire(value: String): ProviderSearchCategory = when (value.trim().lowercase()) {
    "playlist" -> ProviderSearchCategory.Playlist
    "album" -> ProviderSearchCategory.Album
    "user", "artist" -> ProviderSearchCategory.User
    else -> ProviderSearchCategory.Single
}

data class ProviderSong(
    val trackRef: ExtensionTrackRef,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val artwork: ExtensionImage? = null,
    val largeArtwork: ExtensionImage? = null,
    val persistentId: String? = null,
    val sourceHost: String? = null,
    /** 由 Provider 声明的搜索展示分类；未声明时保持与旧扩展兼容的单曲。 */
    val searchCategory: ProviderSearchCategory = ProviderSearchCategory.Single,
    /** null 表示扩展未声明 metadata，emptyList 表示扩展明确隐藏 metadata 行。 */
    val metadata: List<ProviderSearchMetadata>? = null
) {
    /** 兼容旧展示代码；身份来自 Host 绑定的 trackRef，而非 JS 返回字段。 */
    val providerId: String get() = trackRef.extensionId
    val id: String get() = trackRef.opaqueId
    val nowPlayingArtwork: ExtensionImage? get() = largeArtwork ?: artwork
    val persistentTrackRef: PersistentProviderTrackRef?
        get() = persistentId?.takeIf(String::isNotBlank)?.let { id ->
            sourceHost?.takeIf(String::isNotBlank)?.let { source ->
                PersistentProviderTrackRef(source, id)
            }
        }
}
