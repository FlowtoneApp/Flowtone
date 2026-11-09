package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.viewmodel.ProviderCatalogLoadState
import ink.tenqui.flowtone.viewmodel.ProviderPlaylistDetailState

internal enum class ProviderLibraryPresentation {
    Loading,
    Ready,
    Empty,
    Error,
    Refreshing
}

internal fun providerCatalogPresentation(
    hasContent: Boolean,
    loadState: ProviderCatalogLoadState?,
    isRefreshing: Boolean
): ProviderLibraryPresentation = providerLibraryPresentation(
    hasContent = hasContent,
    // 页面可以早于请求状态发布进入 composition。无目录能力由协调器明确标记。
    isLoading = loadState == null || loadState == ProviderCatalogLoadState.Loading ||
        (isRefreshing && loadState == ProviderCatalogLoadState.Failed),
    loadFailed = loadState == ProviderCatalogLoadState.Failed ||
        loadState == ProviderCatalogLoadState.Unavailable,
    isRefreshing = isRefreshing
)

internal fun providerPlaylistDetailPresentation(
    hasContent: Boolean,
    detail: ProviderPlaylistDetailState?
): ProviderLibraryPresentation = providerLibraryPresentation(
    hasContent = hasContent,
    isLoading = detail == null || detail.isLoading,
    loadFailed = detail?.loadFailed == true,
    isRefreshing = detail?.isLoading == true
)

private fun providerLibraryPresentation(
    hasContent: Boolean,
    isLoading: Boolean,
    loadFailed: Boolean,
    isRefreshing: Boolean
): ProviderLibraryPresentation = when {
    hasContent && isRefreshing -> ProviderLibraryPresentation.Refreshing
    hasContent -> ProviderLibraryPresentation.Ready
    isLoading -> ProviderLibraryPresentation.Loading
    loadFailed -> ProviderLibraryPresentation.Error
    else -> ProviderLibraryPresentation.Empty
}

internal const val ProviderPlaylistSkeletonCount = 5
internal const val ProviderDetailSkeletonCount = 7

internal fun providerPlaylistSkeletonKey(providerId: String, index: Int): String =
    "provider-playlists-loading:$providerId:$index"

// 专辑使用包含 NUL 分隔符的 stableKey，歌单使用 provider: 前缀。占位使用独立命名空间。
internal fun providerDetailSkeletonKey(index: Int): String = "provider-detail-loading:$index"
