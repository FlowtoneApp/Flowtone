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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import ink.tenqui.flowtone.data.online.ExtensionManager
import java.util.Locale
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage

private const val ExperimentalAvatarMaxAttempts = 3
private const val ExperimentalAvatarRetryDelayMillis = 5_000L
private const val ExperimentalAvatarLogTag = "ExperimentalArtistAvatar"

/**
 * 临时试验：歌手页可见时查询头像。删除此文件及其调用即可完整移除该行为。
 *
 * 同一查询在本次应用进程内失败三次后不再尝试；重启应用会清空该记录。
 */
@Composable
internal fun rememberExperimentalArtistAvatarUrl(
    songTitle: String,
    artistName: String
): String? {
    val context = LocalContext.current
    val registry = remember(context) { ExtensionManager.get(context).artistAvatarRegistry }
    var avatarUrl by remember(songTitle, artistName) { mutableStateOf<String?>(null) }

    LaunchedEffect(songTitle, artistName) {
        val requestKey = experimentalAvatarRequestKey(songTitle, artistName)
        if (requestKey == null || ExperimentalArtistAvatarRequestGate.isExhausted(requestKey)) {
            return@LaunchedEffect
        }

        repeat(ExperimentalAvatarMaxAttempts) { attempt ->
            val avatar = registry.findArtistAvatar(songTitle, artistName)
            if (avatar != null) {
                avatarUrl = avatar.imageUrl
                return@LaunchedEffect
            }
            if (attempt < ExperimentalAvatarMaxAttempts - 1) {
                delay(ExperimentalAvatarRetryDelayMillis)
            }
        }
        ExperimentalArtistAvatarRequestGate.markExhausted(requestKey)
    }

    return avatarUrl
}

/** 临时试验头像图片层：仅在网络图片成功加载后覆盖并淡入默认占位图。 */
@Composable
internal fun ExperimentalArtistAvatarImage(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    var imageLoaded by remember(imageUrl) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (imageLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "ExperimentalArtistAvatarFade"
    )
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = {
                imageLoaded = true
                Log.d(ExperimentalAvatarLogTag, "image loaded")
            },
            onError = {
                imageLoaded = false
                Log.d(ExperimentalAvatarLogTag, "image load failed")
            },
            modifier = modifier.alpha(imageAlpha)
        )
    }
}

private fun experimentalAvatarRequestKey(songTitle: String, artistName: String): String? {
    val normalizedTitle = songTitle.trim().lowercase(Locale.ROOT)
    val normalizedArtist = artistName.trim().lowercase(Locale.ROOT)
    if (normalizedTitle.isEmpty() || normalizedArtist.isEmpty()) return null
    return "$normalizedTitle\n$normalizedArtist"
}

private object ExperimentalArtistAvatarRequestGate {
    private val exhaustedKeys = mutableSetOf<String>()

    @Synchronized
    fun isExhausted(key: String): Boolean = key in exhaustedKeys

    @Synchronized
    fun markExhausted(key: String) {
        exhaustedKeys += key
    }
}
