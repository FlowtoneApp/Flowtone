package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.SummaryCapability
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityAggregator
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchema
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchemaValidator
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialRequestIdentity
import ink.tenqui.flowtone.data.online.credential.CredentialRequestValidator
import ink.tenqui.flowtone.data.online.credential.identity
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.data.online.permission.NetworkSecurityDowngrade
import ink.tenqui.flowtone.data.online.permission.hasSameAuthoritySemanticsAs

data class ConfigurationRequirementSummary(
    val requiredFields: List<ConfigurationFieldSummary>,
    val omittedFieldCount: Int
)

data class ConfigurationFieldSummary(
    val id: String,
    val label: String
)

/** 仅包含经包边界校验的图标字节；不向 UI 暴露 preview snapshot 的文件路径。 */
data class ExtensionPreviewIcon(
    val bytes: ByteArray
)

data class ExtensionInstallPreview(
    val incoming: NormalizedExtensionDescriptor,
    val summaryCapabilities: List<SummaryCapability>,
    val configurationSummary: ConfigurationRequirementSummary,
    val existingInstallation: NormalizedExtensionDescriptor?,
    val updateDiff: ExtensionUpdateDiff?,
    val snapshotHandle: ExtensionPackageSnapshotHandle? = null,
    val icon: ExtensionPreviewIcon? = null
) {
    val incomingManifest: ExtensionManifest get() = incoming.manifest
    val identity: ExtensionIdentity get() = incoming.identity
    val canonicalCapabilities get() = incoming.canonicalCapabilities
    val configurationSchema get() = incoming.configurationSchema
    val requiresConfiguration: Boolean get() = configurationSchema.requiresConfiguration
    val networkPermissions get() = incoming.networkPermissions
    val credentialRequests get() = incoming.credentialRequests
    val isUpdate: Boolean get() = existingInstallation != null
}

data class ExtensionUpdateDiff(
    val addedCapabilities: Set<AtomicCapabilityId>,
    val removedCapabilities: Set<AtomicCapabilityId>,
    val addedNetworkPermissions: Set<NetworkOriginPermission>,
    val removedNetworkPermissions: Set<NetworkOriginPermission>,
    val securityDowngrades: List<NetworkSecurityDowngrade>,
    val addedCredentialRequests: List<CredentialRequestDefinition>,
    val removedCredentialRequests: List<CredentialRequestDefinition>,
    val addedRequiredFields: List<ConfigurationFieldDefinition>,
    val removedFields: List<ConfigurationFieldDefinition>
) {
    val hasPermissionChanges: Boolean
        get() = addedNetworkPermissions.isNotEmpty() || removedNetworkPermissions.isNotEmpty()
    val hasSecurityDowngrade: Boolean get() = securityDowngrades.isNotEmpty()
    val hasNewCredentialRequests: Boolean get() = addedCredentialRequests.isNotEmpty()
    val hasRemovedPermissions: Boolean get() = removedNetworkPermissions.isNotEmpty()

    companion object {
        fun between(
            previous: NormalizedExtensionDescriptor,
            incoming: NormalizedExtensionDescriptor
        ): ExtensionUpdateDiff {
            val addedNetworkPermissions = incoming.networkPermissions - previous.networkPermissions
            val removedNetworkPermissions = previous.networkPermissions - incoming.networkPermissions
            val securityDowngrades = removedNetworkPermissions
                .filter { it.security == NetworkSecurity.Secure }
                .flatMap { oldPermission ->
                    addedNetworkPermissions
                        .filter { newPermission ->
                            newPermission.security == NetworkSecurity.Insecure &&
                                oldPermission.hasSameAuthoritySemanticsAs(newPermission)
                        }
                        .map { newPermission -> NetworkSecurityDowngrade(oldPermission, newPermission) }
                }

            val previousCredentials = previous.credentialRequests.associateBy { it.identity }
            val incomingCredentials = incoming.credentialRequests.associateBy { it.identity }
            val previousFields = previous.configurationSchema.fields.associateBy { it.id }
            val incomingFields = incoming.configurationSchema.fields.associateBy { it.id }

            return ExtensionUpdateDiff(
                addedCapabilities = incoming.canonicalCapabilities.values -
                    previous.canonicalCapabilities.values,
                removedCapabilities = previous.canonicalCapabilities.values -
                    incoming.canonicalCapabilities.values,
                addedNetworkPermissions = addedNetworkPermissions,
                removedNetworkPermissions = removedNetworkPermissions,
                securityDowngrades = securityDowngrades,
                addedCredentialRequests = incomingCredentials
                    .filterKeys { it !in previousCredentials }
                    .values
                    .toList(),
                removedCredentialRequests = previousCredentials
                    .filterKeys { it !in incomingCredentials }
                    .values
                    .toList(),
                addedRequiredFields = incomingFields.values.filter { incomingField ->
                    incomingField.required && previousFields[incomingField.id]?.required != true
                },
                removedFields = previousFields
                    .filterKeys { it !in incomingFields }
                    .values
                    .toList()
            )
        }
    }
}

object ExtensionInstallPreviewBuilder {
    const val MaxConfigurationSummaryFields = 3

    fun fromDescriptor(
        incoming: NormalizedExtensionDescriptor,
        existingInstallation: NormalizedExtensionDescriptor? = null,
        snapshotHandle: ExtensionPackageSnapshotHandle? = null,
        icon: ExtensionPreviewIcon? = null
    ): ExtensionInstallPreview {
        require(existingInstallation == null || existingInstallation.identity.id == incoming.identity.id) {
            "更新预览只能比较相同 extension ID"
        }
        ConfigurationSchemaValidator.requireValid(incoming.configurationSchema)
        CredentialRequestValidator.requireValid(incoming.credentialRequests)
        val requiredFields = incoming.configurationSchema.fields
            .filter(ConfigurationFieldDefinition::required)
        return ExtensionInstallPreview(
            incoming = incoming,
            summaryCapabilities = SummaryCapabilityAggregator.aggregate(incoming.canonicalCapabilities),
            configurationSummary = ConfigurationRequirementSummary(
                requiredFields = requiredFields
                    .take(MaxConfigurationSummaryFields)
                    .map { ConfigurationFieldSummary(it.id, it.label) },
                omittedFieldCount = (requiredFields.size - MaxConfigurationSummaryFields).coerceAtLeast(0)
            ),
            existingInstallation = existingInstallation,
            updateDiff = existingInstallation?.let { ExtensionUpdateDiff.between(it, incoming) },
            snapshotHandle = snapshotHandle,
            icon = icon
        )
    }

}

object ExtensionAuthorizationPolicy {
    fun newEffectiveNetworkPermissions(
        oldEffectivePermissions: Set<NetworkOriginPermission>,
        newRequestedPermissions: Set<NetworkOriginPermission>,
        newlyApprovedPermissions: Set<NetworkOriginPermission>
    ): Set<NetworkOriginPermission> =
        (oldEffectivePermissions intersect newRequestedPermissions) +
            (newlyApprovedPermissions intersect newRequestedPermissions)

    fun newEffectiveCredentialRequests(
        oldEffectiveRequests: Set<CredentialRequestIdentity>,
        newRequestedDefinitions: Collection<CredentialRequestDefinition>,
        newlyApprovedRequests: Set<CredentialRequestIdentity>
    ): Set<CredentialRequestIdentity> {
        val newlyRequested = newRequestedDefinitions.mapTo(linkedSetOf()) { it.identity }
        return (oldEffectiveRequests intersect newlyRequested) +
            (newlyApprovedRequests intersect newlyRequested)
    }
}
