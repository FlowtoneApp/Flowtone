package ink.tenqui.flowtone.ui.library

internal data class ArtistTopBarVisualElement(
    val stableKey: String,
    val text: String,
    val role: ArtistVisualBreadcrumbRole,
    val x: Float,
    val width: Float,
    val ellipsize: Boolean = false
)

internal data class ArtistTopBarVisualState(
    val elements: List<ArtistTopBarVisualElement>
) {
    init {
        require(elements.map { it.stableKey }.distinct().size == elements.size)
    }
}

internal enum class ArtistTopBarVisualChange {
    UnchangedStatic,
    Moved,
    Changed,
    Inserted,
    Removed
}

internal data class ArtistTopBarVisualDiff(
    val stableKey: String,
    val change: ArtistTopBarVisualChange,
    val oldElement: ArtistTopBarVisualElement?,
    val targetElement: ArtistTopBarVisualElement?
)

internal fun artistTopBarVisualDiff(
    oldState: ArtistTopBarVisualState,
    targetState: ArtistTopBarVisualState
): List<ArtistTopBarVisualDiff> {
    val oldByKey = oldState.elements.associateBy { it.stableKey }
    val targetByKey = targetState.elements.associateBy { it.stableKey }
    return (oldByKey.keys + targetByKey.keys).map { stableKey ->
        val old = oldByKey[stableKey]
        val target = targetByKey[stableKey]
        val change = when {
            old == null -> ArtistTopBarVisualChange.Inserted
            target == null -> ArtistTopBarVisualChange.Removed
            old.text != target.text -> ArtistTopBarVisualChange.Changed
            old.x != target.x -> ArtistTopBarVisualChange.Moved
            else -> ArtistTopBarVisualChange.UnchangedStatic
        }
        ArtistTopBarVisualDiff(
            stableKey = stableKey,
            change = change,
            oldElement = old,
            targetElement = target
        )
    }
}

internal data class ArtistTopBarPresentedElement(
    val element: ArtistTopBarVisualElement,
    val x: Float,
    val alpha: Float
)

private data class ArtistTopBarVisualLayerKey(
    val stableKey: String,
    val text: String
)

internal data class ArtistTopBarVisualTransitionElement(
    val element: ArtistTopBarVisualElement,
    val startX: Float,
    val targetX: Float,
    val startAlpha: Float,
    val targetAlpha: Float
)

internal class ArtistTopBarVisualTransition internal constructor(
    private val elements: List<ArtistTopBarVisualTransitionElement>
) {
    val hasAnimation: Boolean
        get() = elements.any { element ->
            element.startX != element.targetX || element.startAlpha != element.targetAlpha
        }

    fun presentation(progress: Float): List<ArtistTopBarPresentedElement> {
        val fraction = progress.coerceIn(0f, 1f)
        return elements.map { transitionElement ->
            ArtistTopBarPresentedElement(
                element = transitionElement.element,
                x = lerp(
                    transitionElement.startX,
                    transitionElement.targetX,
                    fraction
                ),
                alpha = lerp(
                    transitionElement.startAlpha,
                    transitionElement.targetAlpha,
                    fraction
                )
            )
        }
    }
}

internal fun artistTopBarVisualTransition(
    oldState: ArtistTopBarVisualState,
    targetState: ArtistTopBarVisualState
): ArtistTopBarVisualTransition = ArtistTopBarVisualTransition(
    elements = artistTopBarVisualDiff(oldState, targetState).flatMap { diff ->
        when (diff.change) {
            ArtistTopBarVisualChange.UnchangedStatic,
            ArtistTopBarVisualChange.Moved -> listOf(
                ArtistTopBarVisualTransitionElement(
                    element = requireNotNull(diff.targetElement),
                    startX = requireNotNull(diff.oldElement).x,
                    targetX = diff.targetElement.x,
                    startAlpha = 1f,
                    targetAlpha = 1f
                )
            )

            ArtistTopBarVisualChange.Changed -> {
                val old = requireNotNull(diff.oldElement)
                val target = requireNotNull(diff.targetElement)
                listOf(
                    ArtistTopBarVisualTransitionElement(
                        element = old,
                        startX = old.x,
                        targetX = target.x,
                        startAlpha = 1f,
                        targetAlpha = 0f
                    ),
                    ArtistTopBarVisualTransitionElement(
                        element = target,
                        startX = old.x,
                        targetX = target.x,
                        startAlpha = 0f,
                        targetAlpha = 1f
                    )
                )
            }

            ArtistTopBarVisualChange.Inserted -> {
                val target = requireNotNull(diff.targetElement)
                listOf(
                    ArtistTopBarVisualTransitionElement(
                        element = target,
                        startX = target.x,
                        targetX = target.x,
                        startAlpha = 0f,
                        targetAlpha = 1f
                    )
                )
            }

            ArtistTopBarVisualChange.Removed -> {
                val old = requireNotNull(diff.oldElement)
                listOf(
                    ArtistTopBarVisualTransitionElement(
                        element = old,
                        startX = old.x,
                        targetX = old.x,
                        startAlpha = 1f,
                        targetAlpha = 0f
                    )
                )
            }
        }
    }
)

internal fun artistTopBarRetargetedVisualTransition(
    startPresentation: List<ArtistTopBarPresentedElement>,
    targetState: ArtistTopBarVisualState
): ArtistTopBarVisualTransition {
    val startByLayer = startPresentation.associateBy { presented ->
        presented.element.layerKey()
    }
    val targetByLayer = targetState.elements.associateBy { element -> element.layerKey() }
    val startByStableKey = startPresentation
        .groupBy { presented -> presented.element.stableKey }
        .mapValues { (_, candidates) -> candidates.maxBy { it.alpha } }
    val targetByStableKey = targetState.elements.associateBy { it.stableKey }
    val layerKeys = startByLayer.keys + targetByLayer.keys

    return ArtistTopBarVisualTransition(
        elements = layerKeys.map { layerKey ->
            val start = startByLayer[layerKey]
            val target = targetByLayer[layerKey]
            val startSlot = startByStableKey[layerKey.stableKey]
            val targetSlot = targetByStableKey[layerKey.stableKey]
            ArtistTopBarVisualTransitionElement(
                element = target ?: requireNotNull(start).element,
                startX = start?.x ?: startSlot?.x ?: requireNotNull(target).x,
                targetX = target?.x ?: targetSlot?.x ?: requireNotNull(start).x,
                startAlpha = start?.alpha ?: 0f,
                targetAlpha = if (target == null) 0f else 1f
            )
        }
    )
}

internal fun ArtistTopBarVisualState.asPresentation(): List<ArtistTopBarPresentedElement> =
    elements.map { element ->
        ArtistTopBarPresentedElement(element = element, x = element.x, alpha = 1f)
    }

private fun ArtistTopBarVisualElement.layerKey(): ArtistTopBarVisualLayerKey =
    ArtistTopBarVisualLayerKey(stableKey = stableKey, text = text)

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

