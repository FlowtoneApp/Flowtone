package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarPathBaselineCorrection
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistIdentityTopBarRowTest {
    @Test
    fun everyArtistPathUsesOneTopBarAlignmentContract() {
        assertEquals(FlowtoneTopBarContentHeight, ArtistTopBarLayout.contentHeight)
        assertEquals(36f, ArtistTopBarLayout.avatarSize.value, 0f)
        assertEquals(
            FlowtoneTopBarPathBaselineCorrection,
            ArtistTopBarLayout.breadcrumbBaselineOffsetY
        )
    }

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
