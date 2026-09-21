package ink.tenqui.flowtone.data.online.configuration

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/** Authoritative, app-private storage for ordinary extension configuration only. */
class ExtensionConfigStore(private val root: File) {
    fun load(extensionId: String): Map<String, ConfigurationValue> {
        val file = configFile(extensionId)
        if (!file.isFile) return emptyMap()
        return runCatching {
            val values = JSONObject(file.readText(StandardCharsets.UTF_8)).optJSONObject("values")
                ?: return@runCatching emptyMap()
            buildMap {
                values.keys().forEach { id ->
                    when (val value = values.opt(id)) {
                        is String -> put(id, ConfigurationValue.StringValue(value))
                        is Boolean -> put(id, ConfigurationValue.BooleanValue(value))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    /** Validates, removes orphan/secret values, and atomically replaces one extension namespace. */
    fun save(
        extensionId: String,
        schema: ConfigurationSchema,
        values: Map<String, ConfigurationValue>
    ): Map<String, ConfigurationValue> {
        val result = validateExtensionConfiguration(schema, values)
        require(result.errors.isEmpty()) { "配置无效" }
        val file = configFile(extensionId)
        file.parentFile?.mkdirs()
        val json = JSONObject().put("format", FormatVersion).put(
            "values",
            JSONObject().apply {
                result.persistedValues.forEach { (id, value) ->
                    put(id, when (value) {
                        is ConfigurationValue.StringValue -> value.value
                        is ConfigurationValue.BooleanValue -> value.value
                    })
                }
            }
        )
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.toString(), StandardCharsets.UTF_8)
        runCatching {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return result.persistedValues
    }

    fun delete(extensionId: String) {
        namespace(extensionId).deleteRecursively()
    }

    private fun configFile(extensionId: String): File = namespace(extensionId).resolve("config.json")

    private fun namespace(extensionId: String): File {
        require(SafeExtensionId.matches(extensionId) && ".." !in extensionId) { "扩展 ID 非法" }
        return File(root, extensionId)
    }

    companion object {
        private const val FormatVersion = 1
        private val SafeExtensionId = Regex("[a-zA-Z0-9._-]+")

        fun from(context: Context): ExtensionConfigStore =
            ExtensionConfigStore(context.applicationContext.filesDir.resolve("extension-config"))
    }
}

internal data class ExtensionConfigurationValidation(
    val persistedValues: Map<String, ConfigurationValue>,
    val errors: Map<String, String>
)

internal fun extensionConfigurationDraft(
    schema: ConfigurationSchema,
    saved: Map<String, ConfigurationValue>
): Map<String, ConfigurationValue> = buildMap {
    schema.fields.filterNot { it.type == ConfigurationFieldType.Secret }.forEach { field ->
        val value = saved[field.id] ?: field.defaultValue
        if (value != null && ConfigurationSchemaValidator.isValidValue(field, value)) put(field.id, value)
    }
}

internal fun validateExtensionConfiguration(
    schema: ConfigurationSchema,
    values: Map<String, ConfigurationValue>
): ExtensionConfigurationValidation {
    ConfigurationSchemaValidator.requireValid(schema)
    val errors = linkedMapOf<String, String>()
    val persisted = linkedMapOf<String, ConfigurationValue>()
    schema.fields.forEach { field ->
        if (field.type == ConfigurationFieldType.Secret) return@forEach
        val value = values[field.id]
        val empty = value is ConfigurationValue.StringValue && value.value.isBlank()
        if (field.required && (value == null || empty)) {
            errors[field.id] = "此项为必填项"
        } else if (value != null && !empty && !ConfigurationSchemaValidator.isValidValue(field, value)) {
            errors[field.id] = if (field.type == ConfigurationFieldType.Url) "请输入有效的 HTTP 或 HTTPS 地址" else "配置值无效"
        } else if (value != null && !empty) {
            persisted[field.id] = value
        }
    }
    return ExtensionConfigurationValidation(persisted, errors)
}
