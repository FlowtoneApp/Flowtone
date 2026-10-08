package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.data.online.capability.SummaryCapability
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityAggregator
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityStatus
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.packageformat.ExtensionProviderVisualResolver
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldType
import ink.tenqui.flowtone.data.online.configuration.ConfigurationStateResolver
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.configuration.ExtensionConfigStore
import ink.tenqui.flowtone.data.online.configuration.extensionConfigurationDraft
import ink.tenqui.flowtone.data.online.configuration.validateExtensionConfiguration
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneModalPanel
import ink.tenqui.flowtone.app.ExtensionDiscardChangesConfirmation

internal data class ExtensionSettingsPresentation(
    val capabilities: List<SummaryCapability>,
    val configurationStatus: String,
    val credentialLabels: List<String>
)

internal enum class ExtensionSettingsBackResult { PerformBack, ConfirmDiscard }

internal fun extensionSettingsBackResult(
    draft: Map<String, ConfigurationValue>,
    baseline: Map<String, ConfigurationValue>
): ExtensionSettingsBackResult = if (draft == baseline) {
    ExtensionSettingsBackResult.PerformBack
} else {
    ExtensionSettingsBackResult.ConfirmDiscard
}

internal fun extensionSettingsPresentation(
    installed: InstalledExtension,
    savedValues: Map<String, ConfigurationValue> = emptyMap()
): ExtensionSettingsPresentation {
    val schema = installed.descriptor.configurationSchema
    val configurationState = ConfigurationStateResolver.resolve(schema, savedValues)
    return ExtensionSettingsPresentation(
        capabilities = SummaryCapabilityAggregator.aggregate(installed.descriptor.canonicalCapabilities),
        configurationStatus = when (configurationState) {
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.NotRequired -> "此扩展无需配置"
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.Unconfigured -> "尚未配置"
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.Incomplete -> "配置不完整"
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.Configured -> "已配置"
        },
        credentialLabels = installed.descriptor.credentialRequests.map { it.label }
    )
}

@Composable
internal fun ExtensionSettingsScreen(
    installed: InstalledExtension,
    pageScope: PageTransitionScope,
    onOpenNetworkAccess: (Set<NetworkOriginPermission>) -> Unit,
    onOpenCredentialGrants: (InstalledExtension) -> Unit,
    onUninstalled: () -> Unit,
    onBack: () -> Unit,
    onBackActionChange: ((() -> Unit)?) -> Unit,
    onDiscardChangesConfirmationChange: (ExtensionDiscardChangesConfirmation?) -> Unit,
    modifier: Modifier = Modifier
) {
    val manifest = installed.manifest
    val context = LocalContext.current
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val configStore = remember(context) { ExtensionConfigStore.from(context) }
    val scope = rememberCoroutineScope()
    var uninstalling by remember { mutableStateOf(false) }
    var savedValues by remember(installed.manifest.id) { mutableStateOf<Map<String, ConfigurationValue>>(emptyMap()) }
    var baseline by remember(installed.manifest.id) { mutableStateOf<Map<String, ConfigurationValue>>(emptyMap()) }
    var draft by remember(installed.manifest.id) { mutableStateOf<Map<String, ConfigurationValue>>(emptyMap()) }
    var errors by remember(installed.manifest.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedChoice by remember { mutableStateOf<ConfigurationFieldDefinition?>(null) }
    LaunchedEffect(installed.manifest.id, installed.descriptor.configurationSchema) {
        val loaded = withContext(Dispatchers.IO) { configStore.load(installed.manifest.id) }
        savedValues = loaded
        baseline = extensionConfigurationDraft(installed.descriptor.configurationSchema, loaded)
        draft = baseline
    }
    val presentation = extensionSettingsPresentation(installed, savedValues)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentDiscardChangesConfirmationChange by rememberUpdatedState(
        onDiscardChangesConfirmationChange
    )
    val requestBack = remember(draft, baseline) {
        {
            when (extensionSettingsBackResult(draft, baseline)) {
                ExtensionSettingsBackResult.PerformBack -> currentOnBack()
                ExtensionSettingsBackResult.ConfirmDiscard -> {
                    currentDiscardChangesConfirmationChange(
                        ExtensionDiscardChangesConfirmation(
                            onKeepEditing = {},
                            onDiscard = currentOnBack
                        )
                    )
                }
            }
        }
    }
    val currentBackActionChange by rememberUpdatedState(onBackActionChange)
    DisposableEffect(requestBack) {
        currentBackActionChange(requestBack)
        onDispose {
            currentBackActionChange(null)
            currentDiscardChangesConfirmationChange(null)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        ExtensionIdentityHeader(installed)
        ExtensionCapabilitiesSection(presentation.capabilities, pageScope.elementModifier(1))
        ExtensionNetworkSection(
            count = installed.descriptor.networkPermissions.size,
            onClick = { onOpenNetworkAccess(installed.descriptor.networkPermissions) },
            modifier = pageScope.elementModifier(2)
        )
        if (installed.descriptor.credentialRequests.isNotEmpty()) {
            ExtensionCredentialGrantsSection(
                count = installed.descriptor.credentialRequests.size,
                onClick = { onOpenCredentialGrants(installed) },
                modifier = pageScope.elementModifier(3)
            )
        }
        ExtensionConfigurationSection(
            fields = installed.descriptor.configurationSchema.fields,
            stateLabel = presentation.configurationStatus,
            draft = draft,
            errors = errors,
            saveEnabled = draft != baseline,
            onValueChange = { id, value ->
                draft = draft.toMutableMap().apply { if (value == null) remove(id) else put(id, value) }
                errors = errors - id
            },
            onChoiceClick = { selectedChoice = it },
            onSave = {
                val result = validateExtensionConfiguration(installed.descriptor.configurationSchema, draft)
                errors = result.errors
                if (result.errors.isEmpty()) {
                    scope.launch {
                        val persisted = withContext(Dispatchers.IO) {
                            configStore.save(installed.manifest.id, installed.descriptor.configurationSchema, draft)
                        }
                        extensionManager.refreshRuntimeConfiguration(installed.manifest.id)
                        savedValues = persisted
                        baseline = extensionConfigurationDraft(installed.descriptor.configurationSchema, persisted)
                        draft = baseline
                        Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = pageScope.elementModifier(4)
        )
        if (presentation.credentialLabels.isNotEmpty()) {
            ExtensionReadOnlySection(
                title = "凭证请求",
                values = presentation.credentialLabels,
                modifier = pageScope.elementModifier(5)
            )
        }
        // The action stays visually separated from the read-only descriptor data.
        TextButton(
            onClick = {
                if (uninstalling) return@TextButton
                scope.launch {
                    uninstalling = true
                    val result = uninstallInstalledExtension(extensionManager, manifest.id)
                    uninstalling = false
                    Toast.makeText(
                        context,
                        result.fold(
                            onSuccess = { "扩展已删除" },
                            onFailure = { it.message ?: "扩展删除失败" }
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (result.isSuccess) onUninstalled()
                }
            },
            enabled = !uninstalling,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("卸载扩展", color = MaterialTheme.colorScheme.error)
        }
    }
    selectedChoice?.let { field ->
        ExtensionChoiceOverlay(
            field = field,
            selected = (draft[field.id] as? ConfigurationValue.StringValue)?.value,
            onSelect = { value ->
                draft = draft.toMutableMap().apply { put(field.id, ConfigurationValue.StringValue(value)) }
                errors = errors - field.id
                selectedChoice = null
            },
            onDismiss = { selectedChoice = null }
        )
    }
    }
}

@Composable
private fun ExtensionIdentityHeader(installed: InstalledExtension) {
    val manifest = installed.manifest
    val status = if (installed.runtimeAvailable) "运行环境可用" else "运行环境不可用"
    val statusColor = if (installed.runtimeAvailable) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                ExtensionInstalledIcon(installed, Modifier.size(32.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(manifest.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "${manifest.version} · ${manifest.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (manifest.description.isNotBlank()) {
                Text(
                    text = manifest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor)
            }
        }
    }
}

@Composable
private fun ExtensionInstalledIcon(installed: InstalledExtension, modifier: Modifier = Modifier) {
    val visual = remember(installed) { ExtensionProviderVisualResolver.resolve(installed) }
    val iconFile = visual?.iconFile
    val fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
    if (iconFile == null) {
        Icon(
            painter = painterResource(R.drawable.ic_deployed_code_update),
            contentDescription = null,
            tint = fallbackTint,
            modifier = modifier
        )
        return
    }
    val context = LocalContext.current
    val imageLoader = remember(context) { ExtensionManager.get(context).extensionImageLoader }
    var imageLoaded by remember(iconFile) { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!imageLoaded) {
            Icon(
                painter = painterResource(R.drawable.ic_deployed_code_update),
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.fillMaxSize()
            )
        }
        AsyncImage(
            model = iconFile,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = visual.iconColor?.let { hex ->
                hex.removePrefix("#").toLongOrNull(16)?.let { ColorFilter.tint(androidx.compose.ui.graphics.Color((0xFF000000L or it).toInt())) }
            },
            onSuccess = { imageLoaded = true },
            onError = { imageLoaded = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ExtensionCapabilitiesSection(capabilities: List<SummaryCapability>, modifier: Modifier) {
    ExtensionSection(title = "功能支持", modifier = modifier) {
        capabilities.forEachIndexed { index, capability ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(capability.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = when (capability.status) {
                        SummaryCapabilityStatus.Supported -> "支持"
                        SummaryCapabilityStatus.Partial -> "部分支持"
                        SummaryCapabilityStatus.Unsupported -> "不支持"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (index != capabilities.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f))
        }
    }
}

@Composable
private fun ExtensionNetworkSection(count: Int, onClick: () -> Unit, modifier: Modifier) {
    ExtensionSection(title = "网络访问", modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text("$count 条网络访问规则", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Text("查看详情", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExtensionCredentialGrantsSection(count: Int, onClick: () -> Unit, modifier: Modifier) {
    ExtensionSection(title = "凭据授权", modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$count 项凭据请求",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text("管理授权", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExtensionReadOnlySection(title: String, values: List<String>, modifier: Modifier) {
    ExtensionSection(title, modifier) {
        values.forEachIndexed { index, value ->
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            if (index != values.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f))
        }
    }
}

@Composable
private fun ExtensionConfigurationSection(
    fields: List<ConfigurationFieldDefinition>,
    stateLabel: String,
    draft: Map<String, ConfigurationValue>,
    errors: Map<String, String>,
    saveEnabled: Boolean,
    onValueChange: (String, ConfigurationValue?) -> Unit,
    onChoiceClick: (ConfigurationFieldDefinition) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier
) {
    ExtensionSection("配置", modifier) {
        if (fields.isEmpty()) {
            Text(stateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Text(stateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            fields.forEach { field ->
                when (field.type) {
                    ConfigurationFieldType.Text, ConfigurationFieldType.Url -> {
                        val value = (draft[field.id] as? ConfigurationValue.StringValue)?.value.orEmpty()
                        OutlinedTextField(
                            value = value,
                            onValueChange = { onValueChange(field.id, ConfigurationValue.StringValue(it)) },
                            label = { Text(field.label) },
                            placeholder = field.placeholder?.let { { Text(it) } },
                            supportingText = {
                                Text(errors[field.id] ?: field.description.orEmpty())
                            },
                            isError = field.id in errors,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }
                    ConfigurationFieldType.Toggle -> {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(field.label, style = MaterialTheme.typography.bodyMedium)
                                field.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }
                            }
                            Switch(
                                checked = (draft[field.id] as? ConfigurationValue.BooleanValue)?.value ?: false,
                                onCheckedChange = { onValueChange(field.id, ConfigurationValue.BooleanValue(it)) }
                            )
                        }
                    }
                    ConfigurationFieldType.Choice -> {
                        val selected = (draft[field.id] as? ConfigurationValue.StringValue)?.value
                        val label = field.choices.firstOrNull { it.value == selected }?.label ?: "请选择"
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onChoiceClick(field) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(field.label, style = MaterialTheme.typography.bodyMedium)
                                field.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp)) }
                            }
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ConfigurationFieldType.Secret -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Text(field.label, style = MaterialTheme.typography.bodyMedium)
                            Text(field.description ?: "此配置需要安全存储支持，当前版本暂不可配置。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }
            Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) { Text("保存") }
        }
    }
}

@Composable
private fun ExtensionChoiceOverlay(
    field: ConfigurationFieldDefinition,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    FlowtoneModalOverlayShell(
        visible = true,
        scrimAlpha = .42f,
        panelProgress = 1f,
        panelScale = 1f,
        shadowSafePadding = 12.dp,
        onDismissRequest = onDismiss
    ) {
        FlowtoneModalPanel {
            Text(field.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            field.choices.forEach { choice ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(choice.value) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(choice.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (choice.value == selected) Text("已选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ExtensionSection(title: String, modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).let { if (onClick == null) it else it.clickable(onClick = onClick) },
            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer
        ) { Column(modifier = Modifier.padding(horizontal = 16.dp), content = { content() }) }
    }
}
