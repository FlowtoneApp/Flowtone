package ink.tenqui.flowtone.data.online.packageformat

import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal object ExtensionManifestJson {
    private val SafeId = Regex("[a-zA-Z0-9._-]+")
    private val HexColor = Regex("#[0-9a-fA-F]{6}")

    fun commonManifest(
        json: JSONObject,
        capabilities: List<String>
    ): ExtensionManifest {
        val manifest = ExtensionManifest(
            formatVersion = json.getInt("formatVersion"),
            id = json.getString("id"),
            name = json.getString("name"),
            version = json.getString("version"),
            author = json.getString("author"),
            description = json.optString("description"),
            entry = json.getString("entry"),
            capabilities = capabilities,
            musicSources = json.stringListOrEmpty("musicSources")
                .map { it.trim().lowercase(Locale.ROOT) },
            color = json.optionalString("color"),
            icon = json.optionalString("icon")?.takeIf(::isSafeIconPath),
            iconColor = json.optionalString("iconColor")?.takeIf { it.matches(HexColor) },
            authorUrl = json.optionalString("authorUrl")?.takeIf(::isValidAuthorUrl),
            sourceTypeLabel = json.optionalSourceTypeLabel()
        )
        validateCommon(manifest)
        return manifest
    }

    fun JSONObject.stringList(name: String): List<String> = getJSONArray(name).strings()

    fun JSONObject.stringListOrEmpty(name: String): List<String> =
        optJSONArray(name)?.strings().orEmpty()

    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

    private fun JSONObject.optionalString(name: String): String? =
        optString(name).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.optionalSourceTypeLabel(): String? {
        if (!has("sourceTypeLabel") || isNull("sourceTypeLabel")) return null
        val value = opt("sourceTypeLabel")
        require(value is String) { "sourceTypeLabel 必须是字符串" }
        val label = value.trim()
        require(
            label.isNotEmpty() &&
                label.length <= MaxSourceTypeLabelLength &&
                label.none(Char::isISOControl)
        ) { "sourceTypeLabel 长度或字符无效" }
        return label
    }

    private const val MaxSourceTypeLabelLength = 40

    private fun isValidAuthorUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        (scheme == "http" || scheme == "https") &&
            !uri.host.isNullOrBlank() &&
            (uri.port == -1 || uri.port in 1..65535)
    }.getOrDefault(false)

    private fun validateCommon(manifest: ExtensionManifest) {
        require(manifest.formatVersion == 2) { "不支持的扩展包版本" }
        require(manifest.id.matches(SafeId) && ".." !in manifest.id) { "扩展 ID 非法" }
        require(manifest.entry == "main.js") { "扩展入口必须是 main.js" }
        require(manifest.name.isNotBlank() && manifest.version.isNotBlank()) { "扩展信息不完整" }
        require(manifest.capabilities.isNotEmpty()) { "扩展未声明能力" }
        manifest.musicSources.forEach { source ->
            require(!source.startsWith("*.") && isValidHost(source)) {
                "音乐服务来源非法：$source"
            }
        }
        require(manifest.color == null || manifest.color.matches(HexColor)) {
            "扩展色彩格式非法，仅支持 #RRGGBB"
        }
    }

    private fun isValidHost(host: String): Boolean = host.contains('.') &&
        host.split('.').all { label ->
            label.isNotBlank() && label.all { it.isLetterOrDigit() || it == '-' }
        }

    private fun isSafeIconPath(path: String): Boolean {
        if (!path.startsWith("assets/") || path.length <= "assets/".length) return false
        if ('\\' in path || path.startsWith('/') || ':' in path) return false
        return path.split('/').all { segment ->
            segment.isNotBlank() && segment != "." && segment != ".."
        }
    }
}
