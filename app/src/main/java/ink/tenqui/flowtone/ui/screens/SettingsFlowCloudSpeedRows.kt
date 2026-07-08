package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.FlowCloudSpeedDialogState
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelExitScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelStartScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistScrimMaxAlpha
import ink.tenqui.flowtone.ui.library.CreatePlaylistShadowSafePadding
import ink.tenqui.flowtone.ui.player.coerceFlowCloudSpeed
import ink.tenqui.flowtone.ui.player.flowCloudSpeedToSliderProgress
import ink.tenqui.flowtone.ui.player.formatFlowCloudSpeed
import ink.tenqui.flowtone.ui.player.sliderProgressToFlowCloudSpeed

@Composable
internal fun FlowCloudSpeedRow(
    speed: Float,
    onOpenDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsRowCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onOpenDialog() }
            .padding(
                horizontal = SettingsRowHorizontalPadding,
                vertical = SettingsRowVerticalPadding
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "\u6d41\u4e91\u901f\u5ea6",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "\u5f53\u524d\uff1a${formatFlowCloudSpeed(speed)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "\u8bbe\u7f6e\u6d41\u4e91\u901f\u5ea6",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun FlowCloudSpeedOverlay(
    dialogState: FlowCloudSpeedDialogState,
    selectedSpeed: Float,
    onDismissRequest: () -> Unit,
    onDismissAnimationFinished: () -> Unit,
    onConfirm: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftSpeed by rememberSaveable {
        mutableStateOf(selectedSpeed.coerceFlowCloudSpeed())
    }
    val panelProgress = remember {
        Animatable(0f)
    }
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismissAnimationFinished by rememberUpdatedState(onDismissAnimationFinished)
    val cardInteractionSource = remember {
        MutableInteractionSource()
    }
    val overlayVisible = dialogState == FlowCloudSpeedDialogState.Editing ||
        dialogState == FlowCloudSpeedDialogState.Closing
    val scrimAlpha by animateFloatAsState(
        targetValue = if (dialogState == FlowCloudSpeedDialogState.Editing) {
            CreatePlaylistScrimMaxAlpha
        } else {
            0f
        },
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "FlowCloudSpeedScrim"
    )
    val panelMinScale = if (dialogState == FlowCloudSpeedDialogState.Closing) {
        CreatePlaylistPanelExitScale
    } else {
        CreatePlaylistPanelStartScale
    }
    val panelScale = lerpFlowCloudSpeedDialogFloat(
        start = panelMinScale,
        stop = 1f,
        fraction = panelProgress.value.coerceIn(0f, 1f)
    )

    fun closeWithoutConfirm() {
        onDismissRequest()
    }

    fun closeWithConfirm() {
        currentOnConfirm(draftSpeed.coerceFlowCloudSpeed())
        onDismissRequest()
    }

    LaunchedEffect(dialogState, selectedSpeed) {
        if (dialogState == FlowCloudSpeedDialogState.Editing) {
            draftSpeed = selectedSpeed.coerceFlowCloudSpeed()
        }
    }

    LaunchedEffect(dialogState) {
        when (dialogState) {
            FlowCloudSpeedDialogState.Editing -> {
                panelProgress.snapTo(0f)
                panelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtoneMotion.Easing
                    )
                )
            }

            FlowCloudSpeedDialogState.Closing -> {
                panelProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis / 2,
                        easing = FlowtoneMotion.Easing
                    )
                )
                currentOnDismissAnimationFinished()
                panelProgress.snapTo(0f)
            }

            FlowCloudSpeedDialogState.Idle -> Unit
        }
    }

    FlowtoneModalOverlayShell(
        visible = overlayVisible,
        scrimAlpha = scrimAlpha,
        panelProgress = panelProgress.value,
        panelScale = panelScale,
        shadowSafePadding = CreatePlaylistShadowSafePadding,
        onDismissRequest = ::closeWithoutConfirm,
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = SettingsDialogHorizontalPadding)
                .widthIn(max = SettingsDialogMaxWidth)
                .fillMaxWidth()
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(SettingsDialogCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = SettingsDialogElevation,
            shadowElevation = SettingsDialogElevation
        ) {
            Column(modifier = Modifier.padding(SettingsDialogContentPadding)) {
                Text(
                    text = "\u6d41\u4e91\u901f\u5ea6",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatFlowCloudSpeed(draftSpeed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Slider(
                    value = flowCloudSpeedToSliderProgress(draftSpeed),
                    onValueChange = { progress ->
                        draftSpeed = sliderProgressToFlowCloudSpeed(progress)
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "\u5173 / 0.1x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3x",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = ::closeWithoutConfirm) {
                        Text(text = "\u53d6\u6d88")
                    }
                    TextButton(onClick = ::closeWithConfirm) {
                        Text(text = "\u786e\u5b9a")
                    }
                }
            }
        }
    }
}

private fun lerpFlowCloudSpeedDialogFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
