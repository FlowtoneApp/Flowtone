package ink.tenqui.flowtone.ui.library

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ExtensionManager
import java.util.Locale
import kotlinx.coroutines.delay

private const val ExperimentalAvatarMaxAttempts = 3
private const val ExperimentalAvatarRetryDelayMillis = 5_000L
private const val ExperimentalAvatarLogTag = "ExperimentalArtistAvatar"

@Composable
internal fun rememberExperimentalArtistAvatarImage(
    songTitle: String,
    artistName: String
): ExtensionImage? {
    val context = LocalContext.current
    val registry = remember(context) { ExtensionManager.get(context).artistAvatarRegistry }
    var image by remember(songTitle, artistName) { mutableStateOf<ExtensionImage?>(null) }

    LaunchedEffect(songTitle, artistName) {
        val requestKey = experimentalAvatarRequestKey(songTitle, artistName)
        if (requestKey == null || ExperimentalArtistAvatarRequestGate.isExhausted(requestKey)) return@LaunchedEffect
        repeat(ExperimentalAvatarMaxAttempts) { attempt ->
            val avatar = registry.findArtistAvatar(songTitle, artistName)
            if (avatar != null) {
                image = avatar.image
                return@LaunchedEffect
            }
            if (attempt < ExperimentalAvatarMaxAttempts - 1) delay(ExperimentalAvatarRetryDelayMillis)
        }
        ExperimentalArtistAvatarRequestGate.markExhausted(requestKey)
    }
    return image
}

/** 图片数据保持为 [ExtensionImage]，只经 Flowtone 的专用 Coil Fetcher 取得字节。 */
@Composable
internal fun ExperimentalArtistAvatarImage(
    image: ExtensionImage?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember(context) { ExtensionManager.get(context) }
    var imageLoaded by remember(image) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "ExperimentalArtistAvatarFade"
    )
    if (image != null) {
        AsyncImage(
            model = image,
            imageLoader = manager.extensionImageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { imageLoaded = true; Log.d(ExperimentalAvatarLogTag, "image loaded") },
            onError = { imageLoaded = false; Log.d(ExperimentalAvatarLogTag, "image load failed") },
            modifier = modifier.alpha(imageAlpha)
        )
    }
}

private fun experimentalAvatarRequestKey(songTitle: String, artistName: String): String? {
    val title = songTitle.trim().lowercase(Locale.ROOT)
    val artist = artistName.trim().lowercase(Locale.ROOT)
    return if (title.isEmpty() || artist.isEmpty()) null else "$title\n$artist"
}

private object ExperimentalArtistAvatarRequestGate {
    private val exhaustedKeys = mutableSetOf<String>()
    @Synchronized fun isExhausted(key: String): Boolean = key in exhaustedKeys
    @Synchronized fun markExhausted(key: String) { exhaustedKeys += key }
}
