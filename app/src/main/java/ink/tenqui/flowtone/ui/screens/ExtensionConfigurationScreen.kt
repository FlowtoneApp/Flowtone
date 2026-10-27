package ink.tenqui.flowtone.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
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
import ink.tenqui.flowtone.ui.components.settingsOptionPalette
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
    val optionPalette = settingsOptionPalette()
    val iconAccent = rememberInstalledExtensionIconAccent(installed, optionPalette.isDarkTheme)
    val optionSurfaceColor = optionPalette.surfaceColorWithAccent(
        iconAccent,
        ExtensionCardTintBlend
    )

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
    var failureDialog by remember(extensionId) {
        mutableStateOf<ExtensionConfigurationFailure?>(null)
    }
    var saving by remember(extensionId) { mutableStateOf(false) }
    var selectedChoice by remember(extensionId) {
        mutableStateOf<ConfigurationFieldDefinition?>(null)
    }

    LaunchedEffect(extensionId, schema) {
        configLoading = true
        try {
            val loaded = withContext(Dispatchers.IO) { configStore.load(extensionId) }
            savedValues = loaded
            val loadedDraft = extensionConfigurationDraft(schema, loaded)
            baseline = loadedDraft
            draft = loadedDraft
            errors = emptyMap()
            configError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            configError = "配置读取失败，请查看错误详情。"
            failureDialog = ExtensionConfigurationFailure(
                title = "读取扩展配置失败",
                details = extensionConfigurationFailureDetails("读取扩展配置时发生错误。", error)
            )
        } finally {
            configLoading = false
        }
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
                    saveFeedback?.startsWith("保存失败") == true ||
                    saveFeedback?.startsWith("配置已保存，但") == true,
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
                        failureDialog = null
                    }
                },
                onChoiceClick = { if (!saving) selectedChoice = it },
                onSave = {
                    if (!saving && !configLoading && configError == null) {
                        val validation = try {
                            validateExtensionConfiguration(schema, draft)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            failureDialog = ExtensionConfigurationFailure(
                                title = "验证扩展配置失败",
                                details = extensionConfigurationFailureDetails(
                                    "验证扩展配置时发生错误。",
                                    error
                                )
                            )
                            null
                        }
                        if (validation != null) {
                            errors = validation.errors
                            if (validation.errors.isEmpty()) {
                                val submitted = draft
                                saving = true
                                saveFeedback = null
                                failureDialog = null
                                scope.launch {
                                    try {
                                        val persisted = withContext(Dispatchers.IO) {
                                            configStore.save(extensionId, schema, submitted)
                                        }
                                        savedValues = persisted
                                        val persistedDraft = extensionConfigurationDraft(schema, persisted)
                                        baseline = persistedDraft
                                        draft = persistedDraft

                                        val refreshFailure = try {
                                            val runtimeRefreshed = withContext(Dispatchers.IO) {
                                                extensionManager.refreshRuntimeConfiguration(extensionId)
                                            }
                                            if (runtimeRefreshed) {
                                                null
                                            } else {
                                                val error = IllegalStateException(
                                                    "扩展 runtime 不可用，或配置快照刷新返回 false。扩展 ID：$extensionId"
                                                )
                                                ExtensionConfigurationFailure(
                                                    title = "配置已保存，但新配置尚未生效",
                                                    details = extensionConfigurationFailureDetails(
                                                        "配置已写入，但无法刷新扩展 runtime。",
                                                        error
                                                    )
                                                )
                                            }
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Throwable) {
                                            ExtensionConfigurationFailure(
                                                title = "配置已保存，但新配置尚未生效",
                                                details = extensionConfigurationFailureDetails(
                                                    "配置已写入，但刷新扩展 runtime 时发生错误。",
                                                    error
                                                )
                                            )
                                        }

                                        if (refreshFailure == null) {
                                            saveFeedback = "配置已保存"
                                        } else {
                                            saveFeedback = "配置已保存，但新配置尚未生效"
                                            failureDialog = refreshFailure
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Throwable) {
                                        saveFeedback = "保存失败，请查看错误详情。"
                                        failureDialog = ExtensionConfigurationFailure(
                                            title = "保存扩展配置失败",
                                            details = extensionConfigurationFailureDetails(
                                                "写入扩展配置时发生错误。",
                                                error
                                            )
                                        )
                                    } finally {
                                        saving = false
                                    }
                                }
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
                        failureDialog = null
                    }
                    selectedChoice = null
                },
                onDismiss = { selectedChoice = null }
            )
        }

        failureDialog?.let { failure ->
            AlertDialog(
                onDismissRequest = { failureDialog = null },
                title = { Text(failure.title) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = failure.details,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            copyConfigurationFailure(context, failure.details)
                        }
                    ) {
                        Text("复制错误详情")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { failureDialog = null }) {
                        Text("关闭")
                    }
                }
            )
        }
    }
}

private data class ExtensionConfigurationFailure(
    val title: String,
    val details: String
)

private fun extensionConfigurationFailureDetails(
    context: String,
    error: Throwable
): String = context + "\n\n" + error.stackTraceToString()

private fun copyConfigurationFailure(context: android.content.Context, details: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("扩展配置错误", details))
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, "已复制错误详情", Toast.LENGTH_SHORT).show()
    }
}