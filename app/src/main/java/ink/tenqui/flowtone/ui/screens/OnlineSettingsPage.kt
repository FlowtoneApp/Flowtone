package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.ui.components.OptionGroup

@Composable
internal fun OnlineSettingsPage(
    installedExtensions: List<InstalledExtension>,
    loading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenCredentialSources: () -> Unit,
    onOpenExtensionSettings: (InstalledExtension) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        OnlineManagementEntry(
            title = "安装扩展",
            subtitle = "从设备中选择 .flowtone 扩展包",
            onClick = onInstall,
            modifier = elementModifier(0)
        ) {
            Icon(
                painterResource(R.drawable.ic_deployed_code_update),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SettingsRowIconSize)
            )
        }

        OptionGroup(title = "已安装扩展", modifier = Modifier.padding(top = 24.dp)) {
            if (loading) {
                OnlineExtensionMessage("正在读取已安装扩展…", elementModifier(1))
            } else {
                if (loadError != null) {
                    OnlineExtensionMessage(loadError)
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                if (installedExtensions.isEmpty() && loadError == null) OnlineExtensionMessage(
                    "尚未安装扩展。可从上方选择扩展包开始安装。",
                    elementModifier(1)
                )
                installedExtensions.forEachIndexed { index, installed ->
                    key(installed.manifest.id) {
                        InstalledExtensionRow(
                            installed = installed,
                            onClick = { onOpenExtensionSettings(installed) },
                            modifier = elementModifier(index + 1)
                                .padding(top = if (index == 0) 0.dp else 10.dp)
                        )
                    }
                }
            }
        }

        OptionGroup(title = "用户凭据", modifier = Modifier.padding(top = 24.dp)) {
            OnlineManagementEntry(
                title = "凭据管理",
                subtitle = "管理可供在线扩展申请授权的账户凭据",
                onClick = onOpenCredentialSources,
                modifier = elementModifier(maxOf(installedExtensions.size, 1) + 1)
            ) {
                Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SettingsRowIconSize))
            }
        }
    }
}

@Composable
private fun InstalledExtensionRow(
    installed: InstalledExtension,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manifest = installed.manifest
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(SettingsRowCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SettingsRowHorizontalPadding, vertical = SettingsRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ExtensionInstalledIcon(installed, Modifier.size(32.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    manifest.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "版本 ${manifest.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (manifest.description.isNotBlank()) {
                    Text(
                        manifest.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (!installed.runtimeAvailable) {
                    Text(
                        "运行环境不可用，点击查看详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineExtensionMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
