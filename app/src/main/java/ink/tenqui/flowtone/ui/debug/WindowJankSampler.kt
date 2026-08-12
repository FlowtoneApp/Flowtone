package ink.tenqui.flowtone.ui.debug

import android.app.Activity
import android.util.Log
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import ink.tenqui.flowtone.BuildConfig

/** Window 级帧指标。JankStats 会按设备刷新率判断实际卡顿帧。 */
internal object WindowJankSampler {
    private var jankStats: JankStats? = null
    private var metricsState: PerformanceMetricsState? = null
    private val pendingStates = mutableMapOf<String, String>()

    fun install(activity: Activity) {
        if (!BuildConfig.PERFORMANCE_SAMPLING_ENABLED || jankStats != null) return

        jankStats = JankStats.createAndTrack(activity.window) { frameData ->
            if (frameData.isJank) {
                val state = frameData.states.joinToString { "${it.key}=${it.value}" }
                Log.w(
                    WindowJankLogTag,
                    "jank ui=${frameData.frameDurationUiNanos / NanosPerMillisecond}ms " +
                        "state=[$state]"
                )
            }
        }
        // JankStats 创建后 Holder 才会为该 Window 提供可写的状态对象。
        metricsState = PerformanceMetricsState
            .getHolderForHierarchy(activity.window.decorView)
            .state
        pendingStates.forEach { (key, value) ->
            metricsState?.putState(key, value)
        }
    }

    fun setTrackingEnabled(enabled: Boolean) {
        jankStats?.isTrackingEnabled = enabled
    }

    fun updateState(key: String, value: String) {
        if (BuildConfig.PERFORMANCE_SAMPLING_ENABLED) {
            pendingStates[key] = value
            metricsState?.putState(key, value)
        }
    }
}

private const val WindowJankLogTag = "FlowtoneWindowJank"
private const val NanosPerMillisecond = 1_000_000L
