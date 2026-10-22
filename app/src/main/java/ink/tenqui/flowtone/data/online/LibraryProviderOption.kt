package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.data.search.SearchProviderVisual

/** 当前运行且声明曲库目录能力的扩展来源，能力位只描述 Host 是否可读取对应目录。 */
data class LibraryProviderOption(
    val extensionId: String,
    val name: String,
    val sourceTypeLabel: String?,
    val color: String?,
    val visual: SearchProviderVisual?,
    val supportsSongs: Boolean,
    val supportsAlbums: Boolean
)
