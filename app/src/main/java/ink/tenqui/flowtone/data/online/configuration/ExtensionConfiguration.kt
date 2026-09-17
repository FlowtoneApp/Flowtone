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

data class ConfigurationFieldDefinition(
    val id: String,
    val type: ConfigurationFieldType,
    val label: String,
    val description: String? = null,
    val required: Boolean = false,
    val defaultValue: ConfigurationValue? = null,
    val placeholder: String? = null,
    val choices: List<ConfigurationChoice> = emptyList()
)

data class ConfigurationActionDefinition(
    val id: String,
    val label: String,
    val requiresValidConfig: Boolean = true
)

data class ConfigurationSchema(
    val fields: List<ConfigurationFieldDefinition> = emptyList(),
    val actions: List<ConfigurationActionDefinition> = emptyList()
) {
    val hasSettings: Boolean get() = fields.isNotEmpty() || actions.isNotEmpty()
    val requiresConfiguration: Boolean get() = fields.any(ConfigurationFieldDefinition::required)
}

object ConfigurationSchemaLimits {
    const val MaxFields = 32
    const val MaxActions = 16
    const val MaxChoicesPerField = 32
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

object ConfigurationStateResolver {
    fun resolve(
        schema: ConfigurationSchema,
        values: Map<String, ConfigurationValue>
    ): ConfigurationState {
        ConfigurationSchemaValidator.requireValid(schema)
        val requiredFields = schema.fields.filter(ConfigurationFieldDefinition::required)
        if (requiredFields.isEmpty()) return ConfigurationState.NotRequired

        val effectiveValues = requiredFields.associateWith { field ->
            values[field.id] ?: field.defaultValue
        }
        if (effectiveValues.values.all(::isEmpty)) return ConfigurationState.Unconfigured

        return if (effectiveValues.all { (field, value) ->
                value != null && !isEmpty(value) && ConfigurationSchemaValidator.isValidValue(field, value)
            }
        ) {
            ConfigurationState.Configured
        } else {
            ConfigurationState.Incomplete
        }
    }

    private fun isEmpty(value: ConfigurationValue?): Boolean = when (value) {
        null -> true
        is ConfigurationValue.StringValue -> value.value.isBlank()
        is ConfigurationValue.BooleanValue -> false
    }
}
