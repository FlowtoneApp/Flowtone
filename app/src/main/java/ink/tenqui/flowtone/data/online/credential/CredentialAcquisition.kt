package ink.tenqui.flowtone.data.online.credential

/** 扩展只引用 Host 信任的 Profile，不能声明认证或令牌端点。 */
data class CredentialAcquisitionDefinition(
    val methodId: String,
    val realm: String,
    val label: String,
    val description: String,
    val providerProfile: String,
    val outputs: List<CredentialAcquisitionOutput>,
    val scopes: List<String>
)

data class CredentialAcquisitionOutput(val fieldId: String, val required: Boolean)
