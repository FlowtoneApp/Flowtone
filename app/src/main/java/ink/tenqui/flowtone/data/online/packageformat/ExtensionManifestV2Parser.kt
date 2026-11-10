package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.data.online.ExtensionRequirementLevel
import ink.tenqui.flowtone.data.online.capability.AtomicCapabilityId
import ink.tenqui.flowtone.data.online.capability.CanonicalAtomicCapabilitySet
import ink.tenqui.flowtone.data.online.configuration.ConfigurationActionDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationAtLeastOneGroup
import ink.tenqui.flowtone.data.online.configuration.ConfigurationChoice
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldDefinition
import ink.tenqui.flowtone.data.online.configuration.ConfigurationFieldType
import ink.tenqui.flowtone.data.online.configuration.ConfigurationRequirementLevel
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchema
import ink.tenqui.flowtone.data.online.configuration.ConfigurationSchemaValidator
import ink.tenqui.flowtone.data.online.configuration.ConfigurationValue
import ink.tenqui.flowtone.data.online.credential.CredentialAtLeastOneGroup
import ink.tenqui.flowtone.data.online.credential.CredentialFieldId
import ink.tenqui.flowtone.data.online.credential.CredentialIdentifierType
import ink.tenqui.flowtone.data.online.credential.CredentialRealm
import ink.tenqui.flowtone.data.online.credential.CredentialRequestDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialRequestValidator
import ink.tenqui.flowtone.data.online.credential.CredentialType
import ink.tenqui.flowtone.data.online.credential.NamedSecretType
import ink.tenqui.flowtone.data.online.credential.NamedCredentialRequestField
import ink.tenqui.flowtone.data.online.credential.NamedCredentialRequestContract
import ink.tenqui.flowtone.data.online.credential.CredentialInjection
import ink.tenqui.flowtone.data.online.credential.CredentialTargetOrigin
import ink.tenqui.flowtone.data.online.credential.CredentialAcquisitionDefinition
import ink.tenqui.flowtone.data.online.credential.CredentialAcquisitionOutput
import ink.tenqui.flowtone.data.online.credential.NamedSecretField
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
        val parsedCredentialRequests = parseCredentialRequests(json)
        val credentialAtLeastOneGroups = parseCredentialAtLeastOneGroups(json)
        CredentialRequestValidator.requireValid(parsedCredentialRequests, credentialAtLeastOneGroups)
        val credentialRequests = parsedCredentialRequests.map { request ->
            if (request.credentialType == CredentialType.GenericAccount) {
                request.copy(genericFieldIds = request.contract.fields.map { it.id })
            } else {
                request
            }
        }
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
            networkPermissions = networkPermissions,
            credentialAtLeastOneGroups = credentialAtLeastOneGroups,
            credentialAcquisition = parseCredentialAcquisition(json)
        )
    }

    private fun parseCredentialAcquisition(json: JSONObject): List<CredentialAcquisitionDefinition> {
        if (!json.has("credentialAcquisition")) return emptyList()
        val items = json.getJSONArray("credentialAcquisition")
        require(items.length() <= 16)
        val ids = mutableSetOf<String>()
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            require(item.keys().asSequence().toSet() == setOf("methodId", "type", "realm", "label", "description", "providerProfile", "outputs", "scopes"))
            fun text(key: String): String = (item.opt(key) as? String) ?: throw IllegalArgumentException("获取契约字段非法")
            val id = text("methodId")
            require(id.matches(Regex("[A-Za-z][A-Za-z0-9._-]{0,63}")) && ids.add(id))
            require(text("type") == "authorization_code_pkce")
            val realm = CredentialRealm.normalize(text("realm"))
            require(CredentialRealm.isValid(realm))
            val label = text("label"); val description = text("description"); val profile = text("providerProfile")
            require(label.isNotBlank() && label.length <= 80 && description.isNotBlank() && description.length <= 300)
            require(profile.matches(Regex("[a-z][a-z0-9._-]{0,63}")))
            val array = item.getJSONArray("outputs")
            require(array.length() in 1..2)
            val outputs = List(array.length()) { fieldIndex ->
                val field = array.getJSONObject(fieldIndex)
                require(field.keys().asSequence().toSet() == setOf("fieldId", "type", "required"))
                val fieldId = field.opt("fieldId") as? String ?: throw IllegalArgumentException("字段非法")
                require(field.opt("type") == "token" && field.opt("required") is Boolean)
                NamedSecretField(fieldId, NamedSecretType.Token)
                CredentialAcquisitionOutput(fieldId, field.getBoolean("required"))
            }
            require(outputs.map { it.fieldId }.distinct().size == outputs.size)
            val scopeArray = item.getJSONArray("scopes")
            require(scopeArray.length() <= 32)
            val scopes = List(scopeArray.length()) { scopeArray.get(it) as? String ?: throw IllegalArgumentException("scope 非法") }
            require(scopes.distinct().size == scopes.size && scopes.all { it.isNotEmpty() && it.length <= 128 && it.all { c -> c.code in 33..126 && c != '"' && c != '\\' } })
            CredentialAcquisitionDefinition(id, realm, label, description, profile, outputs, scopes)
        }
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
        val atLeastOneGroups = json.optJSONArray("atLeastOne")?.let { groups ->
            List(groups.length()) { groupIndex ->
                when (val group = groups.get(groupIndex)) {
                    is org.json.JSONArray -> ConfigurationAtLeastOneGroup(
                        fieldIds = List(group.length()) { fieldIndex -> group.getString(fieldIndex) }
                    )
                    is JSONObject -> {
                        val fields = group.optJSONArray("fields")
                            ?: throw IllegalArgumentException("atLeastOne group 缺少 fields")
                        ConfigurationAtLeastOneGroup(
                            fieldIds = List(fields.length()) { fieldIndex -> fields.getString(fieldIndex) },
                            requirement = group.optString("requirement")
                                .takeIf(String::isNotBlank)
                                ?.let(::parseRequirement)
                                ?: ConfigurationRequirementLevel.RuntimeRequired
                        )
                    }
                    else -> throw IllegalArgumentException("atLeastOne group 格式无效")
                }
            }
        }.orEmpty()
        return ConfigurationSchema(fields, actions, atLeastOneGroups)
            .also(ConfigurationSchemaValidator::requireValid)
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
        val requirement = json.optionalText("requirement")
            ?.let(::parseRequirement)
        val legacyRequired = json.optBoolean("required", false)
        require(!legacyRequired || requirement == null ||
            requirement == ConfigurationRequirementLevel.RuntimeRequired
        ) { "required=true 只能与 runtime_required 同时使用" }
        return ConfigurationFieldDefinition(
            id = json.getString("id"),
            type = type,
            label = json.getString("label"),
            description = json.optionalText("description"),
            required = legacyRequired || requirement == ConfigurationRequirementLevel.RuntimeRequired,
            defaultValue = parseDefaultValue(json, type),
            placeholder = json.optionalText("placeholder"),
            choices = choices,
            requirement = requirement
        )
    }

    private fun parseRequirement(value: String): ExtensionRequirementLevel =
        when (value) {
            "runtime_required" -> ExtensionRequirementLevel.RuntimeRequired
            "recommended" -> ExtensionRequirementLevel.Recommended
            "optional" -> ExtensionRequirementLevel.Optional
            else -> throw IllegalArgumentException("未知 requirement")
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
                val named = if (item.has("namedFields") || item.has("origin")) {
                    require(type == CredentialType.GenericAccount && !item.has("fields"))
                    require(item.opt("description") is String && item.getString("description").isNotBlank())
                    val array = item.getJSONArray("namedFields")
                    require(array.length() in 1..16)
                    val fields = List(array.length()) { fieldIndex ->
                        val field = array.getJSONObject(fieldIndex)
                        val fieldType = when (field.getString("type")) {
                            "cookie" -> NamedSecretType.Cookie
                            "token" -> NamedSecretType.Token
                            "opaque" -> NamedSecretType.Opaque
                            else -> throw IllegalArgumentException("未知具名字段类型")
                        }
                        val injection = when (field.getString("injection")) {
                            "cookie" -> CredentialInjection.Cookie
                            "bearer" -> CredentialInjection.Bearer
                            else -> throw IllegalArgumentException("未知认证方式")
                        }
                        require(field.opt("required") is Boolean)
                        require(!field.has("cookieName") || field.opt("cookieName") is String)
                        NamedCredentialRequestField(field.getString("id"), fieldType, field.getBoolean("required"), injection,
                            if (field.has("cookieName")) field.getString("cookieName") else null)
                    }
                    require(fields.map { it.id }.distinct().size == fields.size)
                    NamedCredentialRequestContract(CredentialTargetOrigin.normalize(item.getString("origin")), fields.toSet())
                } else null
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
                        require(fieldsJson != null || named != null) { "GenericAccount credential request 必须声明字段" }
                        val fields = List(fieldsJson?.length() ?: 0) { fieldIndex ->
                            val value = fieldsJson!!.getString(fieldIndex)
                            CredentialFieldId.fromValue(value)
                                ?: throw IllegalArgumentException("未知 generic account credential field")
                        }
                        ParsedCredentialContract(
                            realm = CredentialRealm.normalize(item.getString("realm")),
                            genericFieldIds = fields
                        )
                    }
                }
                val requirement = item.optionalText("requirement")?.let(::parseRequirement)
                val legacyRequired = item.optBoolean("required", false)
                require(!legacyRequired || requirement == null ||
                    requirement == ExtensionRequirementLevel.RuntimeRequired
                ) { "required=true 只能与 runtime_required 同时使用" }
                CredentialRequestDefinition(
                    id = item.getString("id"),
                    credentialType = type,
                    label = item.getString("label"),
                    required = legacyRequired || requirement == ExtensionRequirementLevel.RuntimeRequired,
                    description = item.optionalText("description"),
                    identifiers = contract.identifiers,
                    realm = contract.realm,
                    genericFieldIds = contract.genericFieldIds,
                    requirement = requirement,
                    named = named
                )
            }
        }.orEmpty()
        return requests
    }

    private fun parseCredentialAtLeastOneGroups(json: JSONObject): List<CredentialAtLeastOneGroup> {
        val groups = json.optJSONArray("credentialAtLeastOne") ?: return emptyList()
        return List(groups.length()) { groupIndex ->
            when (val group = groups.get(groupIndex)) {
                is org.json.JSONArray -> CredentialAtLeastOneGroup(
                    requestIds = List(group.length()) { requestIndex -> group.getString(requestIndex) }
                )
                is JSONObject -> {
                    val requests = group.optJSONArray("requests")
                        ?: throw IllegalArgumentException("credentialAtLeastOne group 缺少 requests")
                    CredentialAtLeastOneGroup(
                        requestIds = List(requests.length()) { requestIndex -> requests.getString(requestIndex) },
                        requirement = group.optionalText("requirement")
                            ?.let(::parseRequirement)
                            ?: ExtensionRequirementLevel.RuntimeRequired
                    )
                }
                else -> throw IllegalArgumentException("credentialAtLeastOne group 格式无效")
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
