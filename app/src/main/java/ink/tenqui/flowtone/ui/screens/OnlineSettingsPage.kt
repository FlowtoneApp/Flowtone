package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.data.online.ProviderEntityCapability
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.ui.components.OptionGroup

@Composable
internal fun OnlineSettingsPage(
    installedExtensions: List<InstalledExtension>,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        ExtensionInstallCard(onClick = onInstall, modifier = elementModifier(0))

        if (installedExtensions.isEmpty()) {
            ExtensionEmptyState(modifier = elementModifier(1).padding(top = 24.dp))
        } else {
            OptionGroup(
                title = "已安装扩展",
                modifier = elementModifier(1).padding(top = 24.dp)
            ) {
                installedExtensions.forEachIndexed { index, installed ->
                    InstalledExtensionCard(
                        installed = installed,
                        onUninstall = { onUninstall(installed.manifest.id) },
                        modifier = elementModifier(index + 2)
                            .padding(top = if (index == 0) 0.dp else 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionInstallCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(SettingsRowCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SettingsRowHorizontalPadding,
                vertical = SettingsRowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_deployed_code_update),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SettingsRowIconSize)
            )
            Spacer(modifier = Modifier.width(SettingsSectionRowIconGap))
            Column {
                Text(
                    text = "安装新扩展",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "从设备中选择扩展包",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
                )
            }
        }
    }
}

@Composable
private fun InstalledExtensionCard(
    installed: InstalledExtension,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val manifest = installed.manifest
    val capabilities = listOfNotNull(
        "歌手头像".takeIf { manifest.supportsArtistAvatar },
        "歌手信息".takeIf { manifest.supportsArtistMetadata },
        "音乐服务".takeIf { manifest.supportsMusicProvider },
        "歌曲实体".takeIf { ProviderEntityCapability.Song in manifest.providerEntityCapabilities },
        "专辑实体".takeIf { ProviderEntityCapability.Album in manifest.providerEntityCapabilities }
    ).joinToString(" · ").ifBlank { "当前版本不支持" }
    val statusColor = if (installed.runtimeAvailable) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }
    val status = if (installed.runtimeAvailable) "运行环境可用" else "运行环境不可用"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsRowCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = SettingsRowHorizontalPadding,
                vertical = SettingsRowVerticalPadding
            )
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painter = painterResource(R.drawable.ic_deployed_code_update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(SettingsRowIconSize)
                )
                Spacer(modifier = Modifier.width(SettingsSectionRowIconGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manifest.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${manifest.version} · ${manifest.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
                    )
                }
            }

            if (manifest.description.isNotBlank()) {
                Text(
                    text = manifest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Text(
                text = "能力：$capabilities",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onUninstall) {
                    Text(text = "卸载", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ExtensionEmptyState(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsRowCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SettingsRowHorizontalPadding,
                vertical = SettingsRowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_deployed_code_update),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(SettingsRowIconSize)
            )
            Spacer(modifier = Modifier.width(SettingsSectionRowIconGap))
            Column {
                Text(
                    text = "尚未安装在线扩展",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "在线能力可以通过扩展提供",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
                )
            }
        }
    }
}
