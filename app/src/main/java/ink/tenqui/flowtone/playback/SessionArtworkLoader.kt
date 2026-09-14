package ink.tenqui.flowtone.playback

import android.content.Context
import android.graphics.Bitmap
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ExtensionManager
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads only the active provider artwork through the extension-aware Coil pipeline. */
internal class SessionArtworkLoader(
    private val context: Context,
    private val extensionManager: ExtensionManager
) {
    suspend fun load(image: ExtensionImage): ByteArray? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(image)
            .size(MAX_ARTWORK_EDGE_PX, MAX_ARTWORK_EDGE_PX)
            .allowHardware(false)
            .crossfade(false)
            .build()
        val bitmap = (extensionManager.extensionImageLoader.execute(request) as? SuccessResult)
            ?.image
            ?.toBitmap(MAX_ARTWORK_EDGE_PX, MAX_ARTWORK_EDGE_PX)
            ?: return@withContext null
        ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                return@withContext null
            }
            output.toByteArray().takeIf { it.size <= MAX_ARTWORK_DATA_BYTES }
        }
    }

    private companion object {
        const val MAX_ARTWORK_EDGE_PX = 512
        const val JPEG_QUALITY = 88
        const val MAX_ARTWORK_DATA_BYTES = 1_000_000
    }
}
