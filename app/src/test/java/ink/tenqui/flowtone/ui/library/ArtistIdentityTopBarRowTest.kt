package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarPathBaselineCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistIdentityTopBarRowTest {
    private val contentMotionDistance = 8f

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
            artistTopBarPathLayout(260f, 180f, 300f)
        )
    }

    @Test
    fun measuredAncestorPathCollapsesBeforeCurrentTitle() {
        assertEquals(
            ArtistTopBarPathLayout.CollapsedAncestors,
            artistTopBarPathLayout(480f, 280f, 300f)
        )
    }

    @Test
    fun currentTitleEllipsizesOnlyWhenCollapsedPathStillCannotFit() {
        assertEquals(
            ArtistTopBarPathLayout.EllipsizedCurrent,
            artistTopBarPathLayout(620f, 360f, 300f)
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
    fun visualStateDiffClassifiesEveryStableElementCase() {
        val old = visualState(
            element("static", "Kou!", 10f),
            element("moved", "/", 20f),
            element("changed", "全部专辑", 30f),
            element("removed", "old", 40f)
        )
        val target = visualState(
            element("static", "Kou!", 10f),
            element("moved", "/", 15f),
            element("changed", "Album", 30f),
            element("inserted", "new", 50f)
        )
        val diff = artistTopBarVisualDiff(old, target).associateBy { it.stableKey }

        assertEquals(ArtistTopBarVisualChange.UnchangedStatic, diff["static"]?.change)
        assertEquals(ArtistTopBarVisualChange.Moved, diff["moved"]?.change)
        assertEquals(ArtistTopBarVisualChange.Changed, diff["changed"]?.change)
        assertEquals(ArtistTopBarVisualChange.Inserted, diff["inserted"]?.change)
        assertEquals(ArtistTopBarVisualChange.Removed, diff["removed"]?.change)
    }

    @Test
    fun staggerProgressMapsOneMasterTimelineIntoOverlappingLocalWindows() {
        assertEquals(0f, mapArtistTopBarLocalProgress(0f, 0f, 0.85f), 0f)
        assertTrue(mapArtistTopBarLocalProgress(0.15f, 0f, 0.85f) > 0f)
        assertEquals(1f, mapArtistTopBarLocalProgress(0.85f, 0f, 0.85f), 0f)
        assertEquals(1f, mapArtistTopBarLocalProgress(1f, 0f, 0.85f), 0f)

        assertEquals(0f, mapArtistTopBarLocalProgress(0f, 0.15f, 1f), 0f)
        assertEquals(0f, mapArtistTopBarLocalProgress(0.10f, 0.15f, 1f), 0f)
        assertEquals(0f, mapArtistTopBarLocalProgress(0.15f, 0.15f, 1f), 0f)
        assertTrue(mapArtistTopBarLocalProgress(0.5f, 0.15f, 1f) in 0f..1f)
        assertEquals(1f, mapArtistTopBarLocalProgress(1f, 0.15f, 1f), 0f)
    }

    @Test
    fun unchangedTextAtTheSamePositionCreatesNoAnimatedProperty() {
        val old = visualState(element("ancestor", "Kou!", 46f))
        val target = visualState(element("ancestor", "Kou!", 46f))
        val transition = visualTransition(old, target)

        assertFalse(transition.hasAnimation)
        assertEquals(1f, transition.presentation(0f).single().alpha, 0f)
        assertEquals(46f, transition.presentation(0.5f).single().x, 0f)
        assertEquals(0f, transition.presentation(1f).single().contentTranslationX, 0f)
        assertEquals(transition.presentation(0f), transition.presentation(1f))
    }

    @Test
    fun oneProgressDerivesAllContentAndPlacementPresentation() {
        val old = visualState(
            element("static", "Kou!", 10f),
            element("moved", "/", 30f),
            element("changed", "全部专辑", 40f),
            element("removed", "old", 60f)
        )
        val target = visualState(
            element("static", "Kou!", 10f),
            element("moved", "/", 20f),
            element("changed", "Album", 35f),
            element("inserted", "new", 70f)
        )
        val transition = visualTransition(old, target)

        listOf(0f, 0.5f, 1f).forEach { progress ->
            val presentation = transition.presentation(progress)
            val static = presentation.element("static", "Kou!")
            val moved = presentation.element("moved", "/")
            val changedOld = presentation.element("changed", "全部专辑")
            val changedNew = presentation.element("changed", "Album")
            val inserted = presentation.element("inserted", "new")
            val removed = presentation.element("removed", "old")

            assertEquals(1f, static.alpha, 0f)
            assertEquals(10f, static.x, 0f)
            assertEquals(0f, static.contentTranslationX, 0f)
            assertEquals(1f, moved.alpha, 0f)
            assertEquals(30f - 10f * progress, moved.x, 0.0001f)
            assertEquals(0f, moved.contentTranslationX, 0f)
            assertEquals(1f - progress, changedOld.alpha, 0.0001f)
            assertEquals(
                contentMotionDistance * progress,
                changedOld.contentTranslationX,
                0.0001f
            )
            assertEquals(progress, changedNew.alpha, 0.0001f)
            assertEquals(
                contentMotionDistance * (1f - progress),
                changedNew.contentTranslationX,
                0.0001f
            )
            assertEquals(changedOld.x, changedNew.x, 0.0001f)
            assertEquals(40f - 5f * progress, changedOld.x, 0.0001f)
            assertEquals(progress, inserted.alpha, 0.0001f)
            assertEquals(70f, inserted.x, 0f)
            assertEquals(
                contentMotionDistance * (1f - progress),
                inserted.contentTranslationX,
                0.0001f
            )
            assertEquals(1f - progress, removed.alpha, 0.0001f)
            assertEquals(60f, removed.x, 0f)
            assertEquals(
                contentMotionDistance * progress,
                removed.contentTranslationX,
                0.0001f
            )
        }
    }

    @Test
    fun artistToShortAlbumOnlyInsertsSeparatorAndCurrent() {
        val artist = stateFor("Kou!", emptyList(), ArtistTopBarPathLayout.FullPath, 40f, emptyList())
        val album = stateFor(
            "Kou!",
            listOf("Album"),
            ArtistTopBarPathLayout.FullPath,
            40f,
            listOf(80f)
        )
        val diff = artistTopBarVisualDiff(artist, album).associateBy { it.stableKey }

        assertEquals(ArtistTopBarVisualChange.UnchangedStatic, diff["ancestor"]?.change)
        assertEquals(ArtistTopBarVisualChange.Inserted, diff["separator:0"]?.change)
        assertEquals(ArtistTopBarVisualChange.Inserted, diff["path:0"]?.change)

        val early = visualTransition(artist, album).presentation(0.1f)
        val separator = early.element("separator:0", " / ")
        val title = early.element("path:0", "Album")
        assertTrue(separator.alpha > title.alpha)
        assertTrue(separator.contentTranslationX < title.contentTranslationX)

        val endpoint = visualTransition(artist, album).presentation(1f)
        assertEquals(1f, endpoint.element("separator:0", " / ").alpha, 0f)
        assertEquals(0f, endpoint.element("separator:0", " / ").contentTranslationX, 0f)
        assertEquals(1f, endpoint.element("path:0", "Album").alpha, 0f)
        assertEquals(0f, endpoint.element("path:0", "Album").contentTranslationX, 0f)
    }

    @Test
    fun artistToLongAlbumUsesFinalInsertedPositionsFromTheFirstFrame() {
        val artist = stateFor("Kou!", emptyList(), ArtistTopBarPathLayout.FullPath, 40f, emptyList())
        val album = stateFor(
            "Kou!",
            listOf("Very Long Album"),
            ArtistTopBarPathLayout.CollapsedAncestors,
            8f,
            listOf(160f)
        )
        val start = visualTransition(artist, album).presentation(0f)

        assertEquals(1f, start.element("ancestor", "Kou!").alpha, 0f)
        assertEquals(0f, start.element("ancestor", "Kou!").contentTranslationX, 0f)
        assertEquals(0f, start.element("ancestor", "…").alpha, 0f)
        assertEquals(
            contentMotionDistance,
            start.element("ancestor", "…").contentTranslationX,
            0f
        )
        assertEquals(54f, start.element("separator:0", " / ").x, 0f)
        assertEquals(0f, start.element("separator:0", " / ").alpha, 0f)
        assertEquals(
            contentMotionDistance,
            start.element("separator:0", " / ").contentTranslationX,
            0f
        )
        assertEquals(66f, start.element("path:0", "Very Long Album").x, 0f)
        assertEquals(0f, start.element("path:0", "Very Long Album").alpha, 0f)
        assertEquals(
            contentMotionDistance,
            start.element("path:0", "Very Long Album").contentTranslationX,
            0f
        )
    }

    @Test
    fun nestedArtistAlbumKeepsTheCommonPathAndInsertsOnlyTheLeaf() {
        val albums = stateFor(
            "Kou!",
            listOf("全部专辑"),
            ArtistTopBarPathLayout.FullPath,
            40f,
            listOf(80f)
        )
        val album = stateFor(
            "Kou!",
            listOf("全部专辑", "Album"),
            ArtistTopBarPathLayout.FullPath,
            40f,
            listOf(80f, 80f)
        )
        val diff = artistTopBarVisualDiff(albums, album).associateBy { it.stableKey }

        assertEquals(ArtistTopBarVisualChange.UnchangedStatic, diff["ancestor"]?.change)
        assertEquals(ArtistTopBarVisualChange.UnchangedStatic, diff["separator:0"]?.change)
        assertEquals(ArtistTopBarVisualChange.UnchangedStatic, diff["path:0"]?.change)
        assertEquals(ArtistTopBarVisualChange.Inserted, diff["separator:1"]?.change)
        assertEquals(ArtistTopBarVisualChange.Inserted, diff["path:1"]?.change)
    }

    @Test
    fun changedCurrentReplacesTextWhileSeparatorRemainsStatic() {
        val albums = stateFor(
            "Kou!",
            listOf("全部专辑"),
            ArtistTopBarPathLayout.FullPath,
            40f,
            listOf(80f)
        )
        val album = stateFor(
            "Kou!",
            listOf("Album"),
            ArtistTopBarPathLayout.FullPath,
            40f,
            listOf(80f)
        )
        val diff = artistTopBarVisualDiff(albums, album).associateBy { it.stableKey }

        assertEquals(ArtistTopBarVisualChange.UnchangedStatic, diff["ancestor"]?.change)
        assertEquals(ArtistTopBarVisualChange.UnchangedStatic, diff["separator:0"]?.change)
        assertEquals(ArtistTopBarVisualChange.Changed, diff["path:0"]?.change)

        val halfway = visualTransition(albums, album).presentation(0.5f)
        val separator = halfway.element("separator:0", " / ")
        assertEquals(1f, separator.alpha, 0f)
        assertEquals(0f, separator.contentTranslationX, 0f)
        assertEquals(0.5f, halfway.element("path:0", "全部专辑").alpha, 0f)
        assertEquals(0.5f, halfway.element("path:0", "Album").alpha, 0f)
        assertEquals(
            contentMotionDistance / 2f,
            halfway.element("path:0", "全部专辑").contentTranslationX,
            0f
        )
        assertEquals(
            contentMotionDistance / 2f,
            halfway.element("path:0", "Album").contentTranslationX,
            0f
        )
    }

    @Test
    fun removingBreadcrumbLevelExitsTitleBeforeSeparator() {
        val album = stateFor(
            "Kou!",
            listOf("Album"),
            ArtistTopBarPathLayout.FullPath,
            40f,
            listOf(80f)
        )
        val artist = stateFor("Kou!", emptyList(), ArtistTopBarPathLayout.FullPath, 40f, emptyList())
        val early = visualTransition(album, artist).presentation(0.1f)
        val separator = early.element("separator:0", " / ")
        val title = early.element("path:0", "Album")

        assertTrue(title.alpha < separator.alpha)
        assertTrue(title.contentTranslationX > separator.contentTranslationX)

        val endpoint = visualTransition(album, artist).presentation(1f)
        assertEquals(0f, endpoint.element("separator:0", " / ").alpha, 0f)
        assertEquals(
            contentMotionDistance,
            endpoint.element("separator:0", " / ").contentTranslationX,
            0f
        )
        assertEquals(0f, endpoint.element("path:0", "Album").alpha, 0f)
        assertEquals(
            contentMotionDistance,
            endpoint.element("path:0", "Album").contentTranslationX,
            0f
        )
    }

    @Test
    fun removedElementsRemainInPresentationUntilTheSharedEndpoint() {
        val old = visualState(
            element("ancestor", "Kou!", 46f),
            element("separator:0", " / ", 86f),
            element("path:0", "Album", 98f)
        )
        val target = visualState(element("ancestor", "Kou!", 46f))
        val transition = visualTransition(old, target)
        val titleExitProgress = mapArtistTopBarLocalProgress(0.5f, 0f, 0.85f)

        assertEquals(
            1f - titleExitProgress,
            transition.presentation(0.5f).element("path:0", "Album").alpha,
            0f
        )
        assertEquals(
            contentMotionDistance * titleExitProgress,
            transition.presentation(0.5f).element("path:0", "Album").contentTranslationX,
            0f
        )
        assertEquals(0f, transition.presentation(1f).element("path:0", "Album").alpha, 0f)
        assertEquals(
            contentMotionDistance,
            transition.presentation(1f).element("path:0", "Album").contentTranslationX,
            0f
        )
    }

    @Test
    fun midFlightRetargetStartsFromOneCapturedPresentation() {
        val artist = visualState(element("ancestor", "Kou!", 46f))
        val album = visualState(
            element("ancestor", "…", 46f),
            element("separator:0", " / ", 54f),
            element("path:0", "Album", 66f)
        )
        val snapshot = visualTransition(artist, album).presentation(0.4f)
        val retargetedStart = artistTopBarRetargetedVisualTransition(
            startPresentation = snapshot,
            targetState = artist,
            contentMotionDistancePx = contentMotionDistance
        )
            .presentation(0f)

        snapshot.forEach { element ->
            val resumed = retargetedStart.element(
                element.element.stableKey,
                element.element.text
            )
            assertEquals(element.alpha, resumed.alpha, 0f)
            assertEquals(element.x, resumed.x, 0f)
            assertEquals(element.contentTranslationX, resumed.contentTranslationX, 0f)
        }
    }

    @Test
    fun collapsedTargetGeometryStartsAfterFinalEllipsisWidth() {
        val collapsed = stateFor(
            "Kou!",
            listOf("Long Album"),
            ArtistTopBarPathLayout.CollapsedAncestors,
            8f,
            listOf(120f)
        )
        val elements = collapsed.elements.associateBy { it.stableKey }

        assertEquals(46f, elements.getValue("ancestor").x, 0f)
        assertEquals(54f, elements.getValue("separator:0").x, 0f)
        assertEquals(66f, elements.getValue("path:0").x, 0f)
    }

    @Test
    fun collapseMovesStableSeparatorWithTheSameTransitionProgress() {
        val full = stateFor(
            "Kou!",
            listOf("全部专辑"),
            ArtistTopBarPathLayout.FullPath,
            40f,
            listOf(80f)
        )
        val collapsed = stateFor(
            "Kou!",
            listOf("Very Long Album"),
            ArtistTopBarPathLayout.CollapsedAncestors,
            8f,
            listOf(160f)
        )
        val diff = artistTopBarVisualDiff(full, collapsed).associateBy { it.stableKey }
        val halfway = visualTransition(full, collapsed).presentation(0.5f)

        assertEquals(ArtistTopBarVisualChange.Changed, diff["ancestor"]?.change)
        assertEquals(ArtistTopBarVisualChange.Moved, diff["separator:0"]?.change)
        assertEquals(ArtistTopBarVisualChange.Changed, diff["path:0"]?.change)
        assertEquals(70f, halfway.element("separator:0", " / ").x, 0f)
        assertEquals(1f, halfway.element("separator:0", " / ").alpha, 0f)
        assertEquals(0f, halfway.element("separator:0", " / ").contentTranslationX, 0f)
        assertEquals(0.5f, halfway.element("ancestor", "Kou!").alpha, 0f)
        assertEquals(
            contentMotionDistance / 2f,
            halfway.element("ancestor", "Kou!").contentTranslationX,
            0f
        )
        assertEquals(0.5f, halfway.element("ancestor", "…").alpha, 0f)
        assertEquals(
            contentMotionDistance / 2f,
            halfway.element("ancestor", "…").contentTranslationX,
            0f
        )
    }

    private fun stateFor(
        artistName: String,
        pathSegments: List<String>,
        layout: ArtistTopBarPathLayout,
        leadingWidth: Float,
        pathWidths: List<Float>
    ): ArtistTopBarVisualState = artistTopBarVisualState(
        visualModel = artistVisualBreadcrumbModel(artistName, pathSegments, layout),
        totalWidthPx = 320f,
        avatarWidthPx = 36f,
        titleGapPx = 10f,
        leadingTextWidthPx = leadingWidth,
        separatorWidthPx = 12f,
        pathTextWidthsPx = pathWidths
    )

    private fun visualState(
        vararg elements: ArtistTopBarVisualElement
    ): ArtistTopBarVisualState = ArtistTopBarVisualState(elements.toList())

    private fun visualTransition(
        oldState: ArtistTopBarVisualState,
        targetState: ArtistTopBarVisualState
    ): ArtistTopBarVisualTransition = artistTopBarVisualTransition(
        oldState = oldState,
        targetState = targetState,
        contentMotionDistancePx = contentMotionDistance
    )

    private fun element(
        stableKey: String,
        text: String,
        x: Float
    ): ArtistTopBarVisualElement = ArtistTopBarVisualElement(
        stableKey = stableKey,
        text = text,
        role = ArtistVisualBreadcrumbRole.Ancestor,
        x = x,
        width = 20f
    )

    private fun List<ArtistTopBarPresentedElement>.element(
        stableKey: String,
        text: String
    ): ArtistTopBarPresentedElement = single { presented ->
        presented.element.stableKey == stableKey && presented.element.text == text
    }
}
