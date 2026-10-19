package ink.tenqui.flowtone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchema
import ink.tenqui.flowtone.data.online.configuration.ConfigurationStateResolver
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.configuration.ExtensionConfigStore
import ink.tenqui.flowtone.data.online.configuration.extensionConfigurationDraft
import ink.tenqui.flowtone.data.online.configuration.validateExtensionConfiguration
import ink.tenqui.flowtone.data.online.credential.CredentialGrantEvaluation
import ink.tenqui.flowtone.data.online.credential.CredentialGrantRepository
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.ui.components.FlowtoneSwitch
import ink.tenqui.flowtone.ui.components.FlowtoneModalOverlayShell
import ink.tenqui.flowtone.ui.components.FlowtoneModalPanel
import ink.tenqui.flowtone.ui.components.SettingsCategoryCard
import ink.tenqui.flowtone.ui.components.SettingsOptionSurface
import ink.tenqui.flowtone.ui.components.SettingsOptionSurfaceColor
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ExtensionCardTintBlend = 0.08f
private const val ExtensionIconBackgroundTintBlend = 0.28f

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
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.NotRequired ->
                if (schema.fields.isEmpty()) "此扩展无需配置" else "可选配置"
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.Unconfigured -> "需要配置"
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.Incomplete -> "需要配置"
            ink.tenqui.flowtone.data.online.configuration.ConfigurationState.Configured -> "必需配置已完成"
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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val extensionIconAccent = rememberInstalledExtensionIconAccent(installed, isDarkTheme)
    val defaultOptionSurfaceColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        SettingsOptionSurfaceColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val extensionOptionSurfaceColor = extensionIconAccent?.let { iconAccent ->
        lerp(defaultOptionSurfaceColor, iconAccent, ExtensionCardTintBlend)
    }
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
    var hasBeenCurrent by remember(extensionId) { mutableStateOf(false) }
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
    LaunchedEffect(pageScope.phase) {
        if (pageScope.phase == ink.tenqui.flowtone.ui.components.PageTransitionPhase.Current) {
            if (hasBeenCurrent) refreshGrants()
            hasBeenCurrent = true
        }
    }

    val presentation = remember(installed, savedValues) { extensionSettingsPresentation(installed, savedValues) }
    val attentionMessages = buildList {
        if (configError != null) add("扩展配置读取失败，请重新进入页面。")
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

    BackHandler(onBack = requestBack)

    Box(modifier = modifier.fillMaxSize()) {
        SettingsPageColumn(modifier = Modifier.imePadding()) {
            ExtensionIdentityHeader(
                installed = installed,
                iconAccent = extensionIconAccent,
                modifier = pageScope.elementModifier(0, 5)
            )
            if (attentionMessages.isNotEmpty()) {
                ExtensionAttentionSection(attentionMessages, pageScope.elementModifier(1, 5))
            }
            ExtensionManagementSection(
                credentialCount = installed.descriptor.credentialRequests.size,
                grantSummary = when {
                    grantError -> "授权状态读取失败"
                    grantSummary == null -> "正在读取授权状态…"
                    else -> grantSummary!!.label
                },
                networkCount = installed.descriptor.networkPermissions.size,
                iconAccent = extensionIconAccent,
                onOpenGrants = { onOpenCredentialGrants(installed) },
                onOpenNetwork = { onOpenNetworkAccess(installed.descriptor.networkPermissions) },
                modifier = pageScope.elementModifier(2, 5).padding(top = 26.dp)
            )
            ExtensionConfigurationSection(
                schema = installed.descriptor.configurationSchema,
                cardSurfaceColor = extensionOptionSurfaceColor,
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
                        val relatedFieldIds = installed.descriptor.configurationSchema.atLeastOneGroups
                            .filter { id in it.fieldIds }
                            .flatMap { it.fieldIds }
                        errors = errors - relatedFieldIds - id
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
                                    saveFeedback = if (runtimeRefreshed) "配置已保存" else "配置已保存，但新配置尚未生效"
                                }.onFailure {
                                    if (it is CancellationException) throw it
                                    saveFeedback = "保存失败，请重试。"
                                }
                                saving = false
                            }
                        }
                    }
                },
                modifier = pageScope.elementModifier(3, 5).padding(top = 26.dp)
            )
            Column(
                modifier = pageScope.elementModifier(4, 5).padding(top = 30.dp).fillMaxWidth()
            ) {
                OnlineSectionHeading("移除扩展")
                SettingsOptionSurface(
                    modifier = Modifier.padding(top = 12.dp),
                    surfaceColor = extensionOptionSurfaceColor
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
            }
        }

        selectedChoice?.let { field ->
            ExtensionChoiceOverlay(
                field = field,
                selected = (draft[field.id] as? ConfigurationValue.StringValue)?.value,
                onSelect = { value ->
                    if (!saving) {
                        draft = draft.toMutableMap().apply { put(field.id, ConfigurationValue.StringValue(value)) }
                        val relatedFieldIds = installed.descriptor.configurationSchema.atLeastOneGroups
                            .filter { field.id in it.fieldIds }
                            .flatMap { it.fieldIds }
                        errors = errors - relatedFieldIds - field.id
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
private fun ExtensionIdentityHeader(
    installed: InstalledExtension,
    iconAccent: Color?,
    modifier: Modifier = Modifier
) {
    val manifest = installed.manifest
    val context = LocalContext.current
    val iconBackgroundColor = iconAccent?.let { accent ->
        lerp(MaterialTheme.colorScheme.surfaceContainer, accent, ExtensionIconBackgroundTintBlend)
    } ?: MaterialTheme.colorScheme.surfaceContainer
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = iconBackgroundColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ExtensionInstalledIcon(installed, Modifier.size(36.dp))
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(manifest.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "版本 ${manifest.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                val authorUrl = manifest.authorUrl
                Text(
                    text = "@${manifest.author.removePrefix("@")}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = if (authorUrl == null) TextDecoration.None else TextDecoration.Underline
                    ),
                    color = if (authorUrl == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(enabled = authorUrl != null) {
                            authorUrl?.let { context.openExternalUrl(it) }
                        }
                )
            }
        }
        if (manifest.description.isNotBlank()) {
            var descriptionExpanded by remember(manifest.description) { mutableStateOf(false) }
            var descriptionOverflows by remember(manifest.description) { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    manifest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { result ->
                        if (!descriptionExpanded && descriptionOverflows != result.didOverflowHeight) {
                            descriptionOverflows = result.didOverflowHeight
                        }
                    }
                )
                if (descriptionExpanded || descriptionOverflows) {
                    OutlinedButton(
                        onClick = { descriptionExpanded = !descriptionExpanded },
                        shape = RoundedCornerShape(percent = 50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                    ) {
                        Text(
                            if (descriptionExpanded) "收起简介" else "展开简介",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionAttentionSection(messages: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 20.dp)) {
        OnlineSectionHeading("状态提醒")
        messages.forEach { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ExtensionConfigurationSection(
    schema: ConfigurationSchema,
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
    modifier: Modifier = Modifier,
    cardSurfaceColor: Color? = null
) {
    val fields = schema.fields
    if (fields.isEmpty()) return
    val fieldsById = fields.associateBy { it.id }
    val requiredFields = fields.filter(ConfigurationFieldDefinition::required)
    val groupedFieldIds = schema.atLeastOneGroups.flatMapTo(linkedSetOf()) { it.fieldIds }
    val optionalFields = fields.filter { !it.required && it.id !in groupedFieldIds }
    val renderedGroupFieldIds = mutableSetOf<String>()

    Column(modifier = modifier.fillMaxWidth()) {
        OnlineSectionHeading(title = "扩展配置", subtitle = stateLabel)
        if (requiredFields.isNotEmpty() || schema.atLeastOneGroups.isNotEmpty()) {
            Text(
                "需要配置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 14.dp)
            )
            requiredFields.forEach { field ->
                ExtensionConfigurationOption(
                    modifier = Modifier.padding(top = 8.dp),
                    surfaceColor = cardSurfaceColor
                ) {
                    ExtensionConfigurationField(field, draft, errors, busy, onValueChange, onChoiceClick)
                }
            }
            schema.atLeastOneGroups.forEach { group ->
                val labels = group.fieldIds.mapNotNull { fieldsById[it]?.label }
                if (labels.isNotEmpty()) {
                    Text(
                        "至少填写一项：" + labels.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    group.fieldIds.forEach { fieldId ->
                        fieldsById[fieldId]?.let { field ->
                            if (!field.required && renderedGroupFieldIds.add(field.id)) {
                                ExtensionConfigurationOption(
                                    modifier = Modifier.padding(top = 8.dp),
                                    surfaceColor = cardSurfaceColor
                                ) {
                                    ExtensionConfigurationField(
                                        field, draft, errors, busy, onValueChange, onChoiceClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (optionalFields.isNotEmpty()) {
            Text(
                "可选配置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp)
            )
            optionalFields.forEach { field ->
                ExtensionConfigurationOption(
                    modifier = Modifier.padding(top = 8.dp),
                    surfaceColor = cardSurfaceColor
                ) {
                    ExtensionConfigurationField(field, draft, errors, busy, onValueChange, onChoiceClick)
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

@Composable
private fun ExtensionConfigurationOption(
    modifier: Modifier = Modifier,
    surfaceColor: Color? = null,
    content: @Composable () -> Unit
) {
    SettingsOptionSurface(modifier = modifier, surfaceColor = surfaceColor) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ExtensionConfigurationField(
    field: ConfigurationFieldDefinition,
    draft: Map<String, ConfigurationValue>,
    errors: Map<String, String>,
    busy: Boolean,
    onValueChange: (String, ConfigurationValue?) -> Unit,
    onChoiceClick: (ConfigurationFieldDefinition) -> Unit
) {
    when (field.type) {
        ConfigurationFieldType.Text, ConfigurationFieldType.Url -> {
            val value = (draft[field.id] as? ConfigurationValue.StringValue)?.value.orEmpty()
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(field.id, ConfigurationValue.StringValue(it)) },
                label = { Text(field.label + if (field.required) "（必须）" else "（可选）") },
                placeholder = field.placeholder?.let { { Text(it) } },
                supportingText = { Text(errors[field.id] ?: field.description.orEmpty()) },
                isError = field.id in errors,
                enabled = !busy,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ConfigurationFieldType.Toggle -> {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConfigurationFieldTitle(field, Modifier.weight(1f))
                    FlowtoneSwitch(
                        checked = (draft[field.id] as? ConfigurationValue.BooleanValue)?.value ?: false,
                        onCheckedChange = { onValueChange(field.id, ConfigurationValue.BooleanValue(it)) },
                        enabled = !busy
                    )
                }
                field.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                errors[field.id]?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        ConfigurationFieldType.Choice -> {
            val selected = (draft[field.id] as? ConfigurationValue.StringValue)?.value
            val choiceLabel = field.choices.firstOrNull { it.value == selected }?.label ?: "请选择"
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onChoiceClick(field) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConfigurationFieldTitle(field, Modifier.weight(1f))
                    Text(
                        choiceLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                field.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                errors[field.id]?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        ConfigurationFieldType.Secret -> {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                ConfigurationFieldTitle(field)
                Text(
                    field.description ?: "此配置需要安全存储支持，当前版本暂不可配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConfigurationFieldTitle(field: ConfigurationFieldDefinition, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(field.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (field.required) "必须" else "可选",
            style = MaterialTheme.typography.labelSmall,
            color = if (field.required) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ExtensionManagementSection(
    credentialCount: Int,
    grantSummary: String,
    networkCount: Int,
    iconAccent: Color?,
    onOpenGrants: () -> Unit,
    onOpenNetwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categorySurfaceColor = iconAccent?.let { accent ->
        lerp(SettingsOptionSurfaceColor, accent, ExtensionCardTintBlend)
    } ?: SettingsOptionSurfaceColor
    val credentialIconContainerColor = iconAccent?.let { accent ->
        lerp(MaterialTheme.colorScheme.primaryContainer, accent, ExtensionIconBackgroundTintBlend)
    } ?: MaterialTheme.colorScheme.primaryContainer
    val networkIconContainerColor = iconAccent?.let { accent ->
        lerp(MaterialTheme.colorScheme.secondaryContainer, accent, ExtensionIconBackgroundTintBlend)
    } ?: MaterialTheme.colorScheme.secondaryContainer
    Column(modifier = modifier.fillMaxWidth()) {
        OnlineSectionHeading("访问与授权")
        if (credentialCount > 0) {
            SettingsCategoryCard(
                title = "凭据授权",
                subtitle = grantSummary,
                icon = Icons.Rounded.Key,
                iconContainerColor = credentialIconContainerColor,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenGrants,
                modifier = Modifier.padding(top = 12.dp),
                surfaceColor = categorySurfaceColor
            )
        }
        SettingsCategoryCard(
            title = "网络访问",
            subtitle = if (networkCount == 0) "未申请网络访问"
                else "$networkCount 条访问规则 · 查看完整目标",
            icon = Icons.Rounded.Language,
            iconContainerColor = networkIconContainerColor,
            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onOpenNetwork,
            modifier = Modifier.padding(top = 12.dp),
            surfaceColor = categorySurfaceColor
        )
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
