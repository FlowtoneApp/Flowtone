package ink.tenqui.flowtone.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.components.PageElementEnterScope
import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.pageTransitionCompensatedAlpha
import ink.tenqui.flowtone.ui.components.rememberPageElementEnterScope

@Composable
internal fun rememberProviderRowEnterScope(
    sessionKey: String,
    rowKeys: List<String>,
    viewportKeys: List<String>,
    initiallyEnteredKeys: Set<String>,
    phase: PageTransitionPhase
): PageElementEnterScope = rememberPageElementEnterScope(
    sessionKey = sessionKey,
    elementKeys = providerRowEnterKeys(rowKeys, phase),
    viewportKeys = viewportKeys,
    awaitViewportKeys = true,
    initiallyEnteredKeys = initiallyEnteredKeys,
    durationMillis = FlowtoneMotion.ShortDurationMillis
)

internal fun providerRowEnterKeys(
    rowKeys: List<String>,
    phase: PageTransitionPhase
): List<String> = if (phase == PageTransitionPhase.Outgoing) emptyList() else rowKeys

@Composable
internal fun rememberProviderSkeletonEnterScope(
    sessionKey: String,
    keys: List<String>,
    phase: PageTransitionPhase
): PageElementEnterScope? {
    val enterWithPage = remember(sessionKey) { phase != PageTransitionPhase.Current }
    return if (enterWithPage) null else rememberProviderRowEnterScope(
        sessionKey = sessionKey,
        rowKeys = keys,
        viewportKeys = keys,
        initiallyEnteredKeys = emptySet(),
        phase = phase
    )
}

@Composable
internal fun providerSkeletonRowModifier(
    enterScope: PageElementEnterScope?,
    rowKey: String,
    pageTransition: PageTransitionScope,
    order: Int,
    orderCount: Int
): Modifier {
    val pageModifier = {
        pageTransition.elementModifier(order, orderCount, translationOffsetScale = 0f)
    }
    return enterScope?.providerRowFadeModifier(
        rowKey, pageTransition, order, orderCount, outgoingModifier = pageModifier
    ) ?: pageModifier()
}

@Composable
internal fun PageElementEnterScope.providerRowFadeModifier(
    rowKey: String,
    pageTransition: PageTransitionScope,
    order: Int,
    orderCount: Int,
    outgoingModifier: () -> Modifier
): Modifier {
    val entryAlpha = elementMotionPresentation(rowKey).alpha
    val rowAlpha = if (pageTransition.phase == PageTransitionPhase.Outgoing) {
        // 入场中途退出时保持同一绘制方式，反向返回后也能接续当前透明度。
        val enteredBeforeExit = remember(pageTransition.transitionId, rowKey) { entryAlpha >= 1f }
        if (enteredBeforeExit) return outgoingModifier()
        val exitProgress = PageMotion.elementProgress(pageTransition.progress, order, orderCount)
        pageTransitionCompensatedAlpha(
            desiredAlpha = entryAlpha * (1f - exitProgress),
            appliedContainerAlpha = pageTransition.appliedContainerAlpha
        )
    } else entryAlpha
    return if (rowAlpha < 1f) Modifier.graphicsLayer { alpha = rowAlpha } else Modifier
}
