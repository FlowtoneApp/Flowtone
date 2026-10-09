package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityDefinitions
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageSnapshotHandle
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldType
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity

internal data class ExtensionInstallChangeItems(
    val capabilities: List<String> = emptyList(),
    val networkPermissions: List<String> = emptyList(),
    val credentialRequests: List<String> = emptyList(),
    val configurationFields: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = capabilities.isEmpty() && networkPermissions.isEmpty() &&
            credentialRequests.isEmpty() && configurationFields.isEmpty()
}

internal data class ExtensionSecurityDowngradePresentation(
    val previousOrigin: String,
    val incomingOrigin: String
)

internal data class ExtensionCredentialRequestPresentation(
    val label: String,
    val typeLabel: String,
    val realm: String?,
    val fields: List<String>,
    val identityConstraint: String?,
    val required: Boolean,
    val description: String?
)

internal data class ExtensionConfigurationFieldPresentation(
    val label: String,
    val required: Boolean,
    val unavailable: Boolean
)

internal data class ExtensionCredentialContractChangePresentation(
    val label: String,
    val previous: String,
    val incoming: String
)

internal data class ExtensionInstallIdentityPresentation(
    val name: String,
    val versionLine: String,
    val author: String,
    val description: String?,
    val operation: String
)

internal fun extensionInstallIdentityPresentation(
    preview: ExtensionInstallPreview
): ExtensionInstallIdentityPresentation = ExtensionInstallIdentityPresentation(
    name = preview.incomingManifest.name,
    versionLine = preview.existingInstallation?.let { existing ->
        "版本 ${existing.identity.version} → ${preview.identity.version}"
    } ?: "版本 ${preview.identity.version}",
    author = preview.incomingManifest.author,
    description = preview.incomingManifest.description.takeIf(String::isNotBlank),
    operation = if (preview.isUpdate) "扩展更新" else "首次安装"
)

internal data class ExtensionInstallActionPresentation(val label: String, val enabled: Boolean)

internal fun extensionInstallActionPresentation(
    preview: ExtensionInstallPreview,
    installing: Boolean
): ExtensionInstallActionPresentation = ExtensionInstallActionPresentation(
    label = if (installing) "正在处理…" else if (preview.isUpdate) "更新扩展" else "安装扩展",
    enabled = !installing && preview.snapshotHandle != null
)

internal fun extensionCredentialRequestPresentation(
    request: CredentialRequestDefinition
): ExtensionCredentialRequestPresentation {
    val contract = request.contract
    return ExtensionCredentialRequestPresentation(
        label = request.label,
        typeLabel = request.credentialType.label,
        realm = contract.realm,
        fields = contract.fields.map { it.label },
        identityConstraint = contract.identifiers
            .takeIf { it.isNotEmpty() }
            ?.map { it.field.label }
            ?.sorted()
            ?.joinToString("、", prefix = "身份标识至少填写一项："),
        required = request.required,
        description = request.description
    )
}

internal fun extensionInstallConfigurationFields(
    preview: ExtensionInstallPreview
): List<ExtensionConfigurationFieldPresentation> = preview.configurationSchema.fields.map { field ->
    ExtensionConfigurationFieldPresentation(
        label = field.label,
        required = field.required,
        unavailable = field.type == ConfigurationFieldType.Secret
    )
}

internal fun extensionCredentialContractDescription(request: CredentialRequestDefinition): String {
    val view = extensionCredentialRequestPresentation(request)
    return buildList {
        add(view.typeLabel)
        view.realm?.let { add("服务：$it") }
        add("字段：${view.fields.joinToString("、")}")
        view.identityConstraint?.let(::add)
    }.joinToString(" · ")
}

internal data class ExtensionInstallOverlayPresentation(
    val title: String,
    val actionLabel: String,
    val versionLine: String,
    val configurationSummary: String?,
    val hasInsecureNetworkPermissions: Boolean,
    val added: ExtensionInstallChangeItems,
    val removed: ExtensionInstallChangeItems,
    val changedCredentialRequests: List<ExtensionCredentialContractChangePresentation>,
    val securityDowngrades: List<ExtensionSecurityDowngradePresentation>
) {
    val hasUpdateChanges: Boolean
        get() = !added.isEmpty || !removed.isEmpty || changedCredentialRequests.isNotEmpty() ||
            securityDowngrades.isNotEmpty()
}

internal fun extensionInstallOverlayPresentation(
    preview: ExtensionInstallPreview
): ExtensionInstallOverlayPresentation {
    val diff = preview.updateDiff
    val downgradeOld = diff?.securityDowngrades.orEmpty().mapTo(hashSetOf()) { it.previous }
    val downgradeNew = diff?.securityDowngrades.orEmpty().mapTo(hashSetOf()) { it.incoming }
    return ExtensionInstallOverlayPresentation(
        title = if (preview.isUpdate) "更新扩展？" else "安装扩展？",
        actionLabel = if (preview.isUpdate) "更新" else "安装",
        versionLine = preview.existingInstallation?.let { existing ->
            "${existing.identity.version} → ${preview.identity.version}"
        } ?: preview.identity.version,
        configurationSummary = preview.configurationSummary.requiredFields
            .takeIf { it.isNotEmpty() }
            ?.let { fields ->
                val labels = fields.joinToString("、") { it.label }
                if (preview.configurationSummary.omittedFieldCount > 0) {
                    "$labels 等 ${fields.size + preview.configurationSummary.omittedFieldCount} 项"
                } else {
                    labels
                }
            },
        hasInsecureNetworkPermissions = preview.networkPermissions.any {
            it.security == NetworkSecurity.Insecure
        },
        added = ExtensionInstallChangeItems(
            capabilities = diff?.addedCapabilities.orEmpty()
                .sortedBy { AtomicCapabilityDefinitions.get(it).order }
                .map { AtomicCapabilityDefinitions.get(it).label },
            networkPermissions = diff?.addedNetworkPermissions.orEmpty()
                .filterNot { it in downgradeNew }
                .map(Any::toString)
                .sorted(),
            credentialRequests = diff?.addedCredentialRequests.orEmpty().map {
                "${it.label} · ${extensionCredentialContractDescription(it)}"
            },
            configurationFields = diff?.addedRequiredFields.orEmpty().map { it.label }
        ),
        removed = ExtensionInstallChangeItems(
            capabilities = diff?.removedCapabilities.orEmpty()
                .sortedBy { AtomicCapabilityDefinitions.get(it).order }
                .map { AtomicCapabilityDefinitions.get(it).label },
            networkPermissions = diff?.removedNetworkPermissions.orEmpty()
                .filterNot { it in downgradeOld }
                .map(Any::toString)
                .sorted(),
            credentialRequests = diff?.removedCredentialRequests.orEmpty().map {
                "${it.label} · ${extensionCredentialContractDescription(it)}"
            },
            configurationFields = diff?.removedFields.orEmpty().map { it.label }
        ),
        changedCredentialRequests = diff?.changedCredentialRequests.orEmpty().map { change ->
            ExtensionCredentialContractChangePresentation(
                label = change.incoming.label,
                previous = extensionCredentialContractDescription(change.previous),
                incoming = extensionCredentialContractDescription(change.incoming)
            )
        },
        securityDowngrades = diff?.securityDowngrades.orEmpty().map {
            ExtensionSecurityDowngradePresentation(
                previousOrigin = it.previous.toString(),
                incomingOrigin = it.incoming.toString()
            )
        }
    )
}

internal fun shouldShowExtensionUpdateChanges(
    preview: ExtensionInstallPreview,
    presentation: ExtensionInstallOverlayPresentation
): Boolean = preview.isUpdate

internal const val ExtensionUpdateNoChangesLabel = "无权限变化"

internal suspend fun inspectReplacingExtensionPreview(
    current: ExtensionInstallPreview?,
    discard: (ExtensionPackageSnapshotHandle) -> Unit,
    inspect: suspend () -> ExtensionInstallPreview
): Result<ExtensionInstallPreview> {
    current?.snapshotHandle?.let(discard)
    return runCatching { inspect() }
}

internal fun discardExtensionPreview(
    preview: ExtensionInstallPreview?,
    discard: (ExtensionPackageSnapshotHandle) -> Unit
) {
    preview?.snapshotHandle?.let(discard)
}

internal suspend fun confirmExtensionPreview(
    preview: ExtensionInstallPreview,
    install: suspend (ExtensionPackageSnapshotHandle) -> InstalledExtension
): Result<InstalledExtension> {
    val handle = preview.snapshotHandle
        ?: return Result.failure(IllegalStateException("安装预览缺少 snapshot handle"))
    return runCatching { install(handle) }
}
