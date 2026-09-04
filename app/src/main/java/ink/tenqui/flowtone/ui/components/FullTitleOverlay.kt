package ink.tenqui.flowtone.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class FullTitleOverlayBackResult { DismissOverlay, NavigateBack }

internal fun fullTitleOverlayBackResult(overlayVisible: Boolean): FullTitleOverlayBackResult =
    if (overlayVisible) {
        FullTitleOverlayBackResult.DismissOverlay
    } else {
        FullTitleOverlayBackResult.NavigateBack
    }

internal fun canOpenFullTitleOverlay(hasVisualOverflow: Boolean): Boolean = hasVisualOverflow

/**
 * Lightweight in-layout overlay matching the Provider selector focus treatment. It deliberately
 * stays outside navigation and leaves background blur ownership to the host Scaffold.
 */
@Composable
internal fun FullTitleOverlay(
    title: String,
    progress: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val interactionSource = remember { MutableInteractionSource() }
    BackHandler(onBack = onDismiss)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .rightSwipeBackGesture(onDismiss)
            .background(
                Color.Black.copy(
                    alpha = clampedProgress *
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.12f else 0.18f
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            )
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .heightIn(max = maxHeight * 0.66f)
                .graphicsLayer {
                    alpha = clampedProgress
                    val scale = 0.94f + 0.06f * FlowtoneMotion.Easing.transform(clampedProgress)
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
