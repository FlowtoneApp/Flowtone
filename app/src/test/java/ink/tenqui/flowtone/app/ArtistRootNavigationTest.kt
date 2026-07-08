package ink.tenqui.flowtone.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistRootNavigationTest {
    @Test
    fun miniPlayerEntryReturnsToMiniPlayerFullscreen() {
        val target = artistRootReturnTarget(ArtistRootNavigationMode.MiniPlayer)

        assertEquals(ArtistRootReturnTarget.MiniPlayerFullscreen, target)
    }

    @Test
    fun searchEntryReturnsToPreviousPage() {
        val target = artistRootReturnTarget(ArtistRootNavigationMode.NormalPage)

        assertEquals(ArtistRootReturnTarget.PreviousPage, target)
    }

    @Test
    fun normalPageEntryReturnsToPreviousPage() {
        val target = artistRootReturnTarget(ArtistRootNavigationMode.NormalPage)

        assertEquals(ArtistRootReturnTarget.PreviousPage, target)
    }

    @Test
    fun consecutiveEntriesUseCurrentMode() {
        val targets = listOf(
            ArtistRootNavigationMode.NormalPage,
            ArtistRootNavigationMode.MiniPlayer,
            ArtistRootNavigationMode.NormalPage
        ).map(::artistRootReturnTarget)

        assertEquals(
            listOf(
                ArtistRootReturnTarget.PreviousPage,
                ArtistRootReturnTarget.MiniPlayerFullscreen,
                ArtistRootReturnTarget.PreviousPage
            ),
            targets
        )
    }

    @Test
    fun missingModeFallsBackToPreviousPage() {
        val target = artistRootReturnTarget(null)

        assertEquals(ArtistRootReturnTarget.PreviousPage, target)
    }

    @Test
    fun searchNormalPageArtistOpenStartsSearchExit() {
        val stage = searchReturnStageForArtistOpen(
            searchActive = true,
            navigationMode = ArtistRootNavigationMode.NormalPage
        )

        assertEquals(SearchReturnStage.SearchExitingForArtist, stage)
    }

    @Test
    fun miniPlayerArtistOpenDoesNotStartSearchReturnFlow() {
        val stage = searchReturnStageForArtistOpen(
            searchActive = true,
            navigationMode = ArtistRootNavigationMode.MiniPlayer
        )

        assertEquals(SearchReturnStage.Idle, stage)
    }

    @Test
    fun normalArtistCloseFromSearchRestoresSearchPosition() {
        val shouldRestore = shouldRestoreSearchAfterArtistClose(
            searchActive = true,
            navigationMode = ArtistRootNavigationMode.NormalPage,
            currentStage = SearchReturnStage.ArtistVisible
        )

        assertEquals(true, shouldRestore)
    }

    @Test
    fun miniPlayerArtistCloseDoesNotRestoreSearchPosition() {
        val shouldRestore = shouldRestoreSearchAfterArtistClose(
            searchActive = true,
            navigationMode = ArtistRootNavigationMode.MiniPlayer,
            currentStage = SearchReturnStage.ArtistVisible
        )

        assertEquals(false, shouldRestore)
    }

    @Test
    fun repeatedCloseEventDoesNotRestartSearchRestore() {
        val shouldRestore = shouldRestoreSearchAfterArtistClose(
            searchActive = true,
            navigationMode = ArtistRootNavigationMode.NormalPage,
            currentStage = SearchReturnStage.ArtistExitingToSearch
        )

        assertEquals(false, shouldRestore)
    }

    @Test
    fun restoredPositionStartsSearchReentry() {
        val stage = searchReturnStageAfterPositionRestored(
            SearchReturnStage.SearchPreparing
        )

        assertEquals(SearchReturnStage.SearchReentering, stage)
    }

    @Test
    fun artistExitCompletesBeforeSearchPreparing() {
        val stage = searchReturnStageAfterArtistExit(
            SearchReturnStage.ArtistExitingToSearch
        )

        assertEquals(SearchReturnStage.SearchPreparing, stage)
    }

    @Test
    fun searchReturnAnimationStagesIgnoreRepeatedBack() {
        val stages = listOf(
            SearchReturnStage.SearchExitingForArtist,
            SearchReturnStage.ArtistExitingToSearch,
            SearchReturnStage.SearchPreparing,
            SearchReturnStage.SearchReentering
        )

        assertEquals(true, stages.all(::isSearchReturnAnimationStage))
    }

    @Test
    fun visibleSearchStagesDoNotIgnoreBack() {
        val stages = listOf(
            SearchReturnStage.Idle,
            SearchReturnStage.ArtistVisible
        )

        assertEquals(false, stages.any(::isSearchReturnAnimationStage))
    }

    @Test
    fun miniPlayerSameArtistCollapsesInsteadOfOpeningAgain() {
        val decision = miniPlayerArtistOpenDecision(
            currentArtistName = " Artist A ",
            targetArtistName = "artist a",
            searchReturnStage = SearchReturnStage.ArtistVisible,
            artistRootReturnInProgress = false
        )

        assertEquals(MiniPlayerArtistOpenDecision.CollapseMiniPlayer, decision)
    }

    @Test
    fun miniPlayerSameArtistFromMultiArtistSelectionCollapses() {
        val decision = miniPlayerArtistOpenDecision(
            currentArtistName = "Artist A",
            targetArtistName = " ARTIST A ",
            searchReturnStage = SearchReturnStage.Idle,
            artistRootReturnInProgress = false
        )

        assertEquals(MiniPlayerArtistOpenDecision.CollapseMiniPlayer, decision)
    }

    @Test
    fun miniPlayerDifferentArtistStillOpensNormally() {
        val decision = miniPlayerArtistOpenDecision(
            currentArtistName = "Artist A",
            targetArtistName = "Artist B",
            searchReturnStage = SearchReturnStage.ArtistVisible,
            artistRootReturnInProgress = false
        )

        assertEquals(MiniPlayerArtistOpenDecision.OpenArtistPage, decision)
    }

    @Test
    fun miniPlayerOpenWithoutCurrentArtistOpensNormally() {
        val decision = miniPlayerArtistOpenDecision(
            currentArtistName = null,
            targetArtistName = "Artist A",
            searchReturnStage = SearchReturnStage.Idle,
            artistRootReturnInProgress = false
        )

        assertEquals(MiniPlayerArtistOpenDecision.OpenArtistPage, decision)
    }

    @Test
    fun miniPlayerSameArtistExitingIsIgnored() {
        val decision = miniPlayerArtistOpenDecision(
            currentArtistName = "Artist A",
            targetArtistName = "artist a",
            searchReturnStage = SearchReturnStage.ArtistExitingToSearch,
            artistRootReturnInProgress = false
        )

        assertEquals(MiniPlayerArtistOpenDecision.Ignore, decision)
    }

    @Test
    fun miniPlayerSameArtistReturningToMiniPlayerIsIgnored() {
        val decision = miniPlayerArtistOpenDecision(
            currentArtistName = "Artist A",
            targetArtistName = "artist a",
            searchReturnStage = SearchReturnStage.Idle,
            artistRootReturnInProgress = true
        )

        assertEquals(MiniPlayerArtistOpenDecision.Ignore, decision)
    }

    @Test
    fun blankMiniPlayerArtistOpenIsIgnored() {
        val decision = miniPlayerArtistOpenDecision(
            currentArtistName = "Artist A",
            targetArtistName = " ",
            searchReturnStage = SearchReturnStage.Idle,
            artistRootReturnInProgress = false
        )

        assertEquals(MiniPlayerArtistOpenDecision.Ignore, decision)
    }
}
