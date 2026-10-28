package ink.tenqui.flowtone.ui.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import ink.tenqui.flowtone.data.online.LibraryProviderOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySourceSwitcherTest {
    @Test
    fun localSourceIsFirstAndExtensionsAreSortedByNameThenId() {
        val sources = librarySourceItems(
            listOf(
                provider("z.extension", "Zulu"),
                provider("b.extension", "Able"),
                provider("a.extension", "able"),
                provider("z.extension", "Duplicate")
            )
        )

        assertEquals(LibrarySourceIdentity.Local, sources.first().identity)
        assertEquals("本地音乐", sources.first().name)
        assertEquals(
            listOf(
                "a.extension" to "able",
                "b.extension" to "Able",
                "z.extension" to "Zulu"
            ),
            sources.drop(1).map {
                (it.identity as LibrarySourceIdentity.Extension).extensionId to it.name
            }
        )
    }

    @Test
    fun upwardAndDownwardSwipesMoveOneSourceAndStopAtEnds() {
        assertEquals(2, librarySourceIndexAfterSwipe(1, 4, -90f, 48f))
        assertEquals(0, librarySourceIndexAfterSwipe(1, 4, 90f, 48f))
        assertNull(librarySourceIndexAfterSwipe(0, 4, 90f, 48f))
        assertNull(librarySourceIndexAfterSwipe(3, 4, -90f, 48f))
    }

    @Test
    fun touchSlopAndSingleSourcePreventSwipeSelection() {
        assertNull(librarySourceIndexAfterSwipe(0, 3, -48f, 48f))
        assertNull(librarySourceIndexAfterSwipe(0, 1, -120f, 48f))
    }

    @Test
    fun expandedHeightUsesOnlyRowsAndDoesNotDependOnSelectedIndex() {
        val viewport = Rect(0f, 0f, 480f, 900f)
        val anchor = Rect(24f, 300f, 424f, 376f)

        for (count in listOf(1, 2, 3, 5)) {
            var firstGeometry: LibrarySourceSwitcherGeometry? = null
            for (selectedIndex in 0 until count) {
                val geometry = requireNotNull(
                    calculateLibrarySourceSwitcherGeometry(
                        sourceCount = count,
                        selectedIndex = selectedIndex,
                        collapsedBounds = anchor,
                        collapsedContentBounds = anchor,
                        viewportBounds = viewport,
                        rowHeightPx = 76f,
                        edgeMarginPx = 8f
                    )
                )
                val expectedContentHeight = count * 76f
                val expectedExpandedTop =
                    (anchor.center.y - expectedContentHeight / 2f).coerceIn(
                        8f,
                        viewport.bottom - 8f - expectedContentHeight
                    )

                assertEquals(expectedContentHeight, geometry.contentHeightPx, 0.001f)
                assertEquals(expectedContentHeight, geometry.expandedBounds.height, 0.001f)
                assertEquals(expectedExpandedTop, geometry.expandedBounds.top, 0.001f)
                assertEquals(0f, geometry.contentTopPaddingPx, 0.001f)
                assertEquals(0f, geometry.contentBottomPaddingPx, 0.001f)
                assertEquals(
                    geometry.expandedBounds.top + selectedIndex * 76f + 38f,
                    geometry.rowBounds(selectedIndex).center.y,
                    0.001f
                )
                if (firstGeometry == null) {
                    firstGeometry = geometry
                } else {
                    assertEquals(firstGeometry.expandedBounds, geometry.expandedBounds)
                }
                for (index in 0 until count - 1) {
                    assertEquals(
                        76f,
                        geometry.rowBounds(index + 1).top - geometry.rowBounds(index).top,
                        0.001f
                    )
                }
            }
        }
    }

    @Test
    fun rowSpacingContributesOnlyTheActualInterRowGaps() {
        val geometry = requireNotNull(
            calculateLibrarySourceSwitcherGeometry(
                sourceCount = 3,
                selectedIndex = 1,
                collapsedBounds = Rect(24f, 300f, 424f, 376f),
                collapsedContentBounds = Rect(24f, 300f, 424f, 376f),
                viewportBounds = Rect(0f, 0f, 480f, 900f),
                rowHeightPx = 76f,
                rowSpacingPx = 8f,
                edgeMarginPx = 8f
            )
        )

        assertEquals(244f, geometry.contentHeightPx, 0.001f)
        assertEquals(84f, geometry.rowStridePx, 0.001f)
        assertEquals(84f, geometry.rowBounds(1).top - geometry.rowBounds(0).top, 0.001f)
    }

    @Test
    fun largeSourceListsAreClampedAndScrollWithoutAddingBlankRows() {
        val viewport = Rect(0f, 0f, 480f, 600f)
        val anchor = Rect(24f, 230f, 424f, 306f)
        val geometry = requireNotNull(
            calculateLibrarySourceSwitcherGeometry(
                sourceCount = 11,
                selectedIndex = 10,
                collapsedBounds = anchor,
                collapsedContentBounds = anchor,
                viewportBounds = viewport,
                rowHeightPx = 76f,
                edgeMarginPx = 8f
            )
        )

        assertEquals(836f, geometry.contentHeightPx, 0.001f)
        assertEquals(584f, geometry.expandedBounds.height, 0.001f)
        assertEquals(592f, geometry.expandedBounds.bottom, 0.001f)
        assertEquals(252f, geometry.initialScrollPx, 0.001f)
        assertTrue(geometry.contentHeightPx > geometry.expandedBounds.height)
        assertEquals(
            geometry.expandedBounds.bottom,
            geometry.rowBounds(10).bottom,
            0.001f
        )
    }

    @Test
    fun geometryInterpolationPreservesTheMeasuredCollapsedContentBounds() {
        val collapsed = Rect(18f, 120f, 418f, 196f)
        val expanded = Rect(18f, 60f, 418f, 440f)

        assertEquals(collapsed, librarySourceSwitcherRectAtProgress(collapsed, expanded, 0f))
        assertEquals(expanded, librarySourceSwitcherRectAtProgress(collapsed, expanded, 1f))
        val middle = librarySourceSwitcherRectAtProgress(collapsed, expanded, 0.5f)
        assertEquals(90f, middle.top, 0.001f)
        assertEquals(318f, middle.bottom, 0.001f)
    }

    @Test
    fun rowEntriesStartOutsideTheCollapsedClip() {
        val anchor = Rect(12f, 200f, 412f, 276f)

        val above = librarySourceRowStartBounds(anchor, 0, 1, 76f, 76f)
        val below = librarySourceRowStartBounds(anchor, 2, 1, 76f, 76f)

        assertEquals(anchor.top, above.bottom, 0.001f)
        assertEquals(anchor.bottom, below.top, 0.001f)
    }

    @Test
    fun rootAndOverlayCoordinatesUseTheSameOrigin() {
        assertEquals(
            Rect(20f, 30f, 420f, 106f),
            librarySourceRectInLocalCoordinates(
                Rect(120f, 230f, 520f, 306f),
                Offset(100f, 200f)
            )
        )
    }

    @Test
    fun selectionOnlyStartsFromExpandedAndTappingCurrentCollapses() {
        val expanded = LibrarySourceSwitcherState.Expanded
        val target = librarySourceSwitcherStateAfterSelection(
            state = expanded,
            targetKey = "extension:provider.a",
            selectedKey = "local"
        )

        assertEquals(
            LibrarySourceSwitcherState.Switching("extension:provider.a"),
            target
        )
        assertEquals(
            LibrarySourceSwitcherState.Collapsed,
            librarySourceSwitcherStateAfterSelection(
                state = expanded,
                targetKey = "local",
                selectedKey = "local"
            )
        )
        assertEquals(
            LibrarySourceSwitcherState.Collapsed,
            librarySourceSwitcherStateAfterSelection(
                state = LibrarySourceSwitcherState.Collapsed,
                targetKey = "extension:provider.a",
                selectedKey = "local"
            )
        )
    }

    private fun provider(id: String, name: String) = LibraryProviderOption(
        extensionId = id,
        name = name,
        sourceTypeLabel = null,
        color = null,
        visual = null,
        supportsSongs = true,
        supportsAlbums = false
    )
}
