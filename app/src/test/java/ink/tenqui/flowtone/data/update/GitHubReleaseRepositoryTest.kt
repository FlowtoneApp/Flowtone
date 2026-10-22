package ink.tenqui.flowtone.data.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseRepositoryTest {
    @Test
    fun versionSegmentsAreComparedNumerically() {
        assertTrue(version("0.15.0") > version("0.9.9"))
        assertTrue(version("0.15.10") > version("0.15.2"))
        assertTrue(version("1.0.0") > version("0.99.99"))
    }

    @Test
    fun optionalBuildSegmentAndTagPrefixAreSupported() {
        assertEquals(0, version("v0.15.1").compareTo(version("0.15.1.0")))
        assertEquals(0, version("0.15.1+build.7").compareTo(version("0.15.1")))
        assertTrue(version("0.15.1.2") > version("0.15.1.1"))
    }

    @Test
    fun prereleaseHasSemanticOrdering() {
        assertTrue(version("0.15.1") > version("0.15.1-rc.10"))
        assertTrue(version("0.15.1-rc.10") > version("0.15.1-rc.2"))
        assertTrue(version("0.15.1-beta") > version("0.15.1-alpha"))
        assertTrue(version("0.15.1-alpha.2") > version("0.15.1-alpha"))
    }

    @Test
    fun invalidVersionsAreNotReportedAsNewVersions() {
        listOf("", "latest", "0.15", "v1.two.3", "1.2.3.4.5", "1.2.3-")
            .forEach { assertNull(AppVersion.parse(it)) }
    }

    @Test
    fun selectsHighestVersionWithAnUploadedApkIncludingPreviews() {
        val old = release("0.14.0").put("published_at", "2026-10-22T00:00:00Z")
        val preview = release("0.15.1-rc.2").put("prerelease", true)
        val noPackage = release("0.16.0").put("assets", JSONArray())
        val draft = release("1.0.0").put("draft", true)
        val selected = requireNotNull(parseLatestRelease(json(old, preview, noPackage, draft)))
        assertEquals("v0.15.1-rc.2", selected.tag)
        assertTrue(selected.prerelease)
    }

    @Test
    fun releaseDetailsAndMarkdownArePreserved() {
        val markdown = "# 更新\n\n- **封面**\n\n[详情](https://github.com/FlowtoneApp/Flowtone)"
        val source = release("0.15.1")
            .put("body", markdown)
            .put("author", JSONObject().put("login", "FlowtoneApp"))
        val parsed = requireNotNull(parseLatestRelease(json(source)))
        assertEquals("Flowtone v0.15.1", parsed.title)
        assertEquals(markdown, parsed.body)
        assertEquals("FlowtoneApp", parsed.author)
        assertEquals("2026-10-20T00:00:00Z", parsed.publishedAt)
        assertEquals("Flowtone.apk", parsed.assets.single().name)
        assertEquals(1024L, parsed.assets.single().size)
    }

    @Test
    fun nonApkAndPendingAssetsAreExcluded() {
        val source = release("0.15.1")
        source.getJSONArray("assets").getJSONObject(0).put("state", "new")
        assertNull(parseLatestRelease(json(source)))
        source.getJSONArray("assets").getJSONObject(0)
            .put("state", "uploaded").put("name", "source.zip")
        assertNull(parseLatestRelease(json(source)))
    }

    @Test
    fun emptyReleasesAndMissingBodyAreHandled() {
        assertNull(parseLatestRelease("[]"))
        assertNull(parseLatestRelease(json(release("nightly"))))
        val source = release("0.15.1").put("body", JSONObject.NULL)
        assertEquals("", requireNotNull(parseLatestRelease(json(source))).body)
    }

    private fun version(value: String) = requireNotNull(AppVersion.parse(value))

    private fun json(vararg releases: JSONObject): String = JSONArray(releases.toList()).toString()

    private fun release(version: String): JSONObject = JSONObject()
        .put("tag_name", "v$version")
        .put("name", "Flowtone v$version")
        .put("html_url", "https://github.com/FlowtoneApp/Flowtone/releases/tag/v$version")
        .put("published_at", "2026-10-20T00:00:00Z")
        .put("draft", false)
        .put("body", "")
        .put("assets", JSONArray().put(JSONObject()
            .put("name", "Flowtone.apk")
            .put("state", "uploaded")
            .put("size", 1024)
            .put("browser_download_url",
                "https://github.com/FlowtoneApp/Flowtone/releases/download/v$version/Flowtone.apk")
        ))
}
