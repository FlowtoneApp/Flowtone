package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import ink.tenqui.flowtone.data.online.capability.LegacyCapabilityCanonicalizer
import ink.tenqui.flowtone.data.online.capability.SummaryCapability
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityAggregator
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchema
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchemaValidator
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialRequestIdentity
import ink.tenqui.flowtone.data.online.credential.CredentialRequestValidator
import ink.tenqui.flowtone.data.online.credential.identity
import ink.tenqui.flowtone.data.online.permission.LegacyNetworkPermissionCanonicalizer
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.data.online.permission.NetworkSecurity
import ink.tenqui.flowtone.data.online.permission.NetworkSecurityDowngrade
import ink.tenqui.flowtone.data.online.permission.hasSameAuthoritySemanticsAs

data class ExtensionIdentity(
    val id: String,
    val name: String,
    val version: String,
    val author: String
) {
    companion object {
        fun from(manifest: ExtensionManifest): ExtensionIdentity = ExtensionIdentity(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            author = manifest.author
        )
    }
}

data class ConfigurationRequirementSummary(
    val requiredFields: List<ConfigurationFieldSummary>
)

data class ConfigurationFieldSummary(
    val id: String,
    val label: String
)

data class ExtensionModelSnapshot(
    val identity: ExtensionIdentity,
    val canonicalCapabilities: CanonicalAtomicCapabilitySet,
    val configurationSchema: ConfigurationSchema,
    val networkPermissions: Set<NetworkOriginPermission>,
    val credentialRequests: List<CredentialRequestDefinition>
)

data class ExtensionInstallPreview(
    val incomingManifest: ExtensionManifest,
    val canonicalCapabilities: CanonicalAtomicCapabilitySet,
    val summaryCapabilities: List<SummaryCapability>,
    val configurationSchema: ConfigurationSchema,
    val requiresConfiguration: Boolean,
    val configurationSummary: ConfigurationRequirementSummary,
    val networkPermissions: Set<NetworkOriginPermission>,
    val credentialRequests: List<CredentialRequestDefinition>,
    val existingInstallation: ExtensionModelSnapshot?,
    val updateDiff: ExtensionUpdateDiff?
) {
    val identity: ExtensionIdentity get() = ExtensionIdentity.from(incomingManifest)
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
            previous: ExtensionModelSnapshot,
            incoming: ExtensionModelSnapshot
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
                        .map { newPermission ->
                            NetworkSecurityDowngrade(oldPermission, newPermission)
                        }
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
    fun snapshotFromLegacyManifest(
        manifest: ExtensionManifest,
        configurationSchema: ConfigurationSchema = ConfigurationSchema(),
        credentialRequests: List<CredentialRequestDefinition> = emptyList()
    ): ExtensionModelSnapshot {
        ConfigurationSchemaValidator.requireValid(configurationSchema)
        CredentialRequestValidator.requireValid(credentialRequests)
        return ExtensionModelSnapshot(
            identity = ExtensionIdentity.from(manifest),
            canonicalCapabilities = LegacyCapabilityCanonicalizer.canonicalize(manifest),
            configurationSchema = configurationSchema,
            networkPermissions = LegacyNetworkPermissionCanonicalizer.canonicalize(manifest.networkHosts),
            credentialRequests = credentialRequests
        )
    }

    fun fromLegacyManifest(
        incomingManifest: ExtensionManifest,
        configurationSchema: ConfigurationSchema = ConfigurationSchema(),
        credentialRequests: List<CredentialRequestDefinition> = emptyList(),
        existingInstallation: ExtensionModelSnapshot? = null
    ): ExtensionInstallPreview {
        require(existingInstallation == null || existingInstallation.identity.id == incomingManifest.id) {
            "更新预览只能比较相同 extension ID"
        }
        val incoming = snapshotFromLegacyManifest(
            manifest = incomingManifest,
            configurationSchema = configurationSchema,
            credentialRequests = credentialRequests
        )
        return ExtensionInstallPreview(
            incomingManifest = incomingManifest,
            canonicalCapabilities = incoming.canonicalCapabilities,
            summaryCapabilities = SummaryCapabilityAggregator.aggregate(incoming.canonicalCapabilities),
            configurationSchema = configurationSchema,
            requiresConfiguration = configurationSchema.requiresConfiguration,
            configurationSummary = ConfigurationRequirementSummary(
                requiredFields = configurationSchema.fields
                    .filter(ConfigurationFieldDefinition::required)
                    .map { ConfigurationFieldSummary(it.id, it.label) }
            ),
            networkPermissions = incoming.networkPermissions,
            credentialRequests = credentialRequests,
            existingInstallation = existingInstallation,
            updateDiff = existingInstallation?.let { ExtensionUpdateDiff.between(it, incoming) }
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
