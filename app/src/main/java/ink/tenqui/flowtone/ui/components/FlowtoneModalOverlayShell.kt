package ink.tenqui.flowtone.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal class FlowtoneModalOverlayHostState {
    var entries by mutableStateOf<List<FlowtoneModalOverlayEntry>>(emptyList())
        private set

    var cloudAccent by mutableStateOf<Color?>(null)
        private set

    private var nextEntryId = 0L

    fun updateCloudAccent(color: Color) {
        cloudAccent = color
    }

    fun showOrUpdate(
        entryId: Long?,
        visible: Boolean,
        content: @Composable BoxWithConstraintsScope.() -> Unit,
        onDismissRequest: () -> Unit,
        onExitAnimationFinished: () -> Unit,
        scrimMaxAlpha: Float,
        panelStartScale: Float,
        panelExitScale: Float,
        shadowSafePadding: Dp,
        transitionKey: Any?
    ): Long? {
        val existing = entryId?.let { id -> entries.firstOrNull { it.id == id } }
        if (!visible) {
            existing?.let {
                it.visible = false
                it.content = content
                it.onDismissRequest = onDismissRequest
                it.onExitAnimationFinished = onExitAnimationFinished
                it.scrimMaxAlpha = scrimMaxAlpha
                it.panelStartScale = panelStartScale
                it.panelExitScale = panelExitScale
                it.shadowSafePadding = shadowSafePadding
                it.transitionKey = transitionKey
            }
            return existing?.id
        }

        if (existing != null) {
            existing.visible = true
            existing.content = content
            existing.onDismissRequest = onDismissRequest
            existing.onExitAnimationFinished = null
            existing.scrimMaxAlpha = scrimMaxAlpha
            existing.panelStartScale = panelStartScale
            existing.panelExitScale = panelExitScale
            existing.shadowSafePadding = shadowSafePadding
            if (existing.transitionKey != transitionKey) {
                existing.transitionKey = transitionKey
                existing.restartEnterAnimation = true
            }
            return existing.id
        }

        val newEntry = FlowtoneModalOverlayEntry(
            id = nextEntryId++,
            visible = true,
            content = content,
            onDismissRequest = onDismissRequest,
            scrimMaxAlpha = scrimMaxAlpha,
            panelStartScale = panelStartScale,
            panelExitScale = panelExitScale,
            shadowSafePadding = shadowSafePadding,
            transitionKey = transitionKey
        )
        entries = entries + newEntry
        return newEntry.id
    }

    fun detach(entryId: Long) {
        entries.firstOrNull { it.id == entryId }?.let { entry ->
            if (entry.visible) {
                entry.visible = false
                entry.onExitAnimationFinished = null
            }
        }
    }

    fun finishExit(entryId: Long) {
        val entry = entries.firstOrNull { it.id == entryId } ?: return
        if (entry.visible) return
        entries = entries.filterNot { it.id == entryId }
        entry.onExitAnimationFinished?.invoke()
    }
}

internal class FlowtoneModalOverlayEntry(
    val id: Long,
    visible: Boolean,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
    onDismissRequest: () -> Unit,
    scrimMaxAlpha: Float,
    panelStartScale: Float,
    panelExitScale: Float,
    shadowSafePadding: Dp,
    transitionKey: Any?
) {
    var visible by mutableStateOf(visible)
    var content by mutableStateOf(content)
    var onDismissRequest by mutableStateOf(onDismissRequest)
    var onExitAnimationFinished by mutableStateOf<(() -> Unit)?>(null)
    var scrimMaxAlpha by mutableStateOf(scrimMaxAlpha)
    var panelStartScale by mutableStateOf(panelStartScale)
    var panelExitScale by mutableStateOf(panelExitScale)
    var shadowSafePadding by mutableStateOf(shadowSafePadding)
    var transitionKey by mutableStateOf(transitionKey)
    var restartEnterAnimation by mutableStateOf(false)
    val progress = Animatable(0f)
}

internal val LocalFlowtoneModalOverlayHost =
    staticCompositionLocalOf<FlowtoneModalOverlayHostState?> { null }

internal val LocalFlowtoneModalCloudAccent =
    staticCompositionLocalOf<Color?> { null }

@Composable
internal fun FlowtoneModalOverlayHost(
    state: FlowtoneModalOverlayHostState,
    modifier: Modifier = Modifier
) {
    val cloudAccent = state.cloudAccent ?: MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalFlowtoneModalCloudAccent provides cloudAccent) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val hostScope = this
            state.entries.forEach { entry ->
                key(entry.id) {
                    val visible = entry.visible
                    val transitionKey = entry.transitionKey
                    LaunchedEffect(entry.id, visible, transitionKey) {
                        if (visible && entry.restartEnterAnimation) {
                            entry.restartEnterAnimation = false
                            entry.progress.snapTo(0f)
                        }
                        entry.progress.animateTo(
                            targetValue = if (visible) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = if (visible) {
                                    FlowtoneMotion.DurationMillis
                                } else {
                                    FlowtoneMotion.DurationMillis / 2
                                },
                                easing = FlowtoneMotion.Easing
                            )
                        )
                        if (!visible) state.finishExit(entry.id)
                    }
                    val progress = entry.progress.value.coerceIn(0f, 1f)
                    if (visible || progress > 0.001f) {
                        FlowtoneModalOverlayLayer(
                            progress = progress,
                            closing = !visible,
                            scrimMaxAlpha = entry.scrimMaxAlpha,
                            panelStartScale = entry.panelStartScale,
                            panelExitScale = entry.panelExitScale,
                            shadowSafePadding = entry.shadowSafePadding,
                            onDismissRequest = {
                                if (entry.visible) entry.onDismissRequest()
                            },
                            modifier = Modifier.fillMaxSize(),
                            content = { entry.content(hostScope) }
                        )
                    }
                }
            }
        }
    }
}

private const val FlowtoneModalPanelStartScale = 0.96f
private const val FlowtoneModalPanelExitScale = 0.98f
internal const val FlowtoneModalDefaultScrimAlpha = 0.42f

@Composable
internal fun FlowtoneModalOverlayShell(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimMaxAlpha: Float = FlowtoneModalDefaultScrimAlpha,
    panelStartScale: Float = FlowtoneModalPanelStartScale,
    panelExitScale: Float = FlowtoneModalPanelExitScale,
    shadowSafePadding: Dp = 12.dp,
    onExitAnimationFinished: () -> Unit = {},
    transitionKey: Any? = null,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    val host = LocalFlowtoneModalOverlayHost.current
    val entryIdState = remember(host) { mutableStateOf<Long?>(null) }
    val latestContent = rememberUpdatedState(content)
    val latestOnDismissRequest = rememberUpdatedState(onDismissRequest)
    val latestOnExitAnimationFinished = rememberUpdatedState(onExitAnimationFinished)
    val registeredContent: @Composable BoxWithConstraintsScope.() -> Unit = remember(host) {
        { latestContent.value.invoke(this) }
    }
    val registeredDismissRequest: () -> Unit = remember(host) {
        { latestOnDismissRequest.value.invoke() }
    }
    val registeredExitFinished: () -> Unit = remember(host) {
        { latestOnExitAnimationFinished.value.invoke() }
    }

    BackHandler(enabled = visible, onBack = onDismissRequest)

    if (host == null) {
        val progress by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) {
                    FlowtoneMotion.DurationMillis
                } else {
                    FlowtoneMotion.DurationMillis / 2
                },
                easing = FlowtoneMotion.Easing
            ),
            label = "FlowtoneModalProgress"
        )
        val safeProgress = progress.coerceIn(0f, 1f)
        if (visible || safeProgress > 0.001f) {
            FlowtoneModalOverlayLayer(
                progress = safeProgress,
                closing = !visible,
                scrimMaxAlpha = scrimMaxAlpha,
                panelStartScale = panelStartScale,
                panelExitScale = panelExitScale,
                shadowSafePadding = shadowSafePadding,
                onDismissRequest = {
                    if (visible) onDismissRequest()
                },
                modifier = modifier,
                content = content
            )
        }
    } else {
        DisposableEffect(host) {
            onDispose { entryIdState.value?.let(host::detach) }
        }
        SideEffect {
            entryIdState.value = host.showOrUpdate(
                entryId = entryIdState.value,
                visible = visible,
                content = registeredContent,
                onDismissRequest = registeredDismissRequest,
                onExitAnimationFinished = registeredExitFinished,
                scrimMaxAlpha = scrimMaxAlpha,
                panelStartScale = panelStartScale,
                panelExitScale = panelExitScale,
                shadowSafePadding = shadowSafePadding,
                transitionKey = transitionKey
            )
        }
    }
}

@Composable
private fun FlowtoneModalOverlayLayer(
    progress: Float,
    closing: Boolean,
    scrimMaxAlpha: Float,
    panelStartScale: Float,
    panelExitScale: Float,
    shadowSafePadding: Dp,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val clampedProgress = progress.coerceIn(0f, 1f)
    val panelScale = if (closing) {
        panelExitScale + (1f - panelExitScale) * clampedProgress
    } else {
        panelStartScale + (1f - panelStartScale) * clampedProgress
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val constraintsScope = this

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimMaxAlpha * clampedProgress))
                .clickable(
                    interactionSource = noRippleInteractionSource,
                    indication = null,
                    onClick = onDismissRequest
                )
        )

        if (clampedProgress > 0.001f) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = clampedProgress
                            scaleX = panelScale
                            scaleY = panelScale
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        }
                        .padding(shadowSafePadding),
                    contentAlignment = Alignment.Center
                ) {
                    constraintsScope.content()
                }
            }
        }
    }
}
