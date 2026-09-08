package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.ui.library.ArtistHeroBackgroundKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ArtistIdentityTopBarPresentationTest {
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
    fun backAvatarAndBreadcrumbShareOnePresentationLayer() {
        assertEquals(
            ArtistTopBarPresentedElement.entries.toSet(),
            ArtistTopBarUnifiedPresentedElements
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
}
