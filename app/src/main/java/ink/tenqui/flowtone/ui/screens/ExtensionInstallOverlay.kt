package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityStatus
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneModalPanel
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelExitScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistPanelStartScale
import ink.tenqui.flowtone.ui.library.CreatePlaylistScrimMaxAlpha
import ink.tenqui.flowtone.ui.library.CreatePlaylistShadowSafePadding

@Composable
internal fun ExtensionInstallOverlay(
    preview: ExtensionInstallPreview,
    closing: Boolean,
    installing: Boolean,
    onDismissRequest: () -> Unit,
    onDismissAnimationFinished: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = remember(preview) { extensionInstallOverlayPresentation(preview) }
    val panelProgress = remember(preview.snapshotHandle) { Animatable(0f) }
    val currentDismissFinished by rememberUpdatedState(onDismissAnimationFinished)
    val scrimAlpha = CreatePlaylistScrimMaxAlpha * panelProgress.value.coerceIn(0f, 1f)

    LaunchedEffect(preview.snapshotHandle, closing) {
        if (closing) {
            panelProgress.animateTo(
                0f,
                tween(FlowtoneMotion.DurationMillis / 2, easing = FlowtoneMotion.Easing)
            )
            currentDismissFinished()
        } else {
            panelProgress.snapTo(0f)
            panelProgress.animateTo(
                1f,
                tween(FlowtoneMotion.DurationMillis, easing = FlowtoneMotion.Easing)
            )
        }
    }

    val panelMinScale = if (closing) {
        CreatePlaylistPanelExitScale
    } else {
        CreatePlaylistPanelStartScale
    }
    val panelScale = panelMinScale +
        (1f - panelMinScale) * panelProgress.value.coerceIn(0f, 1f)

    FlowtoneModalOverlayShell(
        visible = true,
        scrimAlpha = scrimAlpha,
        panelProgress = panelProgress.value,
        panelScale = panelScale,
        shadowSafePadding = CreatePlaylistShadowSafePadding,
        onDismissRequest = {
            if (!installing && !closing) onDismissRequest()
        },
        modifier = modifier.fillMaxSize()
    ) {
        val maximumPanelHeight = (maxHeight - 32.dp).coerceAtLeast(240.dp)
        FlowtoneModalPanel(
            modifier = Modifier.heightIn(max = maximumPanelHeight),
            maxWidth = 560.dp,
            horizontalPadding = 16.dp
        ) {
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 18.dp)
            ) {
                IdentitySection(preview, presentation)
                CapabilitySummarySection(preview)
                NetworkPermissionSection(preview, presentation)
                ConfigurationSection(preview, presentation)
                CredentialRequestSection(preview)
                if (preview.isUpdate && presentation.hasUpdateChanges) {
                    UpdateChangesSection(presentation)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissRequest,
                    enabled = !installing && !closing
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    enabled = !installing && !closing,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (installing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(if (installing) "处理中" else presentation.actionLabel)
                }
            }
        }
    }
}

@Composable
private fun IdentitySection(
    preview: ExtensionInstallPreview,
    presentation: ExtensionInstallOverlayPresentation
) {
    Text(
        text = preview.identity.name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        text = "${presentation.versionLine} · ${preview.identity.author}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp)
    )
    preview.incomingManifest.description.trim().takeIf(String::isNotEmpty)?.let { description ->
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
internal fun CapabilitySummarySection(preview: ExtensionInstallPreview) {
    InstallSection(title = "功能支持") {
        preview.summaryCapabilities.forEach { capability ->
            val (statusText, statusColor) = when (capability.status) {
                SummaryCapabilityStatus.Supported ->
                    "支持" to MaterialTheme.colorScheme.primary
                SummaryCapabilityStatus.Partial ->
                    "部分支持" to MaterialTheme.colorScheme.tertiary
                SummaryCapabilityStatus.Unsupported ->
                    "不支持" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = capability.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
internal fun NetworkPermissionSection(
    preview: ExtensionInstallPreview,
    presentation: ExtensionInstallOverlayPresentation
) {
    if (preview.networkPermissions.isEmpty()) return
    InstallSection(title = "网络访问") {
        preview.networkPermissions.sortedBy(Any::toString).forEach { permission ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = permission.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (permission.security == NetworkSecurity.Insecure) {
                    Text(
                        text = "* 不安全",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
        if (presentation.hasInsecureNetworkPermissions) {
            Text(
                text = "通过这些地址传输的数据可能不会被加密。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "当前 Flowtone 版本暂不支持扩展通过 HTTP 进行网络访问。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
internal fun ConfigurationSection(
    preview: ExtensionInstallPreview,
    presentation: ExtensionInstallOverlayPresentation
) {
    if (!preview.requiresConfiguration) return
    InstallSection(title = "安装后需要配置") {
        Text(
            text = presentation.configurationSummary.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun CredentialRequestSection(preview: ExtensionInstallPreview) {
    if (preview.credentialRequests.isEmpty()) return
    InstallSection(title = "凭证访问") {
        preview.credentialRequests.forEachIndexed { index, request ->
            if (index > 0) Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = request.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "此扩展支持请求使用 Flowtone 中保存的 ${request.credentialType.label} 凭证。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (request.required) {
                Text(
                    text = "使用相关功能时可能需要提供 ${request.credentialType.label} 凭证。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
internal fun UpdateChangesSection(presentation: ExtensionInstallOverlayPresentation) {
    InstallSection(title = "权限变化") {
        if (!presentation.added.isEmpty) {
            ChangeItems(title = "新增", items = presentation.added)
        }
        if (!presentation.removed.isEmpty) {
            ChangeItems(
                title = "不再需要",
                items = presentation.removed,
                modifier = Modifier.padding(top = if (presentation.added.isEmpty) 0.dp else 14.dp)
            )
        }
        if (presentation.securityDowngrades.isNotEmpty()) {
            Text(
                text = "安全性降低",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    top = if (presentation.added.isEmpty && presentation.removed.isEmpty) 0.dp else 14.dp
                )
            )
            presentation.securityDowngrades.forEach { downgrade ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${downgrade.previousOrigin}  →  ${downgrade.incomingOrigin}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "* 不安全",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChangeItems(
    title: String,
    items: ExtensionInstallChangeItems,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        ChangeLines("功能", items.capabilities)
        ChangeLines("网络", items.networkPermissions)
        ChangeLines("凭证", items.credentialRequests)
        ChangeLines("配置", items.configurationFields)
    }
}

@Composable
internal fun ChangeLines(category: String, values: List<String>) {
    values.forEach { value ->
        Text(
            text = "• $category：$value",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
internal fun InstallSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(vertical = 9.dp)
        )
        content()
    }
}
