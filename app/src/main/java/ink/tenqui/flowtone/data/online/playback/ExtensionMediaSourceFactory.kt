package ink.tenqui.flowtone.data.online.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import ink.tenqui.flowtone.core.online.ExtensionPlaybackResourceType

/**
 * 仅对 Host 已登记的 opaque extension URI 选择专用媒体源。
 * HLS 类型来自 playback session 元数据，不依赖 URI 的文件扩展名。
 */
@UnstableApi
class ExtensionMediaSourceFactory(
    private val resources: ExtensionPlaybackResourceStore,
    private val extensionDataSourceFactory: ExtensionMediaDataSource.Factory,
    private val fallbackFactory: MediaSource.Factory
) : MediaSource.Factory {
    private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        val resource = uri?.let(resources::resolve)
        return if (resource?.type == ExtensionPlaybackResourceType.Hls) {
            HlsMediaSource.Factory(extensionDataSourceFactory.forResource(resource))
                .also { factory ->
                    drmSessionManagerProvider?.let(factory::setDrmSessionManagerProvider)
                    loadErrorHandlingPolicy?.let(factory::setLoadErrorHandlingPolicy)
                }
                .createMediaSource(mediaItem)
        } else {
            fallbackFactory.createMediaSource(mediaItem)
        }
    }

    override fun getSupportedTypes(): IntArray = fallbackFactory.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider
    ): MediaSource.Factory {
        this.drmSessionManagerProvider = drmSessionManagerProvider
        fallbackFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy
    ): MediaSource.Factory {
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
        fallbackFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }
}
