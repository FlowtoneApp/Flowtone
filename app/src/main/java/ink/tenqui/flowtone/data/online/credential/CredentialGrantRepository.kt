package ink.tenqui.flowtone.data.online.credential

import android.content.Context
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension

/** Host-only lookup and validation surface for persisted user authorization. */
class CredentialGrantRepository(
    private val store: CredentialGrantStore,
    private val sources: CredentialSourceRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    fun grantsForExtension(extensionId: String): List<CredentialGrant> =
        store.grantsForExtension(extensionId)

    fun evaluate(installed: InstalledExtension?, requestId: String): CredentialGrantEvaluation {
        if (installed == null) {
            return CredentialGrantEvaluation(null, false, invalidReason = CredentialGrantInvalidReason.ExtensionNotInstalled)
        }
        val extensionId = installed.manifest.id
        val grant = store.find(extensionId, requestId)
            ?: return CredentialGrantEvaluation(null, false, invalidReason = CredentialGrantInvalidReason.NotGranted)
        if (installed.installationInstanceId == null || grant.extensionInstanceId != installed.installationInstanceId) {
            return CredentialGrantEvaluation(grant, false, invalidReason = CredentialGrantInvalidReason.ExtensionInstallationChanged)
        }
        val request = installed.descriptor.credentialRequests.firstOrNull { it.id == requestId }
            ?: return CredentialGrantEvaluation(grant, false, invalidReason = CredentialGrantInvalidReason.RequestRemoved)
        val currentContract = CredentialRequestContractSnapshot.from(request)
            ?: return CredentialGrantEvaluation(grant, false, invalidReason = CredentialGrantInvalidReason.RequestContractChanged)
        if (grant.requestContract != currentContract) {
            return CredentialGrantEvaluation(grant, false, invalidReason = CredentialGrantInvalidReason.RequestContractChanged)
        }
        val source = sources.get(grant.credentialSourceId)
            ?: return CredentialGrantEvaluation(grant, false, invalidReason = CredentialGrantInvalidReason.CredentialSourceMissing)
        val match = CredentialSourceMatcher.match(source, request)
        if (!match.contractCompatible) {
            return CredentialGrantEvaluation(
                grant, false, sourceMatch = match, sourceLabel = source.label,
                invalidReason = CredentialGrantInvalidReason.CredentialSourceIncompatible
            )
        }
        return CredentialGrantEvaluation(
            grant = grant,
            authorizationValid = true,
            sourceMatch = match,
            sourceLabel = source.label
        )
    }

    /** Revalidates the exact consent summary immediately before writing. */
    fun createOrReplace(
        installed: InstalledExtension,
        requestId: String,
        sourceId: String,
        confirmedContract: CredentialRequestContractSnapshot,
        confirmedInstallInstanceId: String,
        currentInstalled: () -> InstalledExtension?
    ): CredentialGrant = CredentialGrantLifecycleLocks.withGrantBinding(installed.manifest.id, sourceId) {
        val current = currentInstalled()
            ?: throw IllegalArgumentException("Extension is no longer installed")
        require(current.manifest.id == installed.manifest.id &&
            current.installationInstanceId == confirmedInstallInstanceId) {
            "Extension installation changed"
        }
        val request = current.descriptor.credentialRequests.firstOrNull { it.id == requestId }
            ?: throw IllegalArgumentException("Credential request is no longer declared")
        val contract = CredentialRequestContractSnapshot.from(request)
            ?: throw IllegalArgumentException("Credential request is invalid")
        require(contract == confirmedContract) { "Credential request changed" }
        val source = sources.get(sourceId) ?: throw IllegalArgumentException("Credential source is unavailable")
        require(CredentialSourceMatcher.match(source, request).fullyReady) {
            "Credential source is not ready"
        }
        val grant = CredentialGrant(
            extensionId = current.manifest.id,
            extensionInstanceId = checkNotNull(current.installationInstanceId),
            credentialRequestId = requestId,
            credentialSourceId = sourceId,
            requestContract = contract,
            createdAtEpochMillis = nowEpochMillis().coerceAtLeast(1L)
        )
        store.putOrReplace(grant)
        grant
    }

    fun revoke(extensionId: String, requestId: String): Boolean = store.revoke(extensionId, requestId)

    companion object {
        fun from(context: Context): CredentialGrantRepository = CredentialGrantRepository(
            CredentialGrantStore.from(context),
            CredentialSourceRepository.from(context)
        )
    }
}
