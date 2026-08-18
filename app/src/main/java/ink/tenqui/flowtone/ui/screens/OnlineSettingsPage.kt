package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.R

@Composable
internal fun OnlineSettingsPage(
    installedExtensions: List<InstalledExtension>,
    onInstall: () -> Unit,
    onUninstall: (String) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        Surface(
            modifier = elementModifier(0)
                .fillMaxWidth()
                .height(88.dp)
                .clickable(onClick = onInstall),
            shape = RoundedCornerShape(SettingsRowCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_deployed_code_update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "安装新扩展",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        installedExtensions.forEachIndexed { index, installed ->
            val manifest = installed.manifest
            Surface(
                modifier = elementModifier(index + 1)
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 16.dp else 0.dp),
                shape = RoundedCornerShape(SettingsRowCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(Modifier.padding(SettingsRowHorizontalPadding, SettingsRowVerticalPadding)) {
                    Text(manifest.name, style = MaterialTheme.typography.titleMedium)
                    Text("${manifest.version} · ${manifest.author}")
                    Text("能力：${if (manifest.supportsArtistAvatar) "歌手头像" else "当前版本不支持"}")
                    Text("状态：${if (installed.runtimeAvailable) "已安装" else "运行环境不可用"}")
                    TextButton(onClick = { onUninstall(manifest.id) }) { Text("删除扩展") }
                }
            }
        }
    }
}
