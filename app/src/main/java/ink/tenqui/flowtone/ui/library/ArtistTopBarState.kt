package ink.tenqui.flowtone.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

internal data class ArtistTopBarThresholds(
    val showPx: Int,
    val hidePx: Int
)

internal fun artistTopBarThresholds(
    heroHeightPx: Int,
    topBarHeightPx: Int,
    hysteresisPx: Int
): ArtistTopBarThresholds {
    val show = (heroHeightPx - topBarHeightPx).coerceAtLeast(0)
    return ArtistTopBarThresholds(
        showPx = show,
        hidePx = (show - hysteresisPx.coerceAtLeast(0)).coerceAtLeast(0)
    )
}

internal fun artistTopBarVisible(
    wasVisible: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    thresholds: ArtistTopBarThresholds
): Boolean {
    val index = firstVisibleItemIndex.coerceAtLeast(0)
    val offset = firstVisibleItemScrollOffset.coerceAtLeast(0)
    val passedShowThreshold = index > 0 || offset >= thresholds.showPx
    val returnedPastHideThreshold = index == 0 && offset <= thresholds.hidePx
    return when {
        !wasVisible && passedShowThreshold -> true
        wasVisible && returnedPastHideThreshold -> false
        else -> wasVisible
    }
}

internal data class ArtistTopBarVisualPresentation(
    val foregroundAlpha: Float,
    val foregroundTranslationYFraction: Float
)

internal fun artistTopBarVisualPresentation(
    progress: Float
): ArtistTopBarVisualPresentation {
    val safeProgress = progress.coerceIn(0f, 1f)
    return ArtistTopBarVisualPresentation(
        foregroundAlpha = safeProgress,
        foregroundTranslationYFraction = -(1f - safeProgress)
    )
}

internal fun artistTopBarContentOcclusionProgress(
    presentationProgress: Float,
    ownsOcclusion: Boolean
): Float = if (ownsOcclusion) {
    presentationProgress.coerceIn(0f, 1f)
} else {
    0f
}

/**
 * Keeps Artist-path foreground content out of the shared transparent TopBar while leaving the
 * page's Cloud layer untouched. Partial progress uses a destination-out mask so threshold
 * reversals remain continuous; a fully presented TopBar uses a direct clip.
 */
internal fun Modifier.artistTopBarContentOcclusion(
    topBarHeight: Dp,
    progress: Float
): Modifier {
    val safeProgress = progress.coerceIn(0f, 1f)
    return then(
        when {
            safeProgress <= 0f -> Modifier
            safeProgress >= 1f -> Modifier.drawWithContent {
                clipRect(top = topBarHeight.toPx()) {
                    this@drawWithContent.drawContent()
                }
            }
            else -> Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Black.copy(alpha = safeProgress),
                        size = size.copy(height = topBarHeight.toPx()),
                        blendMode = BlendMode.DstOut
                    )
                }
        }
    )
}

@Stable
internal class ArtistTopBarStateOwner internal constructor(
    val entryKey: String,
    initialScrollPosition: ArtistScrollPosition
) {
    var visible by mutableStateOf(initialScrollPosition.firstVisibleItemIndex > 0)
        private set

    val presentationProgress = Animatable(if (visible) 1f else 0f)

    fun update(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        thresholds: ArtistTopBarThresholds
    ) {
        visible = artistTopBarVisible(
            wasVisible = visible,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            thresholds = thresholds
        )
    }
}

internal class ArtistTopBarStateStore {
    private val owners = mutableMapOf<String, ArtistTopBarStateOwner>()

    fun ownerFor(
        entryKey: String,
        initialScrollPosition: ArtistScrollPosition = ArtistScrollPosition()
    ): ArtistTopBarStateOwner = owners.getOrPut(entryKey) {
        ArtistTopBarStateOwner(entryKey, initialScrollPosition)
    }

    fun owner(entryKey: String?): ArtistTopBarStateOwner? = entryKey?.let(owners::get)

    fun retainEntries(entryKeys: Set<String>) {
        owners.keys.retainAll(entryKeys)
    }
}
