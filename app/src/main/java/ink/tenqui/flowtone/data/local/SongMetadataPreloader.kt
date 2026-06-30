package ink.tenqui.flowtone.data.local

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import ink.tenqui.flowtone.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class SongMetadataPreloader(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun preload(songs: List<Song>) {
        val artworkUris = songs.mapNotNull { it.artworkUri }.distinct()
        if (artworkUris.isEmpty()) {
            return
        }

        withContext(Dispatchers.IO) {
            artworkUris.forEach { artworkUri ->
                coroutineContext.ensureActive()
                val request = ImageRequest.Builder(appContext)
                    .data(artworkUri)
                    .size(768, 768)
                    .crossfade(false)
                    .build()
                appContext.imageLoader.execute(request)
            }
        }
    }
}
