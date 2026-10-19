package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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

    @Test fun actualAnimationReversesAtTenFiftyNinetyAndNinetyNinePercent() = runBlocking {
        withTimeout(5_000) {
            listOf(0.1f, 0.5f, 0.9f, 0.99f).forEach { sample ->
                val clock = AnimationClock()
                val progress = Animatable(0f)
                val forward = launch(clock, start = CoroutineStart.UNDISPATCHED) {
                    animatePageTransitionProgressTo(progress, 1f)
                }
                clock.frameAfter(0)
                clock.frameAfter((PageMotion.DurationMillis * sample).toLong())
                assertEquals(sample, progress.value, 0.0001f)
                forward.cancelAndJoin()

                val duration = pageTransitionDurationMillis(progress.value, 0f)
                val reverse = launch(clock, start = CoroutineStart.UNDISPATCHED) {
                    animatePageTransitionProgressTo(progress, 0f)
                }
                clock.frameAfter(0)
                assertEquals(sample, progress.value, 0.0001f)
                clock.frameAfter((duration - 1).toLong())
                assertFalse(reverse.isCompleted)
                assertTrue(progress.value > 0f)
                clock.frameAfter(1)
                reverse.join()

                assertEquals(0f, progress.value, 0f)
                assertEquals((PageMotion.DurationMillis * sample).toLong() + duration, clock.timeMillis)
                assertTrue(duration <= PageMotion.DurationMillis)
            }
        }
    }

    @Test fun lateReverseUsesTheCanonicalVisualCurveRatherThanReplayingItsSlowTail() = runBlocking {
        withTimeout(5_000) {
            val initialProgress = 0.9f
            val progress = Animatable(initialProgress)
            val clock = AnimationClock()
            val duration = pageTransitionDurationMillis(initialProgress, 0f)
            val initialBlur = PageMotion.Easing.transform(initialProgress)
            val animation = launch(clock, start = CoroutineStart.UNDISPATCHED) {
                animatePageTransitionProgressTo(progress, 0f)
            }
            clock.frameAfter(0)
            clock.frameAfter((duration / 4).toLong())
            val currentBlur = PageMotion.Easing.transform(progress.value)
            val recoveredFraction = (initialBlur - currentBlur) / initialBlur
            assertEquals(
                "The first quarter of a reverse must restore visibility and remove blur using the normal curve",
                PageMotion.Easing.transform(0.25f),
                recoveredFraction,
                0.001f
            )
            clock.frameAfter((duration - duration / 4).toLong())
            animation.join()
            assertEquals(0f, progress.value, 0f)
        }
    }

    @Test fun retainedContentHasRecoveredWhenTheReversedPairCommits() = runBlocking {
        withTimeout(5_000) {
            val pair = PageTransitionPairState.initial("A").begin("B")
            val progress = Animatable(0.9f)
            val clock = AnimationClock()
            val reverse = launch(clock, start = CoroutineStart.UNDISPATCHED) {
                animatePageTransitionProgressTo(progress, 0f)
            }
            clock.frameAfter(0)
            val before = outgoingScope(progress.value).retainedContentProgress
            clock.frameAfter(180)
            val halfway = outgoingScope(progress.value).retainedContentProgress
            assertTrue(halfway > before)
            assertTrue(reverse.isActive)
            clock.frameAfter(180)
            reverse.join()

            assertEquals(360L, clock.timeMillis)
            assertEquals(1f, outgoingScope(progress.value).retainedContentProgress, 0f)
            val committed = pair.commit(0f)
            val current = PageTransitionScope(
                phase = checkNotNull(committed.phaseFor(committed.currentSlot)),
                progress = 1f,
                offsetYPx = 24f,
                transitionId = committed.transitionId
            )
            assertEquals("A", committed.slots(1f).current)
            assertEquals(1f, current.retainedContentProgress, 0f)
            // 提交前已完全恢复，Current 不再触发第二个 400ms 入场。
            clock.frameAfter(PageMotion.DurationMillis.toLong())
            assertEquals(1f, current.retainedContentProgress, 0f)
        }
    }

    @Test fun repeatedCancellationKeepsTheSamePairAndUsesTheLatestRemainingDistance() = runBlocking {
        withTimeout(5_000) {
            val pair = PageTransitionPairState.initial("A").begin("B")
            val outgoingIdentity = pair.firstPage?.identity
            val incomingIdentity = pair.secondPage?.identity
            val clock = AnimationClock()
            val progress = Animatable(0.9f)
            val reverse = launch(clock, start = CoroutineStart.UNDISPATCHED) {
                animatePageTransitionProgressTo(progress, 0f)
            }
            clock.frameAfter(0)
            clock.frameAfter(160)
            val interruptedProgress = progress.value
            assertTrue(interruptedProgress > 0f && interruptedProgress < 0.9f)
            reverse.cancelAndJoin()
            val remainingDuration = pageTransitionDurationMillis(interruptedProgress, 1f)

            val forward = launch(clock, start = CoroutineStart.UNDISPATCHED) {
                animatePageTransitionProgressTo(progress, 1f)
            }
            clock.frameAfter(0)
            assertEquals(interruptedProgress, progress.value, 0f)
            clock.frameAfter(remainingDuration.toLong())
            forward.join()

            assertEquals(160L + remainingDuration, clock.timeMillis)
            assertTrue(remainingDuration <= PageMotion.DurationMillis)
            assertEquals(outgoingIdentity, pair.firstPage?.identity)
            assertEquals(incomingIdentity, pair.secondPage?.identity)
            assertEquals("B", pair.commit(1f).slots(1f).current)
        }
    }

    @Test fun aNormalCompletedNavigationDisposesAAndBackBeginsANewFullTransition() {
        val pair = PageTransitionPairState.initial("A").begin("B")
        val previousAIdentity = pair.firstPage?.identity
        val settledB = pair.commit(1f)
        assertNull(settledB.firstPage)

        val back = settledB.begin("A")
        assertEquals("B", back.slots(0f).outgoing)
        assertEquals("A", back.slots(0f).incoming)
        assertTrue(previousAIdentity != back.firstPage?.identity)
        assertEquals(PageMotion.DurationMillis, pageTransitionDurationMillis(0f, 1f))
    }

    @Test fun anObsoleteCompletionCannotCommitEvenAfterTheTargetReturnsToTheSamePage() {
        assertFalse(
            pageTransitionCanCommitPair(
                animationTransitionId = 1,
                activeTransitionId = 1,
                animationGeneration = Any(),
                activeGeneration = Any(),
                currentProgress = 1f,
                targetProgress = 1f,
                latestTarget = "B",
                outgoing = "A",
                incoming = "B",
                samePage = String::equals
            )
        )
    }

    @Test fun nestedRetainedContentUsesTheParentProgressThroughoutReversal() {
        val local = PageTransitionScope(PageTransitionPhase.Current, 1f, 24f, 0)
        listOf(0.99f, 0.9f, 0.5f, 0.1f, 0f).forEach { progress ->
            val parent = outgoingScope(progress)
            val nested = parent.combineWith(local)
            assertEquals(PageTransitionPhase.Outgoing, nested.phase)
            assertEquals(progress, nested.progress, 0f)
            assertEquals(parent.retainedContentProgress, nested.retainedContentProgress, 0f)
        }
    }

    @Test fun fullForwardMainProgressIsStillLinear() {
        val easing = PageMotion.progressAnimationEasing(0f, 1f)
        (0..100).forEach { frame ->
            val fraction = frame / 100f
            assertEquals(fraction, easing.transform(fraction), 0f)
        }
    }

    @Test fun bothRetargetDirectionsKeepEndpointsAndUseTheOriginalVisualCurve() {
        listOf(0.1f, 0.5f, 0.9f, 0.99f).forEach { start ->
            listOf(0f, 1f).forEach { target ->
                val easing = PageMotion.progressAnimationEasing(start, target)
                val initialVisual = PageMotion.Easing.transform(start)
                val targetVisual = PageMotion.Easing.transform(target)
                var previous = start
                (0..100).forEach { frame ->
                    val fraction = frame / 100f
                    val mapped = start + (target - start) * easing.transform(fraction)
                    val expectedVisual = initialVisual +
                        (targetVisual - initialVisual) * PageMotion.Easing.transform(fraction)
                    assertEquals(expectedVisual, PageMotion.Easing.transform(mapped), 0.0001f)
                    assertTrue(mapped >= minOf(start, target) && mapped <= maxOf(start, target))
                    if (target == 0f) assertTrue(mapped <= previous) else assertTrue(mapped >= previous)
                    previous = mapped
                }
                assertEquals(0f, easing.transform(0f), 0f)
                assertEquals(1f, easing.transform(1f), 0f)
            }
        }
    }

    @Test fun retainedContentKeepsTheExistingForwardPresentation() {
        listOf(0f, 0.1f, 0.5f, 0.9f, 1f).forEach { progress ->
            assertEquals(1f - progress, outgoingScope(progress).retainedContentProgress, 0f)
            val incoming = PageTransitionScope(PageTransitionPhase.Incoming, progress, 24f, 1)
            // 子内容原本不另播入场，由外层 elementModifier 负责原有错峰。
            assertEquals(1f, incoming.retainedContentProgress, 0f)
        }
    }

    private fun outgoingScope(progress: Float) =
        PageTransitionScope(PageTransitionPhase.Outgoing, progress, 24f, 1)

    private class AnimationClock : MonotonicFrameClock {
        private val frames = BroadcastFrameClock()
        var timeMillis = 0L
            private set

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
            frames.withFrameNanos(onFrame)

        suspend fun frameAfter(millis: Long) {
            timeMillis += millis
            frames.sendFrame(timeMillis * 1_000_000)
            yield()
        }
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
