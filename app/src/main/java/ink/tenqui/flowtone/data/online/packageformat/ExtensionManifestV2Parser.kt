package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import ink.tenqui.flowtone.data.online.configuration.ConfigurationActionDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationChoice
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldType
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchema
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchemaValidator
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialIdentifierType
import ink.tenqui.flowtone.data.online.credential.CredentialRealm
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialRequestValidator
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermission
import ink.tenqui.flowtone.data.online.permission.NetworkOriginPermissionParser
import org.json.JSONObject

internal object ExtensionManifestV2Parser {
    private data class ParsedCredentialContract(
        val identifiers: List<CredentialIdentifierType> = emptyList(),
        val realm: String? = null,
        val genericFieldIds: List<CredentialFieldId> = emptyList()
    )

    private val LegacyCapabilityIds = setOf(
        "artist_avatar",
        "artist_metadata",
        "music_provider",
        "song",
        "album"
    )

    fun parse(json: JSONObject): NormalizedExtensionDescriptor {
        val capabilityIds = with(ExtensionManifestJson) { json.stringList("capabilities") }
        require(capabilityIds.none { it in LegacyCapabilityIds }) {
            "Manifest v2 不能声明 legacy capability"
        }
        val knownCapabilities = capabilityIds.mapNotNull(AtomicCapabilityId::fromValue).toSet()
        val unknownCapabilities = capabilityIds.filter { AtomicCapabilityId.fromValue(it) == null }.distinct()
        val configuration = parseConfiguration(json.optJSONObject("configuration"))
        val credentialRequests = parseCredentialRequests(json)
        val networkPermissions = parseNetworkPermissions(json)
        val manifest = ExtensionManifestJson.commonManifest(
            json = json,
            capabilities = capabilityIds
        )
        return NormalizedExtensionDescriptor(
            manifest = manifest,
            canonicalCapabilities = CanonicalAtomicCapabilitySet(knownCapabilities),
            unknownAtomicCapabilityIds = unknownCapabilities,
            configurationSchema = configuration,
            credentialRequests = credentialRequests,
            networkPermissions = networkPermissions
        )
    }

    private fun parseConfiguration(json: JSONObject?): ConfigurationSchema {
        if (json == null) return ConfigurationSchema()
        val fields = json.optJSONArray("fields")?.let { array ->
            List(array.length()) { index -> parseField(array.getJSONObject(index)) }
        }.orEmpty()
        val actions = json.optJSONArray("actions")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ConfigurationActionDefinition(
                    id = item.getString("id"),
                    label = item.getString("label"),
                    requiresValidConfig = item.optBoolean("requiresValidConfig", true)
                )
            }
        }.orEmpty()
        return ConfigurationSchema(fields, actions).also(ConfigurationSchemaValidator::requireValid)
    }

    private fun parseField(json: JSONObject): ConfigurationFieldDefinition {
        val type = when (json.getString("type").lowercase()) {
            "text" -> ConfigurationFieldType.Text
            "url" -> ConfigurationFieldType.Url
            "secret" -> ConfigurationFieldType.Secret
            "toggle" -> ConfigurationFieldType.Toggle
            "choice" -> ConfigurationFieldType.Choice
            else -> throw IllegalArgumentException("未知 configuration field type")
        }
        val choices = json.optJSONArray("choices")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ConfigurationChoice(item.getString("value"), item.getString("label"))
            }
        }.orEmpty()
        return ConfigurationFieldDefinition(
            id = json.getString("id"),
            type = type,
            label = json.getString("label"),
            description = json.optionalText("description"),
            required = json.optBoolean("required", false),
            defaultValue = parseDefaultValue(json, type),
            placeholder = json.optionalText("placeholder"),
            choices = choices
        )
    }

    private fun parseDefaultValue(
        json: JSONObject,
        type: ConfigurationFieldType
    ): ConfigurationValue? {
        if (!json.has("defaultValue") || json.isNull("defaultValue")) return null
        return when (type) {
            ConfigurationFieldType.Toggle -> ConfigurationValue.BooleanValue(json.getBoolean("defaultValue"))
            else -> ConfigurationValue.StringValue(json.getString("defaultValue"))
        }
    }

    private fun parseCredentialRequests(json: JSONObject): List<CredentialRequestDefinition> {
        val requests = json.optJSONArray("credentialRequests")?.let { array ->
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val type = CredentialType.fromValue(item.getString("type").lowercase())
                    ?: throw IllegalArgumentException("未知 credential request type")
                val contract = when (type) {
                    CredentialType.WebDav -> {
                        require(!item.has("realm")) {
                            "WebDAV credential request 不允许 realm"
                        }
                        require(!item.has("fields")) {
                            "WebDAV credential request 不允许 fields"
                        }
                        require(!item.has("identifiers")) {
                            "WebDAV credential request 不允许 identifiers"
                        }
                        ParsedCredentialContract()
                    }

                    CredentialType.AccountPassword -> {
                        require(!item.has("realm")) {
                            "AccountPassword credential request 不允许 realm"
                        }
                        require(!item.has("fields")) {
                            "AccountPassword credential request 不允许 fields"
                        }
                        val identifiersJson = item.optJSONArray("identifiers")
                            ?: throw IllegalArgumentException(
                                "AccountPassword credential request 必须声明 identifiers"
                            )
                        val identifiers = List(identifiersJson.length()) { identifierIndex ->
                            val value = identifiersJson.getString(identifierIndex)
                            CredentialIdentifierType.fromValue(value)
                                ?: throw IllegalArgumentException("未知 credential identifier")
                        }
                        ParsedCredentialContract(identifiers = identifiers)
                    }

                    CredentialType.GenericAccount -> {
                        require(!item.has("identifiers")) {
                            "GenericAccount credential request 不允许 identifiers"
                        }
                        require(item.has("realm")) {
                            "GenericAccount credential request 必须声明 realm"
                        }
                        val fieldsJson = item.optJSONArray("fields")
                            ?: throw IllegalArgumentException(
                                "GenericAccount credential request 必须声明 fields"
                            )
                        val fields = List(fieldsJson.length()) { fieldIndex ->
                            val value = fieldsJson.getString(fieldIndex)
                            CredentialFieldId.fromValue(value)
                                ?: throw IllegalArgumentException("未知 generic account credential field")
                        }
                        ParsedCredentialContract(
                            realm = CredentialRealm.normalize(item.getString("realm")),
                            genericFieldIds = fields
                        )
                    }
                }
                CredentialRequestDefinition(
                    id = item.getString("id"),
                    credentialType = type,
                    label = item.getString("label"),
                    required = item.optBoolean("required", false),
                    description = item.optionalText("description"),
                    identifiers = contract.identifiers,
                    realm = contract.realm,
                    genericFieldIds = contract.genericFieldIds
                )
            }
        }.orEmpty()
        CredentialRequestValidator.requireValid(requests)
        return requests.map { request ->
            if (request.credentialType == CredentialType.GenericAccount) {
                request.copy(genericFieldIds = request.contract.fields.map { it.id })
            } else {
                request
            }
        }
    }

    private fun parseNetworkPermissions(json: JSONObject): Set<NetworkOriginPermission> {
        val network = json.optJSONObject("permissions")?.optJSONObject("network") ?: return emptySet()
        require(!network.has("hosts")) { "Manifest v2 网络权限必须使用 origins" }
        return with(ExtensionManifestJson) { network.stringListOrEmpty("origins") }
            .mapTo(linkedSetOf(), NetworkOriginPermissionParser::parse)
    }

    private fun JSONObject.optionalText(name: String): String? =
        optString(name).takeIf(String::isNotEmpty)
}
