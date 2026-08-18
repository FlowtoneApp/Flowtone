package ink.tenqui.flowtone.data.online.packageformat

import org.json.JSONObject

data class ExtensionManifest(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val entry: String,
    val capabilities: List<String>,
    val networkHosts: List<String>
) {
    val supportsArtistAvatar: Boolean get() = "artist_avatar" in capabilities
    val supportsMusicProvider: Boolean get() = "music_provider" in capabilities
}

object ExtensionManifestParser {
    private val SafeId = Regex("[a-zA-Z0-9._-]+")

    fun parse(text: String): ExtensionManifest {
        val json = JSONObject(text)
        val capabilities = json.getJSONArray("capabilities").let { array ->
            List(array.length()) { array.getString(it) }
        }
        val network = json.optJSONObject("permissions")?.optJSONObject("network")
        val hosts = network?.optJSONArray("hosts")?.let { array ->
            List(array.length()) { array.getString(it).lowercase() }
        }.orEmpty()
        return ExtensionManifest(
            formatVersion = json.getInt("formatVersion"),
            id = json.getString("id"),
            name = json.getString("name"),
            version = json.getString("version"),
            author = json.getString("author"),
            description = json.optString("description"),
            entry = json.getString("entry"),
            capabilities = capabilities,
            networkHosts = hosts
        ).also(::validate)
    }

    fun validate(manifest: ExtensionManifest) {
        require(manifest.formatVersion == 1) { "不支持的扩展包版本" }
        require(manifest.id.matches(SafeId) && ".." !in manifest.id) { "扩展 ID 非法" }
        require(manifest.entry == "main.js") { "v1 入口必须是 main.js" }
        require(manifest.name.isNotBlank() && manifest.version.isNotBlank()) { "扩展信息不完整" }
        require(manifest.capabilities.isNotEmpty()) { "扩展未声明能力" }
        manifest.networkHosts.forEach { rule ->
            require(isValidHostRule(rule)) { "网络 host 规则非法：$rule" }
        }
    }

    private fun isValidHostRule(rule: String): Boolean {
        val host = rule.removePrefix("*.")
        return rule.isNotBlank() && '*' !in host && host.contains('.') &&
            host.split('.').all { label -> label.isNotBlank() && label.all { it.isLetterOrDigit() || it == '-' } }
    }
}

data class InstalledExtension(
    val manifest: ExtensionManifest,
    val directory: java.io.File,
    val runtimeAvailable: Boolean
)
