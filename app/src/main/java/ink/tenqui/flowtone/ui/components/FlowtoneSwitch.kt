package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FlowtoneSwitchOnTrack = Color(0xFF526FA5)
private val FlowtoneSwitchOffTrack = Color(0xFFC4CDE0)
private val FlowtoneSwitchStartSpacing = 12.dp

@Composable
fun FlowtoneSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.padding(start = FlowtoneSwitchStartSpacing),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = FlowtoneSwitchOnTrack,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = FlowtoneSwitchOffTrack,
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.38f),
            disabledCheckedTrackColor = FlowtoneSwitchOnTrack.copy(alpha = 0.38f),
            disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.38f),
            disabledUncheckedTrackColor = FlowtoneSwitchOffTrack.copy(alpha = 0.38f),
            disabledUncheckedBorderColor = Color.Transparent
        )
    )
}
