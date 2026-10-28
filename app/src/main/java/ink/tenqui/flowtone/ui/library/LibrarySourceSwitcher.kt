package ink.tenqui.flowtone.ui.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import ink.tenqui.flowtone.data.online.LibraryProviderOption
import ink.tenqui.flowtone.data.online.ProviderPlaylist
import ink.tenqui.flowtone.data.search.SearchProviderVisual
import ink.tenqui.flowtone.viewmodel.ProviderCatalogLoadState
import kotlin.math.abs

internal sealed interface LibrarySourceIdentity {
    val key: String

    data object Local : LibrarySourceIdentity {
        override val key: String = "local"
    }

    data class Extension(val extensionId: String) : LibrarySourceIdentity {
        override val key: String = "extension:$extensionId"
    }
}

internal data class LibrarySourceItem(
    val identity: LibrarySourceIdentity,
    val name: String,
    val typeLabel: String,
    val color: String? = null,
    val visual: SearchProviderVisual? = null,
    val supportsSongs: Boolean = false,
    val supportsAlbums: Boolean = false,
    val supportsPlaylists: Boolean = false
)

internal sealed interface LibrarySourceSwitcherState {
    data object Collapsed : LibrarySourceSwitcherState
    data object Expanded : LibrarySourceSwitcherState
    data class Switching(val targetKey: String) : LibrarySourceSwitcherState
}

internal enum class LibrarySourceCardRenderOwner {
    Static,
    Overlay
}

internal data class LibrarySourceContentState(
    val sourceItems: List<LibrarySourceItem>,
    val selectedSource: LibrarySourceItem,
    val switcherState: LibrarySourceSwitcherState,
    val swipeEnabled: Boolean,
    val swipeDirection: Int,
    val providerPlaylists: List<ProviderPlaylist>?,
    val providerPlaylistLoadState: ProviderCatalogLoadState?
)

internal fun librarySourceItems(
    providers: List<LibraryProviderOption>
): List<LibrarySourceItem> {
    val uniqueProviders = providers
        .asSequence()
        .filter { it.extensionId.isNotBlank() }
        .distinctBy(LibraryProviderOption::extensionId)
        .sortedWith(
            compareBy<LibraryProviderOption, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                .thenBy(LibraryProviderOption::extensionId)
        )
        .map { provider ->
            LibrarySourceItem(
                identity = LibrarySourceIdentity.Extension(provider.extensionId),
                name = provider.name,
                typeLabel = provider.sourceTypeLabel ?: "扩展来源",
                color = provider.color,
                visual = provider.visual,
                supportsSongs = provider.supportsSongs,
                supportsAlbums = provider.supportsAlbums,
                supportsPlaylists = provider.supportsPlaylists
            )
        }
        .toList()
    return listOf(
        LibrarySourceItem(
            identity = LibrarySourceIdentity.Local,
            name = "本地音乐",
            typeLabel = "此设备",
            supportsSongs = true
        )
    ) + uniqueProviders
}

internal fun librarySourceIndexAfterSwipe(
    selectedIndex: Int,
    sourceCount: Int,
    dragDistanceY: Float,
    thresholdPx: Float
): Int? {
    if (sourceCount <= 1 || abs(dragDistanceY) <= thresholdPx) return null
    val targetIndex = selectedIndex + if (dragDistanceY < 0f) 1 else -1
    return targetIndex.takeIf { it in 0 until sourceCount }
}

internal fun librarySourceSwitcherStateAfterSelection(
    state: LibrarySourceSwitcherState,
    targetKey: String,
    selectedKey: String
): LibrarySourceSwitcherState = when {
    state !is LibrarySourceSwitcherState.Expanded -> state
    targetKey == selectedKey -> LibrarySourceSwitcherState.Collapsed
    else -> LibrarySourceSwitcherState.Switching(targetKey)
}

internal data class LibrarySourceSwitcherGeometry(
    val collapsedBounds: Rect,
    val collapsedContentBounds: Rect,
    val expandedBounds: Rect,
    val rowHeightPx: Float,
    val rowSpacingPx: Float,
    val contentTopPaddingPx: Float,
    val contentBottomPaddingPx: Float,
    val contentHeightPx: Float,
    val initialScrollPx: Float,
    val selectedIndex: Int
) {
    val rowStridePx: Float get() = rowHeightPx + rowSpacingPx

    fun rowBounds(index: Int, scrollOffsetPx: Float = initialScrollPx): Rect {
        val top = expandedBounds.top + contentTopPaddingPx + index * rowStridePx - scrollOffsetPx
        return Rect(
            left = expandedBounds.left,
            top = top,
            right = expandedBounds.right,
            bottom = top + rowHeightPx
        )
    }
}

internal fun calculateLibrarySourceSwitcherGeometry(
    sourceCount: Int,
    selectedIndex: Int,
    collapsedBounds: Rect,
    collapsedContentBounds: Rect,
    viewportBounds: Rect,
    rowHeightPx: Float,
    rowSpacingPx: Float = 0f,
    edgeMarginPx: Float = 0f
): LibrarySourceSwitcherGeometry? {
    if (sourceCount <= 0 || rowHeightPx <= 0f) return null
    val safeSelectedIndex = selectedIndex.coerceIn(0, sourceCount - 1)
    val stride = rowHeightPx + rowSpacingPx
    val contentHeight = sourceCount * rowHeightPx + (sourceCount - 1) * rowSpacingPx
    val safeMargin = edgeMarginPx.coerceAtLeast(0f)
    val availableHeight = (viewportBounds.height - 2f * safeMargin).coerceAtLeast(0f)
    val expandedHeight = contentHeight.coerceAtMost(availableHeight)
    val minTop = viewportBounds.top + safeMargin
    val maxTop = (viewportBounds.bottom - safeMargin - expandedHeight).coerceAtLeast(minTop)
    val anchorCenteredTop = collapsedBounds.center.y - expandedHeight / 2f
    val expandedTop = anchorCenteredTop.coerceIn(minTop, maxTop)
    val expandedBounds = Rect(
        left = collapsedBounds.left,
        top = expandedTop,
        right = collapsedBounds.right,
        bottom = expandedTop + expandedHeight
    )
    val maxScroll = (contentHeight - expandedHeight).coerceAtLeast(0f)
    val selectedCenterInContent = safeSelectedIndex * stride + rowHeightPx / 2f
    val initialScroll =
        (selectedCenterInContent - expandedHeight / 2f).coerceIn(0f, maxScroll)

    return LibrarySourceSwitcherGeometry(
        collapsedBounds = collapsedBounds,
        collapsedContentBounds = collapsedContentBounds,
        expandedBounds = expandedBounds,
        rowHeightPx = rowHeightPx,
        rowSpacingPx = rowSpacingPx,
        contentTopPaddingPx = 0f,
        contentBottomPaddingPx = 0f,
        contentHeightPx = contentHeight,
        initialScrollPx = initialScroll,
        selectedIndex = safeSelectedIndex
    )
}

internal fun librarySourceSwitcherRectAtProgress(
    start: Rect,
    end: Rect,
    progress: Float
): Rect {
    val fraction = progress.coerceIn(0f, 1f)
    fun lerp(startValue: Float, endValue: Float) = startValue + (endValue - startValue) * fraction
    return Rect(
        left = lerp(start.left, end.left),
        top = lerp(start.top, end.top),
        right = lerp(start.right, end.right),
        bottom = lerp(start.bottom, end.bottom)
    )
}

internal fun librarySourceRowStartBounds(
    collapsedBounds: Rect,
    sourceIndex: Int,
    selectedIndex: Int,
    rowHeightPx: Float,
    rowStridePx: Float
): Rect {
    val top = when {
        sourceIndex < selectedIndex -> collapsedBounds.top -
            (selectedIndex - sourceIndex) * rowStridePx
        sourceIndex > selectedIndex -> collapsedBounds.bottom +
            (sourceIndex - selectedIndex - 1) * rowStridePx
        else -> collapsedBounds.top
    }
    return Rect(
        left = collapsedBounds.left,
        top = top,
        right = collapsedBounds.right,
        bottom = top + rowHeightPx
    )
}

internal fun librarySourceRectInLocalCoordinates(boundsInRoot: Rect, rootOriginInRoot: Offset): Rect =
    Rect(
        left = boundsInRoot.left - rootOriginInRoot.x,
        top = boundsInRoot.top - rootOriginInRoot.y,
        right = boundsInRoot.right - rootOriginInRoot.x,
        bottom = boundsInRoot.bottom - rootOriginInRoot.y
    )
