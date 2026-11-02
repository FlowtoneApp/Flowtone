package ink.tenqui.flowtone.ui.library

internal enum class LibraryCategory(val title: String) {
    Playlists("歌单"), Favorites("收藏"), Albums("专辑"), Songs("歌曲"), Artists("艺术家")
}

internal fun libraryCategories(sourceKey: String, source: LibrarySourceItem?): List<LibraryCategory> = when {
    sourceKey == "mine" -> listOf(LibraryCategory.Playlists)
    sourceKey == LibrarySourceIdentity.Local.key -> listOf(LibraryCategory.Songs, LibraryCategory.Albums, LibraryCategory.Artists)
    source?.identity is LibrarySourceIdentity.Extension -> buildList {
        if (source.supportsPlaylists) add(LibraryCategory.Playlists)
        add(LibraryCategory.Favorites)
        if (source.supportsAlbums) add(LibraryCategory.Albums)
        if (source.supportsSongs) add(LibraryCategory.Songs)
    }
    else -> emptyList()
}

internal fun validLibraryCategory(categories: List<LibraryCategory>, requested: LibraryCategory?): LibraryCategory? =
    requested?.takeIf { it in categories } ?: categories.firstOrNull()
