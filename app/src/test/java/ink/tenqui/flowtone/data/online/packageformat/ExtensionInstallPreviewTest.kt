package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityId
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityStatus
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldType
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchema
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.data.online.credential.identity
import ink.tenqui.flowtone.data.online.permission.NetworkOrigin
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionInstallPreviewTest {
    @Test
    fun previewCanonicalizesLegacyManifestWithoutRuntimeWork() {
        val manifest = manifest(
            capabilities = listOf("music_provider", "song"),
            networkHosts = listOf("Example.COM")
        )
        val config = ConfigurationSchema(
            fields = listOf(
                ConfigurationFieldDefinition(
                    id = "server",
                    type = ConfigurationFieldType.Url,
                    label = "服务器地址",
                    required = true
                )
            )
        )

        val preview = ExtensionInstallPreviewBuilder.fromLegacyManifest(manifest, config)

        assertTrue(AtomicCapabilityId.SearchSongPage in preview.canonicalCapabilities)
        assertTrue(AtomicCapabilityId.PlaybackResourceResolve in preview.canonicalCapabilities)
        assertTrue(AtomicCapabilityId.CatalogSongsList in preview.canonicalCapabilities)
        assertEquals(
            SummaryCapabilityStatus.Supported,
            preview.summaryCapabilities.single { it.id == SummaryCapabilityId.Playback }.status
        )
        assertTrue(preview.requiresConfiguration)
        assertEquals(listOf("服务器地址"), preview.configurationSummary.requiredFields.map { it.label })
        assertEquals("https://example.com", preview.networkPermissions.single().toString())
        assertEquals(null, preview.updateDiff)
    }

    @Test
    fun addedAndRemovedNetworkPermissionsAreDetected() {
        val retained = permission("https", "retained.example")
        val removed = permission("https", "removed.example")
        val added = permission("https", "added.example")

        val diff = ExtensionUpdateDiff.between(
            snapshot(networkPermissions = setOf(retained, removed)),
            snapshot(networkPermissions = setOf(retained, added))
        )

        assertEquals(setOf(added), diff.addedNetworkPermissions)
        assertEquals(setOf(removed), diff.removedNetworkPermissions)
        assertTrue(diff.hasPermissionChanges)
        assertTrue(diff.hasRemovedPermissions)
    }

    @Test
    fun httpsToHttpIsAReportedSecurityDowngrade() {
        val https = permission("https", "example.com")
        val http = permission("http", "example.com")

        val diff = ExtensionUpdateDiff.between(
            snapshot(networkPermissions = setOf(https)),
            snapshot(networkPermissions = setOf(http))
        )

        assertEquals(setOf(http), diff.addedNetworkPermissions)
        assertEquals(setOf(https), diff.removedNetworkPermissions)
        assertEquals(1, diff.securityDowngrades.size)
        assertTrue(diff.hasSecurityDowngrade)
    }

    @Test
    fun httpToHttpsIsNotASecurityDowngrade() {
        val diff = ExtensionUpdateDiff.between(
            snapshot(networkPermissions = setOf(permission("http", "example.com"))),
            snapshot(networkPermissions = setOf(permission("https", "example.com")))
        )

        assertFalse(diff.hasSecurityDowngrade)
    }

    @Test
    fun addedAndRemovedCredentialRequestsAreDetectedByStableIdentity() {
        val removed = credential("old", "旧凭证")
        val added = credential("new", "新凭证")

        val diff = ExtensionUpdateDiff.between(
            snapshot(credentialRequests = listOf(removed)),
            snapshot(credentialRequests = listOf(added))
        )

        assertEquals(listOf(added), diff.addedCredentialRequests)
        assertEquals(listOf(removed), diff.removedCredentialRequests)
        assertTrue(diff.hasNewCredentialRequests)
    }

    @Test
    fun labelChangeDoesNotCreateANewCredentialRequest() {
        val diff = ExtensionUpdateDiff.between(
            snapshot(credentialRequests = listOf(credential("account", "旧名称"))),
            snapshot(credentialRequests = listOf(credential("account", "新名称")))
        )

        assertTrue(diff.addedCredentialRequests.isEmpty())
        assertTrue(diff.removedCredentialRequests.isEmpty())
    }

    @Test
    fun identicalPermissionsHaveNoPermissionChanges() {
        val permission = permission("https", "example.com")

        val diff = ExtensionUpdateDiff.between(
            snapshot(networkPermissions = setOf(permission)),
            snapshot(networkPermissions = setOf(permission))
        )

        assertFalse(diff.hasPermissionChanges)
        assertFalse(diff.hasSecurityDowngrade)
        assertFalse(diff.hasRemovedPermissions)
    }

    @Test
    fun capabilityAndBasicConfigurationChangesAreDetected() {
        val oldField = field("old", required = false)
        val newlyRequired = field("server", required = true)
        val diff = ExtensionUpdateDiff.between(
            snapshot(
                capabilities = CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.SearchSongPage),
                configurationSchema = ConfigurationSchema(fields = listOf(oldField))
            ),
            snapshot(
                capabilities = CanonicalAtomicCapabilitySet.of(AtomicCapabilityId.PlaybackResourceResolve),
                configurationSchema = ConfigurationSchema(fields = listOf(newlyRequired))
            )
        )

        assertEquals(setOf(AtomicCapabilityId.PlaybackResourceResolve), diff.addedCapabilities)
        assertEquals(setOf(AtomicCapabilityId.SearchSongPage), diff.removedCapabilities)
        assertEquals(listOf(newlyRequired), diff.addedRequiredFields)
        assertEquals(listOf(oldField), diff.removedFields)
    }

    @Test
    fun effectivePermissionsDropUnrequestedAndRequireApprovalForNewOnes() {
        val retained = permission("https", "retained.example")
        val removed = permission("https", "removed.example")
        val newlyRequested = permission("https", "new.example")

        assertEquals(
            setOf(retained),
            ExtensionAuthorizationPolicy.newEffectiveNetworkPermissions(
                oldEffectivePermissions = setOf(retained, removed),
                newRequestedPermissions = setOf(retained, newlyRequested),
                newlyApprovedPermissions = emptySet()
            )
        )
        assertEquals(
            setOf(retained, newlyRequested),
            ExtensionAuthorizationPolicy.newEffectiveNetworkPermissions(
                oldEffectivePermissions = setOf(retained, removed),
                newRequestedPermissions = setOf(retained, newlyRequested),
                newlyApprovedPermissions = setOf(newlyRequested)
            )
        )
    }

    @Test
    fun effectiveCredentialRequestsDropRemovedGrants() {
        val retained = credential("retained", "保留")
        val removed = credential("removed", "移除")
        val added = credential("added", "新增")

        val withoutApproval = ExtensionAuthorizationPolicy.newEffectiveCredentialRequests(
            oldEffectiveRequests = setOf(retained.identity, removed.identity),
            newRequestedDefinitions = listOf(retained, added),
            newlyApprovedRequests = emptySet()
        )
        val withApproval = ExtensionAuthorizationPolicy.newEffectiveCredentialRequests(
            oldEffectiveRequests = setOf(retained.identity, removed.identity),
            newRequestedDefinitions = listOf(retained, added),
            newlyApprovedRequests = setOf(added.identity)
        )

        assertEquals(setOf(retained.identity), withoutApproval)
        assertEquals(setOf(retained.identity, added.identity), withApproval)
    }

    private fun snapshot(
        capabilities: CanonicalAtomicCapabilitySet = CanonicalAtomicCapabilitySet.Empty,
        configurationSchema: ConfigurationSchema = ConfigurationSchema(),
        networkPermissions: Set<NetworkOriginPermission> = emptySet(),
        credentialRequests: List<CredentialRequestDefinition> = emptyList()
    ) = ExtensionModelSnapshot(
        identity = ExtensionIdentity("example", "Example", "1", "Test"),
        canonicalCapabilities = capabilities,
        configurationSchema = configurationSchema,
        networkPermissions = networkPermissions,
        credentialRequests = credentialRequests
    )

    private fun permission(scheme: String, host: String) =
        NetworkOriginPermission(NetworkOrigin.of(scheme, host))

    private fun credential(id: String, label: String) = CredentialRequestDefinition(
        id = id,
        credentialType = CredentialType.WebDav,
        label = label
    )

    private fun field(id: String, required: Boolean) = ConfigurationFieldDefinition(
        id = id,
        type = ConfigurationFieldType.Text,
        label = id,
        required = required
    )

    private fun manifest(
        capabilities: List<String>,
        networkHosts: List<String>
    ) = ExtensionManifest(
        formatVersion = 1,
        id = "example.extension",
        name = "Example",
        version = "1.0.0",
        author = "Test",
        description = "",
        entry = "main.js",
        capabilities = capabilities,
        networkHosts = networkHosts
    )
}
