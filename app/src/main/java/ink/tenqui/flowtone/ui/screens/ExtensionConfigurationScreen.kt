package ink.tenqui.flowtone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.ExtensionDiscardChangesConfirmation
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.configuration.ExtensionConfigStore
import ink.tenqui.flowtone.data.online.configuration.extensionConfigurationDraft
import ink.tenqui.flowtone.data.online.configuration.validateExtensionConfiguration
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.SettingsOptionSurfaceColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ExtensionConfigurationScreen(
    installed: InstalledExtension,
    pageScope: PageTransitionScope,
    onBack: () -> Unit,
    onBackActionChange: ((() -> Unit)?) -> Unit,
    onDiscardChangesConfirmationChange: (ExtensionDiscardChangesConfirmation?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val extensionId = installed.manifest.id
    val schema = installed.descriptor.configurationSchema
    val configStore = remember(context) { ExtensionConfigStore.from(context) }
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val scope = rememberCoroutineScope()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val iconAccent = rememberInstalledExtensionIconAccent(installed, isDarkTheme)
    val defaultOptionSurfaceColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        SettingsOptionSurfaceColor
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val optionSurfaceColor = iconAccent?.let { accent ->
        lerp(defaultOptionSurfaceColor, accent, ExtensionCardTintBlend)
    }

    var savedValues by remember(extensionId) {
        mutableStateOf<Map<String, ConfigurationValue>>(emptyMap())
    }
    var baseline by remember(extensionId) {
        mutableStateOf<Map<String, ConfigurationValue>>(emptyMap())
    }
    var draft by remember(extensionId) {
        mutableStateOf<Map<String, ConfigurationValue>>(emptyMap())
    }
    var errors by remember(extensionId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var configLoading by remember(extensionId) { mutableStateOf(true) }
    var configError by remember(extensionId) { mutableStateOf<String?>(null) }
    var saveFeedback by remember(extensionId) { mutableStateOf<String?>(null) }
    var saving by remember(extensionId) { mutableStateOf(false) }
    var selectedChoice by remember(extensionId) {
        mutableStateOf<ConfigurationFieldDefinition?>(null)
    }

    LaunchedEffect(extensionId, schema) {
        configLoading = true
        runCatching { withContext(Dispatchers.IO) { configStore.load(extensionId) } }
            .onSuccess { loaded ->
                savedValues = loaded
                val loadedDraft = extensionConfigurationDraft(schema, loaded)
                baseline = loadedDraft
                draft = loadedDraft
                errors = emptyMap()
                configError = null
            }
            .onFailure {
                if (it is CancellationException) throw it
                configError = "配置读取失败，请重试。"
            }
        configLoading = false
    }

    val presentation = remember(installed, savedValues) {
        extensionSettingsPresentation(installed, savedValues)
    }
    val stateLabel = extensionConfigurationDisplayStatus(
        savedStatus = presentation.configurationStatus,
        dirty = draft != baseline,
        saving = saving,
        loading = configLoading
    )
    val currentOnBack by rememberUpdatedState(onBack)
    val currentDiscardChangesConfirmationChange by rememberUpdatedState(
        onDiscardChangesConfirmationChange
    )
    val requestBack = remember(draft, baseline, saving) {
        {
            if (!saving) {
                when (extensionSettingsBackResult(draft, baseline)) {
                    ExtensionSettingsBackResult.PerformBack -> currentOnBack()
                    ExtensionSettingsBackResult.ConfirmDiscard ->
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

    BackHandler(onBack = requestBack)

    Box(modifier = modifier.fillMaxSize()) {
        SettingsPageColumn(modifier = Modifier.imePadding()) {
            ExtensionConfigurationSection(
                schema = schema,
                stateLabel = stateLabel,
                draft = draft,
                errors = errors,
                feedback = configError ?: saveFeedback,
                feedbackIsError = configError != null ||
                    saveFeedback?.startsWith("保存失败") == true,
                busy = saving || configLoading || configError != null,
                saveEnabled = draft != baseline && !saving && !configLoading &&
                    configError == null,
                onValueChange = { id, value ->
                    if (!saving) {
                        draft = draft.toMutableMap().apply {
                            if (value == null) remove(id) else put(id, value)
                        }
                        val relatedFieldIds = schema.atLeastOneGroups
                            .filter { id in it.fieldIds }
                            .flatMap { it.fieldIds }
                        errors = errors - relatedFieldIds - id
                        saveFeedback = null
                    }
                },
                onChoiceClick = { if (!saving) selectedChoice = it },
                onSave = {
                    if (!saving && !configLoading && configError == null) {
                        val validation = validateExtensionConfiguration(schema, draft)
                        errors = validation.errors
                        if (validation.errors.isEmpty()) {
                            val submitted = draft
                            saving = true
                            saveFeedback = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        configStore.save(extensionId, schema, submitted)
                                    }
                                }.onSuccess { persisted ->
                                    savedValues = persisted
                                    val persistedDraft = extensionConfigurationDraft(schema, persisted)
                                    baseline = persistedDraft
                                    draft = persistedDraft
                                    val runtimeRefreshed = runCatching {
                                        withContext(Dispatchers.IO) {
                                            extensionManager.refreshRuntimeConfiguration(extensionId)
                                        }
                                    }.getOrElse {
                                        if (it is CancellationException) throw it
                                        false
                                    }
                                    saveFeedback = if (runtimeRefreshed) {
                                        "配置已保存"
                                    } else {
                                        "配置已保存，但新配置尚未生效"
                                    }
                                }.onFailure {
                                    if (it is CancellationException) throw it
                                    saveFeedback = "保存失败，请重试。"
                                }
                                saving = false
                            }
                        }
                    }
                },
                modifier = pageScope.elementModifier(0, 1),
                cardSurfaceColor = optionSurfaceColor
            )
        }

        selectedChoice?.let { field ->
            ExtensionChoiceOverlay(
                field = field,
                selected = (draft[field.id] as? ConfigurationValue.StringValue)?.value,
                onSelect = { value ->
                    if (!saving) {
                        draft = draft.toMutableMap().apply {
                            put(field.id, ConfigurationValue.StringValue(value))
                        }
                        val relatedFieldIds = schema.atLeastOneGroups
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
    }
}