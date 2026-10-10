package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.PageTransitionScope

@Composable
internal fun OnlineSettingsSectionPage(
    pageScope: PageTransitionScope,
    onOpenExtensions: () -> Unit,
    onOpenCredentialSources: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 32.dp)
    ) {
        item(key = "online-heading") {
            OnlineSectionHeading(
                title = "在线管理",
                subtitle = "管理扩展和账户凭据",
                modifier = pageScope.elementModifier(0, 3).padding(bottom = 10.dp)
            )
        }
        item(key = "extensions") {
            OnlineManagementEntry(
                title = "扩展",
                subtitle = "安装和管理在线服务扩展",
                onClick = onOpenExtensions,
                modifier = pageScope.elementModifier(1, 3).padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        item(key = "credentials") {
            OnlineManagementEntry(
                title = "凭据",
                subtitle = "创建和管理账户凭据",
                onClick = onOpenCredentialSources,
                modifier = pageScope.elementModifier(2, 3).padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
