package ink.tenqui.flowtone.ui.library

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import ink.tenqui.flowtone.BuildConfig
import ink.tenqui.flowtone.ui.components.PageTransitionPhase
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal const val ArtistSongsHeaderDiagnosticTag = "FlowtoneArtistHeaderDiag"

internal data class ArtistSongsHeaderDiagnosticInput(
    val session: String,
    val phase: PageTransitionPhase,
    val progress: Float,
    val transitionId: Int,
    val hasAction: Boolean,
    val actionTitleState: String,
    val songsPresent: Boolean,
    val songCount: Int,
    val contentPresentation: String,
    val headerHeight: Int?
)

private data class ArtistSongsHeaderDiagnosticSignature(
    val phase: PageTransitionPhase,
    val hasAction: Boolean,
    val actionTitleState: String,
    val songsPresent: Boolean,
    val songCount: Int,
    val contentPresentation: String,
    val headerHeight: Int?
)

private class ArtistSongsHeaderDiagnosticHolder(
    private val instance: Long
) {
    private var lastEmitted: ArtistSongsHeaderDiagnosticSignature? = null

    fun emitIfChanged(input: ArtistSongsHeaderDiagnosticInput) {
        val signature = ArtistSongsHeaderDiagnosticSignature(
            phase = input.phase,
            hasAction = input.hasAction,
            actionTitleState = input.actionTitleState,
            songsPresent = input.songsPresent,
            songCount = input.songCount,
            contentPresentation = input.contentPresentation,
            headerHeight = input.headerHeight
        )
        if (signature == lastEmitted) return
        lastEmitted = signature
        Log.d(
            ArtistSongsHeaderDiagnosticTag,
            "event=header_state_changed session=${input.session} instance=$instance " +
                "phase=${input.phase} progress=${input.progress.diagFmt()} " +
                "transitionId=${input.transitionId} hasAction=${input.hasAction} " +
                "actionTitle=${input.actionTitleState} songsPresent=${input.songsPresent} " +
                "songCount=${input.songCount} presentation=${input.contentPresentation} " +
                "headerHeight=${input.headerHeight?.toString() ?: "n/a"}px"
        )
    }
}

@Composable
internal fun ArtistSongsHeaderDiagnostic(input: ArtistSongsHeaderDiagnosticInput?) {
    if (!BuildConfig.DEBUG || input == null) return

    val instance = remember { ArtistSongsHeaderDiagnosticIds.incrementAndGet() }
    val holder = remember(instance) { ArtistSongsHeaderDiagnosticHolder(instance) }
    SideEffect { holder.emitIfChanged(input) }
}

private fun Float.diagFmt(): String = String.format(Locale.US, "%.3f", this)

private val ArtistSongsHeaderDiagnosticIds = AtomicLong(0)
