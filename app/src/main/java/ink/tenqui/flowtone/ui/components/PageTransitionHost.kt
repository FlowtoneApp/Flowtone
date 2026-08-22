package ink.tenqui.flowtone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first

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
    content: @Composable PageTransitionScope.(T) -> Unit
) {
    // Each slot keeps its identity while it is current and then outgoing.
    // Only the other slot is created for the incoming page.
    var firstPage by remember { mutableStateOf<PageSnapshot<T>?>(PageSnapshot(targetState)) }
    var secondPage by remember { mutableStateOf<PageSnapshot<T>?>(null) }
    var currentSlot by remember { mutableStateOf(PageSlot.First) }
    var transitioning by remember { mutableStateOf(false) }
    var transitionId by remember { mutableStateOf(0) }
    var queuedTarget by remember { mutableStateOf(targetState) }
    val progress = remember { Animatable(1f) }
    val offsetYPx = with(LocalDensity.current) { PageMotion.Offset.toPx() }

    // Transitions are intentionally non-interruptible, but navigation intent is not dropped.
    LaunchedEffect(targetState) {
        queuedTarget = targetState
    }

    LaunchedEffect(Unit) {
        while (true) {
            fun currentPageValue(): T {
                return when (currentSlot) {
                    PageSlot.First -> checkNotNull(firstPage).value
                    PageSlot.Second -> checkNotNull(secondPage).value
                }
            }

            val nextState = snapshotFlow { queuedTarget }
                .first { target -> target != currentPageValue() }
            val outgoingSlot = currentSlot
            val incomingSlot = outgoingSlot.other()
            val incomingPage = PageSnapshot(nextState)
            when (incomingSlot) {
                PageSlot.First -> firstPage = incomingPage
                PageSlot.Second -> secondPage = incomingPage
            }
            transitionId += 1
            transitioning = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = PageMotion.DurationMillis,
                    easing = LinearEasing
                )
            )
            when (outgoingSlot) {
                PageSlot.First -> firstPage = null
                PageSlot.Second -> secondPage = null
            }
            currentSlot = incomingSlot
            transitioning = false
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
