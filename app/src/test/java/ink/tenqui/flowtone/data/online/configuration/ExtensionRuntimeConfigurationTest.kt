package ink.tenqui.flowtone.data.online.configuration

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionRuntimeConfigurationTest {
    private val schema = ConfigurationSchema(
        fields = listOf(
            ConfigurationFieldDefinition("text", ConfigurationFieldType.Text, "Text"),
            ConfigurationFieldDefinition("url", ConfigurationFieldType.Url, "Url"),
            ConfigurationFieldDefinition("toggle", ConfigurationFieldType.Toggle, "Toggle"),
            ConfigurationFieldDefinition(
                "choice",
                ConfigurationFieldType.Choice,
                "Choice",
                choices = listOf(ConfigurationChoice("a", "A"))
            ),
            ConfigurationFieldDefinition(
                "defaulted",
                ConfigurationFieldType.Text,
                "Defaulted",
                defaultValue = ConfigurationValue.StringValue("default")
            ),
            ConfigurationFieldDefinition("empty", ConfigurationFieldType.Text, "Empty"),
            ConfigurationFieldDefinition("secret", ConfigurationFieldType.Secret, "Secret")
        )
    )

    @Test
    fun ordinaryValuesKeepTheirDeclaredRuntimeTypes() {
        val snapshot = extensionRuntimeConfigurationSnapshot(
            schema,
            mapOf(
                "text" to ConfigurationValue.StringValue("text value"),
                "url" to ConfigurationValue.StringValue("https://example.com"),
                "toggle" to ConfigurationValue.BooleanValue(true),
                "choice" to ConfigurationValue.StringValue("a")
            )
        )

        assertEquals(
            ConfigurationValue.StringValue("text value"),
            snapshot.valueFor("text")
        )
        assertEquals(
            ConfigurationValue.StringValue("https://example.com"),
            snapshot.valueFor("url")
        )
        assertEquals(
            ConfigurationValue.BooleanValue(true),
            snapshot.valueFor("toggle")
        )
        assertEquals(
            ConfigurationValue.StringValue("a"),
            snapshot.valueFor("choice")
        )
    }

    @Test
    fun defaultsAndMissingValuesFollowTheSettingsDraftRules() {
        val snapshot = extensionRuntimeConfigurationSnapshot(schema, emptyMap())

        assertEquals(
            ConfigurationValue.StringValue("default"),
            snapshot.valueFor("defaulted")
        )
        assertEquals(null, snapshot.valueFor("empty"))
    }

    @Test
    fun undeclaredAndSecretFieldsAreNeverReadable() {
        val snapshot = extensionRuntimeConfigurationSnapshot(schema, emptyMap())

        assertEquals(ExtensionRuntimeConfigurationRead.FieldNotDeclared, snapshot.get("other"))
        assertEquals(ExtensionRuntimeConfigurationRead.SecretDenied, snapshot.get("secret"))
        assertEquals(false, "set" in snapshot.fields)
    }

    private fun ExtensionRuntimeConfigurationSnapshot.valueFor(fieldId: String): ConfigurationValue? =
        (get(fieldId) as ExtensionRuntimeConfigurationRead.Value).value
}
