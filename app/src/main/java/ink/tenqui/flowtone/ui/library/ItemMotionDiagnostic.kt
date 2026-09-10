package ink.tenqui.flowtone.ui.library

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import ink.tenqui.flowtone.BuildConfig
import ink.tenqui.flowtone.ui.components.PageMotion
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.pageElementVisualState
import java.util.Locale

internal const val ItemMotionDiagnosticTag = "FlowtoneItemMotionDiag"

internal data class ItemMotionDiagnosticSample(
    val key: Any,
    val lazyIndex: Int,
    val ordinal: Int,
    val order: Int,
    val orderCount: Int,
    val motionProgress: Float,
    val layoutXPx: Int,
    val layoutYPx: Int,
    val widthPx: Int,
    val heightPx: Int
)

@Composable
internal fun ItemMotionDiagnostic(
    sessionKey: Any,
    page: String,
    transition: PageTransitionScope,
    samples: List<ItemMotionDiagnosticSample>,
    snapshotSource: String? = null,
    frozenKeys: List<Any> = emptyList()
) {
    if (!BuildConfig.DEBUG || samples.isEmpty()) return

    val density = LocalDensity.current.density
    val sampleBucket = if (transition.phase == PageTransitionPhase.Current) {
        ItemMotionDiagnosticFractions.lastIndex
    } else {
        ItemMotionDiagnosticFractions.indexOfLast { fraction ->
            transition.progress.coerceIn(0f, 1f) >= fraction
        }.coerceAtLeast(0)
    }
    var loggedBuckets by remember(sessionKey, transition.transitionId, transition.phase) {
        mutableStateOf(emptySet<Int>())
    }
    val sampleSignature = samples.joinToString(separator = "|") { sample ->
        "${sample.key}:${sample.layoutXPx}:${sample.layoutYPx}:${sample.widthPx}:${sample.heightPx}"
    }

    LaunchedEffect(sampleBucket, sampleSignature) {
        if (sampleBucket in loggedBuckets) return@LaunchedEffect
        loggedBuckets = loggedBuckets + sampleBucket

        val phase = transition.phase
        val targetFraction = ItemMotionDiagnosticFractions[sampleBucket]
        val offsetYPx = PageMotion.Offset.value * density
        val presentations = samples.take(ItemMotionDiagnosticItemLimit).map { sample ->
            val localProgress = if (phase == PageTransitionPhase.Current) {
                1f
            } else {
                PageMotion.elementProgress(
                    pageProgress = sample.motionProgress,
                    order = sample.order,
                    orderCount = sample.orderCount
                )
            }
            val visualState = pageElementVisualState(
                phase = phase,
                elementProgress = localProgress,
                signedOffsetYPx = offsetYPx
            )
            ItemMotionDiagnosticPresentation(
                sample = sample,
                localProgress = localProgress,
                alpha = visualState.alpha,
                translationYPx = visualState.translationY
            )
        }

        presentations.forEachIndexed { index, presentation ->
            val sample = presentation.sample
            val previous = presentations.getOrNull(index - 1)
            val visualY = sample.layoutYPx + presentation.translationYPx
            val layoutPitch = previous?.let { sample.layoutYPx - it.sample.layoutYPx }
            val visualPitch = previous?.let {
                visualY - (it.sample.layoutYPx + it.translationYPx)
            }
            val pitchDelta = if (layoutPitch != null && visualPitch != null) {
                visualPitch - layoutPitch
            } else {
                null
            }
            val layoutGap = previous?.let {
                sample.layoutYPx - (it.sample.layoutYPx + it.sample.heightPx)
            }
            val visualGap = previous?.let {
                visualY - (it.sample.layoutYPx + it.sample.heightPx + it.translationYPx)
            }
            Log.d(
                ItemMotionDiagnosticTag,
                "page=$page phase=$phase sample=${targetFraction.fmt()} " +
                    "progress=${transition.progress.fmt()} source=${snapshotSource ?: "n/a"} " +
                    "key=${sample.key} index=${sample.lazyIndex} ordinal=${sample.ordinal} " +
                    "order=${sample.order}/${sample.orderCount} " +
                    "motionProgress=${sample.motionProgress.fmt()} " +
                    "localProgress=${presentation.localProgress.fmt()} " +
                    "alpha=${presentation.alpha.fmt()} " +
                    "layoutX=${sample.layoutXPx}px layoutY=${sample.layoutYPx}px " +
                    "width=${sample.widthPx}px height=${sample.heightPx}px " +
                    "translationY=${presentation.translationYPx.fmt()}px " +
                    "visualY=${visualY.fmt()}px " +
                    "layoutPitch=${layoutPitch?.fmt() ?: "n/a"}px " +
                    "visualPitch=${visualPitch?.fmt() ?: "n/a"}px " +
                    "pitchDelta=${pitchDelta?.fmt() ?: "n/a"}px " +
                    "layoutGap=${layoutGap?.fmt() ?: "n/a"}px " +
                    "visualGap=${visualGap?.fmt() ?: "n/a"}px " +
                    "frozen=${frozenKeys.take(ItemMotionDiagnosticFrozenKeyLimit)}"
            )
        }
    }
}

@Composable
internal fun ArtistOverviewGeometryDiagnostic(
    sessionKey: Any,
    transition: PageTransitionScope,
    metadataState: String,
    infoCardHeightPx: Int,
    heroHeightPx: Int,
    songLayouts: List<ItemMotionDiagnosticSample>,
    bannerInitiallyCached: Boolean
) {
    if (!BuildConfig.DEBUG) return

    val density = LocalDensity.current.density
    LaunchedEffect(sessionKey, metadataState) {
        Log.d(
            ItemMotionDiagnosticTag,
            "page=ArtistPreview event=metadata_observed session=$sessionKey " +
                "phase=${transition.phase} progress=${transition.progress.fmt()} " +
                "bannerInitiallyCached=$bannerInitiallyCached $metadataState"
        )
    }

    val layoutSignature = songLayouts.take(3).joinToString(separator = "|") { sample ->
        "${sample.key}:${sample.layoutYPx}:${sample.heightPx}"
    }
    var geometryLogCount by remember(sessionKey) { mutableStateOf(0) }
    LaunchedEffect(infoCardHeightPx, heroHeightPx, layoutSignature) {
        if (geometryLogCount >= ItemMotionDiagnosticGeometryLogLimit) return@LaunchedEffect
        geometryLogCount += 1
        val songs = songLayouts.take(3).joinToString(separator = ";") { sample ->
            "${sample.key}@${sample.layoutYPx}px/h${sample.heightPx}px"
        }
        Log.d(
            ItemMotionDiagnosticTag,
            "page=ArtistPreview event=geometry session=$sessionKey " +
                "phase=${transition.phase} progress=${transition.progress.fmt()} " +
                "density=${density.fmt()} infoCardHeight=${infoCardHeightPx}px " +
                "heroHeight=${heroHeightPx}px songs=[$songs]"
        )
    }
}

internal fun logItemMotionDiagnosticEvent(message: String) {
    if (BuildConfig.DEBUG) Log.d(ItemMotionDiagnosticTag, message)
}

private data class ItemMotionDiagnosticPresentation(
    val sample: ItemMotionDiagnosticSample,
    val localProgress: Float,
    val alpha: Float,
    val translationYPx: Float
)

private fun Float.fmt(): String = String.format(Locale.US, "%.3f", this)
private fun Int.fmt(): String = toString()

private val ItemMotionDiagnosticFractions = listOf(0f, 0.15f, 0.30f, 0.50f, 0.80f, 1f)
private const val ItemMotionDiagnosticItemLimit = 5
private const val ItemMotionDiagnosticFrozenKeyLimit = 7
private const val ItemMotionDiagnosticGeometryLogLimit = 12
