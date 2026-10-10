package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.rememberArtworkBackgroundColor

@Composable
internal fun ExtensionInstallScreen(
    preview: ExtensionInstallPreview,
    installing: Boolean,
    onConfirm: () -> Unit,
    onOpenNetworkAccess: () -> Unit,
    pageScope: PageTransitionScope,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val extractedIconColor = rememberArtworkBackgroundColor(
        artworkData = preview.icon?.bytes,
        imageLoader = context.imageLoader,
        fallbackColor = colors.primaryContainer,
        isDarkTheme = colors.background.luminance() <= 0.5f
    ) ?: colors.primaryContainer
    val iconColor = preview.incomingManifest.color?.let(::extensionThemeColor) ?: extractedIconColor
    val presentation = remember(preview) { extensionInstallOverlayPresentation(preview) }
    val action = remember(preview, installing) { extensionInstallActionPresentation(preview, installing) }
    val credentialViews = remember(preview) { preview.credentialRequests.map(::extensionCredentialRequestPresentation) }
    val configurationViews = remember(preview) { extensionInstallConfigurationFields(preview) }
    val hasNetwork = preview.networkPermissions.isNotEmpty()
    val hasCredentials = credentialViews.isNotEmpty()
    val hasConfiguration = configurationViews.isNotEmpty()
    val orderCount = 3 + listOf(preview.isUpdate, hasNetwork, hasCredentials, hasConfiguration).count { it }
    var order = 0

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ExtensionPreviewIdentity(
                preview = preview,
                accent = iconColor,
                modifier = pageScope.elementModifier(order++, orderCount)
            )
            if (preview.isUpdate) {
                ExtensionUpdateChangesSection(
                    presentation = presentation,
                    modifier = pageScope.elementModifier(order++, orderCount).padding(top = 20.dp)
                )
            }
            if (hasNetwork) {
                ExtensionPreviewNetworkSection(
                    preview = preview,
                    onOpenNetworkAccess = onOpenNetworkAccess,
                    enabled = !installing,
                    modifier = pageScope.elementModifier(order++, orderCount).padding(top = 26.dp)
                )
            }
            if (hasCredentials) {
                ExtensionPreviewCredentialsSection(
                    requests = credentialViews,
                    modifier = pageScope.elementModifier(order++, orderCount).padding(top = 26.dp)
                )
            }
            OnlineCapabilitySection(
                preview.summaryCapabilities,
                pageScope.elementModifier(order++, orderCount).padding(top = 26.dp)
            )
            if (hasConfiguration) {
                ExtensionPreviewConfigurationSection(
                    configurationViews,
                    pageScope.elementModifier(order++, orderCount).padding(top = 26.dp)
                )
            }
        }

        Column(
            modifier = pageScope.elementModifier(order, orderCount)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            val previewAvailable = preview.snapshotHandle != null
            if (!previewAvailable) {
                Text(
                    "安装预览不可用，请重新选择扩展包。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Button(
                onClick = { if (action.enabled) onConfirm() },
                enabled = action.enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                if (installing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    action.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ExtensionPreviewIdentity(
    preview: ExtensionInstallPreview,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val identity = remember(preview) { extensionInstallIdentityPresentation(preview) }
    val shape = RoundedCornerShape(28.dp)
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(modifier = Modifier.clip(shape)) {
            ExtensionInstallHeaderCloud(
                color = accent,
                modifier = Modifier.matchParentSize()
            )
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    identity.operation,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = accent.copy(alpha = .7f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            preview.icon?.let { icon ->
                                AsyncImage(
                                    model = icon.bytes,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(10.dp)
                                )
                            } ?: Icon(
                                Icons.Rounded.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            identity.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            identity.versionLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            "开发者：${identity.author}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                identity.description?.let { description ->
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionUpdateChangesSection(
    presentation: ExtensionInstallOverlayPresentation,
    modifier: Modifier = Modifier
) {
    OptionGroup(title = "本次更新的变化", modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SettingsRowCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!presentation.hasUpdateChanges) {
                    Text(ExtensionUpdateNoChangesLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "功能、网络规则、凭据请求和配置要求没有可报告的变化。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                } else {
                    if (!presentation.added.isEmpty) {
                        ExtensionChangeGroup("新增", presentation.added)
                    }
                    if (!presentation.removed.isEmpty) {
                        if (!presentation.added.isEmpty) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        ExtensionChangeGroup("移除", presentation.removed)
                    }
                    if (presentation.changedCredentialRequests.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("凭据请求范围变化", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        presentation.changedCredentialRequests.forEach { change ->
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(change.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("原范围：${change.previous}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                                Text("新范围：${change.incoming}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                        Text(
                            "若已授权这些请求，旧契约对应的授权将在更新时清理；更新后需重新授权。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    if (presentation.securityDowngrades.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(
                            "安全性降低",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        presentation.securityDowngrades.forEach { downgrade ->
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(downgrade.previousOrigin, style = MaterialTheme.typography.bodyMedium)
                                Text("↓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                Text(downgrade.incomingOrigin, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text(
                            "这些声明从 HTTPS 变为 HTTP；当前 Flowtone 运行时仍不支持 HTTP 访问。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionChangeGroup(title: String, items: ExtensionInstallChangeItems) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    listOf(
        "功能" to items.capabilities,
        "网络" to items.networkPermissions,
        "凭据" to items.credentialRequests,
        "配置" to items.configurationFields
    ).forEach { (category, values) ->
        values.forEach { value ->
            Text(
                "• $category：$value",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
            )
        }
    }
}

@Composable
private fun ExtensionPreviewNetworkSection(
    preview: ExtensionInstallPreview,
    onOpenNetworkAccess: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val permissions = remember(preview) { preview.networkPermissions.sortedBy(Any::toString) }
    val hasHttp = permissions.any { it.security == NetworkSecurity.Insecure }
    OptionGroup(title = "网络访问 · ${permissions.size} 条", modifier = modifier) {
            Column(modifier = Modifier.fillMaxWidth()) {
                permissions.forEachIndexed { index, permission ->
                    Text(permission.toString(), style = MaterialTheme.typography.bodyMedium)
                    if (index != permissions.lastIndex) HorizontalDivider(Modifier.padding(vertical = 9.dp))
                }
                TextButton(onClick = onOpenNetworkAccess, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("查看权限详情")
                }
        }
        if (hasHttp) {
            Text(
                "已声明 HTTP 地址，但当前 Flowtone 运行时不支持扩展通过 HTTP 访问网络。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ExtensionPreviewCredentialsSection(
    requests: List<ExtensionCredentialRequestPresentation>,
    modifier: Modifier = Modifier
) {
    OptionGroup(title = "凭据请求 · ${requests.size} 项", modifier = modifier) {
            Column(modifier = Modifier.fillMaxWidth()) {
                requests.forEachIndexed { index, request ->
                    Column {
                        Text(request.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            request.typeLabel + if (request.required) " · 使用相关功能时需要" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        request.realm?.let { realm ->
                            Text("服务：$realm", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                        Text("请求字段：${request.fields.joinToString("、")}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        request.identityConstraint?.let { constraint ->
                            Text(constraint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                        request.description?.takeIf(String::isNotBlank)?.let { description ->
                            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                    if (index != requests.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                }
        }
        Text(
            "安装扩展不等于授权使用账户凭据。安装后仍需在凭据授权页面由你明确选择并确认。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 9.dp)
        )
    }
}

@Composable
private fun ExtensionPreviewConfigurationSection(
    fields: List<ExtensionConfigurationFieldPresentation>,
    modifier: Modifier = Modifier
) {
    OptionGroup(title = "安装后配置", modifier = modifier) {
            Column(modifier = Modifier.fillMaxWidth()) {
                fields.forEachIndexed { index, field ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(field.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (field.required) "必填" else "可选",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    if (field.unavailable) {
                        Text(
                            "此 Secret 配置项在当前版本暂不可填写。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (index != fields.lastIndex) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                }
        }
        Text(
            "安装预览不会写入扩展配置；安装后可在扩展详情页填写。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 9.dp)
        )
    }
}

@Composable
private fun ExtensionInstallHeaderCloud(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val radius = size.width
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = .26f),
                        color.copy(alpha = .12f),
                        color.copy(alpha = .04f),
                        Color.Transparent
                    ),
                    center = Offset(size.width, 0f),
                    radius = radius
                ),
                radius = radius,
                center = Offset(size.width, 0f)
            )
        }
    )
}

private fun extensionThemeColor(value: String): Color? {
    val rgb = value.removePrefix("#").toLongOrNull(16) ?: return null
    if (rgb !in 0x000000L..0xFFFFFFL) return null
    return Color((0xFF000000L or rgb).toInt())
}
