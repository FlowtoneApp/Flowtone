package ink.tenqui.flowtone.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneModalPanel
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelExitScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelHeight
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelMaxWidth
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelMinWidth
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelStartScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistScrimMaxAlpha
import ink.tenqui.flowtone.ui.library.CreatePlaylistShadowSafePadding

/** Uses the same full-viewport window structure and motion as the app's playlist confirmation. */
@Composable
internal fun ExtensionDiscardChangesOverlay(
    title: String = "放弃未保存的更改？",
    message: String = "未保存的配置更改将会丢失。",
    keepLabel: String = "继续编辑",
    discardLabel: String = "放弃更改",
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelProgress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var completion by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(closing) {
        if (closing) {
            panelProgress.animateTo(
                0f,
                tween(FlowtoneMotion.DurationMillis / 2, easing = FlowtoneMotion.Easing)
            )
            completion?.invoke()
        } else {
            panelProgress.snapTo(0f)
            panelProgress.animateTo(
                1f,
                tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing)
            )
        }
    }
    fun closeAfterAnimation(action: () -> Unit) {
        if (!closing) {
            completion = action
            closing = true
        }
    }
    val panelMinScale = if (closing) CreatePlaylistPanelExitScale else CreatePlaylistPanelStartScale
    val panelScale = panelMinScale + (1f - panelMinScale) * panelProgress.value.coerceIn(0f, 1f)
    FlowtoneModalOverlayShell(
        visible = true,
        scrimAlpha = CreatePlaylistScrimMaxAlpha * panelProgress.value.coerceIn(0f, 1f),
        panelProgress = panelProgress.value,
        panelScale = panelScale,
        shadowSafePadding = CreatePlaylistShadowSafePadding,
        onDismissRequest = { closeAfterAnimation(onKeepEditing) },
        modifier = modifier
    ) {
        val availableWidth = maxWidth - CreatePlaylistShadowSafePadding - CreatePlaylistShadowSafePadding
        val panelWidth = when {
            availableWidth < CreatePlaylistPanelMinWidth -> availableWidth
            availableWidth > CreatePlaylistPanelMaxWidth -> CreatePlaylistPanelMaxWidth
            else -> availableWidth
        }
        FlowtoneModalPanel(
            modifier = Modifier.width(panelWidth).height(CreatePlaylistPanelHeight),
            maxWidth = null,
            horizontalPadding = 0.dp
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { closeAfterAnimation(onKeepEditing) }) { Text(keepLabel) }
                Button(
                    onClick = { closeAfterAnimation(onDiscard) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text(discardLabel) }
            }
        }
    }
}
