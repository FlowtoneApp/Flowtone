package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension

internal enum class OnlineInstalledListState { Loading, Empty, Content, Error }

internal fun onlineInstalledListState(count: Int, loading: Boolean, error: String?): OnlineInstalledListState = when {
    count > 0 -> OnlineInstalledListState.Content
    loading -> OnlineInstalledListState.Loading
    error != null -> OnlineInstalledListState.Error
    else -> OnlineInstalledListState.Empty
}

@Composable
internal fun OnlineSettingsPage(
    installedExtensions: List<InstalledExtension>,
    loading: Boolean,
    loadError: String?,
    inspecting: Boolean,
    inspectError: String?,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenExtensionSettings: (InstalledExtension) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    val listState = onlineInstalledListState(installedExtensions.size, loading, loadError)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp)
    ) {
        item(key = "install") {
            OnlinePrimaryAction(
                title = if (inspecting) "正在检查扩展包…" else "安装扩展",
                subtitle = "选择设备中的 .flowtone 扩展包",
                icon = Icons.Rounded.Add,
                onClick = onInstall,
                enabled = !inspecting,
                modifier = elementModifier(0)
            )
        }
        inspectError?.let { message ->
            item(key = "inspect-error") {
                Text(message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item(key = "installed-heading") {
            OnlineSectionHeading(
                title = "已安装扩展",
                subtitle = if (loading && installedExtensions.isEmpty()) "正在读取…" else "${installedExtensions.size} 个扩展",
                modifier = elementModifier(1).padding(top = 30.dp, bottom = 8.dp)
            )
        }
        if (loadError != null) {
            item(key = "load-error") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(loadError, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
        if (listState == OnlineInstalledListState.Loading) {
            item(key = "loading") { OnlineListMessage("正在读取已安装扩展…") }
        } else if (listState == OnlineInstalledListState.Empty) {
            item(key = "empty") { OnlineListMessage("还没有扩展。选择上方的扩展包即可开始。") }
        }
        itemsIndexed(installedExtensions, key = { _, installed -> installed.manifest.id }) { index, installed ->
            InstalledExtensionLine(
                installed = installed,
                onClick = { onOpenExtensionSettings(installed) },
                modifier = elementModifier(index + 2)
            )
            if (index != installedExtensions.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            }
        }
    }
}

@Composable
private fun InstalledExtensionLine(
    installed: InstalledExtension,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manifest = installed.manifest
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                ExtensionInstalledIcon(installed, Modifier.size(29.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(manifest.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("版本 ${manifest.version}" +
                if (!installed.runtimeAvailable) " · 运行环境不可用" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (installed.runtimeAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OnlineListMessage(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
}
