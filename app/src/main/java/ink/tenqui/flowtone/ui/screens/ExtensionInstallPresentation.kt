package ink.tenqui.flowtone.ui.screens

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityDefinitions
import ink.tenqui.flowtone.data.online.packageformat.ExtensionInstallPreview
import ink.tenqui.flowtone.data.online.packageformat.ExtensionPackageSnapshotHandle
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
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

internal data class ExtensionInstallOverlayPresentation(
    val title: String,
    val actionLabel: String,
    val versionLine: String,
    val configurationSummary: String?,
    val hasInsecureNetworkPermissions: Boolean,
    val added: ExtensionInstallChangeItems,
    val removed: ExtensionInstallChangeItems,
    val securityDowngrades: List<ExtensionSecurityDowngradePresentation>
) {
    val hasUpdateChanges: Boolean
        get() = !added.isEmpty || !removed.isEmpty || securityDowngrades.isNotEmpty()
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
            credentialRequests = diff?.addedCredentialRequests.orEmpty().map { it.label },
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
            credentialRequests = diff?.removedCredentialRequests.orEmpty().map { it.label },
            configurationFields = diff?.removedFields.orEmpty().map { it.label }
        ),
        securityDowngrades = diff?.securityDowngrades.orEmpty().map {
            ExtensionSecurityDowngradePresentation(
                previousOrigin = it.previous.toString(),
                incomingOrigin = it.incoming.toString()
            )
        }
    )
}

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
