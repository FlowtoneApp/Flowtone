package ink.tenqui.flowtone.data.online.credential

/** Canonical permission scope saved at the time a user approves a request. */
data class CredentialRequestContractSnapshot(
    val credentialType: CredentialType,
    val realm: String?,
    val requestedFieldIds: Set<CredentialFieldId>,
    val identityConstraints: Set<CredentialIdentifierType>
) {
    fun isWellFormed(): Boolean {
        val canonicalRealm = realm?.let(CredentialRealm::normalize)
        if (realm != canonicalRealm) return false
        val expectedFields = when (credentialType) {
            CredentialType.WebDav -> {
                if (realm != null || identityConstraints.isNotEmpty()) return false
                setOf(
                    CredentialFieldId.Endpoint,
                    CredentialFieldId.Username,
                    CredentialFieldId.Password
                )
            }

            CredentialType.AccountPassword -> {
                if (realm != null || identityConstraints.isEmpty()) return false
                identityConstraints.mapTo(mutableSetOf()) { it.fieldId } + CredentialFieldId.Password
            }

            CredentialType.GenericAccount -> {
                if (realm == null || !CredentialRealm.isValid(realm) || identityConstraints.isNotEmpty()) {
                    return false
                }
                if (requestedFieldIds.isEmpty()) return false
                if (requestedFieldIds.any {
                        it !in setOf(CredentialFieldId.Account, CredentialFieldId.Password, CredentialFieldId.Cookie)
                    }
                ) return false
                requestedFieldIds
            }
        }
        return requestedFieldIds == expectedFields
    }

    companion object {
        fun from(request: CredentialRequestDefinition): CredentialRequestContractSnapshot? {
            if (CredentialRequestValidator.validate(listOf(request)).isNotEmpty()) return null
            val contract = request.contract
            return CredentialRequestContractSnapshot(
                credentialType = contract.credentialType,
                realm = contract.realm?.let(CredentialRealm::normalize),
                requestedFieldIds = contract.fields.mapTo(mutableSetOf()) { it.id },
                identityConstraints = contract.identifiers.toSet()
            ).takeIf(CredentialRequestContractSnapshot::isWellFormed)
        }
    }
}

/** A grant contains request metadata only. Secret material remains in CredentialSecretStore. */
data class CredentialGrant(
    val extensionId: String,
    val extensionInstanceId: String,
    val credentialRequestId: String,
    val credentialSourceId: String,
    val requestContract: CredentialRequestContractSnapshot,
    val createdAtEpochMillis: Long
)

/** One-shot, exact-scope token for the system authentication callback. */
data class CredentialGrantAuthorizationBinding(
    val extensionId: String,
    val extensionInstanceId: String,
    val requestId: String,
    val sourceId: String,
    val requestContract: CredentialRequestContractSnapshot
)

class CredentialGrantAuthorizationToken internal constructor(
    internal val nonce: Long,
    val binding: CredentialGrantAuthorizationBinding
)

class CredentialGrantAuthorizationGate {
    private var nextNonce = 1L
    private var active: CredentialGrantAuthorizationToken? = null
    private var keyguardConfirmationPending = false

    @Synchronized
    fun begin(binding: CredentialGrantAuthorizationBinding): CredentialGrantAuthorizationToken? {
        if (active != null || !binding.requestContract.isWellFormed()) return null
        keyguardConfirmationPending = false
        return CredentialGrantAuthorizationToken(nextNonce++, binding).also { active = it }
    }

    @Synchronized
    fun isCurrent(token: CredentialGrantAuthorizationToken): Boolean = active == token

    @Synchronized
    fun markKeyguardConfirmation(token: CredentialGrantAuthorizationToken) {
        if (active == token) keyguardConfirmationPending = true
    }

    @Synchronized
    fun finishKeyguardConfirmation(token: CredentialGrantAuthorizationToken) {
        if (active == token) keyguardConfirmationPending = false
    }

    @Synchronized
    fun isKeyguardConfirmationPending(): Boolean = active != null && keyguardConfirmationPending

    /** Holds invalidation off only for the small Host-owned persistence commit. */
    @Synchronized
    fun <T> commitIfCurrent(token: CredentialGrantAuthorizationToken, block: () -> T): T? {
        if (active != token) return null
        active = null
        keyguardConfirmationPending = false
        return block()
    }

    @Synchronized
    fun cancel(token: CredentialGrantAuthorizationToken? = null) {
        if (token == null || active == token) {
            active = null
            keyguardConfirmationPending = false
        }
    }

    @Synchronized
    fun close() {
        active = null
        keyguardConfirmationPending = false
    }
}

enum class CredentialGrantInvalidReason {
    NotGranted,
    ExtensionNotInstalled,
    ExtensionInstallationChanged,
    RequestRemoved,
    RequestContractChanged,
    CredentialSourceMissing,
    CredentialSourceIncompatible
}

data class CredentialGrantEvaluation(
    val grant: CredentialGrant?,
    /** Whether the persisted grant still names the current install, request, and compatible source. */
    val authorizationValid: Boolean,
    /** Readiness is evaluated separately; a valid grant can point to unavailable Secret data. */
    val sourceMatch: CredentialSourceMatch? = null,
    val sourceLabel: String? = null,
    val invalidReason: CredentialGrantInvalidReason? = null
) {
    val sourceReady: Boolean get() = sourceMatch?.fullyReady == true
    val usable: Boolean get() = authorizationValid && sourceReady
}
