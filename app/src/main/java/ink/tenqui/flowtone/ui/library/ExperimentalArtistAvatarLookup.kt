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
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.data.local.localArtistStableId
import ink.tenqui.flowtone.data.online.ExtensionManager

private const val ExperimentalAvatarLogTag = "ExperimentalArtistAvatar"
private const val ArtistAvatarUiCacheMaxEntries = 64

private object ArtistAvatarUiMemoryCache {
    private val resolvedImages = LinkedHashMap<String, ExtensionImage>(16, 0.75f, true)
    private val loadedImages = LinkedHashMap<ExtensionImage, Unit>(16, 0.75f, true)

    fun queryKey(songTitle: String, artistName: String): String =
        "${localArtistStableId(artistName)}\n${songTitle.trim()}"

    @Synchronized
    fun resolvedImage(key: String): ExtensionImage? = resolvedImages[key]

    @Synchronized
    fun rememberResolvedImage(key: String, image: ExtensionImage) {
        resolvedImages[key] = image
        trimToLimit(resolvedImages)
    }

    @Synchronized
    fun wasLoaded(image: ExtensionImage): Boolean = loadedImages[image] != null

    @Synchronized
    fun rememberLoaded(image: ExtensionImage) {
        loadedImages[image] = Unit
        trimToLimit(loadedImages)
    }

    @Synchronized
    fun forgetLoaded(image: ExtensionImage) {
        loadedImages.remove(image)
    }

    private fun <K, V> trimToLimit(values: LinkedHashMap<K, V>) {
        while (values.size > ArtistAvatarUiCacheMaxEntries) {
            values.remove(values.entries.first().key)
        }
    }
}

@Composable
internal fun rememberExperimentalArtistAvatarImage(
    songTitle: String,
    artistName: String
): ExtensionImage? {
    val context = LocalContext.current
    val registry = remember(context) { ExtensionManager.get(context).artistAvatarRegistry }
    val queryKey = remember(songTitle, artistName) {
        ArtistAvatarUiMemoryCache.queryKey(songTitle, artistName)
    }
    var image by remember(queryKey) {
        mutableStateOf(ArtistAvatarUiMemoryCache.resolvedImage(queryKey))
    }

    LaunchedEffect(queryKey) {
        if (image == null) {
            image = registry.findArtistAvatar(songTitle, artistName)?.image?.also { resolved ->
                ArtistAvatarUiMemoryCache.rememberResolvedImage(queryKey, resolved)
            }
        }
    }
    return image
}

@Composable
internal fun rememberArtistMetadata(artistName: String): ArtistMetadata? {
    val context = LocalContext.current
    val registry = remember(context) { ExtensionManager.get(context).artistMetadataRegistry }
    var metadata by remember(artistName) { mutableStateOf<ArtistMetadata?>(null) }

    LaunchedEffect(artistName) {
        metadata = registry.findArtistMetadata(artistName)
    }
    return metadata
}

/** 图片数据保持为 [ExtensionImage]，只经 Flowtone 的专用 Coil Fetcher 取得字节。 */
@Composable
internal fun ExperimentalArtistAvatarImage(
    image: ExtensionImage?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember(context) { ExtensionManager.get(context) }
    var imageLoaded by remember(image) {
        mutableStateOf(image != null && ArtistAvatarUiMemoryCache.wasLoaded(image))
    }
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
            onSuccess = {
                ArtistAvatarUiMemoryCache.rememberLoaded(image)
                imageLoaded = true
                Log.d(ExperimentalAvatarLogTag, "image loaded")
            },
            onError = {
                ArtistAvatarUiMemoryCache.forgetLoaded(image)
                imageLoaded = false
                Log.d(ExperimentalAvatarLogTag, "image load failed")
            },
            modifier = modifier.alpha(imageAlpha)
        )
    }
}
