package ink.tenqui.flowtone.ui.library

private const val ArtistTopBarLeadingStaggerStartFraction = 0f
private const val ArtistTopBarLeadingStaggerEndFraction = 0.85f
private const val ArtistTopBarTrailingStaggerStartFraction = 0.15f
private const val ArtistTopBarTrailingStaggerEndFraction = 1f

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
    val alpha: Float,
    val contentTranslationX: Float
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
    val targetAlpha: Float,
    val startContentTranslationX: Float,
    val targetContentTranslationX: Float,
    val contentProgressStartFraction: Float = 0f,
    val contentProgressEndFraction: Float = 1f
)

internal class ArtistTopBarVisualTransition internal constructor(
    private val elements: List<ArtistTopBarVisualTransitionElement>
) {
    val hasAnimation: Boolean
        get() = elements.any { element ->
            element.startX != element.targetX ||
                element.startAlpha != element.targetAlpha ||
                element.startContentTranslationX != element.targetContentTranslationX
        }

    fun presentation(progress: Float): List<ArtistTopBarPresentedElement> {
        val fraction = progress.coerceIn(0f, 1f)
        return elements.map { transitionElement ->
            val contentProgress = mapArtistTopBarLocalProgress(
                masterProgress = fraction,
                startFraction = transitionElement.contentProgressStartFraction,
                endFraction = transitionElement.contentProgressEndFraction
            )
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
                    contentProgress
                ),
                contentTranslationX = lerp(
                    transitionElement.startContentTranslationX,
                    transitionElement.targetContentTranslationX,
                    contentProgress
                )
            )
        }
    }
}

internal fun mapArtistTopBarLocalProgress(
    masterProgress: Float,
    startFraction: Float,
    endFraction: Float
): Float {
    require(endFraction > startFraction)
    return ((masterProgress.coerceIn(0f, 1f) - startFraction) /
        (endFraction - startFraction)).coerceIn(0f, 1f)
}

internal fun artistTopBarVisualTransition(
    oldState: ArtistTopBarVisualState,
    targetState: ArtistTopBarVisualState,
    contentMotionDistancePx: Float
): ArtistTopBarVisualTransition = ArtistTopBarVisualTransition(
    elements = artistTopBarVisualDiff(oldState, targetState).let { diffs ->
        val changeByKey = diffs.associate { diff -> diff.stableKey to diff.change }
        diffs.flatMap { diff ->
            val contentProgressWindow = artistTopBarContentProgressWindow(
                stableKey = diff.stableKey,
                change = diff.change,
                changeByKey = changeByKey
            )
            when (diff.change) {
                ArtistTopBarVisualChange.UnchangedStatic,
                ArtistTopBarVisualChange.Moved -> listOf(
                    ArtistTopBarVisualTransitionElement(
                        element = requireNotNull(diff.targetElement),
                        startX = requireNotNull(diff.oldElement).x,
                        targetX = diff.targetElement.x,
                        startAlpha = 1f,
                        targetAlpha = 1f,
                        startContentTranslationX = 0f,
                        targetContentTranslationX = 0f
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
                            targetAlpha = 0f,
                            startContentTranslationX = 0f,
                            targetContentTranslationX = contentMotionDistancePx
                        ),
                        ArtistTopBarVisualTransitionElement(
                            element = target,
                            startX = old.x,
                            targetX = target.x,
                            startAlpha = 0f,
                            targetAlpha = 1f,
                            startContentTranslationX = contentMotionDistancePx,
                            targetContentTranslationX = 0f
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
                            targetAlpha = 1f,
                            startContentTranslationX = contentMotionDistancePx,
                            targetContentTranslationX = 0f,
                            contentProgressStartFraction = contentProgressWindow.start,
                            contentProgressEndFraction = contentProgressWindow.end
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
                            targetAlpha = 0f,
                            startContentTranslationX = 0f,
                            targetContentTranslationX = contentMotionDistancePx,
                            contentProgressStartFraction = contentProgressWindow.start,
                            contentProgressEndFraction = contentProgressWindow.end
                        )
                    )
                }
            }
        }
    }
)

internal fun artistTopBarRetargetedVisualTransition(
    startPresentation: List<ArtistTopBarPresentedElement>,
    targetState: ArtistTopBarVisualState,
    contentMotionDistancePx: Float
): ArtistTopBarVisualTransition {
    val startByLayer = startPresentation.associateBy { presented ->
        presented.element.layerKey()
    }
    val targetByLayer = targetState.elements.associateBy { element -> element.layerKey() }
    val startByStableKey = startPresentation
        .groupBy { presented -> presented.element.stableKey }
        .mapValues { (_, candidates) -> candidates.maxBy { it.alpha } }
    val targetByStableKey = targetState.elements.associateBy { it.stableKey }
    val startStableKeys = startByStableKey.keys
    val targetStableKeys = targetByStableKey.keys
    val retargetChangeByKey = (startStableKeys + targetStableKeys).associateWith { stableKey ->
        when {
            stableKey !in startStableKeys -> ArtistTopBarVisualChange.Inserted
            stableKey !in targetStableKeys -> ArtistTopBarVisualChange.Removed
            else -> ArtistTopBarVisualChange.UnchangedStatic
        }
    }
    val layerKeys = startByLayer.keys + targetByLayer.keys

    return ArtistTopBarVisualTransition(
        elements = layerKeys.map { layerKey ->
            val start = startByLayer[layerKey]
            val target = targetByLayer[layerKey]
            val startSlot = startByStableKey[layerKey.stableKey]
            val targetSlot = targetByStableKey[layerKey.stableKey]
            val contentProgressWindow = artistTopBarContentProgressWindow(
                stableKey = layerKey.stableKey,
                change = retargetChangeByKey.getValue(layerKey.stableKey),
                changeByKey = retargetChangeByKey
            )
            ArtistTopBarVisualTransitionElement(
                element = target ?: requireNotNull(start).element,
                startX = start?.x ?: startSlot?.x ?: requireNotNull(target).x,
                targetX = target?.x ?: targetSlot?.x ?: requireNotNull(start).x,
                startAlpha = start?.alpha ?: 0f,
                targetAlpha = if (target == null) 0f else 1f,
                startContentTranslationX = start?.contentTranslationX
                    ?: contentMotionDistancePx,
                targetContentTranslationX = if (target == null) {
                    contentMotionDistancePx
                } else {
                    0f
                },
                contentProgressStartFraction = contentProgressWindow.start,
                contentProgressEndFraction = contentProgressWindow.end
            )
        }
    )
}

internal fun ArtistTopBarVisualState.asPresentation(): List<ArtistTopBarPresentedElement> =
    elements.map { element ->
        ArtistTopBarPresentedElement(
            element = element,
            x = element.x,
            alpha = 1f,
            contentTranslationX = 0f
        )
    }

private fun ArtistTopBarVisualElement.layerKey(): ArtistTopBarVisualLayerKey =
    ArtistTopBarVisualLayerKey(stableKey = stableKey, text = text)

private data class ArtistTopBarContentProgressWindow(
    val start: Float,
    val end: Float
)

private fun artistTopBarContentProgressWindow(
    stableKey: String,
    change: ArtistTopBarVisualChange,
    changeByKey: Map<String, ArtistTopBarVisualChange>
): ArtistTopBarContentProgressWindow {
    val separatorIndex = stableKey.removePrefix("separator:")
        .takeIf { stableKey.startsWith("separator:") }
    val pathIndex = stableKey.removePrefix("path:")
        .takeIf { stableKey.startsWith("path:") }
    val pairedChange = when {
        separatorIndex != null -> changeByKey["path:$separatorIndex"]
        pathIndex != null -> changeByKey["separator:$pathIndex"]
        else -> null
    }
    return when {
        change == ArtistTopBarVisualChange.Inserted &&
            pairedChange == ArtistTopBarVisualChange.Inserted &&
            separatorIndex != null -> ArtistTopBarContentProgressWindow(
            start = ArtistTopBarLeadingStaggerStartFraction,
            end = ArtistTopBarLeadingStaggerEndFraction
        )
        change == ArtistTopBarVisualChange.Inserted &&
            pairedChange == ArtistTopBarVisualChange.Inserted &&
            pathIndex != null -> ArtistTopBarContentProgressWindow(
            start = ArtistTopBarTrailingStaggerStartFraction,
            end = ArtistTopBarTrailingStaggerEndFraction
        )
        change == ArtistTopBarVisualChange.Removed &&
            pairedChange == ArtistTopBarVisualChange.Removed &&
            pathIndex != null -> ArtistTopBarContentProgressWindow(
            start = ArtistTopBarLeadingStaggerStartFraction,
            end = ArtistTopBarLeadingStaggerEndFraction
        )
        change == ArtistTopBarVisualChange.Removed &&
            pairedChange == ArtistTopBarVisualChange.Removed &&
            separatorIndex != null -> ArtistTopBarContentProgressWindow(
            start = ArtistTopBarTrailingStaggerStartFraction,
            end = ArtistTopBarTrailingStaggerEndFraction
        )
        else -> ArtistTopBarContentProgressWindow(start = 0f, end = 1f)
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction
