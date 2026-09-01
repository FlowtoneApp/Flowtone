package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val transitionId: Int
) {
    fun elementModifier(
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount
    ): Modifier = elementModifierAt(progress, order, orderCount)

    fun elementModifierAt(
        pageProgress: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount
    ): Modifier {
        val elementProgress = PageMotion.elementProgress(
            pageProgress = pageProgress,
            order = order,
            orderCount = orderCount
        )
        val alpha = when (phase) {
            PageTransitionPhase.Outgoing -> 1f - elementProgress
            PageTransitionPhase.Incoming,
            PageTransitionPhase.Current -> elementProgress
        }
        val translationY = when (phase) {
            PageTransitionPhase.Outgoing -> offsetYPx * elementProgress
            PageTransitionPhase.Incoming -> offsetYPx * (1f - elementProgress)
            PageTransitionPhase.Current -> 0f
        }
        return Modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
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
        orderCount: Int = PageMotion.DefaultOrderCount
    ): Modifier = elementAppearanceModifierAt(pageProgress, order, orderCount)

    fun elementExitModifierAt(
        pageProgress: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount
    ): Modifier = elementAppearanceModifierAt(pageProgress, order, orderCount)

    fun elementAppearanceModifierAt(
        pageProgress: Float,
        order: Int,
        orderCount: Int = PageMotion.DefaultOrderCount
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
        return elementModifierAt(pageProgress, order, orderCount)
            .blur(PageMotion.PageBlurRadius * blurProgress)
    }

    /** Only page backgrounds use this alpha; content keeps its own element alpha. */
    fun backgroundModifier(): Modifier {
        val alpha = when (phase) {
            PageTransitionPhase.Incoming -> PageMotion.Easing.transform(progress)
            PageTransitionPhase.Outgoing,
            PageTransitionPhase.Current -> 1f
        }
        return Modifier.graphicsLayer { this.alpha = alpha }
    }

    internal fun combineWith(local: PageTransitionScope): PageTransitionScope {
        return when {
            phase == PageTransitionPhase.Outgoing -> this
            local.phase == PageTransitionPhase.Outgoing -> local
            phase == PageTransitionPhase.Incoming -> PageTransitionScope(
                phase = PageTransitionPhase.Incoming,
                progress = minOf(progress, local.progress),
                offsetYPx = offsetYPx,
                transitionId = maxOf(transitionId, local.transitionId)
            )
            local.phase == PageTransitionPhase.Incoming -> local
            else -> PageTransitionScope(
                phase = PageTransitionPhase.Current,
                progress = 1f,
                offsetYPx = offsetYPx,
                transitionId = maxOf(transitionId, local.transitionId)
            )
        }
    }
}

private data class PageSnapshot<T>(val value: T)

private enum class PageSlot {
    First,
    Second;

    fun other(): PageSlot = when (this) {
        First -> Second
        Second -> First
    }
}

@Composable
internal fun <T> PageTransitionHost(
    targetState: T,
    modifier: Modifier = Modifier,
    parentScope: PageTransitionScope? = null,
    reversibleTransitionKey: ((T) -> Any?)? = null,
    isReversibleTransition: ((T, T) -> Boolean)? = null,
    content: @Composable PageTransitionScope.(T) -> Unit
) {
    // Each slot keeps its identity while it is current and then outgoing.
    // Only the other slot is created for the incoming page.
    var firstPage by remember { mutableStateOf<PageSnapshot<T>?>(PageSnapshot(targetState)) }
    var secondPage by remember { mutableStateOf<PageSnapshot<T>?>(null) }
    var currentSlot by remember { mutableStateOf(PageSlot.First) }
    var transitioning by remember { mutableStateOf(false) }
    var transitionId by remember { mutableStateOf(0) }
    val progress = remember { Animatable(1f) }
    val offsetYPx = with(LocalDensity.current) { PageMotion.Offset.toPx() }

    fun pageValue(slot: PageSlot): T {
        return when (slot) {
            PageSlot.First -> checkNotNull(firstPage).value
            PageSlot.Second -> checkNotNull(secondPage).value
        }
    }

    fun samePage(first: T, second: T): Boolean {
        if (first == second) return true
        val keyProvider = reversibleTransitionKey ?: return false
        val firstKey = keyProvider(first) ?: return false
        val secondKey = keyProvider(second) ?: return false
        return firstKey == secondKey
    }

    fun clearPage(slot: PageSlot) {
        when (slot) {
            PageSlot.First -> firstPage = null
            PageSlot.Second -> secondPage = null
        }
    }

    suspend fun animateProgressTo(targetValue: Float) {
        val remainingFraction = abs(targetValue - progress.value)
        if (remainingFraction <= PageTransitionEndpointThreshold) return
        progress.animateTo(
            targetValue = targetValue,
            animationSpec = tween(
                durationMillis = maxOf(
                    1,
                    (PageMotion.DurationMillis * remainingFraction).roundToInt()
                ),
                easing = LinearEasing
            )
        )
    }

    // A new target cancels the current animateTo. Reversible pairs keep both slots and
    // drive the same master progress toward the endpoint represented by the latest target.
    LaunchedEffect(targetState) {
        val requestedTarget = targetState
        while (true) {
            if (!transitioning) {
                if (samePage(requestedTarget, pageValue(currentSlot))) {
                    return@LaunchedEffect
                }

                val outgoingSlot = currentSlot
                val incomingSlot = outgoingSlot.other()
                val incomingPage = PageSnapshot(requestedTarget)
                when (incomingSlot) {
                    PageSlot.First -> firstPage = incomingPage
                    PageSlot.Second -> secondPage = incomingPage
                }
                transitionId += 1
                transitioning = true
                progress.snapTo(0f)
            }

            val outgoingSlot = currentSlot
            val incomingSlot = outgoingSlot.other()
            val outgoingPage = pageValue(outgoingSlot)
            val incomingPage = pageValue(incomingSlot)
            val pairIsReversible =
                isReversibleTransition?.invoke(outgoingPage, incomingPage) == true
            val targetEndpoint = if (
                pairIsReversible && samePage(requestedTarget, outgoingPage)
            ) {
                0f
            } else {
                1f
            }

            animateProgressTo(targetEndpoint)

            if (targetEndpoint == 0f) {
                clearPage(incomingSlot)
            } else {
                clearPage(outgoingSlot)
                currentSlot = incomingSlot
            }
            transitioning = false

            if (samePage(requestedTarget, pageValue(currentSlot))) {
                return@LaunchedEffect
            }
            // A genuinely different third target keeps the previous queued behavior:
            // finish the current pair first, then establish the next pair from its endpoint.
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

        key(slot) {
            val localScope = PageTransitionScope(
                phase = phase,
                progress = if (phase == PageTransitionPhase.Current) {
                    1f
                } else {
                    transitionProgress
                },
                offsetYPx = offsetYPx,
                transitionId = transitionId
            )
            val scope = parentScope?.combineWith(localScope) ?: localScope
            val pageModifier = Modifier
                .fillMaxSize()
                .zIndex(if (phase == PageTransitionPhase.Incoming) 1f else 0f)

            val visualModifier = if (phase == PageTransitionPhase.Outgoing) {
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

    val firstPhase = when {
        !transitioning && currentSlot == PageSlot.First -> PageTransitionPhase.Current
        transitioning && currentSlot == PageSlot.First -> PageTransitionPhase.Outgoing
        transitioning && currentSlot == PageSlot.Second -> PageTransitionPhase.Incoming
        else -> null
    }
    val secondPhase = when {
        !transitioning && currentSlot == PageSlot.Second -> PageTransitionPhase.Current
        transitioning && currentSlot == PageSlot.Second -> PageTransitionPhase.Outgoing
        transitioning && currentSlot == PageSlot.First -> PageTransitionPhase.Incoming
        else -> null
    }

    Box(
        modifier = modifier
    ) {
        RenderPageSlot(PageSlot.First, firstPage, firstPhase)
        RenderPageSlot(PageSlot.Second, secondPage, secondPhase)
    }
}

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
    initiallyEnteredKeys: Set<Any> = emptySet()
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
                            durationMillis = PageMotion.DurationMillis,
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
