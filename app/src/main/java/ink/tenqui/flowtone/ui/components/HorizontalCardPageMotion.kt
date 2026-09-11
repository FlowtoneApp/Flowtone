package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
internal fun rememberHorizontalCardPageMotion(
    sessionKey: Any,
    listState: LazyListState,
    pageTransition: PageTransitionScope
): PageViewportStaggerMotionScope {
    val visibleKeys by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo
                .sortedWith(compareBy({ item -> item.offset }, { item -> item.index }))
                .map { item -> item.key }
                .distinct()
        }
    }
    return rememberPageViewportStaggerMotion(
        sessionKey = sessionKey,
        visibleKeys = visibleKeys,
        pageTransition = pageTransition
    )
}
