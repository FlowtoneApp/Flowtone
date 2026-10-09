package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ink.tenqui.flowtone.app.ExtensionDiscardChangesConfirmation
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.capability.SummaryCapability
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityAggregator
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldType
import ink.tenqui.flowtone.data.online.configuration.ConfigurationStateResolver
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.configuration.ExtensionConfigStore
import ink.tenqui.flowtone.data.online.configuration.extensionConfigurationDraft
import ink.tenqui.flowtone.data.online.configuration.validateExtensionConfiguration
import ink.tenqui.flowtone.data.online.credential.CredentialGrantEvaluation
import ink.tenqui.flowtone.data.online.credential.CredentialGrantRepository
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneModalPanel
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ExtensionSettingsPresentation(
    val capabilities: List<SummaryCapability>,
    val configurationStatus: String,
    val credentialLabels: List<String>
)

internal data class ExtensionGrantSummary(
    val requestCount: Int,
    val authorizedCount: Int,
    val readyCount: Int
) {
    val label: String get() = "$authorizedCount 项已授权 · ${requestCount - authorizedCount} 项未授权" +
        if (authorizedCount > readyCount) " · ${authorizedCount - readyCount} 项凭据未就绪" else ""
}

internal fun extensionGrantSummary(evaluations: List<CredentialGrantEvaluation>): ExtensionGrantSummary =
    ExtensionGrantSummary(
        requestCount = evaluations.size,
        authorizedCount = evaluations.count { it.authorizationValid },
        readyCount = evaluations.count { it.usable }
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

internal fun extensionConfigurationDisplayStatus(
    savedStatus: String,
    dirty: Boolean,
    saving: Boolean,
    loading: Boolean
): String = when {
    loading -> "正在读取配置…"
    saving -> "正在保存…"
    dirty -> "有未保存的更改"
    else -> savedStatus
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val configStore = remember(context) { ExtensionConfigStore.from(context) }
    val grantRepository = remember(context) { CredentialGrantRepository.from(context) }
    val scope = rememberCoroutineScope()
    val extensionId = installed.manifest.id
    var savedValues by remember(extensionId) { mutableStateOf<Map<String, ConfigurationValue>>(emptyMap()) }
    var baseline by remember(extensionId) { mutableStateOf<Map<String, ConfigurationValue>>(emptyMap()) }
    var draft by remember(extensionId) { mutableStateOf<Map<String, ConfigurationValue>>(emptyMap()) }
    var errors by remember(extensionId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var configLoading by remember(extensionId) { mutableStateOf(true) }
    var configError by remember(extensionId) { mutableStateOf<String?>(null) }
    var saveFeedback by remember(extensionId) { mutableStateOf<String?>(null) }
    var saving by remember(extensionId) { mutableStateOf(false) }
    var selectedChoice by remember(extensionId) { mutableStateOf<ConfigurationFieldDefinition?>(null) }
    var grantSummary by remember(extensionId) { mutableStateOf<ExtensionGrantSummary?>(null) }
    var grantError by remember(extensionId) { mutableStateOf(false) }
    var grantRefreshing by remember(extensionId) { mutableStateOf(false) }
    var confirmUninstall by remember(extensionId) { mutableStateOf(false) }
    var uninstalling by remember(extensionId) { mutableStateOf(false) }
    var uninstallError by remember(extensionId) { mutableStateOf<String?>(null) }

    fun refreshGrants() {
        if (grantRefreshing || installed.descriptor.credentialRequests.isEmpty()) return
        grantRefreshing = true
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val current = extensionManager.installedExtensions()
                        .firstOrNull { it.manifest.id == extensionId }
                    installed.descriptor.credentialRequests.map { request ->
                        grantRepository.evaluate(current, request.id)
                    }
                }
            }.onSuccess { evaluations ->
                grantSummary = extensionGrantSummary(evaluations)
                grantError = false
            }.onFailure {
                if (it is CancellationException) throw it
                grantError = true
            }
            grantRefreshing = false
        }
    }

    LaunchedEffect(extensionId, installed.descriptor.configurationSchema) {
        configLoading = true
        runCatching { withContext(Dispatchers.IO) { configStore.load(extensionId) } }
            .onSuccess { loaded ->
                savedValues = loaded
                baseline = extensionConfigurationDraft(installed.descriptor.configurationSchema, loaded)
                draft = baseline
                configError = null
            }
            .onFailure {
                if (it is CancellationException) throw it
                configError = "配置读取失败，请重新进入页面。"
            }
        configLoading = false
    }
    LaunchedEffect(extensionId) { refreshGrants() }
    DisposableEffect(lifecycleOwner, extensionId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshGrants()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val presentation = remember(installed, savedValues) { extensionSettingsPresentation(installed, savedValues) }
    val attentionMessages = buildList {
        if (!installed.runtimeAvailable) add("运行环境当前不可用，扩展功能可能无法使用。")
        if (configError != null) add("扩展配置读取失败，请重新进入页面。")
        else if (!configLoading && presentation.configurationStatus in setOf("尚未配置", "配置不完整")) {
            add("扩展配置尚未完成。")
        }
        if (grantError) add("凭据授权状态读取失败，请进入授权页检查。")
        grantSummary?.let { summary ->
            if (summary.authorizedCount < summary.requestCount) add("部分凭据请求尚未授权。")
            if (summary.readyCount < summary.authorizedCount) add("部分已授权凭据当前不可用。")
        }
    }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentDiscardChangesConfirmationChange by rememberUpdatedState(onDiscardChangesConfirmationChange)
    val requestBack = remember(draft, baseline, saving, uninstalling) {
        {
            if (!saving && !uninstalling) {
                when (extensionSettingsBackResult(draft, baseline)) {
                    ExtensionSettingsBackResult.PerformBack -> currentOnBack()
                    ExtensionSettingsBackResult.ConfirmDiscard -> currentDiscardChangesConfirmationChange(
                        ExtensionDiscardChangesConfirmation(onKeepEditing = {}, onDiscard = currentOnBack)
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
        SettingsPageColumn {
            ExtensionIdentityHeader(installed, pageScope.elementModifier(0, 7))
            if (attentionMessages.isNotEmpty()) {
                ExtensionAttentionSection(attentionMessages, pageScope.elementModifier(1, 7))
            }
            ExtensionConfigurationSection(
                fields = installed.descriptor.configurationSchema.fields,
                stateLabel = extensionConfigurationDisplayStatus(
                    presentation.configurationStatus, draft != baseline, saving, configLoading
                ),
                draft = draft,
                errors = errors,
                feedback = configError ?: saveFeedback,
                feedbackIsError = configError != null || saveFeedback?.startsWith("保存失败") == true,
                busy = saving || configLoading || configError != null,
                saveEnabled = draft != baseline && !saving && !configLoading && configError == null,
                onValueChange = { id, value ->
                    if (!saving) {
                        draft = draft.toMutableMap().apply { if (value == null) remove(id) else put(id, value) }
                        errors = errors - id
                        saveFeedback = null
                    }
                },
                onChoiceClick = { if (!saving) selectedChoice = it },
                onSave = {
                    if (!saving && !configLoading && configError == null) {
                        val validation = validateExtensionConfiguration(installed.descriptor.configurationSchema, draft)
                        errors = validation.errors
                        if (validation.errors.isEmpty()) {
                            val submitted = draft
                            saving = true
                            saveFeedback = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        configStore.save(extensionId, installed.descriptor.configurationSchema, submitted)
                                    }
                                }.onSuccess { persisted ->
                                    savedValues = persisted
                                    baseline = extensionConfigurationDraft(installed.descriptor.configurationSchema, persisted)
                                    draft = baseline
                                    val runtimeRefreshed = runCatching {
                                        withContext(Dispatchers.IO) {
                                            extensionManager.refreshRuntimeConfiguration(extensionId)
                                        }
                                    }.getOrElse {
                                        if (it is CancellationException) throw it
                                        false
                                    }
                                    saveFeedback = if (runtimeRefreshed) "配置已保存" else "配置已保存，运行环境尚未刷新"
                                }.onFailure {
                                    if (it is CancellationException) throw it
                                    saveFeedback = "保存失败，请重试。"
                                }
                                saving = false
                            }
                        }
                    }
                },
                modifier = pageScope.elementModifier(2, 7).padding(top = 24.dp)
            )

            if (installed.descriptor.credentialRequests.isNotEmpty()) {
                ExtensionCredentialGrantsSection(
                    summary = when {
                        grantError -> "授权状态读取失败"
                        grantSummary == null -> "正在读取授权状态…"
                        else -> grantSummary!!.label
                    },
                    count = installed.descriptor.credentialRequests.size,
                    onClick = { onOpenCredentialGrants(installed) },
                    modifier = pageScope.elementModifier(3, 7).padding(top = 24.dp)
                )
            }
            ExtensionNetworkSection(
                count = installed.descriptor.networkPermissions.size,
                onClick = { onOpenNetworkAccess(installed.descriptor.networkPermissions) },
                modifier = pageScope.elementModifier(4, 7).padding(top = 24.dp)
            )
            OnlineCapabilitySection(
                presentation.capabilities,
                pageScope.elementModifier(5, 7).padding(top = 24.dp)
            )
            OptionGroup(title = "卸载", modifier = pageScope.elementModifier(6, 7).padding(top = 28.dp)) {
                TextButton(
                    onClick = { if (!uninstalling) confirmUninstall = true },
                    enabled = !uninstalling && !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uninstalling) "正在卸载…" else "卸载扩展", color = MaterialTheme.colorScheme.error)
                }
                uninstallError?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        selectedChoice?.let { field ->
            ExtensionChoiceOverlay(
                field = field,
                selected = (draft[field.id] as? ConfigurationValue.StringValue)?.value,
                onSelect = { value ->
                    if (!saving) {
                        draft = draft.toMutableMap().apply { put(field.id, ConfigurationValue.StringValue(value)) }
                        errors = errors - field.id
                        saveFeedback = null
                    }
                    selectedChoice = null
                },
                onDismiss = { selectedChoice = null }
            )
        }
        if (confirmUninstall) {
            ExtensionUninstallConfirmation(
                name = installed.manifest.name,
                onDismiss = { confirmUninstall = false },
                onConfirm = {
                    if (!uninstalling) {
                        confirmUninstall = false
                        uninstalling = true
                        uninstallError = null
                        scope.launch {
                            val result = uninstallInstalledExtension(extensionManager, extensionId)
                            if (result.isSuccess) {
                                onUninstalled()
                            } else {
                                uninstallError = "卸载失败，请重试。"
                            }
                            uninstalling = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExtensionIdentityHeader(installed: InstalledExtension, modifier: Modifier = Modifier) {
    val manifest = installed.manifest
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                ExtensionInstalledIcon(installed, Modifier.size(36.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                manifest.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "版本 ${manifest.version} · ${manifest.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (manifest.description.isNotBlank()) {
                Text(
                    manifest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Text(
                if (installed.runtimeAvailable) "运行环境可用" else "运行环境不可用",
                style = MaterialTheme.typography.labelMedium,
                color = if (installed.runtimeAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun ExtensionAttentionSection(messages: List<String>, modifier: Modifier = Modifier) {
    OptionGroup(title = "需要处理", modifier = modifier.padding(top = 24.dp)) {
        messages.forEach { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun ExtensionConfigurationSection(
    fields: List<ConfigurationFieldDefinition>,
    stateLabel: String,
    draft: Map<String, ConfigurationValue>,
    errors: Map<String, String>,
    feedback: String?,
    feedbackIsError: Boolean,
    busy: Boolean,
    saveEnabled: Boolean,
    onValueChange: (String, ConfigurationValue?) -> Unit,
    onChoiceClick: (ConfigurationFieldDefinition) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    OptionGroup(title = "扩展配置", modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SettingsRowCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                fields.forEach { field ->
                    when (field.type) {
                        ConfigurationFieldType.Text, ConfigurationFieldType.Url -> {
                            val value = (draft[field.id] as? ConfigurationValue.StringValue)?.value.orEmpty()
                            OutlinedTextField(
                                value = value,
                                onValueChange = { onValueChange(field.id, ConfigurationValue.StringValue(it)) },
                                label = { Text(field.label) },
                                placeholder = field.placeholder?.let { { Text(it) } },
                                supportingText = { Text(errors[field.id] ?: field.description.orEmpty()) },
                                isError = field.id in errors,
                                enabled = !busy,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            )
                        }
                        ConfigurationFieldType.Toggle -> {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(field.label, style = MaterialTheme.typography.bodyMedium)
                                    field.description?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = (draft[field.id] as? ConfigurationValue.BooleanValue)?.value ?: false,
                                    onCheckedChange = { onValueChange(field.id, ConfigurationValue.BooleanValue(it)) },
                                    enabled = !busy
                                )
                            }
                        }
                        ConfigurationFieldType.Choice -> {
                            val selected = (draft[field.id] as? ConfigurationValue.StringValue)?.value
                            val label = field.choices.firstOrNull { it.value == selected }?.label ?: "请选择"
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onChoiceClick(field) }.padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(field.label, style = MaterialTheme.typography.bodyMedium)
                                    field.description?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        ConfigurationFieldType.Secret -> {
                            Text(
                                field.description ?: "此配置需要安全存储支持，当前版本暂不可配置。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
                feedback?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (feedbackIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (fields.isNotEmpty()) {
                    Button(
                        onClick = onSave,
                        enabled = saveEnabled,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)
                    ) { Text(if (busy && stateLabel == "正在保存…") "正在保存…" else "保存配置") }
                }
            }
        }
    }
}

@Composable
private fun ExtensionCredentialGrantsSection(
    count: Int,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OptionGroup(title = "凭据授权", modifier = modifier) {
        OnlineManagementEntry(
            title = "$count 项凭据请求",
            subtitle = summary,
            onClick = onClick
        ) {
            Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SettingsRowIconSize))
        }
    }
}

@Composable
private fun ExtensionNetworkSection(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OptionGroup(title = "网络访问", modifier = modifier) {
        OnlineManagementEntry(
            title = if (count == 0) "未声明网络访问规则" else "$count 条网络访问规则",
            subtitle = if (count == 0) "查看网络访问详情" else "访问范围由扩展声明，请核对允许的服务",
            onClick = onClick
        ) {
            Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SettingsRowIconSize))
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
private fun ExtensionUninstallConfirmation(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    FlowtoneModalOverlayShell(
        visible = true,
        scrimAlpha = .42f,
        panelProgress = 1f,
        panelScale = 1f,
        shadowSafePadding = 12.dp,
        onDismissRequest = onDismiss
    ) {
        FlowtoneModalPanel {
            Text("卸载扩展？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "确定卸载“$name”？此扩展的配置和凭据授权也会被移除。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = onConfirm) { Text("卸载", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
