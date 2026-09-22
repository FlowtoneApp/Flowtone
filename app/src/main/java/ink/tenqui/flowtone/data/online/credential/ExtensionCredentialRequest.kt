package ink.tenqui.flowtone.data.online.credential

import java.util.Locale

enum class CredentialType(val value: String, val label: String) {
    WebDav("webdav", "WebDAV 凭证"),
    AccountPassword("account_password", "账户密码凭证"),
    GenericAccount("generic_account", "通用账户凭证");

    companion object {
        fun fromValue(value: String): CredentialType? = entries.firstOrNull { it.value == value }
    }
}

/** Host 定义的凭据字段 ID；扩展只能选择既有语义，不能声明自己的共享字段。 */
enum class CredentialFieldId(val value: String) {
    Endpoint("endpoint"),
    Account("account"),
    Username("username"),
    UserId("userId"),
    Email("email"),
    Phone("phone"),
    Password("password"),
    Cookie("cookie");

    companion object {
        fun fromValue(value: String): CredentialFieldId? = entries.firstOrNull { it.value == value }
    }
}

enum class CredentialFieldValueType {
    String,
    Url
}

enum class CredentialFieldSensitivity {
    Normal,
    Secret
}

data class CredentialFieldDefinition(
    val id: CredentialFieldId,
    val label: String,
    val valueType: CredentialFieldValueType,
    val sensitivity: CredentialFieldSensitivity,
    val order: Int,
    val description: String? = null
) {
    val isSensitive: Boolean get() = sensitivity == CredentialFieldSensitivity.Secret
}

/** 所有可跨扩展复用的字段均由 Host 固定定义，供后续 Vault 与账户页共同使用。 */
object CredentialFieldDefinitions {
    val Endpoint = CredentialFieldDefinition(
        id = CredentialFieldId.Endpoint,
        label = "服务器地址",
        valueType = CredentialFieldValueType.Url,
        sensitivity = CredentialFieldSensitivity.Normal,
        order = 0
    )
    val Username = CredentialFieldDefinition(
        id = CredentialFieldId.Username,
        label = "用户名",
        valueType = CredentialFieldValueType.String,
        sensitivity = CredentialFieldSensitivity.Normal,
        order = 2
    )
    val Account = CredentialFieldDefinition(
        id = CredentialFieldId.Account,
        label = "账号",
        valueType = CredentialFieldValueType.String,
        sensitivity = CredentialFieldSensitivity.Normal,
        order = 1
    )
    val UserId = CredentialFieldDefinition(
        id = CredentialFieldId.UserId,
        label = "用户 ID",
        valueType = CredentialFieldValueType.String,
        sensitivity = CredentialFieldSensitivity.Normal,
        order = 3
    )
    val Email = CredentialFieldDefinition(
        id = CredentialFieldId.Email,
        label = "邮箱",
        valueType = CredentialFieldValueType.String,
        sensitivity = CredentialFieldSensitivity.Normal,
        order = 4
    )
    val Phone = CredentialFieldDefinition(
        id = CredentialFieldId.Phone,
        label = "手机号",
        valueType = CredentialFieldValueType.String,
        sensitivity = CredentialFieldSensitivity.Normal,
        order = 5
    )
    val Password = CredentialFieldDefinition(
        id = CredentialFieldId.Password,
        label = "密码",
        valueType = CredentialFieldValueType.String,
        sensitivity = CredentialFieldSensitivity.Secret,
        order = 6
    )
    val Cookie = CredentialFieldDefinition(
        id = CredentialFieldId.Cookie,
        label = "Cookie",
        valueType = CredentialFieldValueType.String,
        sensitivity = CredentialFieldSensitivity.Secret,
        order = 7
    )

    val all: List<CredentialFieldDefinition> = listOf(
        Endpoint, Account, Username, UserId, Email, Phone, Password, Cookie
    )

    fun get(id: CredentialFieldId): CredentialFieldDefinition = all.first { it.id == id }
}

/** AccountPassword 只可选择 Host 已定义的身份标识。 */
enum class CredentialIdentifierType(val value: String, val fieldId: CredentialFieldId) {
    Username("username", CredentialFieldId.Username),
    UserId("userId", CredentialFieldId.UserId),
    Email("email", CredentialFieldId.Email),
    Phone("phone", CredentialFieldId.Phone);

    val field: CredentialFieldDefinition get() = CredentialFieldDefinitions.get(fieldId)

    companion object {
        fun fromValue(value: String): CredentialIdentifierType? = entries.firstOrNull {
            it.value == value
        }
    }
}

object CredentialRealm {
    private val HostnameLike = Regex(
        "(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
    )

    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    fun isValid(value: String): Boolean = value == normalize(value) && HostnameLike.matches(value)
}

data class CredentialRequestContract(
    val credentialType: CredentialType,
    val identifiers: Set<CredentialIdentifierType>,
    val realm: String? = null,
    val genericFieldIds: Set<CredentialFieldId> = emptySet()
) {
    val fields: List<CredentialFieldDefinition>
        get() = when (credentialType) {
            CredentialType.WebDav -> listOf(
                CredentialFieldDefinitions.Endpoint,
                CredentialFieldDefinitions.Username,
                CredentialFieldDefinitions.Password
            )

            CredentialType.AccountPassword -> (
                identifiers.map(CredentialIdentifierType::field) + CredentialFieldDefinitions.Password
                ).sortedBy(CredentialFieldDefinition::order)

            CredentialType.GenericAccount -> genericFieldIds
                .map(CredentialFieldDefinitions::get)
                .sortedBy(CredentialFieldDefinition::order)
        }

    /** WebDAV 的三项均为必填；账户密码凭证仅固定要求密码，身份标识由用户择一提供。 */
    val requiredFieldIds: Set<CredentialFieldId>
        get() = when (credentialType) {
            CredentialType.WebDav -> fields.mapTo(linkedSetOf()) { it.id }
            CredentialType.AccountPassword -> setOf(CredentialFieldId.Password)
            CredentialType.GenericAccount -> genericFieldIds
        }

    /** 账户密码凭证需要在声明的 identifier 中至少提供一项；WebDAV 使用固定字段。 */
    val minimumIdentifierCount: Int
        get() = when (credentialType) {
            CredentialType.WebDav -> 0
            CredentialType.AccountPassword -> 1
            CredentialType.GenericAccount -> 0
        }
}

data class CredentialRequestDefinition(
    val id: String,
    val credentialType: CredentialType,
    val label: String,
    val required: Boolean = false,
    val description: String? = null,
    val identifiers: List<CredentialIdentifierType> = emptyList(),
    val realm: String? = null,
    val genericFieldIds: List<CredentialFieldId> = emptyList()
) {
    val contract: CredentialRequestContract
        get() = CredentialRequestContract(
            credentialType = credentialType,
            identifiers = identifiers.toSet(),
            realm = realm?.let(CredentialRealm::normalize),
            genericFieldIds = genericFieldIds.toSet()
        )
}

data class CredentialRequestIdentity(
    val id: String,
    val credentialType: CredentialType
)

val CredentialRequestDefinition.identity: CredentialRequestIdentity
    get() = CredentialRequestIdentity(id, credentialType)

object CredentialRequestLimits {
    const val MaxRequests = 16
    const val MaxIdLength = 64
    const val MaxLabelLength = 80
    const val MaxDescriptionLength = 300
}

data class CredentialRequestViolation(
    val path: String,
    val reason: String
)

object CredentialRequestValidator {
    private val SafeId = Regex("[a-zA-Z][a-zA-Z0-9._-]{0,63}")
    private val GenericAccountFields = setOf(
        CredentialFieldId.Account,
        CredentialFieldId.Password,
        CredentialFieldId.Cookie
    )

    fun validate(requests: List<CredentialRequestDefinition>): List<CredentialRequestViolation> = buildList {
        if (requests.size > CredentialRequestLimits.MaxRequests) {
            add(CredentialRequestViolation("credentialRequests", "凭证请求数量超过上限"))
        }
        val ids = mutableSetOf<String>()
        requests.forEachIndexed { index, request ->
            val path = "credentialRequests[$index]"
            if (request.id.length > CredentialRequestLimits.MaxIdLength || !SafeId.matches(request.id)) {
                add(CredentialRequestViolation("$path.id", "ID 必须使用安全稳定字符且长度不超过上限"))
            }
            if (!ids.add(request.id)) {
                add(CredentialRequestViolation("$path.id", "凭证请求 ID 重复"))
            }
            if (request.label.isBlank() || request.label.length > CredentialRequestLimits.MaxLabelLength) {
                add(CredentialRequestViolation("$path.label", "label 为空或过长"))
            }
            if (request.description != null &&
                request.description.length > CredentialRequestLimits.MaxDescriptionLength
            ) {
                add(CredentialRequestViolation("$path.description", "description 超过长度上限"))
            }
            when (request.credentialType) {
                CredentialType.WebDav -> {
                    if (request.identifiers.isNotEmpty()) {
                        add(CredentialRequestViolation("$path.identifiers", "WebDAV 凭证不允许声明身份标识"))
                    }
                    if (request.realm != null) {
                        add(CredentialRequestViolation("$path.realm", "WebDAV 凭证不允许 realm"))
                    }
                    if (request.genericFieldIds.isNotEmpty()) {
                        add(CredentialRequestViolation("$path.fields", "WebDAV 凭证不允许声明 fields"))
                    }
                }

                CredentialType.AccountPassword -> {
                    if (request.identifiers.isEmpty()) {
                        add(CredentialRequestViolation("$path.identifiers", "账户密码凭证至少需要一个身份标识"))
                    }
                    if (request.identifiers.size != request.identifiers.toSet().size) {
                        add(CredentialRequestViolation("$path.identifiers", "身份标识不能重复"))
                    }
                    if (request.realm != null) {
                        add(CredentialRequestViolation("$path.realm", "账户密码凭证不允许 realm"))
                    }
                    if (request.genericFieldIds.isNotEmpty()) {
                        add(CredentialRequestViolation("$path.fields", "账户密码凭证不允许声明 fields"))
                    }
                }

                CredentialType.GenericAccount -> {
                    if (request.identifiers.isNotEmpty()) {
                        add(CredentialRequestViolation("$path.identifiers", "通用账户凭证不允许 identifiers"))
                    }
                    if (request.realm == null || !CredentialRealm.isValid(request.realm)) {
                        add(CredentialRequestViolation("$path.realm", "realm 必须是规范的 hostname-like 服务标识"))
                    }
                    if (request.genericFieldIds.isEmpty()) {
                        add(CredentialRequestViolation("$path.fields", "通用账户凭证至少需要一个字段"))
                    }
                    if (request.genericFieldIds.size != request.genericFieldIds.toSet().size) {
                        add(CredentialRequestViolation("$path.fields", "通用账户凭证字段不能重复"))
                    }
                    if (request.genericFieldIds.any { it !in GenericAccountFields }) {
                        add(CredentialRequestViolation("$path.fields", "通用账户凭证只能使用 account、password、cookie"))
                    }
                }
            }
        }
    }

    fun requireValid(requests: List<CredentialRequestDefinition>) {
        val violations = validate(requests)
        require(violations.isEmpty()) {
            violations.joinToString(separator = "; ") { "${it.path}: ${it.reason}" }
        }
    }
}
