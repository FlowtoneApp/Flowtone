package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ink.tenqui.flowtone.app.FlowtonePageEasing
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SongRecordThresholdDialog(
    selectedSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var draftSeconds by rememberSaveable(selectedSeconds) {
        mutableStateOf(selectedSeconds.coerceSongRecordThreshold())
    }
    var visible by remember {
        mutableStateOf(false)
    }
    var closing by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()

    fun closeAfterAnimation(action: () -> Unit) {
        if (closing) {
            return
        }

        closing = true
        visible = false
        scope.launch {
            delay(DialogScaleExitDurationMillis.toLong())
            action()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            closeAfterAnimation(onDismiss)
        },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = DialogScaleEnterDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = DialogScaleExitDurationMillis,
                        easing = FlowtonePageEasing
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.scrim.copy(
                                alpha = SettingsDialogScrimAlpha
                            )
                        )
                        .clickable {
                            closeAfterAnimation(onDismiss)
                        }
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = DialogScaleEnterDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = DialogScaleEnterDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = DialogScaleExitDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(
                        durationMillis = DialogScaleExitDurationMillis,
                        easing = FlowtonePageEasing
                    )
                )
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = SettingsDialogHorizontalPadding)
                        .widthIn(max = SettingsDialogMaxWidth)
                        .fillMaxWidth(),
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
                                onClick = {
                                    closeAfterAnimation(onDismiss)
                                }
                            ) {
                                Text(text = "取消")
                            }
                            TextButton(
                                onClick = {
                                    val confirmedSeconds = draftSeconds
                                    closeAfterAnimation {
                                        onConfirm(confirmedSeconds)
                                    }
                                }
                            ) {
                                Text(text = "确定")
                            }
                        }
                    }
                }
            }
        }
    }
}
