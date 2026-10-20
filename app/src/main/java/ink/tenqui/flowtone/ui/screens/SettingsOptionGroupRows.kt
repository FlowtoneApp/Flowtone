package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import ink.tenqui.flowtone.app.FlowtonePageEasing
import ink.tenqui.flowtone.ui.components.SettingsOptionIcon
import ink.tenqui.flowtone.ui.components.SettingsOptionSurface

@Composable
internal fun SettingsExpandableOptionRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    expandIconRotation: Float,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SettingsOptionSurface(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(
                    horizontal = SettingsRowHorizontalPadding,
                    vertical = SettingsRowVerticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsOptionIcon(icon)
            Spacer(modifier = Modifier.width(SettingsSectionRowIconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = expandIconRotation }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) + fadeIn(tween(220, easing = FlowtonePageEasing)),
            exit = shrinkVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) + fadeOut(tween(180, easing = FlowtonePageEasing))
        ) {
            content()
        }
    }
}
