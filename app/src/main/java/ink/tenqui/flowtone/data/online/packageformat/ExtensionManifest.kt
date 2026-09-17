package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchema
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import org.json.JSONObject

data class ExtensionManifest(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val entry: String,
    /** Manifest v2 直接声明的 canonical Atomic ID。 */
    val capabilities: List<String>,
    /** 可解析持久歌曲身份的服务来源，不能拿网络权限代替。 */
    val musicSources: List<String> = emptyList(),
    /** 可选 Provider 品牌色，仅用于 Host 控制的展示。格式为 #RRGGBB。 */
    val color: String? = null,
    val icon: String? = null,
    val iconColor: String? = null
)

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

/** v1/v2 解析后供 Host、preview 与未来 runtime registration 共用的统一声明。 */
data class NormalizedExtensionDescriptor(
    val manifest: ExtensionManifest,
    val canonicalCapabilities: CanonicalAtomicCapabilitySet,
    val unknownAtomicCapabilityIds: List<String>,
    val configurationSchema: ConfigurationSchema,
    val credentialRequests: List<CredentialRequestDefinition>,
    val networkPermissions: Set<NetworkOriginPermission>
) {
    val identity: ExtensionIdentity get() = ExtensionIdentity.from(manifest)
}

object ExtensionManifestParser {
    fun parse(text: String): ExtensionManifest = parseNormalized(text).manifest

    fun parseNormalized(text: String): NormalizedExtensionDescriptor {
        val json = JSONObject(text)
        return when (json.getInt("formatVersion")) {
            1 -> throw IllegalArgumentException(
                "此扩展使用已经废弃的 Flowtone 扩展格式，请更新扩展后重试"
            )
            2 -> ExtensionManifestV2Parser.parse(json)
            else -> throw IllegalArgumentException("不支持的扩展包版本")
        }
    }
}

data class InstalledExtension(
    val descriptor: NormalizedExtensionDescriptor,
    val directory: java.io.File,
    val runtimeAvailable: Boolean
) {
    val manifest: ExtensionManifest get() = descriptor.manifest
}
