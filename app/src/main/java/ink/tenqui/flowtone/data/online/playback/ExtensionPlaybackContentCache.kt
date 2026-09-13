package ink.tenqui.flowtone.data.online.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@UnstableApi
internal class ExtensionPlaybackContentCache(
    context: Context,
    private val upstreamFactory: ExtensionMediaDataSource.Factory
) : AutoCloseable {
    private val cache = SimpleCache(
        context.cacheDir.resolve(CacheDirectoryName),
        LeastRecentlyUsedCacheEvictor(MaxCacheBytes),
        StandaloneDatabaseProvider(context)
    )

    val playbackDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    suspend fun preloadPrefix(mediaItem: MediaItem, percentage: Int): Long =
        withContext(Dispatchers.IO) {
        val uri = mediaItem.localConfiguration?.uri ?: return@withContext 0L
        val boundedPercentage = percentage.coerceIn(0, 100)
        if (boundedPercentage == 0) return@withContext 0L

        val probe = upstreamFactory.createDataSource() as ExtensionMediaDataSource
        try {
            probe.open(
                DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(1L)
                    .setLength(1L)
                    .build()
            )
            coroutineContext.ensureActive()
            if (probe.responseStatusCode != 206) return@withContext 0L
            val totalLength = probe.responseTotalLength ?: return@withContext 0L
            val targetLength = preloadByteCount(totalLength, boundedPercentage)
            if (targetLength <= 0L) return@withContext 0L

            val dataSource = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSourceForDownloading()
            try {
                dataSource.open(
                    DataSpec.Builder()
                        .setUri(uri)
                        .setPosition(0L)
                        .setLength(targetLength)
                        .build()
                )
                val buffer = ByteArray(ReadBufferBytes)
                while (true) {
                    coroutineContext.ensureActive()
                    if (dataSource.read(buffer, 0, buffer.size) == C.RESULT_END_OF_INPUT) break
                }
            } finally {
                dataSource.close()
            }
            return@withContext targetLength
        } finally {
            probe.close()
        }
    }

    override fun close() {
        cache.release()
    }

    private companion object {
        const val CacheDirectoryName = "online-playback"
        const val MaxCacheBytes = 256L * 1024L * 1024L
        const val ReadBufferBytes = 32 * 1024
    }
}

internal fun preloadByteCount(totalBytes: Long, percentage: Int): Long {
    if (totalBytes <= 0L || percentage <= 0) return 0L
    val boundedPercentage = percentage.coerceAtMost(100)
    return (totalBytes / 100L * boundedPercentage +
        totalBytes % 100L * boundedPercentage / 100L)
        .coerceIn(1L, totalBytes)
}
