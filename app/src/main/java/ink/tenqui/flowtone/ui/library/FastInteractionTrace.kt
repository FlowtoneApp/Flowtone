package ink.tenqui.flowtone.ui.library

import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import ink.tenqui.flowtone.BuildConfig
import ink.tenqui.flowtone.core.model.Song

internal object FastInteractionTrace {
    private const val Tag = "FlowtoneHomeInput"

    fun record(
        stage: String,
        page: String,
        song: Song? = null,
        details: String = ""
    ) {
        if (BuildConfig.BUILD_TYPE != "fast") return
        val identity = song?.let { "${it.sourceType}:${it.id}:${it.uri}" } ?: "-"
        Log.d(
            Tag,
            "INTERACTION tNs=${SystemClock.elapsedRealtimeNanos()} " +
                "homeMode=${if (BuildConfig.MINIMAL_HOME_EXPERIMENT_ENABLED) "minimal" else "normal"} " +
                "homeIsolation=${if (BuildConfig.BUILD_TYPE == "fast") BuildConfig.HOME_ISOLATION_MODE else "normal"} " +
                "overscrollMode=${if (BuildConfig.HOME_OVERSCROLL_EXPERIMENT_ENABLED) "B" else "A"} " +
                "stage=$stage page=$page song=$identity" +
                "${if (details.isBlank()) "" else " $details"}"
        )
    }
}

internal fun Modifier.observeFastInteractionPointerEvents(page: String): Modifier =
    if (BuildConfig.BUILD_TYPE != "fast") {
        this
    } else {
        pointerInput(page) {
            observeFastInteractionPointerEvents(page)
        }
    }

private suspend fun PointerInputScope.observeFastInteractionPointerEvents(page: String) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { change ->
                if (change.pressed && !change.previousPressed) {
                    FastInteractionTrace.record(
                        stage = "POINTER_DOWN",
                        page = page,
                        details = "pointer=${change.id.value} consumed=${change.isConsumed}"
                    )
                } else if (!change.pressed && change.previousPressed) {
                    FastInteractionTrace.record(
                        stage = "POINTER_UP",
                        page = page,
                        details = "pointer=${change.id.value} consumed=${change.isConsumed}"
                    )
                }
            }
        }
    }
}
