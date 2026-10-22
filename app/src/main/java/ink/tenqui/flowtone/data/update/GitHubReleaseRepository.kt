package ink.tenqui.flowtone.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class ReleaseAsset(val name: String, val url: String, val size: Long)

internal data class AppRelease(
    val tag: String,
    val version: AppVersion,
    val title: String,
    val body: String,
    val url: String,
    val publishedAt: String,
    val author: String,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>
)

/** 与项目三段/四段版本号兼容；按数值比较各段，构建 metadata 不影响先后。 */
internal data class AppVersion(
    private val parts: List<Long>,
    private val prerelease: List<String>
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (index in 0..3) {
            val result = parts.getOrElse(index) { 0L }
                .compareTo(other.parts.getOrElse(index) { 0L })
            if (result != 0) return result
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return when {
                prerelease.isEmpty() && other.prerelease.isEmpty() -> 0
                prerelease.isEmpty() -> 1
                else -> -1
            }
        }
        for (index in 0 until minOf(prerelease.size, other.prerelease.size)) {
            val left = prerelease[index]
            val right = other.prerelease[index]
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            val result = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val Pattern = Regex(
            """[vV]?(\d+\.\d+\.\d+(?:\.\d+)?)(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?(?:\+[0-9A-Za-z.-]+)?"""
        )

        fun parse(value: String): AppVersion? {
            val match = Pattern.matchEntire(value.trim()) ?: return null
            val parts = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
            val prerelease = match.groupValues[2].takeIf(String::isNotEmpty)?.split('.').orEmpty()
            return AppVersion(parts, prerelease)
        }
    }
}

internal class UpdateCheckException(message: String) : IOException(message)

internal class GitHubReleaseRepository {
    suspend fun latestRelease(): AppRelease = withContext(Dispatchers.IO) {
        // releases/latest 排除了预发布版本；Flowtone 的公开预览包也需要参与检查。
        val connection = URL(ReleaseApiUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "Flowtone-UpdateChecker")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw UpdateCheckException(
                    if (status == 403 || status == 429) "GitHub 请求受限，请稍后重试"
                    else "检查更新失败（HTTP $status）"
                )
            }
            val content = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MaxResponseBytes) {
                        throw UpdateCheckException("更新信息过大")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
            ensureActive()
            parseLatestRelease(content) ?: throw UpdateCheckException("暂未找到可用的 APK 发布版本")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val ReleasesUrl = "https://github.com/FlowtoneApp/Flowtone/releases"
        private const val ReleaseApiUrl =
            "https://api.github.com/repos/FlowtoneApp/Flowtone/releases?per_page=100"
        private const val MaxResponseBytes = 2 * 1024 * 1024
    }
}

/** 选择有 APK 的最高版本，避免较晚补发的旧版本或草稿触发错误的更新提示。 */
internal fun parseLatestRelease(json: String): AppRelease? {
    val releases = JSONArray(json)
    return (0 until releases.length()).mapNotNull { index ->
        val item = releases.getJSONObject(index)
        if (item.optBoolean("draft")) return@mapNotNull null
        val tag = item.optString("tag_name")
        val version = AppVersion.parse(tag) ?: return@mapNotNull null
        val sourceAssets = item.optJSONArray("assets") ?: JSONArray()
        val assets = (0 until sourceAssets.length()).mapNotNull assetLoop@ { assetIndex ->
            val asset = sourceAssets.getJSONObject(assetIndex)
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true) ||
                asset.optString("state") != "uploaded" ||
                !url.startsWith("https://github.com/")
            ) return@assetLoop null
            ReleaseAsset(name, url, asset.optLong("size"))
        }
        val url = item.optString("html_url")
        if (assets.isEmpty() || !url.startsWith(GitHubReleaseRepository.ReleasesUrl + "/")) {
            return@mapNotNull null
        }
        AppRelease(
            tag = tag,
            version = version,
            title = item.optString("name").ifBlank { tag },
            body = item.optString("body", ""),
            url = url,
            publishedAt = item.optString("published_at"),
            author = item.optJSONObject("author")?.optString("login").orEmpty(),
            prerelease = item.optBoolean("prerelease"),
            assets = assets
        )
    }.maxWithOrNull(compareBy<AppRelease> { it.version }.thenBy { it.publishedAt })
}
