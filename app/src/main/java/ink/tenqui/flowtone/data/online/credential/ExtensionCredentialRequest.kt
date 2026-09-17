package ink.tenqui.flowtone.data.online.credential

enum class CredentialType(val value: String, val label: String) {
    WebDav("webdav", "WebDAV")
}

data class CredentialRequestDefinition(
    val id: String,
    val credentialType: CredentialType,
    val label: String,
    val required: Boolean = false,
    val description: String? = null
)

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
        }
    }

    fun requireValid(requests: List<CredentialRequestDefinition>) {
        val violations = validate(requests)
        require(violations.isEmpty()) {
            violations.joinToString(separator = "; ") { "${it.path}: ${it.reason}" }
        }
    }
}
