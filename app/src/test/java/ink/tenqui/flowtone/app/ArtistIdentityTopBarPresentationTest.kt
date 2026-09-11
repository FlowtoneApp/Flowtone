package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.ui.library.ArtistHeroBackgroundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ArtistIdentityTopBarPresentationTest {
    private val artist = SecondaryDestination.Artist("Kou!")
    private val artistRoute = ArtistTopBarRoute(
        artistEntryKey = "artist-entry",
        artist = artist
    )

    @Test
    fun hiddenIdentityStartsAboveAndFullyTransparent() {
        val hidden = artistTopBarIdentityPresentation(0f)

        assertEquals(0f, hidden.alpha, 0f)
        assertTrue(hidden.translationYFraction < 0f)
    }

    @Test
    fun visibleIdentityEndsAtRestAndFullyOpaque() {
        val visible = artistTopBarIdentityPresentation(1f)

        assertEquals(1f, visible.alpha, 0f)
        assertEquals(0f, visible.translationYFraction, 0f)
    }

    @Test
    fun exitRetracesTheSamePresentationPath() {
        val enterPath = (0..10).map { step ->
            artistTopBarIdentityPresentation(step / 10f)
        }
        val exitPath = (0..10).map { step ->
            artistTopBarIdentityPresentation(1f - step / 10f)
        }

        enterPath.zip(exitPath.reversed()).forEach { (enter, exit) ->
            assertEquals(enter.alpha, exit.alpha, 0.0001f)
            assertEquals(enter.translationYFraction, exit.translationYFraction, 0.0001f)
        }
        assertEquals(16f, ArtistTopBarIdentityMotionDistance.value, 0f)
    }

    @Test
    fun backAndAvatarFormTheStableShell() {
        assertEquals(
            ArtistTopBarStableShellElement.entries.toSet(),
            ArtistTopBarStableShellElements
        )
    }

    @Test
    fun rapidThresholdReverseContinuesFromTheCurrentProgress() {
        val inFlight = artistTopBarIdentityPresentation(0.43f)
        val reversedFromSameFrame = artistTopBarIdentityPresentation(0.43f)

        assertEquals(inFlight, reversedFromSameFrame)
        assertFalse(inFlight.alpha == 0f || inFlight.alpha == 1f)
    }

    @Test
    fun artistTopBarSurfaceRemainsTransparentForEveryHeroKind() {
        ArtistHeroBackgroundKind.entries.forEach { kind ->
            assertEquals(
                ArtistTopBarSurfaceTreatment.Transparent,
                artistTopBarSurfaceTreatment(kind)
            )
        }
    }

    @Test
    fun branchIsAbsentWithoutALiveOrRetainedArtistPresentation() {
        assertEquals(
            null,
            artistTopBarPresentationTarget(
                liveRoute = null,
                retainedRoute = null,
                liveIdentityVisible = false
            )
        )
    }

    @Test
    fun liveArtistRouteKeepsTheWholeTopBarVisible() {
        val target = checkNotNull(
            artistTopBarPresentationTarget(
                liveRoute = artistRoute,
                retainedRoute = null,
                liveIdentityVisible = true
            )
        )

        assertEquals(artistRoute, target.route)
        assertTrue(target.targetVisible)
        assertFalse(target.exiting)
    }

    @Test
    fun artistLineagePathChangesKeepTheWholeTopBarVisible() {
        val songsRoute = artistRoute.copy(pathSegments = listOf("全部歌曲"))
        val albumRoute = artistRoute.copy(pathSegments = listOf("全部专辑", "Album"))

        listOf(songsRoute, albumRoute).forEach { route ->
            val target = checkNotNull(
                artistTopBarPresentationTarget(
                    liveRoute = route,
                    retainedRoute = artistRoute,
                    liveIdentityVisible = true
                )
            )

            assertTrue(target.targetVisible)
            assertFalse(target.exiting)
            assertEquals(route, target.route)
        }
    }

    @Test
    fun missingLiveArtistRouteKeepsTheRetainedPresentationOnlyForExit() {
        val target = checkNotNull(
            artistTopBarPresentationTarget(
                liveRoute = null,
                retainedRoute = artistRoute,
                liveIdentityVisible = false
            )
        )

        assertEquals(artistRoute, target.route)
        assertFalse(target.targetVisible)
        assertTrue(target.exiting)
        assertFalse(artistTopBarExitSnapshotShouldRelease(0.25f))
        assertTrue(artistTopBarExitSnapshotShouldRelease(0f))
    }

    @Test
    fun aNewArtistRouteWinsOverAnotherArtistsRetainedPresentation() {
        val otherRoute = ArtistTopBarRoute(
            artistEntryKey = "other-artist-entry",
            artist = SecondaryDestination.Artist("Other")
        )

        val target = checkNotNull(
            artistTopBarPresentationTarget(
                liveRoute = otherRoute,
                retainedRoute = artistRoute,
                liveIdentityVisible = true
            )
        )

        assertEquals(otherRoute, target.route)
        assertTrue(target.targetVisible)
        assertFalse(target.exiting)
    }

    @Test
    fun backdropConsumesTheSameProgressAsTheArtistTopBarContent() {
        listOf(0f, 0.4f, 1f).forEach { progress ->
            assertEquals(
                artistTopBarIdentityPresentation(progress).alpha,
                artistTopBarBackdropAlpha(progress),
                0f
            )
        }
    }
}
