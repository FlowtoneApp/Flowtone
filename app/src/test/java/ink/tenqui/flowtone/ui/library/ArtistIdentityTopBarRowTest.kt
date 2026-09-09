package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarPathBaselineCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun visualModelKeepsCurrentTitleAfterCollapsingAncestors() {
        val full = artistVisualBreadcrumbModel(
            artistName = "Kou!",
            pathSegments = listOf("Album"),
            layout = ArtistTopBarPathLayout.FullPath
        )
        val collapsed = artistVisualBreadcrumbModel(
            artistName = "Kou!",
            pathSegments = listOf("Album"),
            layout = ArtistTopBarPathLayout.CollapsedAncestors
        )
        val ellipsized = artistVisualBreadcrumbModel(
            artistName = "Kou!",
            pathSegments = listOf("A very long Album"),
            layout = ArtistTopBarPathLayout.EllipsizedCurrent
        )

        assertEquals("Kou!", full.leadingText)
        assertEquals("…", collapsed.leadingText)
        assertEquals(listOf("Album"), collapsed.pathSlots)
        assertEquals(0, collapsed.currentSlotIndex)
        assertFalse(collapsed.ellipsizeCurrent)
        assertEquals(listOf("A very long Album"), ellipsized.pathSlots)
        assertTrue(ellipsized.ellipsizeCurrent)
    }

    @Test
    fun artistToShortAlbumOnlyInsertsSeparatorAndCurrent() {
        val artist = artistVisualBreadcrumbModel("Kou!", emptyList())
        val album = artistVisualBreadcrumbModel("Kou!", listOf("Album"))
        val diff = artistVisualBreadcrumbDiff(artist, album)

        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["ancestor"])
        assertEquals(ArtistVisualBreadcrumbChange.Inserted, diff["separator:0"])
        assertEquals(ArtistVisualBreadcrumbChange.Inserted, diff["path:0"])
    }

    @Test
    fun artistAlbumsToAlbumPreservesCommonPathAndAnimatesTheInsertedLeaf() {
        val albums = artistVisualBreadcrumbModel("Kou!", listOf("全部专辑"))
        val album = artistVisualBreadcrumbModel("Kou!", listOf("全部专辑", "Album"))
        val diff = artistVisualBreadcrumbDiff(albums, album)

        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["ancestor"])
        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["separator:0"])
        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["path:0"])
        assertEquals(ArtistVisualBreadcrumbChange.Inserted, diff["separator:1"])
        assertEquals(ArtistVisualBreadcrumbChange.Inserted, diff["path:1"])
    }

    @Test
    fun longAlbumCollapseChangesOnlyAncestorWhileKeepingExistingCurrent() {
        val full = artistVisualBreadcrumbModel(
            artistName = "Kou!",
            pathSegments = listOf("Album"),
            layout = ArtistTopBarPathLayout.FullPath
        )
        val collapsed = artistVisualBreadcrumbModel(
            artistName = "Kou!",
            pathSegments = listOf("Album"),
            layout = ArtistTopBarPathLayout.CollapsedAncestors
        )
        val diff = artistVisualBreadcrumbDiff(full, collapsed)

        assertEquals(ArtistVisualBreadcrumbChange.Changed, diff["ancestor"])
        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["separator:0"])
        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["path:0"])
    }

    @Test
    fun changedCurrentIsTheOnlyReplacementForSiblingDestinations() {
        val albums = artistVisualBreadcrumbModel("Kou!", listOf("全部专辑"))
        val album = artistVisualBreadcrumbModel("Kou!", listOf("Album"))
        val diff = artistVisualBreadcrumbDiff(albums, album)

        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["ancestor"])
        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, diff["separator:0"])
        assertEquals(ArtistVisualBreadcrumbChange.Changed, diff["path:0"])
    }

    @Test
    fun backUsesTheSameSegmentDiffInReverse() {
        val albums = artistVisualBreadcrumbModel("Kou!", listOf("全部专辑"))
        val album = artistVisualBreadcrumbModel("Kou!", listOf("全部专辑", "Album"))
        val reverse = artistVisualBreadcrumbDiff(album, albums)

        assertEquals(ArtistVisualBreadcrumbChange.Unchanged, reverse["path:0"])
        assertEquals(ArtistVisualBreadcrumbChange.Removed, reverse["separator:1"])
        assertEquals(ArtistVisualBreadcrumbChange.Removed, reverse["path:1"])
    }
}
