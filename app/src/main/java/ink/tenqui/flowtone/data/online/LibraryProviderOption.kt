package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.data.search.SearchProviderVisual

/** 当前运行的扩展曲库来源；能力位只描述 Host 可读取的集合。 */
data class LibraryProviderOption(
    val extensionId: String,
    val name: String,
    val sourceTypeLabel: String?,
    val color: String?,
    val visual: SearchProviderVisual?,
    val supportsSongs: Boolean,
    val supportsAlbums: Boolean,
    val supportsPlaylists: Boolean = false
)
