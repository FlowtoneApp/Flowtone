package ink.tenqui.flowtone.data.online.packageformat

import android.graphics.BitmapFactory
import ink.tenqui.flowtone.data.search.SearchProviderVisual
import java.io.File
import kotlin.math.abs

/** 在扩展目录边界内解析并校验 Provider 的本地图标；任何失败都只返回 fallback 所需的空资源。 */
internal object ExtensionProviderVisualResolver {
    private val SvgViewBox = Regex(
        """viewBox\s*=\s*[\"']\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s+([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s+([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s+([+-]?(?:\d+(?:\.\d*)?|\.\d+))\s*[\"']""",
        RegexOption.IGNORE_CASE
    )
    private val SvgWidthHeight = Regex(
        """<svg\b[^>]*\bwidth\s*=\s*[\"']\s*(\d+(?:\.\d+)?)\s*[\"'][^>]*\bheight\s*=\s*[\"']\s*(\d+(?:\.\d+)?)\s*[\"']""",
        RegexOption.IGNORE_CASE
    )

    fun resolve(extension: InstalledExtension): SearchProviderVisual? = runCatching {
        val iconColor = extension.manifest.iconColor
        val iconFile = extension.manifest.icon?.let { path ->
            resolveIconFile(extension.directory, path)
        }
        if (iconFile != null || iconColor != null) {
            SearchProviderVisual(iconFile = iconFile, iconColor = iconColor)
        } else {
            null
        }
    }.getOrNull()

    private fun resolveIconFile(root: File, relativePath: String): File? {
        val rootPath = runCatching { root.canonicalFile.toPath() }.getOrNull() ?: return null
        if (!relativePath.startsWith("assets/") || '\\' in relativePath || ".." in relativePath) return null
        val file = runCatching { File(root, relativePath).canonicalFile }.getOrNull() ?: return null
        if (!file.isFile || !file.toPath().startsWith(rootPath)) return null
        return when (file.extension.lowercase()) {
            "png", "webp", "jpg", "jpeg" -> file.takeIf(::isValidRaster)
            "svg" -> file.takeIf(::isValidSvg)
            else -> null
        }
    }

    private fun isValidRaster(file: File): Boolean {
        if (file.length() > RasterMaxBytes) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth in 1..MaxRasterEdge &&
            options.outHeight in 1..MaxRasterEdge &&
            options.outWidth == options.outHeight
    }

    private fun isValidSvg(file: File): Boolean {
        if (file.length() > SvgMaxBytes) return false
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        val viewBox = SvgViewBox.find(text)
        if (viewBox != null) {
            val width = viewBox.groupValues[3].toDoubleOrNull() ?: return false
            val height = viewBox.groupValues[4].toDoubleOrNull() ?: return false
            return width > 0.0 && height > 0.0 && abs(width - height) < 0.0001
        }
        val widthHeight = SvgWidthHeight.find(text) ?: return false
        val width = widthHeight.groupValues[1].toDoubleOrNull() ?: return false
        val height = widthHeight.groupValues[2].toDoubleOrNull() ?: return false
        return width > 0.0 && width == height
    }

    private const val MaxRasterEdge = 512
    private const val RasterMaxBytes = 1L * 1024 * 1024
    private const val SvgMaxBytes = 256L * 1024
}
