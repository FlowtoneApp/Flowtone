package ink.tenqui.flowtone.data.online.packageformat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUiRegressionFixtureVariantTest {
    @Test
    fun debugAndFastShareOneFixtureWhileReleaseRemainsExcluded() {
        val buildScript = projectFile("app/build.gradle.kts", "build.gradle.kts").readText()
        val extensionManager = projectFile(
            "app/src/main/java/ink/tenqui/flowtone/data/online/ExtensionManager.kt",
            "src/main/java/ink/tenqui/flowtone/data/online/ExtensionManager.kt"
        ).readText()
        val fixtureRoot = checkNotNull(
            projectFile(
                "app/src/internalTestFixture/provider-artist-profile-fixture/manifest.json",
                "src/internalTestFixture/provider-artist-profile-fixture/manifest.json"
            ).parentFile
        )

        assertTrue(fixtureRoot.resolve("manifest.json").isFile)
        assertTrue(fixtureRoot.resolve("main.js").isFile)
        assertTrue(buildScript.contains("listOf(\"debug\", \"fast\").forEach"))
        assertTrue(buildScript.contains("src/internalTestFixture/provider-artist-profile-fixture"))
        assertFalse(buildScript.contains("src/debug/provider-artist-profile-fixture"))
        assertTrue(buildScript.fastBuildType().contains("isDebuggable = false"))
        assertTrue(buildScript.fastBuildType().contains("\"UI_TEST_FIXTURE_ENABLED\","))
        assertTrue(buildScript.fastBuildType().contains("\"true\""))
        assertFalse(buildScript.releaseBuildType().contains("UI_TEST_FIXTURE_ENABLED"))
        assertTrue(extensionManager.contains("BuildConfig.UI_TEST_FIXTURE_ENABLED"))
        assertFalse(extensionManager.contains("if (!BuildConfig.DEBUG) return"))
    }

    private fun String.fastBuildType(): String =
        substringAfter("create(\"fast\")").substringBefore("matchingFallbacks")

    private fun String.releaseBuildType(): String =
        substringAfter("release {").substringBefore("create(\"benchmark\")")

    private fun projectFile(vararg candidates: String): File = candidates
        .map(::File)
        .firstOrNull(File::isFile)
        ?: error("Project file is missing: ${candidates.joinToString()}")
}
