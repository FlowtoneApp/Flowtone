package ink.tenqui.flowtone.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistIdentityTopBarRowTest {
    @Test
    fun measuredFullPathIsUsedWhenItFits() {
        assertEquals(
            ArtistTopBarPathLayout.FullPath,
            artistTopBarPathLayout(
                fullPathNaturalWidthPx = 260f,
                collapsedPathNaturalWidthPx = 180f,
                availableWidthPx = 300f
            )
        )
    }

    @Test
    fun measuredAncestorPathCollapsesBeforeCurrentTitle() {
        assertEquals(
            ArtistTopBarPathLayout.CollapsedAncestors,
            artistTopBarPathLayout(
                fullPathNaturalWidthPx = 480f,
                collapsedPathNaturalWidthPx = 280f,
                availableWidthPx = 300f
            )
        )
    }

    @Test
    fun currentTitleEllipsizesOnlyWhenCollapsedPathStillCannotFit() {
        assertEquals(
            ArtistTopBarPathLayout.EllipsizedCurrent,
            artistTopBarPathLayout(
                fullPathNaturalWidthPx = 620f,
                collapsedPathNaturalWidthPx = 360f,
                availableWidthPx = 300f
            )
        )
    }

    @Test
    fun layoutDecisionUsesMeasuredPixelsRatherThanCharacterCount() {
        assertEquals(
            ArtistTopBarPathLayout.CollapsedAncestors,
            artistTopBarPathLayout(301f, 240f, 300f)
        )
        assertEquals(
            ArtistTopBarPathLayout.FullPath,
            artistTopBarPathLayout(299f, 240f, 300f)
        )
    }
}
