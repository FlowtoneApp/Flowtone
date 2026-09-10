package ink.tenqui.flowtone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal data class PageViewportStaggerSnapshot(
    val transitionId: Int,
    val keys: List<Any>,
    val capturedPageProgress: Float
)

internal fun updatedPageViewportStaggerSnapshot(
    current: PageViewportStaggerSnapshot?,
    phase: PageTransitionPhase,
    transitionId: Int,
    visibleKeys: List<Any>,
    pageProgress: Float
): PageViewportStaggerSnapshot? {
    if (phase == PageTransitionPhase.Current) return null
    if (current?.transitionId == transitionId) return current
    val orderedKeys = visibleKeys.distinct()
    if (orderedKeys.isEmpty()) return current
    return PageViewportStaggerSnapshot(
        transitionId = transitionId,
        keys = orderedKeys,
        capturedPageProgress = pageProgress.coerceIn(0f, 1f)
    )
}

internal fun pageViewportStaggerOrder(
    phase: PageTransitionPhase,
    visibleOrdinal: Int,
    visibleCount: Int
): Int {
    val safeCount = visibleCount.coerceAtLeast(1)
    val safeOrdinal = visibleOrdinal.coerceIn(0, safeCount - 1)
    return if (phase == PageTransitionPhase.Outgoing) {
        safeCount - 1 - safeOrdinal
    } else {
        safeOrdinal
    }
}

internal fun pageViewportStaggerVisualState(
    phase: PageTransitionPhase,
    pageProgress: Float,
    visibleOrdinal: Int,
    visibleCount: Int,
    offsetYPx: Float
): PageElementVisualState {
    if (phase == PageTransitionPhase.Current) {
        return PageElementVisualState(alpha = 1f, translationY = 0f)
    }
    val order = pageViewportStaggerOrder(phase, visibleOrdinal, visibleCount)
    val elementProgress = PageMotion.elementProgress(pageProgress, order, visibleCount)
    return pageElementVisualState(phase, elementProgress, offsetYPx)
}

@Composable
internal fun rememberPageViewportStaggerMotion(
    sessionKey: Any,
    visibleKeys: List<Any>,
    pageTransition: PageTransitionScope
): PageViewportStaggerMotionScope {
    var frozenViewport by remember(sessionKey) {
        mutableStateOf<PageViewportStaggerSnapshot?>(null)
    }
    LaunchedEffect(
        sessionKey,
        pageTransition.transitionId,
        pageTransition.phase,
        visibleKeys
    ) {
        frozenViewport = updatedPageViewportStaggerSnapshot(
            current = frozenViewport,
            phase = pageTransition.phase,
            transitionId = pageTransition.transitionId,
            visibleKeys = visibleKeys,
            pageProgress = pageTransition.progress
        )
    }

    if (pageTransition.phase == PageTransitionPhase.Current) {
        return remember(sessionKey, pageTransition.transitionId) {
            PageViewportStaggerMotionScope.current()
        }
    }

    val activeViewport = frozenViewport
        ?.takeIf { snapshot -> snapshot.transitionId == pageTransition.transitionId }
        ?: visibleKeys.takeIf(List<Any>::isNotEmpty)?.let { keys ->
            PageViewportStaggerSnapshot(
                transitionId = pageTransition.transitionId,
                keys = keys.distinct(),
                capturedPageProgress = pageTransition.progress.coerceIn(0f, 1f)
            )
        }
    val diagnosticSnapshotSource = when {
        frozenViewport?.transitionId == pageTransition.transitionId -> "frozen"
        activeViewport != null -> "live-pending-freeze"
        else -> "awaiting-viewport"
    }
    val motionProgress = if (
        pageTransition.phase == PageTransitionPhase.Incoming && activeViewport != null
    ) {
        val remaining = (1f - activeViewport.capturedPageProgress).coerceAtLeast(0.0001f)
        ((pageTransition.progress - activeViewport.capturedPageProgress) / remaining)
            .coerceIn(0f, 1f)
    } else {
        pageTransition.progress
    }
    val orderByKey = activeViewport?.keys?.withIndex()?.associate { (index, key) ->
        key to index
    }.orEmpty()

    return PageViewportStaggerMotionScope(
        pageTransition = pageTransition,
        orderByKey = orderByKey,
        visibleCount = orderByKey.size,
        pageProgress = motionProgress,
        awaitingViewport = activeViewport == null,
        diagnosticSnapshotSource = diagnosticSnapshotSource
    )
}

internal class PageViewportStaggerMotionScope internal constructor(
    private val pageTransition: PageTransitionScope?,
    private val orderByKey: Map<Any, Int>,
    private val visibleCount: Int,
    private val pageProgress: Float,
    private val awaitingViewport: Boolean,
    private val diagnosticSnapshotSource: String
) {
    internal fun diagnosticSnapshotSource(): String = diagnosticSnapshotSource

    internal fun diagnosticKeys(): List<Any> = orderByKey.entries
        .sortedBy(Map.Entry<Any, Int>::value)
        .map(Map.Entry<Any, Int>::key)

    internal fun diagnosticState(key: Any): PageViewportStaggerDiagnosticState? {
        if (pageTransition == null) {
            return PageViewportStaggerDiagnosticState(
                visibleOrdinal = 0,
                order = 0,
                orderCount = 1,
                pageProgress = 1f,
                awaitingViewport = false
            )
        }
        val visibleOrdinal = orderByKey[key] ?: return null
        return PageViewportStaggerDiagnosticState(
            visibleOrdinal = visibleOrdinal,
            order = pageViewportStaggerOrder(
                phase = pageTransition.phase,
                visibleOrdinal = visibleOrdinal,
                visibleCount = visibleCount
            ),
            orderCount = visibleCount.coerceAtLeast(1),
            pageProgress = pageProgress,
            awaitingViewport = awaitingViewport
        )
    }

    fun itemModifier(key: Any): Modifier {
        val transition = pageTransition ?: return Modifier
        if (awaitingViewport) {
            return if (transition.phase == PageTransitionPhase.Incoming) {
                Modifier.graphicsLayer { alpha = 0f }
            } else {
                Modifier
            }
        }
        val visibleOrdinal = orderByKey[key] ?: return Modifier
        val order = pageViewportStaggerOrder(
            phase = transition.phase,
            visibleOrdinal = visibleOrdinal,
            visibleCount = visibleCount
        )
        return transition.elementModifierAt(
            pageProgress = pageProgress,
            order = order,
            orderCount = visibleCount
        )
    }

    companion object {
        fun current(): PageViewportStaggerMotionScope = PageViewportStaggerMotionScope(
            pageTransition = null,
            orderByKey = emptyMap(),
            visibleCount = 0,
            pageProgress = 1f,
            awaitingViewport = false,
            diagnosticSnapshotSource = "current"
        )
    }
}

internal data class PageViewportStaggerDiagnosticState(
    val visibleOrdinal: Int,
    val order: Int,
    val orderCount: Int,
    val pageProgress: Float,
    val awaitingViewport: Boolean
)
