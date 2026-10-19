package ink.tenqui.flowtone.data.online.configuration

import java.net.URI

enum class ConfigurationFieldType {
    Text,
    Url,
    Secret,
    Toggle,
    Choice
}

sealed interface ConfigurationValue {
    data class StringValue(val value: String) : ConfigurationValue
    data class BooleanValue(val value: Boolean) : ConfigurationValue
}

data class ConfigurationChoice(
    val value: String,
    val label: String
)

/** Provider-declared completion level for extension configuration fields and groups. */
enum class ConfigurationRequirementLevel {
    RuntimeRequired,
    Recommended,
    Optional
}

data class ConfigurationFieldDefinition(
    val id: String,
    val type: ConfigurationFieldType,
    val label: String,
    val description: String? = null,
    /** Legacy source compatibility; new manifests should use [requirement]. */
    val required: Boolean = false,
    val defaultValue: ConfigurationValue? = null,
    val placeholder: String? = null,
    val choices: List<ConfigurationChoice> = emptyList(),
    val requirement: ConfigurationRequirementLevel? = null
) {
    val effectiveRequirement: ConfigurationRequirementLevel
        get() = requirement ?: if (required) {
            ConfigurationRequirementLevel.RuntimeRequired
        } else {
            ConfigurationRequirementLevel.Optional
        }
}

data class ConfigurationActionDefinition(
    val id: String,
    val label: String,
    val requiresValidConfig: Boolean = true
)

data class ConfigurationAtLeastOneGroup(
    val fieldIds: List<String>,
    val requirement: ConfigurationRequirementLevel = ConfigurationRequirementLevel.RuntimeRequired
)

data class ConfigurationSchema(
    val fields: List<ConfigurationFieldDefinition> = emptyList(),
    val actions: List<ConfigurationActionDefinition> = emptyList(),
    /** Each group requires at least one valid, non-empty field value at its declared level. */
    val atLeastOneGroups: List<ConfigurationAtLeastOneGroup> = emptyList()
) {
    val hasSettings: Boolean get() = fields.isNotEmpty() || actions.isNotEmpty()
    val requiresConfiguration: Boolean
        get() = fields.any {
            it.effectiveRequirement == ConfigurationRequirementLevel.RuntimeRequired
        } || atLeastOneGroups.any {
            it.requirement == ConfigurationRequirementLevel.RuntimeRequired
        }
    val hasRecommendedConfiguration: Boolean
        get() = fields.any {
            it.effectiveRequirement == ConfigurationRequirementLevel.Recommended
        } || atLeastOneGroups.any {
            it.requirement == ConfigurationRequirementLevel.Recommended
        }
}

object ConfigurationSchemaLimits {
    const val MaxFields = 32
    const val MaxActions = 16
    const val MaxChoicesPerField = 32
    const val MaxAtLeastOneGroups = 16
    const val MaxIdLength = 64
    const val MaxLabelLength = 80
    const val MaxDescriptionLength = 300
    const val MaxPlaceholderLength = 120
    const val MaxChoiceValueLength = 128
}

data class ConfigurationSchemaViolation(
    val path: String,
    val reason: String
)

object ConfigurationSchemaValidator {
    private val SafeId = Regex("[a-zA-Z][a-zA-Z0-9._-]{0,63}")

    fun validate(schema: ConfigurationSchema): List<ConfigurationSchemaViolation> = buildList {
        if (schema.fields.size > ConfigurationSchemaLimits.MaxFields) {
            add(ConfigurationSchemaViolation("fields", "字段数量超过上限"))
        }
        if (schema.actions.size > ConfigurationSchemaLimits.MaxActions) {
            add(ConfigurationSchemaViolation("actions", "操作数量超过上限"))
        }

        val fieldIds = mutableSetOf<String>()
        schema.fields.forEachIndexed { index, field ->
            val path = "fields[$index]"
            validateId(field.id, "$path.id")
            if (!fieldIds.add(field.id)) {
                add(ConfigurationSchemaViolation("$path.id", "字段 ID 重复"))
            }
            validateLabel(field.label, "$path.label")
            if (
                field.required &&
                field.requirement != null &&
                field.requirement != ConfigurationRequirementLevel.RuntimeRequired
            ) {
                add(ConfigurationSchemaViolation("$path.requirement", "required=true 只能与运行必须要求同时使用"))
            }
            validateOptionalText(
                field.description,
                ConfigurationSchemaLimits.MaxDescriptionLength,
                "$path.description"
            )
            validateOptionalText(
                field.placeholder,
                ConfigurationSchemaLimits.MaxPlaceholderLength,
                "$path.placeholder"
            )
            if (field.type == ConfigurationFieldType.Secret && field.defaultValue != null) {
                add(ConfigurationSchemaViolation("$path.defaultValue", "Secret 不允许默认值"))
            }
            if (field.type == ConfigurationFieldType.Choice) {
                if (field.choices.isEmpty()) {
                    add(ConfigurationSchemaViolation("$path.choices", "Choice 至少需要一个选项"))
                }
                if (field.choices.size > ConfigurationSchemaLimits.MaxChoicesPerField) {
                    add(ConfigurationSchemaViolation("$path.choices", "Choice 选项数量超过上限"))
                }
                val values = mutableSetOf<String>()
                field.choices.forEachIndexed { choiceIndex, choice ->
                    val choicePath = "$path.choices[$choiceIndex]"
                    if (choice.value.isBlank() || choice.value.length > ConfigurationSchemaLimits.MaxChoiceValueLength) {
                        add(ConfigurationSchemaViolation("$choicePath.value", "Choice value 为空或过长"))
                    }
                    if (!values.add(choice.value)) {
                        add(ConfigurationSchemaViolation("$choicePath.value", "Choice value 重复"))
                    }
                    validateLabel(choice.label, "$choicePath.label")
                }
            } else if (field.choices.isNotEmpty()) {
                add(ConfigurationSchemaViolation("$path.choices", "只有 Choice 字段可以声明选项"))
            }
            field.defaultValue?.let { value ->
                if (!isValidValue(field, value)) {
                    add(ConfigurationSchemaViolation("$path.defaultValue", "默认值与字段类型或约束不符"))
                }
            }
        }

        if (schema.atLeastOneGroups.size > ConfigurationSchemaLimits.MaxAtLeastOneGroups) {
            add(ConfigurationSchemaViolation("atLeastOne", "至少一项约束数量超过上限"))
        }
        val groupKeys = mutableSetOf<Set<String>>()
        schema.atLeastOneGroups.forEachIndexed { index, group ->
            val path = "atLeastOne[$index]"
            if (group.requirement == ConfigurationRequirementLevel.Optional) {
                add(ConfigurationSchemaViolation("$path.requirement", "至少一项约束不能声明为可选配置"))
            }
            if (group.fieldIds.size !in 2..ConfigurationSchemaLimits.MaxFields) {
                add(ConfigurationSchemaViolation(path, "每组必须引用 2 到 " + ConfigurationSchemaLimits.MaxFields + " 个字段"))
            }
            if (group.fieldIds.distinct().size != group.fieldIds.size) {
                add(ConfigurationSchemaViolation("$path.fields", "同一组内字段不能重复"))
            }
            if (!groupKeys.add(group.fieldIds.toSet())) {
                add(ConfigurationSchemaViolation(path, "至少一项约束重复"))
            }
            group.fieldIds.forEachIndexed { fieldIndex, fieldId ->
                val field = schema.fields.firstOrNull { it.id == fieldId }
                if (field == null) {
                    add(ConfigurationSchemaViolation("$path.fields[$fieldIndex]", "引用了未声明的字段"))
                } else if (field.type == ConfigurationFieldType.Secret) {
                    add(ConfigurationSchemaViolation("$path.fields[$fieldIndex]", "此版本不能将 Secret 字段用于配置约束"))
                }
            }
        }

        val actionIds = mutableSetOf<String>()
        schema.actions.forEachIndexed { index, action ->
            val path = "actions[$index]"
            validateId(action.id, "$path.id")
            validateLabel(action.label, "$path.label")
            if (!actionIds.add(action.id)) {
                add(ConfigurationSchemaViolation("$path.id", "操作 ID 重复"))
            }
            if (action.id in fieldIds) {
                add(ConfigurationSchemaViolation("$path.id", "操作 ID 与字段 ID 冲突"))
            }
        }
    }

    fun requireValid(schema: ConfigurationSchema) {
        val violations = validate(schema)
        require(violations.isEmpty()) {
            violations.joinToString(separator = "; ") { "${it.path}: ${it.reason}" }
        }
    }

    fun hasValidValue(
        field: ConfigurationFieldDefinition,
        value: ConfigurationValue?
    ): Boolean = value != null && !isEmpty(value) && isValidValue(field, value)

    fun isValidValue(
        field: ConfigurationFieldDefinition,
        value: ConfigurationValue
    ): Boolean = when (field.type) {
        ConfigurationFieldType.Text,
        ConfigurationFieldType.Secret -> value is ConfigurationValue.StringValue

        ConfigurationFieldType.Url -> value is ConfigurationValue.StringValue && isValidUrl(value.value)
        ConfigurationFieldType.Toggle -> value is ConfigurationValue.BooleanValue
        ConfigurationFieldType.Choice -> value is ConfigurationValue.StringValue &&
            field.choices.any { it.value == value.value }
    }

    private fun isEmpty(value: ConfigurationValue): Boolean = when (value) {
        is ConfigurationValue.StringValue -> value.value.isBlank()
        is ConfigurationValue.BooleanValue -> false
    }

    private fun MutableList<ConfigurationSchemaViolation>.validateId(value: String, path: String) {
        if (value.length > ConfigurationSchemaLimits.MaxIdLength || !SafeId.matches(value)) {
            add(ConfigurationSchemaViolation(path, "ID 必须使用安全稳定字符且长度不超过上限"))
        }
    }

    private fun MutableList<ConfigurationSchemaViolation>.validateLabel(value: String, path: String) {
        if (value.isBlank() || value.length > ConfigurationSchemaLimits.MaxLabelLength) {
            add(ConfigurationSchemaViolation(path, "label 为空或过长"))
        }
    }

    private fun MutableList<ConfigurationSchemaViolation>.validateOptionalText(
        value: String?,
        maximumLength: Int,
        path: String
    ) {
        if (value != null && value.length > maximumLength) {
            add(ConfigurationSchemaViolation(path, "文本超过长度上限"))
        }
    }

    private fun isValidUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

enum class ConfigurationState {
    NotRequired,
    Unconfigured,
    Incomplete,
    Configured
}

data class ConfigurationRequirementProgress(
    val runtimeRequirementsSatisfied: Boolean,
    val recommendationsSatisfied: Boolean
) {
    val allRequiredAndRecommendedSatisfied: Boolean
        get() = runtimeRequirementsSatisfied && recommendationsSatisfied
}

object ConfigurationStateResolver {
    fun resolve(
        schema: ConfigurationSchema,
        values: Map<String, ConfigurationValue>
    ): ConfigurationState {
        ConfigurationSchemaValidator.requireValid(schema)
        val requiredFields = schema.fields.filter {
            it.effectiveRequirement == ConfigurationRequirementLevel.RuntimeRequired
        }
        val requiredGroups = schema.atLeastOneGroups.filter {
            it.requirement == ConfigurationRequirementLevel.RuntimeRequired
        }
        if (requiredFields.isEmpty() && requiredGroups.isEmpty()) {
            return ConfigurationState.NotRequired
        }

        val fieldsById = schema.fields.associateBy(ConfigurationFieldDefinition::id)
        val requiredValueIds = buildSet {
            requiredFields.forEach { add(it.id) }
            requiredGroups.forEach { addAll(it.fieldIds) }
        }
        if (requiredValueIds.all { fieldId ->
                val field = fieldsById.getValue(fieldId)
                isEmpty(values[fieldId] ?: field.defaultValue)
            }
        ) {
            return ConfigurationState.Unconfigured
        }

        val requiredFieldsSatisfied = requiredFields.all { field ->
            ConfigurationSchemaValidator.hasValidValue(
                field,
                values[field.id] ?: field.defaultValue
            )
        }
        val groupsSatisfied = requiredGroups.all { group ->
            group.fieldIds.any { fieldId ->
                val field = fieldsById.getValue(fieldId)
                ConfigurationSchemaValidator.hasValidValue(
                    field,
                    values[fieldId] ?: field.defaultValue
                )
            }
        }
        return if (requiredFieldsSatisfied && groupsSatisfied) {
            ConfigurationState.Configured
        } else {
            ConfigurationState.Incomplete
        }
    }

    fun resolveRequirementProgress(
        schema: ConfigurationSchema,
        values: Map<String, ConfigurationValue>
    ): ConfigurationRequirementProgress {
        val runtimeState = resolve(schema, values)
        val runtimeSatisfied = runtimeState == ConfigurationState.NotRequired ||
            runtimeState == ConfigurationState.Configured
        val fieldsById = schema.fields.associateBy(ConfigurationFieldDefinition::id)
        val recommendedFieldsSatisfied = schema.fields
            .filter { it.effectiveRequirement == ConfigurationRequirementLevel.Recommended }
            .all { field ->
                ConfigurationSchemaValidator.hasValidValue(
                    field,
                    values[field.id] ?: field.defaultValue
                )
            }
        val recommendedGroupsSatisfied = schema.atLeastOneGroups
            .filter { it.requirement == ConfigurationRequirementLevel.Recommended }
            .all { group ->
                group.fieldIds.any { fieldId ->
                    val field = fieldsById.getValue(fieldId)
                    ConfigurationSchemaValidator.hasValidValue(
                        field,
                        values[fieldId] ?: field.defaultValue
                    )
                }
            }
        return ConfigurationRequirementProgress(
            runtimeRequirementsSatisfied = runtimeSatisfied,
            recommendationsSatisfied = recommendedFieldsSatisfied && recommendedGroupsSatisfied
        )
    }

    private fun isEmpty(value: ConfigurationValue?): Boolean = when (value) {
        null -> true
        is ConfigurationValue.StringValue -> value.value.isBlank()
        is ConfigurationValue.BooleanValue -> false
    }
}
