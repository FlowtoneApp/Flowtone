package ink.tenqui.flowtone.data.online.configuration

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionConfigStoreTest {
    @Test
    fun namespacesAreIsolatedAndSurviveStoreRecreation() {
        val root = Files.createTempDirectory("extension-config").toFile()
        val schema = textSchema()
        ExtensionConfigStore(root).save("a.extension", schema, mapOf("text" to ConfigurationValue.StringValue("A")))
        ExtensionConfigStore(root).save("b.extension", schema, mapOf("text" to ConfigurationValue.StringValue("B")))

        val recreated = ExtensionConfigStore(root)
        assertEquals("A", (recreated.load("a.extension")["text"] as ConfigurationValue.StringValue).value)
        assertEquals("B", (recreated.load("b.extension")["text"] as ConfigurationValue.StringValue).value)
    }

    @Test
    fun validatesTextUrlToggleAndChoiceBeforePersisting() {
        val root = Files.createTempDirectory("extension-config").toFile()
        val store = ExtensionConfigStore(root)
        val schema = ordinarySchema()
        val saved = store.save("example.extension", schema, mapOf(
            "text" to ConfigurationValue.StringValue("hello"),
            "url" to ConfigurationValue.StringValue("https://example.com"),
            "toggle" to ConfigurationValue.BooleanValue(true),
            "choice" to ConfigurationValue.StringValue("a")
        ))

        assertEquals(4, saved.size)
        assertTrue(validateExtensionConfiguration(schema, mapOf("url" to ConfigurationValue.StringValue("bad"))).errors.containsKey("url"))
        assertTrue(validateExtensionConfiguration(schema, mapOf("choice" to ConfigurationValue.StringValue("missing"))).errors.containsKey("choice"))
    }

    @Test
    fun defaultsEnterDraftButAreNotPersistedUntilSave() {
        val root = Files.createTempDirectory("extension-config").toFile()
        val store = ExtensionConfigStore(root)
        val schema = ConfigurationSchema(listOf(ConfigurationFieldDefinition(
            id = "text", type = ConfigurationFieldType.Text, label = "Text", required = true,
            defaultValue = ConfigurationValue.StringValue("default")
        )))

        assertEquals("default", (extensionConfigurationDraft(schema, store.load("example.extension"))["text"] as ConfigurationValue.StringValue).value)
        assertTrue(store.load("example.extension").isEmpty())
    }

    @Test
    fun orphanAndSecretValuesNeverReachNewConfigFile() {
        val root = Files.createTempDirectory("extension-config").toFile()
        val store = ExtensionConfigStore(root)
        val schema = ConfigurationSchema(listOf(
            ConfigurationFieldDefinition("text", ConfigurationFieldType.Text, "Text"),
            ConfigurationFieldDefinition("secret", ConfigurationFieldType.Secret, "Secret")
        ))
        val saved = store.save("example.extension", schema, mapOf(
            "text" to ConfigurationValue.StringValue("kept"),
            "orphan" to ConfigurationValue.StringValue("dropped"),
            "secret" to ConfigurationValue.StringValue("must-not-write")
        ))

        assertEquals(setOf("text"), saved.keys)
        assertFalse(root.resolve("example.extension/config.json").readText().contains("must-not-write"))
    }

    @Test
    fun requiredStateUsesSavedValuesAndDeleteOnlyAffectsOneNamespace() {
        val root = Files.createTempDirectory("extension-config").toFile()
        val store = ExtensionConfigStore(root)
        val schema = textSchema()
        assertEquals(ConfigurationState.Unconfigured, ConfigurationStateResolver.resolve(schema, emptyMap()))
        store.save("a.extension", schema, mapOf("text" to ConfigurationValue.StringValue("ready")))
        store.save("b.extension", schema, mapOf("text" to ConfigurationValue.StringValue("other")))
        assertEquals(ConfigurationState.Configured, ConfigurationStateResolver.resolve(schema, store.load("a.extension")))

        store.delete("a.extension")
        assertTrue(store.load("a.extension").isEmpty())
        assertFalse(store.load("b.extension").isEmpty())
    }

    private fun textSchema() = ConfigurationSchema(listOf(
        ConfigurationFieldDefinition("text", ConfigurationFieldType.Text, "Text", required = true)
    ))

    private fun ordinarySchema() = ConfigurationSchema(listOf(
        ConfigurationFieldDefinition("text", ConfigurationFieldType.Text, "Text"),
        ConfigurationFieldDefinition("url", ConfigurationFieldType.Url, "Url"),
        ConfigurationFieldDefinition("toggle", ConfigurationFieldType.Toggle, "Toggle"),
        ConfigurationFieldDefinition("choice", ConfigurationFieldType.Choice, "Choice", choices = listOf(ConfigurationChoice("a", "A")))
    ))
}
