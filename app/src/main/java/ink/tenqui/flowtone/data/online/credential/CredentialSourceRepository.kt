package ink.tenqui.flowtone.data.online.credential

import android.content.Context

/** 统一编排元数据与 Secret 密文存储；CredentialSource 不承载 Secret 明文。 */
class CredentialSourceRepository(
    private val sourceStore: CredentialSourceStore,
    private val secretStore: CredentialSecretStore,
    private val grantStore: CredentialGrantStore? = null
) {
    fun list(): List<CredentialSource> = sourceStore.list().map(::withSecretPresence)

    fun get(id: String): CredentialSource? = sourceStore.get(id)?.let(::withSecretPresence)

    /** 仅供 Host 在用户主动查看时读取，结果不进入 CredentialSource 元数据。 */
    internal fun readSecret(sourceId: String, fieldId: CredentialFieldId): CredentialSecretReadResult =
        secretStore.get(sourceId, fieldId)

    internal fun namedSecretFields(sourceId: String): List<NamedSecretStatus> =
        CredentialGrantLifecycleLocks.withSource(sourceId) {
            requireGenericSource(sourceId)
            secretStore.namedFields(sourceId)
        }

    fun match(source: CredentialSource, request: CredentialRequestDefinition): CredentialSourceMatch = matchInternal(source, request, null)

    internal fun matchForExtension(source: CredentialSource, request: CredentialRequestDefinition,
                                   installed: ink.tenqui.flowtone.data.online.packageformat.InstalledExtension): CredentialSourceMatch =
        matchInternal(source, request, installed)

    private fun matchInternal(source: CredentialSource, request: CredentialRequestDefinition,
                              installed: ink.tenqui.flowtone.data.online.packageformat.InstalledExtension?): CredentialSourceMatch {
        val named = request.named ?: return CredentialSourceMatcher.match(source, request)
        if (source.credentialType != CredentialType.GenericAccount || source.realm != request.contract.realm) {
            return CredentialSourceMatch(false, false, false)
        }
        val available = namedSecretFields(source.id).associateBy { it.field.id }
        val missing = mutableListOf<String>()
        val unavailable = mutableListOf<String>()
        val optional = mutableListOf<String>()
        val restricted = mutableListOf<String>()
        named.fields.sortedBy { it.id }.forEach { field ->
            val actual = available[field.id]
            val compatible = actual?.oauthRestricted != true || (installed != null && isOAuthCompatible(source.id, actual.field, installed))
            val ready = actual?.field?.type == field.type && actual.state == CredentialSecretState.Configured && compatible
            if (!ready) {
                if (!compatible && field.required) restricted += field.id
                else if (!field.required) optional += field.id
                else if (actual != null && actual.field.type == field.type && actual.state == CredentialSecretState.Unavailable) unavailable += field.id
                else missing += field.id
            }
        }
        return CredentialSourceMatch(true, true, missing.isEmpty() && unavailable.isEmpty() && restricted.isEmpty(), missing, unavailable, optional, restricted)
    }

    /** Until a general client-compatibility contract exists, only the fixed debug fixture is recognized. */
    internal fun isOAuthCompatible(sourceId: String, field: NamedSecretField,
                                   installed: ink.tenqui.flowtone.data.online.packageformat.InstalledExtension): Boolean =
        CredentialGrantLifecycleLocks.withSource(sourceId) {
            val binding = runCatching { secretStore.oauthBinding(sourceId, field) }.getOrNull() ?: return@withSource false
            ink.tenqui.flowtone.BuildConfig.DEBUG && installed.manifest.id == "flowtone.oauth.test" &&
                binding.profileId == "flowtone-debug-test" && binding.clientId == "flowtone-debug-public" &&
                binding.realm == "example.test" && binding.scopes.isEmpty() &&
                installed.descriptor.credentialAcquisition.any { method ->
                    method.providerProfile == binding.profileId && method.realm == binding.realm && method.scopes.sorted() == binding.scopes &&
                        method.outputs.size == 1 && method.outputs.single().fieldId == field.id && method.outputs.single().required
                }
        }

    internal fun namedSecretState(sourceId: String, field: NamedSecretField): CredentialSecretState =
        CredentialGrantLifecycleLocks.withSource(sourceId) {
            requireGenericSource(sourceId)
            secretStore.namedState(sourceId, field)
        }

    internal fun readNamedSecret(sourceId: String, field: NamedSecretField): CredentialSecretReadResult =
        CredentialGrantLifecycleLocks.withSource(sourceId) {
            requireGenericSource(sourceId)
            secretStore.readNamed(sourceId, field)
        }

    internal fun putNamedSecret(sourceId: String, field: NamedSecretField, plaintext: String) =
        CredentialGrantLifecycleLocks.withSource(sourceId) {
            requireGenericSource(sourceId)
            require(!secretStore.isOAuthRestricted(sourceId, field)) { "OAuth 字段需要先删除后再手动导入" }
            secretStore.putNamed(sourceId, field, plaintext)
        }

    internal fun importNamedSecret(sourceId: String, field: NamedSecretField, plaintext: String, replaceConfirmed: Boolean) =
        CredentialGrantLifecycleLocks.withSource(sourceId) {
            importNamedSecretLocked(sourceId, field, plaintext, replaceConfirmed)
        }

    internal fun importNamedSecretForRequest(
        extensionId: String, instanceId: String, requestId: String,
        confirmedRequest: CredentialRequestDefinition, sourceId: String,
        field: NamedSecretField, plaintext: String, replaceConfirmed: Boolean,
        currentInstalled: () -> ink.tenqui.flowtone.data.online.packageformat.InstalledExtension?
    ) = CredentialGrantLifecycleLocks.withGrantBinding(extensionId, sourceId) {
        val installed = currentInstalled()
        require(installed?.manifest?.id == extensionId && installed.installationInstanceId == instanceId)
        val request = installed.descriptor.credentialRequests.firstOrNull { it.id == requestId }
        require(request != null && request == confirmedRequest && CredentialRequestContractSnapshot.from(request) != null)
        require(request.named?.fields?.any { it.id == field.id && it.type == field.type } == true)
        val source = sourceStore.get(sourceId)
        require(source?.credentialType == CredentialType.GenericAccount && source.realm == request.contract.realm)
        importNamedSecretLocked(sourceId, field, plaintext, replaceConfirmed)
    }

    internal fun saveOAuthAccessToken(
        extensionId: String, instanceId: String, declaration: CredentialAcquisitionDefinition,
        sourceId: String, binding: OAuthCredentialBinding, plaintext: String, replaceConfirmed: Boolean,
        currentInstalled: () -> ink.tenqui.flowtone.data.online.packageformat.InstalledExtension?
    ) = CredentialGrantLifecycleLocks.withGrantBinding(extensionId, sourceId) {
        val installed = currentInstalled()
        require(installed?.manifest?.id == extensionId && installed.installationInstanceId == instanceId)
        require(declaration in installed.descriptor.credentialAcquisition)
        require(declaration.providerProfile == binding.profileId && declaration.realm == binding.realm && declaration.scopes.sorted() == binding.scopes)
        require(declaration.outputs.size == 1 && declaration.outputs.single().fieldId == "oauth.access_token" && declaration.outputs.single().required)
        val source = sourceStore.get(sourceId)
        require(source?.credentialType == CredentialType.GenericAccount && source.realm == declaration.realm)
        val field = NamedSecretField("oauth.access_token", NamedSecretType.Token)
        secretStore.putOAuthNamed(sourceId, field, plaintext, binding, replaceConfirmed)
        check(secretStore.namedState(sourceId, field) == CredentialSecretState.Configured)
    }

    private fun importNamedSecretLocked(sourceId: String, field: NamedSecretField, plaintext: String, replaceConfirmed: Boolean) {
        requireGenericSource(sourceId)
        require(!secretStore.isOAuthRestricted(sourceId, field)) { "OAuth 字段需要先删除后再手动导入" }
        val existing = secretStore.namedFields(sourceId).firstOrNull { it.field.id == field.id }
        require(existing == null || existing.field == field) { "字段类型冲突" }
        require(existing == null || replaceConfirmed) { "替换需要确认" }
        secretStore.putNamed(sourceId, field, plaintext)
        check(secretStore.namedState(sourceId, field) == CredentialSecretState.Configured) { "字段保存后不可用" }
    }

    internal fun deleteNamedSecret(sourceId: String, field: NamedSecretField) =
        CredentialGrantLifecycleLocks.withSource(sourceId) {
            requireGenericSource(sourceId)
            secretStore.deleteNamed(sourceId, field)
        }

    private fun requireGenericSource(sourceId: String) {
        require(sourceStore.get(sourceId)?.credentialType == CredentialType.GenericAccount) { "账户不可用或类型不支持" }
    }

    fun save(
        id: String?,
        input: CredentialSourceInput,
        secretMutations: Map<CredentialFieldId, CredentialSecretMutation>
    ): CredentialSource = if (id == null) saveLocked(null, input, secretMutations) else
        CredentialGrantLifecycleLocks.withSource(id) { saveLocked(id, input, secretMutations) }

    private fun saveLocked(
        id: String?, input: CredentialSourceInput,
        secretMutations: Map<CredentialFieldId, CredentialSecretMutation>
    ): CredentialSource {
        val metadataViolations = CredentialSourceValidator.validateInput(input)
        val secretViolations = validateSecretMutations(input.credentialType, secretMutations)
        require(metadataViolations.isEmpty() && secretViolations.isEmpty()) {
            "Credential source draft is invalid"
        }

        val savedMetadata = if (id == null) {
            sourceStore.create(input)
        } else {
            sourceStore.update(id, input)
        }

        try {
            secretMutations.forEach { (field, mutation) ->
                when (mutation) {
                    CredentialSecretMutation.Keep -> Unit
                    is CredentialSecretMutation.Replace -> secretStore.put(savedMetadata.id, field, mutation.value)
                    CredentialSecretMutation.Delete -> secretStore.delete(savedMetadata.id, field)
                }
            }
        } catch (e: CredentialSecretStoreException) {
            // 跨文件保存无法原子回滚；保留元数据供重试，并向调用方报告部分完成。
            throw CredentialSourceSaveException(savedMetadata, e)
        }
        return withSecretPresence(savedMetadata)
    }

    /** 先撤销引用，再删除 Secret 和 metadata；中途失败会向调用方报告且不留下可用授权。 */
    fun delete(id: String): Boolean = CredentialGrantLifecycleLocks.withSource(id) {
        if (sourceStore.get(id) == null) return@withSource false
        // Revoke references first so a partially failed deletion cannot leave usable authorization.
        grantStore?.deleteForCredentialSource(id)
        secretStore.deleteAll(id)
        sourceStore.delete(id)
    }

    private fun withSecretPresence(source: CredentialSource): CredentialSource = source.copy(
        secretFieldStates = CredentialSourceContracts.secretFields(source.credentialType)
            .associateWith { secretStore.state(source.id, it) }
    )

    companion object {
        fun from(context: Context): CredentialSourceRepository {
            val appContext = context.applicationContext
            return CredentialSourceRepository(
                CredentialSourceStore(appContext.filesDir.resolve("credential-sources")),
                CredentialSecretStore(appContext.noBackupFilesDir.resolve("credential-secrets")),
                CredentialGrantStore.from(appContext)
            )
        }

        fun validateSecretMutations(
            type: CredentialType,
            mutations: Map<CredentialFieldId, CredentialSecretMutation>
        ): List<CredentialSourceViolation> = buildList {
            mutations.forEach { (field, mutation) ->
                val definition = CredentialFieldDefinitions.get(field)
                if (!definition.isSensitive || field !in CredentialSourceContracts.secretFields(type)) {
                    add(CredentialSourceViolation("secrets.${field.value}", "字段不能作为此凭证的 Secret 保存"))
                } else if (mutation is CredentialSecretMutation.Replace && mutation.value.isEmpty()) {
                    add(CredentialSourceViolation("secrets.${field.value}", "Secret 不能为空；请使用清除操作"))
                }
            }
        }
    }
}

class CredentialSourceSaveException(
    val persistedMetadata: CredentialSource,
    cause: CredentialSecretStoreException
) : Exception("Credential metadata was saved but Secret changes failed", cause)
