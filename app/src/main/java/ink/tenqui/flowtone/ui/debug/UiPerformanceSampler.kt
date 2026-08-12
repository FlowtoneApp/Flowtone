package ink.tenqui.flowtone.ui.debug

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import ink.tenqui.flowtone.BuildConfig
import kotlinx.coroutines.isActive

/**
 * 为 Compose 区域输出低频性能采样日志。
 *
 * 仅 fast 与 benchmark 构建启用；debug 与 release 构建会直接返回原 Modifier。
 */
@Composable
internal fun Modifier.performanceSample(
    name: String,
    context: () -> String = { "" }
): Modifier {
    if (!BuildConfig.PERFORMANCE_SAMPLING_ENABLED) return this

    val stats = remember(name) { UiPerformanceStats() }
    val latestContext by rememberUpdatedState(context)

    SideEffect {
        stats.recompositionCount += 1
        WindowJankSampler.updateState("Ui$name", context())
    }
    LaunchedEffect(name) {
        var previousFrameNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (previousFrameNanos != 0L) {
                stats.recordFrame((frameNanos - previousFrameNanos) / NanosPerMillisecond)
            }
            previousFrameNanos = frameNanos

            if (stats.shouldReport()) {
                Log.d(PerformanceLogTag, stats.format(name, latestContext()))
                stats.resetInterval()
            }
        }
    }

    return this
        .onGloballyPositioned {
            stats.layoutCount += 1
        }
        .drawWithContent {
            val drawStartNanos = System.nanoTime()
            drawContent()
            stats.recordDraw((System.nanoTime() - drawStartNanos) / NanosPerMillisecond)
        }
}

internal inline fun <T> performanceSampleOperation(
    section: String,
    operation: String,
    block: () -> T
): T {
    if (!BuildConfig.PERFORMANCE_SAMPLING_ENABLED) return block()

    val startedAtNanos = System.nanoTime()
    return block().also {
        val elapsedMillis = (System.nanoTime() - startedAtNanos) / NanosPerMillisecond
        if (elapsedMillis >= ExpensiveOperationMillis) {
            Log.d(
                PerformanceLogTag,
                "[$section] operation=$operation elapsed=${"%.2f".format(elapsedMillis)}ms"
            )
        }
    }
}

private class UiPerformanceStats {
    var recompositionCount = 0
    var layoutCount = 0
    private var drawCount = 0
    private var drawTotalMillis = 0.0
    private var frameCount = 0
    private var slowFrameCount = 0
    private var jankFrameCount = 0
    private var worstFrameMillis = 0.0
    private var intervalStartedAtMillis = SystemClock.elapsedRealtime()

    fun recordFrame(frameMillis: Double) {
        frameCount += 1
        if (frameMillis > SlowFrameMillis) slowFrameCount += 1
        if (frameMillis > JankFrameMillis) jankFrameCount += 1
        worstFrameMillis = maxOf(worstFrameMillis, frameMillis)
    }

    fun recordDraw(drawMillis: Double) {
        drawCount += 1
        drawTotalMillis += drawMillis
    }

    fun shouldReport(): Boolean =
        SystemClock.elapsedRealtime() - intervalStartedAtMillis >= ReportIntervalMillis

    fun format(name: String, context: String): String {
        val averageDrawMillis = if (drawCount == 0) 0.0 else drawTotalMillis / drawCount
        return "[$name] recompose=$recompositionCount layout=$layoutCount draw=$drawCount " +
            "drawAvg=${"%.2f".format(averageDrawMillis)}ms frames=$frameCount " +
            "slow16=$slowFrameCount jank24=$jankFrameCount " +
            "worst=${"%.2f".format(worstFrameMillis)}ms $context"
    }

    fun resetInterval() {
        recompositionCount = 0
        layoutCount = 0
        drawCount = 0
        drawTotalMillis = 0.0
        frameCount = 0
        slowFrameCount = 0
        jankFrameCount = 0
        worstFrameMillis = 0.0
        intervalStartedAtMillis = SystemClock.elapsedRealtime()
    }
}

private const val PerformanceLogTag = "FlowtoneUiPerf"
private const val ReportIntervalMillis = 2_000L
private const val SlowFrameMillis = 16.7
private const val JankFrameMillis = 24.0
private const val NanosPerMillisecond = 1_000_000.0
private const val ExpensiveOperationMillis = 4.0
