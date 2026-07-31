package ink.tenqui.flowtone.ui.player.lyrics

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import ink.tenqui.flowtone.ui.player.CrossfadeArtworkImage

@Composable
internal fun LyricsBlurredArtworkBackground(
    imageRequest: ImageRequest?,
    visibilityProgress: Float,
    waitForArtworkLoad: Boolean,
    modifier: Modifier = Modifier
) {
    if (visibilityProgress <= 0.001f) {
        return
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val artworkModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier
            .fillMaxSize()
            .blur(72.dp)
    } else {
        // Android 9-11 使用已缩小的背景请求并加深遮罩，避免依赖 RenderEffect。
        Modifier.fillMaxSize()
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = visibilityProgress.coerceIn(0f, 1f)
            scaleX = 1.18f
            scaleY = 1.18f
        }
    ) {
        CrossfadeArtworkImage(
            imageRequest = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            waitForImageLoad = waitForArtworkLoad,
            modifier = artworkModifier
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(alpha = if (isDarkTheme) 0.58f else 0.48f)
                )
        )
    }
}
