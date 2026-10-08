package ink.tenqui.flowtone.data.online.credential

import android.content.Context

/** 统一编排元数据与 Secret 密文存储；CredentialSource 不承载 Secret 明文。 */
class CredentialSourceRepository(
    private val sourceStore: CredentialSourceStore,
    private val secretStore: CredentialSecretStore
) {
    fun list(): List<CredentialSource> = sourceStore.list().map(::withSecretPresence)

    fun get(id: String): CredentialSource? = sourceStore.get(id)?.let(::withSecretPresence)

    /** 仅供 Host 在用户主动查看时读取，结果不进入 CredentialSource 元数据。 */
    internal fun readSecret(sourceId: String, fieldId: CredentialFieldId): CredentialSecretReadResult =
        secretStore.get(sourceId, fieldId)

    fun save(
        id: String?,
        input: CredentialSourceInput,
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

    /** 删除时先移除 Secret，Secret 清理失败则保留 metadata。 */
    fun delete(id: String): Boolean {
        if (sourceStore.get(id) == null) return false
        secretStore.deleteAll(id)
        return sourceStore.delete(id)
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
                CredentialSecretStore(appContext.noBackupFilesDir.resolve("credential-secrets"))
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
