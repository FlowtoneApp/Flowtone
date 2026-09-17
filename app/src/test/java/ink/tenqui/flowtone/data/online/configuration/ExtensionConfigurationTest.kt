package ink.tenqui.flowtone.data.online.configuration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionConfigurationTest {
    @Test
    fun secretDefaultIsRejected() {
        val violations = ConfigurationSchemaValidator.validate(
            schema(
                field(
                    id = "password",
                    type = ConfigurationFieldType.Secret,
                    defaultValue = ConfigurationValue.StringValue("secret")
                )
            )
        )

        assertTrue(violations.any { it.path.endsWith("defaultValue") })
    }

    @Test
    fun choiceDefaultMustExistInChoices() {
        val violations = ConfigurationSchemaValidator.validate(
            schema(
                field(
                    id = "quality",
                    type = ConfigurationFieldType.Choice,
                    defaultValue = ConfigurationValue.StringValue("lossless"),
                    choices = listOf(ConfigurationChoice("high", "高品质"))
                )
            )
        )

        assertTrue(violations.any { it.path.endsWith("defaultValue") })
    }

    @Test
    fun duplicateFieldIdIsRejected() {
        val violations = ConfigurationSchemaValidator.validate(
            schema(field("server"), field("server"))
        )

        assertTrue(violations.any { it.reason.contains("重复") })
    }

    @Test
    fun choiceRequiresOptionsAndUniqueValues() {
        val noOptions = ConfigurationSchemaValidator.validate(
            schema(field("quality", ConfigurationFieldType.Choice))
        )
        val duplicateOptions = ConfigurationSchemaValidator.validate(
            schema(
                field(
                    id = "quality",
                    type = ConfigurationFieldType.Choice,
                    choices = listOf(
                        ConfigurationChoice("high", "高品质"),
                        ConfigurationChoice("high", "另一高品质")
                    )
                )
            )
        )

        assertTrue(noOptions.any { it.path.endsWith("choices") })
        assertTrue(duplicateOptions.any { it.reason.contains("重复") })
    }

    @Test
    fun defaultValueMustMatchFieldType() {
        val violations = ConfigurationSchemaValidator.validate(
            schema(
                field(
                    id = "enabled",
                    type = ConfigurationFieldType.Toggle,
                    defaultValue = ConfigurationValue.StringValue("true")
                )
            )
        )

        assertTrue(violations.any { it.path.endsWith("defaultValue") })
    }

    @Test
    fun actionIdCannotConflictWithFieldId() {
        val violations = ConfigurationSchemaValidator.validate(
            ConfigurationSchema(
                fields = listOf(field("connection")),
                actions = listOf(ConfigurationActionDefinition("connection", "测试连接"))
            )
        )

        assertTrue(violations.any { it.reason.contains("冲突") })
    }

    @Test
    fun requiredInputsAllEmptyAreUnconfigured() {
        val schema = schema(
            field("server", ConfigurationFieldType.Url, required = true),
            field("username", required = true)
        )

        assertEquals(
            ConfigurationState.Unconfigured,
            ConfigurationStateResolver.resolve(schema, emptyMap())
        )
    }

    @Test
    fun partiallyFilledRequiredInputsAreIncomplete() {
        val schema = schema(
            field("server", ConfigurationFieldType.Url, required = true),
            field("username", required = true)
        )

        assertEquals(
            ConfigurationState.Incomplete,
            ConfigurationStateResolver.resolve(
                schema,
                mapOf("server" to ConfigurationValue.StringValue("https://example.com"))
            )
        )
    }

    @Test
    fun invalidRequiredUrlIsIncomplete() {
        val schema = schema(field("server", ConfigurationFieldType.Url, required = true))

        assertEquals(
            ConfigurationState.Incomplete,
            ConfigurationStateResolver.resolve(
                schema,
                mapOf("server" to ConfigurationValue.StringValue("not a URL"))
            )
        )
    }

    @Test
    fun allRequiredInputsValidAreConfigured() {
        val schema = schema(
            field("server", ConfigurationFieldType.Url, required = true),
            field("username", required = true),
            field(
                id = "enabled",
                type = ConfigurationFieldType.Toggle,
                required = true
            )
        )

        assertEquals(
            ConfigurationState.Configured,
            ConfigurationStateResolver.resolve(
                schema,
                mapOf(
                    "server" to ConfigurationValue.StringValue("https://example.com"),
                    "username" to ConfigurationValue.StringValue("listener"),
                    "enabled" to ConfigurationValue.BooleanValue(false)
                )
            )
        )
    }

    @Test
    fun schemaWithoutRequiredFieldsIsNotRequiredAndCanStillHaveSettings() {
        val schema = ConfigurationSchema(
            actions = listOf(ConfigurationActionDefinition("test", "测试连接"))
        )

        assertEquals(ConfigurationState.NotRequired, ConfigurationStateResolver.resolve(schema, emptyMap()))
        assertTrue(schema.hasSettings)
        assertFalse(schema.requiresConfiguration)
    }

    private fun schema(vararg fields: ConfigurationFieldDefinition) =
        ConfigurationSchema(fields = fields.toList())

    private fun field(
        id: String,
        type: ConfigurationFieldType = ConfigurationFieldType.Text,
        required: Boolean = false,
        defaultValue: ConfigurationValue? = null,
        choices: List<ConfigurationChoice> = emptyList()
    ) = ConfigurationFieldDefinition(
        id = id,
        type = type,
        label = id,
        required = required,
        defaultValue = defaultValue,
        choices = choices
    )
}
