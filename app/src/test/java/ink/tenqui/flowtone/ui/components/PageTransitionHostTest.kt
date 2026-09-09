package ink.tenqui.flowtone.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTransitionHostTest {
    @Test fun reversingAtPartialEntryUsesOnlyCurrentRemainingProgress() {
        assertEquals(0.4f, pageTransitionRemainingFraction(0.4f, 0f), 0.0001f)
        assertEquals(0.6f, pageTransitionRemainingFraction(0.4f, 1f), 0.0001f)
    }

    @Test fun profileCardEntersFromNegativeYAndSettlesAtZero() {
        val start = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0f,
            signedOffsetYPx = -9.6f
        )
        val end = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 1f,
            signedOffsetYPx = -9.6f
        )

        assertTrue(start.translationY < 0f)
        assertEquals(0f, end.translationY, 0.0001f)
        assertEquals(0f, start.alpha, 0.0001f)
        assertEquals(1f, end.alpha, 0.0001f)
    }

    @Test fun artistBackButtonUsesTheSameNegativeYDirection() {
        val start = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0f,
            signedOffsetYPx = -8f
        )
        val reversing = pageElementVisualState(
            phase = PageTransitionPhase.Incoming,
            elementProgress = 0.4f,
            signedOffsetYPx = -8f
        )

        assertEquals(-8f, start.translationY, 0.0001f)
        assertTrue(reversing.translationY in -8f..0f)
        assertEquals(0.4f, reversing.alpha, 0.0001f)
    }

    @Test fun backAtEverySampledProgressTargetsZeroImmediately() {
        listOf(0.1f, 0.5f, 0.9f, 0.99f).forEach { progress ->
            val action = pageTransitionRetargetAction(
                requestedTarget = "artist",
                outgoing = "artist",
                incoming = "album",
                samePage = String::equals
            )

            assertEquals(PageTransitionRetargetAction.ReverseToOutgoing, action)
            assertEquals(0f, pageTransitionTargetEndpoint(action), 0.0001f)
            assertEquals(
                (PageMotion.DurationMillis * progress).toInt(),
                pageTransitionDurationMillis(progress, 0f)
            )
            assertFalse(pageTransitionEndpointReached(progress, 1f))
        }
    }

    @Test fun onlyTheCoroutineThatReachesItsEndpointCanCleanUpThePair() {
        assertFalse(pageTransitionEndpointReached(0.99f, 1f))
        assertTrue(pageTransitionEndpointReached(0f, 0f))
        assertTrue(pageTransitionEndpointReached(1f, 1f))
    }

    @Test fun anEndpointCannotCommitBeforeTheAnimatableActuallyReachesIt() {
        assertFalse(
            pageTransitionEndpointReached(
                currentProgress = 0.99f,
                targetProgress = 1f
            )
        )
    }

    @Test fun localElementCompletionCannotCommitTheMasterPair() {
        val masterProgress = 0.8f
        val localProgress = PageMotion.elementProgress(
            pageProgress = masterProgress,
            order = 0,
            orderCount = PageMotion.DefaultOrderCount
        )

        assertTrue(localProgress > masterProgress)
        assertFalse(pageTransitionEndpointReached(masterProgress, 1f))
    }

    @Test fun endpointCleanupRequiresTheLatestTargetToStillOwnThatEndpoint() {
        assertFalse(
            pageTransitionEndpointStillRequested(
                targetProgress = 1f,
                latestTarget = "artist",
                outgoing = "artist",
                incoming = "album",
                samePage = String::equals
            )
        )
        assertTrue(
            pageTransitionEndpointStillRequested(
                targetProgress = 0f,
                latestTarget = "artist",
                outgoing = "artist",
                incoming = "album",
                samePage = String::equals
            )
        )
    }

    @Test fun retainedPairCannotCommitAtAnyLateBackSample() {
        val generation = Any()
        listOf(0.7f, 0.8f, 0.9f, 0.95f, 0.99f).forEach { progress ->
            assertFalse(
                pageTransitionCanCommitPair(
                    animationTransitionId = 1,
                    activeTransitionId = 1,
                    animationGeneration = generation,
                    activeGeneration = generation,
                    currentProgress = progress,
                    targetProgress = 1f,
                    latestTarget = "album",
                    outgoing = "artist",
                    incoming = "album",
                    samePage = String::equals
                )
            )
        }
    }

    @Test fun finalFrameBackRevokesForwardEndpointCleanupOwnership() {
        val generation = Any()
        assertFalse(
            pageTransitionCanCommitPair(
                animationTransitionId = 1,
                activeTransitionId = 1,
                animationGeneration = generation,
                activeGeneration = generation,
                currentProgress = 1f,
                targetProgress = 1f,
                latestTarget = "artist",
                outgoing = "artist",
                incoming = "album",
                samePage = String::equals
            )
        )
    }

    @Test fun forwardDuringReverseTargetsOneFromTheCurrentProgress() {
        val action = pageTransitionRetargetAction(
            requestedTarget = "album",
            outgoing = "artist",
            incoming = "album",
            samePage = String::equals
        )

        assertEquals(PageTransitionRetargetAction.KeepIncoming, action)
        assertEquals(1f, pageTransitionTargetEndpoint(action), 0.0001f)
        assertEquals(240, pageTransitionDurationMillis(0.4f, 1f))
    }

    @Test fun tinyRetargetDistanceStillHasANonZeroDuration() {
        assertEquals(1, pageTransitionDurationMillis(0.5f, 0.50001f))
    }

    @Test fun aGenuinelyNewTargetReplacesTheRetainedIncomingSlotImmediately() {
        assertEquals(
            PageTransitionRetargetAction.ReplaceIncoming,
            pageTransitionRetargetAction(
                requestedTarget = "artist-a-2",
                outgoing = "artist-a-1",
                incoming = "main",
                samePage = String::equals
            )
        )
    }

    @Test fun reversibleTargetsKeepUsingTheExistingSlotPair() {
        assertEquals(
            PageTransitionRetargetAction.ReverseToOutgoing,
            pageTransitionRetargetAction("artist", "artist", "album", String::equals)
        )
        assertEquals(
            PageTransitionRetargetAction.KeepIncoming,
            pageTransitionRetargetAction("album", "artist", "album", String::equals)
        )
    }

    @Test fun pushRetainsAAsOutgoingAndBAsIncomingUntilEndpointCommit() {
        val initial = PageTransitionPairState.initial("A")
        val pair = initial.begin("B")

        assertEquals("A", pair.slots(0f).outgoing)
        assertEquals("B", pair.slots(0f).incoming)
        assertEquals(PageTransitionPhase.Outgoing, pair.phaseFor(PageSlot.First))
        assertEquals(PageTransitionPhase.Incoming, pair.phaseFor(PageSlot.Second))
        assertEquals(initial.firstPage?.identity, pair.firstPage?.identity)

        val committed = pair.commit(1f)
        assertNull(committed.firstPage)
        assertEquals("B", committed.slots(1f).current)
        assertEquals(PageTransitionPhase.Current, committed.phaseFor(PageSlot.Second))
    }

    @Test fun reverseCommitReusesTheOriginalSlotsAndDisposesOnlyIncoming() {
        val pair = PageTransitionPairState.initial("A").begin("B")
        val artistIdentity = pair.firstPage?.identity
        val albumIdentity = pair.secondPage?.identity

        val reversed = pair.commit(0f)

        assertEquals(artistIdentity, reversed.firstPage?.identity)
        assertNull(reversed.secondPage)
        assertEquals("A", reversed.slots(1f).current)
        assertEquals(1, albumIdentity)
    }

    @Test fun incomingSlotPresentationCannotHideOutgoingOnTheFirstFrame() {
        assertEquals(1f, pageTransitionSlotAlpha(PageTransitionPhase.Outgoing, 0f), 0f)
        assertEquals(0f, pageTransitionSlotAlpha(PageTransitionPhase.Incoming, 0f), 0f)
        assertTrue(pageTransitionSlotAlpha(PageTransitionPhase.Incoming, 0.5f) in 0f..1f)
        assertEquals(1f, pageTransitionSlotAlpha(PageTransitionPhase.Incoming, 1f), 0f)
    }

    @Test fun slotAlphaCompensationPreservesTheExistingElementAlphaContract() {
        val pageProgress = 0.5f
        val slotAlpha = pageTransitionSlotAlpha(PageTransitionPhase.Incoming, pageProgress)
        val desiredElementAlpha = PageMotion.elementProgress(
            pageProgress = pageProgress,
            order = 8,
            orderCount = PageMotion.DefaultOrderCount
        )
        val localElementAlpha = pageTransitionCompensatedAlpha(
            desiredAlpha = desiredElementAlpha,
            appliedContainerAlpha = slotAlpha
        )

        assertEquals(desiredElementAlpha, slotAlpha * localElementAlpha, 0.0001f)
    }

    @Test fun oldCompletionCannotCommitAfterReverseOrReplacementTakesOwnership() {
        val forwardGeneration = Any()
        val reverseGeneration = Any()

        assertFalse(
            pageTransitionCanCommitPair(
                animationTransitionId = 1,
                activeTransitionId = 1,
                animationGeneration = forwardGeneration,
                activeGeneration = reverseGeneration,
                currentProgress = 1f,
                targetProgress = 1f,
                latestTarget = "A",
                outgoing = "A",
                incoming = "B",
                samePage = String::equals
            )
        )
        assertFalse(
            pageTransitionCanCommitPair(
                animationTransitionId = 1,
                activeTransitionId = 2,
                animationGeneration = forwardGeneration,
                activeGeneration = forwardGeneration,
                currentProgress = 1f,
                targetProgress = 1f,
                latestTarget = "C",
                outgoing = "A",
                incoming = "C",
                samePage = String::equals
            )
        )
    }

    @Test fun activePairCommitsOnlyAtItsRequestedStableEndpoint() {
        val generation = Any()

        assertTrue(
            pageTransitionCanCommitPair(
                animationTransitionId = 3,
                activeTransitionId = 3,
                animationGeneration = generation,
                activeGeneration = generation,
                currentProgress = 1f,
                targetProgress = 1f,
                latestTarget = "B",
                outgoing = "A",
                incoming = "B",
                samePage = String::equals
            )
        )
    }

    @Test fun thirdDestinationReplacesOnlyIncomingAndKeepsOutgoingIdentity() {
        val pair = PageTransitionPairState.initial("A").begin("B")
        val outgoingIdentity = pair.firstPage?.identity

        val replaced = pair.replaceIncoming("C")

        assertEquals(outgoingIdentity, replaced.firstPage?.identity)
        assertEquals("A", replaced.slots(0f).outgoing)
        assertEquals("C", replaced.slots(0f).incoming)
        assertTrue(replaced.transitionId > pair.transitionId)
    }

    @Test fun outgoingElementProgressChangesContinuouslyAcrossTheMasterRange() {
        val alphas = listOf(0f, 0.1f, 0.5f, 0.9f, 1f).map { progress ->
            pageElementVisualState(
                phase = PageTransitionPhase.Outgoing,
                elementProgress = progress,
                signedOffsetYPx = 24f
            ).alpha
        }

        assertEquals(1f, alphas.first(), 0f)
        assertEquals(0f, alphas.last(), 0f)
        alphas.zipWithNext().forEach { (previous, next) ->
            assertTrue(next < previous)
        }
    }
}
