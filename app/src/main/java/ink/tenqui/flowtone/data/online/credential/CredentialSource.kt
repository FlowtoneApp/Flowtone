package ink.tenqui.flowtone.data.online.credential

import java.net.URI

/** 用户拥有的凭据元数据；本轮只持久化非 Secret 字段。 */
data class CredentialSource(
    val id: String,
    val credentialType: CredentialType,
    val label: String,
    val realm: String? = null,
    val publicFields: Map<CredentialFieldId, String> = emptyMap(),
    /** 由 Secret Store 派生的运行时状态，不参与 CredentialSourceStore 序列化。 */
    val secretFieldStates: Map<CredentialFieldId, CredentialSecretState> = emptyMap()
)

data class CredentialSourceInput(
    val credentialType: CredentialType,
    val label: String,
    val realm: String? = null,
    val publicFields: Map<CredentialFieldId, String> = emptyMap()
)

enum class CredentialSecretState {
    NotConfigured,
    Configured,
    Unavailable
}

sealed interface CredentialSecretMutation {
    data object Keep : CredentialSecretMutation
    data class Replace(val value: String) : CredentialSecretMutation {
        override fun toString(): String = "Replace([redacted])"
    }
    data object Delete : CredentialSecretMutation
}

/** 输入始终从空值开始；清空输入表示 Keep，显式清除操作才表示 Delete。 */
data class CredentialSecretDraft(
    val input: String = "",
    val mutation: CredentialSecretMutation = CredentialSecretMutation.Keep
) {
    fun edit(value: String): CredentialSecretDraft = copy(
        input = value,
        mutation = if (value.isEmpty()) CredentialSecretMutation.Keep else CredentialSecretMutation.Replace(value)
    )

    fun clear(): CredentialSecretDraft = copy(input = "", mutation = CredentialSecretMutation.Delete)

    override fun toString(): String = "CredentialSecretDraft(input=[redacted], mutation=$mutation)"
}

object CredentialSourceContracts {
    fun publicFields(type: CredentialType): Set<CredentialFieldId> = when (type) {
        CredentialType.WebDav -> setOf(CredentialFieldId.Endpoint, CredentialFieldId.Username)
        CredentialType.AccountPassword -> setOf(
            CredentialFieldId.Username,
            CredentialFieldId.UserId,
            CredentialFieldId.Email,
            CredentialFieldId.Phone
        )
        CredentialType.GenericAccount -> setOf(CredentialFieldId.Account)
    }

    fun secretFields(type: CredentialType): Set<CredentialFieldId> = when (type) {
        CredentialType.WebDav,
        CredentialType.AccountPassword -> setOf(CredentialFieldId.Password)
        CredentialType.GenericAccount -> setOf(CredentialFieldId.Password, CredentialFieldId.Cookie)
    }
}

data class CredentialSourceViolation(
    val field: String,
    val reason: String
)

object CredentialSourceValidator {
    private val SafeSourceId = Regex("cs_[a-zA-Z0-9-]{8,128}")

    fun validate(source: CredentialSource): List<CredentialSourceViolation> = buildList {
        if (!SafeSourceId.matches(source.id)) {
            add(CredentialSourceViolation("id", "凭据 ID 非法"))
        }
        validateInput(
            CredentialSourceInput(
                credentialType = source.credentialType,
                label = source.label,
                realm = source.realm,
                publicFields = source.publicFields
            )
        ).forEach(::add)
    }

    fun validateInput(input: CredentialSourceInput): List<CredentialSourceViolation> = buildList {
        if (input.label.isBlank()) {
            add(CredentialSourceViolation("label", "请输入凭证名称"))
        } else if (input.label.length > 80) {
            add(CredentialSourceViolation("label", "凭证名称不能超过 80 个字符"))
        }
        when (input.credentialType) {
            CredentialType.WebDav -> {
                val endpoint = input.publicFields[CredentialFieldId.Endpoint]
                if (endpoint.isNullOrBlank()) {
                    add(CredentialSourceViolation("publicFields.endpoint", "请输入 WebDAV 地址"))
                }
                if (input.realm != null) {
                    add(CredentialSourceViolation("realm", "此凭据类型不允许 realm"))
                }
            }
            CredentialType.GenericAccount -> {
                if (input.realm == null || !CredentialRealm.isValid(input.realm)) {
                    add(CredentialSourceViolation("realm", "realm 必须是规范的 hostname-like 服务标识"))
                }
            }
            CredentialType.AccountPassword -> if (input.realm != null) {
                add(CredentialSourceViolation("realm", "此凭据类型不允许 realm"))
            }
        }
        val allowed = CredentialSourceContracts.publicFields(input.credentialType)
        input.publicFields.forEach { (field, value) ->
            if (field !in allowed) {
                add(CredentialSourceViolation("publicFields.${field.value}", "字段不属于此凭据类型"))
            } else if (value.isBlank() && field != CredentialFieldId.Endpoint) {
                add(CredentialSourceViolation("publicFields.${field.value}", "字段不能为空"))
            } else if (field == CredentialFieldId.Endpoint && value.isNotBlank() && !isValidHttpUrl(value)) {
                add(CredentialSourceViolation("publicFields.endpoint", "请输入有效的 HTTP 或 HTTPS 地址"))
            }
        }
    }

    fun requireValid(source: CredentialSource) {
        require(validate(source).isEmpty()) { "凭据元数据无效" }
    }

    fun requireValid(input: CredentialSourceInput) {
        require(validateInput(input).isEmpty()) { "凭据元数据无效" }
    }

    private fun isValidHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

internal fun CredentialSourceInput.normalized(): CredentialSourceInput = copy(
    label = label.trim(),
    realm = realm?.let(CredentialRealm::normalize),
    publicFields = publicFields.mapValues { (_, value) -> value.trim() }
)

data class CredentialSourceMatch(
    val contractCompatible: Boolean,
    val metadataCompatible: Boolean,
    val secretReady: Boolean
) {
    val fullyReady: Boolean get() = contractCompatible && metadataCompatible && secretReady
}

object CredentialSourceMatcher {
    fun match(source: CredentialSource, request: CredentialRequestDefinition): CredentialSourceMatch {
        val contractCompatible = source.credentialType == request.credentialType && when (request.credentialType) {
            CredentialType.GenericAccount -> source.realm == request.contract.realm &&
                request.contract.fields.map { it.id }.all {
                    it in CredentialSourceContracts.publicFields(source.credentialType) ||
                        it in CredentialSourceContracts.secretFields(source.credentialType)
                }
            CredentialType.WebDav,
            CredentialType.AccountPassword -> true
        }
        if (!contractCompatible) return CredentialSourceMatch(false, false, false)

        val metadataCompatible = when (request.credentialType) {
            CredentialType.WebDav -> source.hasPublic(CredentialFieldId.Endpoint) &&
                source.hasPublic(CredentialFieldId.Username)
            CredentialType.AccountPassword -> request.identifiers.any { source.hasPublic(it.fieldId) }
            CredentialType.GenericAccount -> request.contract.fields
                .filterNot { it.isSensitive }
                .all { source.hasPublic(it.id) }
        }
        val requestedSecretFields = request.contract.fields.filter { it.isSensitive }
        return CredentialSourceMatch(
            contractCompatible = true,
            metadataCompatible = metadataCompatible,
            secretReady = requestedSecretFields.all {
                source.secretFieldStates[it.id] == CredentialSecretState.Configured
            }
        )
    }

    private fun CredentialSource.hasPublic(field: CredentialFieldId): Boolean =
        !publicFields[field].isNullOrBlank()
}
