package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class PageTransitionPhase {
    Current,
    Outgoing,
    Incoming
}

internal class PageTransitionScope internal constructor(
    val phase: PageTransitionPhase,
    val progress: Float,
    private val offsetYPx: Float,
    val transitionId: Int,
    internal val appliedContainerAlpha: Float = 1f
) {
    fun elementModifier(
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount,
        translationOffsetScale: Float = 1f
    ): Modifier = elementModifierAt(progress, order, orderCount, translationOffsetScale)

    fun elementModifierAt(
        pageProgress: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount,
        translationOffsetScale: Float = 1f
    ): Modifier {
        val elementProgress = PageMotion.elementProgress(
            pageProgress = pageProgress,
            order = order,
            orderCount = orderCount
        )
        val visualState = pageElementVisualState(
            phase = phase,
            elementProgress = elementProgress,
            signedOffsetYPx = offsetYPx * translationOffsetScale
        )
        return Modifier.graphicsLayer {
            alpha = pageTransitionCompensatedAlpha(
                desiredAlpha = visualState.alpha,
                appliedContainerAlpha = appliedContainerAlpha
            )
            translationY = visualState.translationY
        }
    }

    /**
     * The full incoming element presentation: the standard staggered alpha/translation plus
     * the same blur radius used by page transitions. Dynamic content uses this instead of
     * recreating individual motion channels.
     */
    fun elementEnterModifierAt(
        pageProgress: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount,
        translationOffsetScale: Float = 1f
    ): Modifier = elementAppearanceModifierAt(
        pageProgress,
        order,
        orderCount,
        translationOffsetScale
    )

    fun elementExitModifierAt(
        pageProgress: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount,
        translationOffsetScale: Float = 1f
    ): Modifier = elementAppearanceModifierAt(
        pageProgress,
        order,
        orderCount,
        translationOffsetScale
    )

    fun elementAppearanceModifierAt(
        pageProgress: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount,
        translationOffsetScale: Float = 1f
    ): Modifier {
        val elementProgress = PageMotion.elementProgress(
            pageProgress = pageProgress,
            order = order,
            orderCount = orderCount
        )
        val blurProgress = when (phase) {
            PageTransitionPhase.Incoming -> 1f - elementProgress
            PageTransitionPhase.Outgoing -> elementProgress
            PageTransitionPhase.Current -> 0f
        }
        return elementModifierAt(pageProgress, order, orderCount, translationOffsetScale)
            .blur(
                radius = PageMotion.PageBlurRadius * blurProgress,
                edgeTreatment = BlurredEdgeTreatment.Unbounded
            )
    }

    /** Keeps the canonical background alpha after the Host's slot presentation is applied. */
    fun backgroundModifier(): Modifier {
        val desiredAlpha = when (phase) {
            PageTransitionPhase.Incoming -> PageMotion.Easing.transform(progress)
            PageTransitionPhase.Outgoing,
            PageTransitionPhase.Current -> 1f
        }
        return Modifier.graphicsLayer {
            alpha = pageTransitionCompensatedAlpha(
                desiredAlpha = desiredAlpha,
                appliedContainerAlpha = appliedContainerAlpha
            )
        }
    }

    internal fun combineWith(local: PageTransitionScope): PageTransitionScope {
        val combinedContainerAlpha =
            appliedContainerAlpha * local.appliedContainerAlpha
        return when {
            phase == PageTransitionPhase.Outgoing -> PageTransitionScope(
                phase = phase,
                progress = progress,
                offsetYPx = offsetYPx,
                transitionId = maxOf(transitionId, local.transitionId),
                appliedContainerAlpha = combinedContainerAlpha
            )
            local.phase == PageTransitionPhase.Outgoing -> PageTransitionScope(
                phase = local.phase,
                progress = local.progress,
                offsetYPx = local.offsetYPx,
                transitionId = maxOf(transitionId, local.transitionId),
                appliedContainerAlpha = combinedContainerAlpha
            )
            phase == PageTransitionPhase.Incoming -> PageTransitionScope(
                phase = PageTransitionPhase.Incoming,
                progress = minOf(progress, local.progress),
                offsetYPx = offsetYPx,
                transitionId = maxOf(transitionId, local.transitionId),
                appliedContainerAlpha = combinedContainerAlpha
            )
            local.phase == PageTransitionPhase.Incoming -> PageTransitionScope(
                phase = local.phase,
                progress = local.progress,
                offsetYPx = local.offsetYPx,
                transitionId = maxOf(transitionId, local.transitionId),
                appliedContainerAlpha = combinedContainerAlpha
            )
            else -> PageTransitionScope(
                phase = PageTransitionPhase.Current,
                progress = 1f,
                offsetYPx = offsetYPx,
                transitionId = maxOf(transitionId, local.transitionId),
                appliedContainerAlpha = combinedContainerAlpha
            )
        }
    }
}

internal fun pageTransitionSlotAlpha(
    phase: PageTransitionPhase,
    progress: Float
): Float = when (phase) {
    PageTransitionPhase.Incoming -> PageMotion.Easing.transform(progress.coerceIn(0f, 1f))
    PageTransitionPhase.Outgoing,
    PageTransitionPhase.Current -> 1f
}

internal fun pageTransitionCompensatedAlpha(
    desiredAlpha: Float,
    appliedContainerAlpha: Float
): Float {
    val desired = desiredAlpha.coerceIn(0f, 1f)
    val applied = appliedContainerAlpha.coerceIn(0f, 1f)
    if (desired <= 0f || applied <= 0f) return 0f
    return (desired / applied).coerceIn(0f, 1f)
}

internal data class PageElementVisualState(
    val alpha: Float,
    val translationY: Float
)

internal fun pageElementVisualState(
    phase: PageTransitionPhase,
    elementProgress: Float,
    signedOffsetYPx: Float
): PageElementVisualState {
    val progress = elementProgress.coerceIn(0f, 1f)
    return PageElementVisualState(
        alpha = when (phase) {
            PageTransitionPhase.Outgoing -> 1f - progress
            PageTransitionPhase.Incoming,
            PageTransitionPhase.Current -> progress
        },
        translationY = when (phase) {
            PageTransitionPhase.Outgoing -> signedOffsetYPx * progress
            PageTransitionPhase.Incoming -> signedOffsetYPx * (1f - progress)
            PageTransitionPhase.Current -> 0f
        }
    )
}

internal data class PageTransitionPresentation(
    val phase: PageTransitionPhase,
    val progress: Float,
    val transitionId: Int,
    private val appliedContainerAlpha: Float = 1f
) {
    fun elementAppearanceModifier(
        offsetYPx: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount,
        translationOffsetScale: Float = 1f
    ): Modifier = PageTransitionScope(
        phase = phase,
        progress = progress,
        offsetYPx = offsetYPx,
        transitionId = transitionId,
        appliedContainerAlpha = appliedContainerAlpha
    ).elementAppearanceModifierAt(
        pageProgress = progress,
        order = order,
        orderCount = orderCount,
        translationOffsetScale = translationOffsetScale
    )
}

internal fun PageTransitionScope.presentation(): PageTransitionPresentation =
    PageTransitionPresentation(phase, progress, transitionId, appliedContainerAlpha)

internal data class PageSnapshot<T>(
    val value: T,
    val identity: Int
)

internal data class PageTransitionSlots<T>(
    val current: T? = null,
    val outgoing: T? = null,
    val incoming: T? = null,
    val progress: Float = 1f,
    val transitionId: Int = 0
)

internal enum class PageTransitionRetargetAction {
    KeepIncoming,
    ReverseToOutgoing,
    ReplaceIncoming
}

internal fun <T> pageTransitionRetargetAction(
    requestedTarget: T,
    outgoing: T,
    incoming: T,
    samePage: (T, T) -> Boolean
): PageTransitionRetargetAction = when {
    samePage(requestedTarget, outgoing) -> PageTransitionRetargetAction.ReverseToOutgoing
    samePage(requestedTarget, incoming) -> PageTransitionRetargetAction.KeepIncoming
    else -> PageTransitionRetargetAction.ReplaceIncoming
}

internal fun pageTransitionTargetEndpoint(action: PageTransitionRetargetAction): Float =
    when (action) {
        PageTransitionRetargetAction.ReverseToOutgoing -> 0f
        PageTransitionRetargetAction.KeepIncoming,
        PageTransitionRetargetAction.ReplaceIncoming -> 1f
    }

internal fun pageTransitionDurationMillis(
    currentProgress: Float,
    targetProgress: Float
): Int = maxOf(
    1,
    (PageMotion.DurationMillis *
        pageTransitionRemainingFraction(currentProgress, targetProgress)).roundToInt()
)

internal fun pageTransitionEndpointReached(
    currentProgress: Float,
    targetProgress: Float
): Boolean = currentProgress == targetProgress

internal fun <T> pageTransitionEndpointStillRequested(
    targetProgress: Float,
    latestTarget: T,
    outgoing: T,
    incoming: T,
    samePage: (T, T) -> Boolean
): Boolean = if (targetProgress == 0f) {
    samePage(latestTarget, outgoing)
} else {
    samePage(latestTarget, incoming)
}

internal fun <T> pageTransitionCanCommitPair(
    animationTransitionId: Int,
    activeTransitionId: Int,
    animationGeneration: Any,
    activeGeneration: Any,
    currentProgress: Float,
    targetProgress: Float,
    latestTarget: T,
    outgoing: T,
    incoming: T,
    samePage: (T, T) -> Boolean
): Boolean = animationTransitionId == activeTransitionId &&
    animationGeneration === activeGeneration &&
    pageTransitionEndpointReached(currentProgress, targetProgress) &&
    pageTransitionEndpointStillRequested(
        targetProgress = targetProgress,
        latestTarget = latestTarget,
        outgoing = outgoing,
        incoming = incoming,
        samePage = samePage
    )

internal enum class PageSlot {
    First,
    Second;

    fun other(): PageSlot = when (this) {
        First -> Second
        Second -> First
    }
}

internal data class PageTransitionPairState<T>(
    val firstPage: PageSnapshot<T>?,
    val secondPage: PageSnapshot<T>?,
    val currentSlot: PageSlot,
    val transitioning: Boolean,
    val transitionId: Int,
    val nextSnapshotIdentity: Int
) {
    fun page(slot: PageSlot): PageSnapshot<T>? = when (slot) {
        PageSlot.First -> firstPage
        PageSlot.Second -> secondPage
    }

    fun pageValue(slot: PageSlot): T = checkNotNull(page(slot)).value

    fun begin(requestedTarget: T): PageTransitionPairState<T> {
        check(!transitioning)
        val incomingSlot = currentSlot.other()
        val snapshot = PageSnapshot(requestedTarget, nextSnapshotIdentity)
        return withPage(incomingSlot, snapshot).copy(
            transitioning = true,
            transitionId = transitionId + 1,
            nextSnapshotIdentity = nextSnapshotIdentity + 1
        )
    }

    fun replaceIncoming(requestedTarget: T): PageTransitionPairState<T> {
        check(transitioning)
        val incomingSlot = currentSlot.other()
        val snapshot = PageSnapshot(requestedTarget, nextSnapshotIdentity)
        return withPage(incomingSlot, snapshot).copy(
            transitionId = transitionId + 1,
            nextSnapshotIdentity = nextSnapshotIdentity + 1
        )
    }

    fun commit(targetEndpoint: Float): PageTransitionPairState<T> {
        check(transitioning)
        val incomingSlot = currentSlot.other()
        return if (targetEndpoint == 0f) {
            withPage(incomingSlot, null).copy(transitioning = false)
        } else {
            withPage(currentSlot, null).copy(
                currentSlot = incomingSlot,
                transitioning = false
            )
        }
    }

    fun phaseFor(slot: PageSlot): PageTransitionPhase? = when {
        !transitioning && currentSlot == slot -> PageTransitionPhase.Current
        transitioning && currentSlot == slot -> PageTransitionPhase.Outgoing
        transitioning && currentSlot.other() == slot -> PageTransitionPhase.Incoming
        else -> null
    }

    fun slots(progress: Float): PageTransitionSlots<T> = if (transitioning) {
        PageTransitionSlots(
            outgoing = pageValue(currentSlot),
            incoming = pageValue(currentSlot.other()),
            progress = progress,
            transitionId = transitionId
        )
    } else {
        PageTransitionSlots(
            current = pageValue(currentSlot),
            progress = 1f,
            transitionId = transitionId
        )
    }

    private fun withPage(
        slot: PageSlot,
        snapshot: PageSnapshot<T>?
    ): PageTransitionPairState<T> = when (slot) {
        PageSlot.First -> copy(firstPage = snapshot)
        PageSlot.Second -> copy(secondPage = snapshot)
    }

    companion object {
        fun <T> initial(targetState: T): PageTransitionPairState<T> =
            PageTransitionPairState(
                firstPage = PageSnapshot(targetState, identity = 0),
                secondPage = null,
                currentSlot = PageSlot.First,
                transitioning = false,
                transitionId = 0,
                nextSnapshotIdentity = 1
            )
    }
}

private data class PageTransitionTargetRequest<T>(
    val target: T,
    val generation: Any
)

@Composable
internal fun <T> PageTransitionHost(
    targetState: T,
    modifier: Modifier = Modifier,
    parentScope: PageTransitionScope? = null,
    reversibleTransitionKey: ((T) -> Any?)? = null,
    outgoingPageBlurEnabled: (T) -> Boolean = { true },
    onSlotsChanged: (PageTransitionSlots<T>) -> Unit = {},
    content: @Composable PageTransitionScope.(T) -> Unit
) {
    // Pair mutations are atomic: a composition can observe either the stable endpoint or a
    // complete outgoing/incoming pair, never an intermediate half-cleared slot set.
    var pairState by remember {
        mutableStateOf(PageTransitionPairState.initial(targetState))
    }
    val progress = remember { Animatable(1f) }
    val targetGeneration = remember(targetState) { Any() }
    val latestTargetRequest = rememberUpdatedState(
        PageTransitionTargetRequest(targetState, targetGeneration)
    )
    val offsetYPx = with(LocalDensity.current) { PageMotion.Offset.toPx() }

    fun samePage(first: T, second: T): Boolean {
        if (first == second) return true
        val keyProvider = reversibleTransitionKey ?: return false
        val firstKey = keyProvider(first) ?: return false
        val secondKey = keyProvider(second) ?: return false
        return firstKey == secondKey
    }

    suspend fun animateProgressTo(targetValue: Float) {
        val remainingFraction = pageTransitionRemainingFraction(progress.value, targetValue)
        if (remainingFraction <= PageTransitionEndpointThreshold) {
            progress.snapTo(targetValue)
            return
        }
        progress.animateTo(
            targetValue = targetValue,
            animationSpec = tween(
                durationMillis = pageTransitionDurationMillis(progress.value, targetValue),
                easing = LinearEasing
            )
        )
    }

    // A new request cancels animateTo, but not the retained pair. Reverse navigation therefore
    // drives the same Animatable from its current value toward the opposite endpoint.
    LaunchedEffect(targetGeneration) {
        val requestedTarget = targetState
        while (true) {
            if (!pairState.transitioning) {
                if (samePage(requestedTarget, pairState.pageValue(pairState.currentSlot))) {
                    return@LaunchedEffect
                }
                pairState = pairState.begin(requestedTarget)
                progress.snapTo(0f)
            }

            val outgoingSlot = pairState.currentSlot
            val incomingSlot = outgoingSlot.other()
            val retargetAction = pageTransitionRetargetAction(
                requestedTarget = requestedTarget,
                outgoing = pairState.pageValue(outgoingSlot),
                incoming = pairState.pageValue(incomingSlot),
                samePage = ::samePage
            )
            if (retargetAction == PageTransitionRetargetAction.ReplaceIncoming) {
                pairState = pairState.replaceIncoming(requestedTarget)
                progress.snapTo(0f)
            }
            val targetEndpoint = pageTransitionTargetEndpoint(retargetAction)
            val animationTransitionId = pairState.transitionId
            val animationGeneration = targetGeneration

            animateProgressTo(targetEndpoint)

            val activeRequest = latestTargetRequest.value
            val activePair = pairState
            if (
                !pageTransitionCanCommitPair(
                    animationTransitionId = animationTransitionId,
                    activeTransitionId = activePair.transitionId,
                    animationGeneration = animationGeneration,
                    activeGeneration = activeRequest.generation,
                    currentProgress = progress.value,
                    targetProgress = targetEndpoint,
                    latestTarget = activeRequest.target,
                    outgoing = activePair.pageValue(activePair.currentSlot),
                    incoming = activePair.pageValue(activePair.currentSlot.other()),
                    samePage = ::samePage
                )
            ) {
                return@LaunchedEffect
            }

            pairState = activePair.commit(targetEndpoint)

            if (samePage(requestedTarget, pairState.pageValue(pairState.currentSlot))) {
                return@LaunchedEffect
            }
            // A target that changes after endpoint cleanup starts a fresh pair here.
        }
    }

    val transitionProgress = progress.value

    @Composable
    fun RenderPageSlot(
        slot: PageSlot,
        snapshot: PageSnapshot<T>?,
        phase: PageTransitionPhase?
    ) {
        if (snapshot == null || phase == null) return

        key(slot, snapshot.identity) {
            val localProgress = if (phase == PageTransitionPhase.Current) {
                1f
            } else {
                transitionProgress
            }
            val slotAlpha = if (
                parentScope == null || parentScope.phase == PageTransitionPhase.Current
            ) {
                pageTransitionSlotAlpha(phase, localProgress)
            } else {
                // The active parent transition owns the subtree's container presentation.
                1f
            }
            val localScope = PageTransitionScope(
                phase = phase,
                progress = localProgress,
                offsetYPx = offsetYPx,
                transitionId = pairState.transitionId,
                appliedContainerAlpha = slotAlpha
            )
            val scope = parentScope?.combineWith(localScope) ?: localScope
            val pageModifier = Modifier
                .fillMaxSize()
                .zIndex(if (phase == PageTransitionPhase.Incoming) 1f else 0f)
                .graphicsLayer { alpha = slotAlpha }

            val visualModifier = if (
                phase == PageTransitionPhase.Outgoing &&
                outgoingPageBlurEnabled(snapshot.value)
            ) {
                pageModifier.blur(
                    PageMotion.PageBlurRadius *
                        PageMotion.Easing.transform(transitionProgress)
                )
            } else {
                pageModifier
            }
            // Keep the page content in one Compose group while Current becomes Outgoing.
            // Only its visual modifier changes, preserving remember/LazyListState identity.
            Box(modifier = visualModifier) {
                content(scope, snapshot.value)
            }
        }
    }

    val firstPhase = pairState.phaseFor(PageSlot.First)
    val secondPhase = pairState.phaseFor(PageSlot.Second)
    val slots = pairState.slots(transitionProgress)
    SideEffect { onSlotsChanged(slots) }

    Box(
        modifier = modifier
    ) {
        RenderPageSlot(PageSlot.First, pairState.firstPage, firstPhase)
        RenderPageSlot(PageSlot.Second, pairState.secondPage, secondPhase)
    }
}

internal fun pageTransitionRemainingFraction(currentProgress: Float, targetProgress: Float): Float =
    abs(targetProgress - currentProgress)

private const val PageTransitionEndpointThreshold = 0.0001f

@Composable
internal fun PageTransitionElement(
    scope: PageTransitionScope,
    order: Int,
    orderCount: Int = PageMotion.DefaultOrderCount,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.then(scope.elementModifier(order, orderCount))
    ) {
        content()
    }
}

/**
 * Reuses the page-element motion for content that arrives after a page has become Current.
 * State is intentionally scoped to a caller-provided presentation session rather than a Lazy
 * item, so disposal outside the viewport does not replay an item's first appearance.
 */
@Composable
internal fun rememberPageElementEnterScope(
    sessionKey: Any,
    elementKeys: List<Any>,
    viewportKeys: List<Any> = emptyList(),
    awaitViewportKeys: Boolean = false,
    initiallyEnteredKeys: Set<Any> = emptySet(),
    durationMillis: Int = PageMotion.DurationMillis
): PageElementEnterScope {
    val density = LocalDensity.current
    val offsetYPx = with(density) { PageMotion.Offset.toPx() }
    val enteredKeys = remember(sessionKey) {
        mutableStateMapOf<Any, Unit>().apply {
            initiallyEnteredKeys.forEach { key -> this[key] = Unit }
        }
    }
    val activeBatchesByKey = remember(sessionKey) {
        mutableStateMapOf<Any, PageElementEnterBatch>()
    }
    val latestElementKeys = rememberUpdatedState(elementKeys.distinct())
    val latestViewportKeys = rememberUpdatedState(viewportKeys.distinct())

    LaunchedEffect(sessionKey) {
        snapshotFlow { latestElementKeys.value to latestViewportKeys.value }
            .collect { (currentKeys, currentViewportKeys) ->
                if (awaitViewportKeys && currentKeys.isNotEmpty() && currentViewportKeys.isEmpty()) {
                    return@collect
                }
                val newKeys = pageElementKeysAwaitingEntry(
                    elementKeys = currentKeys,
                    enteredKeys = enteredKeys.keys,
                    activeKeys = activeBatchesByKey.keys
                )
                if (newKeys.isEmpty()) return@collect

                val staggerKeys = pageElementStaggerKeys(
                    newKeys = newKeys,
                    viewportKeys = currentViewportKeys
                )

                val batch = PageElementEnterBatch(
                    keys = newKeys,
                    orderByKey = staggerKeys.withIndex().associate { (index, key) -> key to index },
                    orderCount = staggerKeys.size.coerceAtLeast(1),
                    progress = Animatable(0f)
                )
                newKeys.forEach { key -> activeBatchesByKey[key] = batch }

                launch {
                    batch.progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = durationMillis.coerceAtLeast(1),
                            easing = LinearEasing
                        )
                    )
                    batch.keys.forEach { key ->
                        if (activeBatchesByKey[key] === batch) {
                            activeBatchesByKey.remove(key)
                            enteredKeys[key] = Unit
                        }
                    }
                }
            }
    }

    return remember(sessionKey, offsetYPx) {
        PageElementEnterScope(
            enteredKeys = enteredKeys,
            activeBatchesByKey = activeBatchesByKey,
            offsetYPx = offsetYPx
        )
    }
}

internal class PageElementEnterScope internal constructor(
    private val enteredKeys: SnapshotStateMap<Any, Unit>,
    private val activeBatchesByKey: SnapshotStateMap<Any, PageElementEnterBatch>,
    private val offsetYPx: Float
) {
    fun markEntered(keys: Collection<Any>) {
        keys.forEach { key ->
            activeBatchesByKey.remove(key)
            enteredKeys[key] = Unit
        }
    }

    fun elementModifier(key: Any): Modifier {
        if (key in enteredKeys) return Modifier

        val batch = activeBatchesByKey[key]
        val progress = batch?.progress?.value ?: 0f
        val orderCount = batch?.orderCount ?: 1
        val order = batch?.orderByKey?.get(key) ?: orderCount - 1
        return PageTransitionScope(
            phase = PageTransitionPhase.Incoming,
            progress = progress,
            offsetYPx = offsetYPx,
            transitionId = 0
        ).elementEnterModifierAt(progress, order, orderCount)
    }

    fun elementMotionModifier(key: Any): Modifier {
        if (key in enteredKeys) return Modifier

        val batch = activeBatchesByKey[key]
        val progress = batch?.progress?.value ?: 0f
        val orderCount = batch?.orderCount ?: 1
        val order = batch?.orderByKey?.get(key) ?: orderCount - 1
        return PageTransitionScope(
            phase = PageTransitionPhase.Incoming,
            progress = progress,
            offsetYPx = offsetYPx,
            transitionId = 0
        ).elementModifierAt(progress, order, orderCount)
    }
}

internal class PageElementEnterBatch(
    val keys: List<Any>,
    val orderByKey: Map<Any, Int>,
    val orderCount: Int,
    val progress: Animatable<Float, AnimationVector1D>
)

internal fun pageElementKeysAwaitingEntry(
    elementKeys: List<Any>,
    enteredKeys: Set<Any>,
    activeKeys: Set<Any>
): List<Any> = elementKeys.distinct().filter { key ->
    key !in enteredKeys && key !in activeKeys
}

internal fun pageElementStaggerKeys(
    newKeys: List<Any>,
    viewportKeys: List<Any>
): List<Any> {
    val newKeySet = newKeys.toSet()
    val visibleNewKeys = viewportKeys.distinct().filter(newKeySet::contains)
    return visibleNewKeys.ifEmpty {
        newKeys.take(PageMotion.DefaultOrderCount)
    }
}

@Composable
internal fun rememberPageElementExitScope(
    sessionKey: Any,
    elementKeys: List<Any>,
    viewportKeys: List<Any> = emptyList(),
    onFinished: () -> Unit
): PageElementExitScope {
    val density = LocalDensity.current
    val offsetYPx = with(density) { PageMotion.Offset.toPx() }
    val progress = remember(sessionKey) { Animatable(0f) }
    val latestOnFinished = rememberUpdatedState(onFinished)
    val staggerKeys = remember(elementKeys, viewportKeys) {
        pageElementStaggerKeys(elementKeys, viewportKeys)
    }
    val orderByKey = remember(staggerKeys) {
        staggerKeys.withIndex().associate { (index, key) -> key to index }
    }

    LaunchedEffect(sessionKey) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = PageMotion.DurationMillis,
                easing = LinearEasing
            )
        )
        latestOnFinished.value()
    }

    return remember(sessionKey, offsetYPx, orderByKey, staggerKeys) {
        PageElementExitScope(
            progress = progress,
            orderByKey = orderByKey,
            orderCount = staggerKeys.size.coerceAtLeast(1),
            offsetYPx = offsetYPx
        )
    }
}

internal class PageElementExitScope internal constructor(
    private val progress: Animatable<Float, AnimationVector1D>,
    private val orderByKey: Map<Any, Int>,
    private val orderCount: Int,
    private val offsetYPx: Float
) {
    fun elementModifier(key: Any): Modifier {
        val order = orderByKey[key] ?: orderCount - 1
        return PageTransitionScope(
            phase = PageTransitionPhase.Outgoing,
            progress = progress.value,
            offsetYPx = offsetYPx,
            transitionId = 0
        ).elementExitModifierAt(progress.value, order, orderCount)
    }

    fun hiddenModifier(): Modifier = Modifier.graphicsLayer { alpha = 0f }
}

internal data class PageElementKeyDiff(
    val retainedKeys: Set<Any>,
    val enteringKeys: List<Any>,
    val exitingKeys: List<Any>
)

internal fun pageElementKeyDiff(
    previousKeys: List<Any>,
    currentKeys: List<Any>
): PageElementKeyDiff {
    val previousKeySet = previousKeys.toSet()
    val currentKeySet = currentKeys.toSet()
    return PageElementKeyDiff(
        retainedKeys = previousKeySet.intersect(currentKeySet),
        enteringKeys = currentKeys.distinct().filterNot(previousKeySet::contains),
        exitingKeys = previousKeys.distinct().filterNot(currentKeySet::contains)
    )
}
