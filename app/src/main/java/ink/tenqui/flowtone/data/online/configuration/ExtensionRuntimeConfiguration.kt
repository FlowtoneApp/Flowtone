package ink.tenqui.flowtone.data.online.configuration

/** Read-only ordinary-configuration snapshot for one already-bound extension runtime. */
internal data class ExtensionRuntimeConfigurationSnapshot(
    val schemaValid: Boolean,
    val fields: Map<String, ConfigurationFieldType>,
    val values: Map<String, ConfigurationValue>
) {
    fun get(fieldId: String): ExtensionRuntimeConfigurationRead = when {
        !schemaValid -> ExtensionRuntimeConfigurationRead.InvalidSchema
        fields[fieldId] == null -> ExtensionRuntimeConfigurationRead.FieldNotDeclared
        fields[fieldId] == ConfigurationFieldType.Secret -> ExtensionRuntimeConfigurationRead.SecretDenied
        else -> ExtensionRuntimeConfigurationRead.Value(values[fieldId])
    }
}

internal sealed interface ExtensionRuntimeConfigurationRead {
    data class Value(val value: ConfigurationValue?) : ExtensionRuntimeConfigurationRead
    data object FieldNotDeclared : ExtensionRuntimeConfigurationRead
    data object SecretDenied : ExtensionRuntimeConfigurationRead
    data object InvalidSchema : ExtensionRuntimeConfigurationRead
}

/** Uses the same saved/default resolution as the settings draft, never including Secret values. */
internal fun extensionRuntimeConfigurationSnapshot(
    schema: ConfigurationSchema,
    savedValues: Map<String, ConfigurationValue>
): ExtensionRuntimeConfigurationSnapshot {
    val valid = ConfigurationSchemaValidator.validate(schema).isEmpty()
    return ExtensionRuntimeConfigurationSnapshot(
        schemaValid = valid,
        fields = if (valid) schema.fields.associate { it.id to it.type } else emptyMap(),
        values = if (valid) extensionConfigurationDraft(schema, savedValues) else emptyMap()
    )
}
