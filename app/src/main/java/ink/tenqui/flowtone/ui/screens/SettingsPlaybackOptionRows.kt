package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.SongRecordThresholdDialogState
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelExitScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelStartScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistScrimMaxAlpha
import ink.tenqui.flowtone.ui.library.CreatePlaylistShadowSafePadding
import kotlin.math.roundToInt

@Composable
internal fun SongRecordThresholdOverlay(
    dialogState: SongRecordThresholdDialogState,
    selectedSeconds: Int,
    onDismissRequest: () -> Unit,
    onDismissAnimationFinished: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftSeconds by rememberSaveable {
        mutableStateOf(selectedSeconds.coerceSongRecordThreshold())
    }
    var pendingConfirmSeconds by remember {
        mutableStateOf<Int?>(null)
    }
    val panelProgress = remember {
        Animatable(0f)
    }
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismissAnimationFinished by rememberUpdatedState(onDismissAnimationFinished)
    val cardInteractionSource = remember {
        MutableInteractionSource()
    }
    val overlayVisible = dialogState == SongRecordThresholdDialogState.Editing ||
        dialogState == SongRecordThresholdDialogState.Closing
    val scrimAlpha by animateFloatAsState(
        targetValue = if (dialogState == SongRecordThresholdDialogState.Editing) {
            CreatePlaylistScrimMaxAlpha
        } else {
            0f
        },
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "SongRecordThresholdScrim"
    )
    val panelMinScale = if (dialogState == SongRecordThresholdDialogState.Closing) {
        CreatePlaylistPanelExitScale
    } else {
        CreatePlaylistPanelStartScale
    }
    val panelScale = lerpSettingsDialogFloat(
        start = panelMinScale,
        stop = 1f,
        fraction = panelProgress.value.coerceIn(0f, 1f)
    )

    fun closeWithoutConfirm() {
        pendingConfirmSeconds = null
        onDismissRequest()
    }

    fun closeWithConfirm() {
        pendingConfirmSeconds = draftSeconds
        onDismissRequest()
    }

    LaunchedEffect(dialogState, selectedSeconds) {
        if (dialogState == SongRecordThresholdDialogState.Editing) {
            draftSeconds = selectedSeconds.coerceSongRecordThreshold()
        }
    }

    LaunchedEffect(dialogState) {
        when (dialogState) {
            SongRecordThresholdDialogState.Editing -> {
                panelProgress.snapTo(0f)
                panelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis,
                        easing = FlowtoneMotion.Easing
                    )
                )
            }

            SongRecordThresholdDialogState.Closing -> {
                panelProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = FlowtoneMotion.DurationMillis / 2,
                        easing = FlowtoneMotion.Easing
                    )
                )
                pendingConfirmSeconds?.let { confirmedSeconds ->
                    currentOnConfirm(confirmedSeconds)
                    pendingConfirmSeconds = null
                }
                currentOnDismissAnimationFinished()
                panelProgress.snapTo(0f)
            }

            SongRecordThresholdDialogState.Idle -> Unit
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
                    text = "歌曲记录阈值",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "同一首歌真实播放达到这个时间后，才会计入今日听歌。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "$draftSeconds 秒",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Slider(
                    value = draftSeconds.toFloat(),
                    onValueChange = { value ->
                        val seconds = value
                            .roundToInt()
                            .coerceSongRecordThreshold()
                        draftSeconds = seconds
                    },
                    valueRange = MinSongRecordThresholdSeconds.toFloat()..
                        MaxSongRecordThresholdSeconds.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${MinSongRecordThresholdSeconds} 秒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${MaxSongRecordThresholdSeconds} 秒",
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
                    TextButton(
                        onClick = ::closeWithoutConfirm
                    ) {
                        Text(text = "取消")
                    }
                    TextButton(
                        onClick = ::closeWithConfirm
                    ) {
                        Text(text = "确定")
                    }
                }
            }
        }
    }
}

private fun lerpSettingsDialogFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
