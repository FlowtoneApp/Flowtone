package ink.tenqui.flowtone.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.search.SearchProviderVisual
import coil3.compose.AsyncImage

/** Search 与曲库来源共用扩展图标加载、iconColor tint 和失败回退语义。 */
@Composable
internal fun ProviderExtensionIcon(
    visual: SearchProviderVisual?,
    fallbackColor: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    cachedPainter: Painter? = null,
    onPainterLoaded: ((Painter) -> Unit)? = null
) {
    val iconColor = visual?.iconColor?.let(::extensionManifestColor)
    val fallbackIconColor = iconColor ?: fallbackColor
    val iconFile = visual?.iconFile
    if (iconFile == null) {
        Icon(
            imageVector = Icons.Rounded.Radio,
            contentDescription = null,
            tint = fallbackIconColor,
            modifier = modifier.size(iconSize)
        )
        return
    }

    if (cachedPainter != null) {
        Image(
            painter = cachedPainter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = iconColor?.let(ColorFilter::tint),
            modifier = modifier.size(iconSize)
        )
        return
    }

    val context = LocalContext.current
    val imageLoader = remember(context) { ExtensionManager.get(context).extensionImageLoader }
    var localImageLoaded by remember(iconFile) { mutableStateOf(false) }
    Box(
        modifier = modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        if (!localImageLoaded) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                tint = fallbackIconColor,
                modifier = Modifier.size(iconSize)
            )
        }
        AsyncImage(
            model = iconFile,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = iconColor?.let(ColorFilter::tint),
            onSuccess = { state ->
                localImageLoaded = true
                onPainterLoaded?.invoke(state.painter)
            },
            onError = {
                localImageLoaded = false
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal fun extensionManifestColor(hex: String): Color =
    Color((0xFF000000L or hex.removePrefix("#").toLong(16)).toInt())
