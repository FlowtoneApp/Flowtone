package ink.tenqui.flowtone.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.SourceType
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.core.online.ExtensionTrackRef
import ink.tenqui.flowtone.data.online.ProviderSong

/** Binder-safe logical queue wire format used by MediaController and MediaSession. */
object PlaybackQueueMediaItemCodec {
    private const val VERSION = 1
    private const val EXTRA_VERSION = "flowtone.logical.version"
    private const val EXTRA_QUEUE_ID = "flowtone.logical.queue_id"
    private const val EXTRA_STABLE_ID = "flowtone.logical.stable_id"
    private const val EXTRA_SOURCE_INDEX = "flowtone.logical.source_index"
    private const val EXTRA_SONG_ID = "flowtone.logical.song_id"
    private const val EXTRA_SONG_SOURCE = "flowtone.logical.song_source"
    private const val EXTRA_SONG_URI = "flowtone.logical.song_uri"
    private const val EXTRA_DURATION = "flowtone.logical.duration"
    private const val EXTRA_ARTWORK_URI = "flowtone.logical.artwork_uri"
    private const val EXTRA_ALBUM_ID = "flowtone.logical.album_id"
    private const val EXTRA_ALBUM_TITLE = "flowtone.logical.album_title"
    private const val EXTRA_DISPLAY_NAME = "flowtone.logical.display_name"
    private const val EXTRA_TRACK_KIND = "flowtone.logical.track_kind"
    private const val EXTRA_TRACK_LOCAL_ID = "flowtone.logical.track_local_id"
    private const val EXTRA_TRACK_HOST = "flowtone.logical.track_host"
    private const val EXTRA_TRACK_PERSISTENT_ID = "flowtone.logical.track_persistent_id"
    private const val EXTRA_PROVIDER_EXTENSION = "flowtone.logical.provider_extension"
    private const val EXTRA_PROVIDER_OPAQUE_ID = "flowtone.logical.provider_opaque_id"
    private const val EXTRA_PROVIDER_PERSISTENT_ID = "flowtone.logical.provider_persistent_id"
    private const val EXTRA_PROVIDER_SOURCE_HOST = "flowtone.logical.provider_source_host"
    private const val EXTRA_ARTWORK_EXTENSION = "flowtone.logical.artwork_extension"
    private const val EXTRA_ARTWORK_URL = "flowtone.logical.artwork_url"
    private const val EXTRA_LARGE_ARTWORK_EXTENSION = "flowtone.logical.large_artwork_extension"
    private const val EXTRA_LARGE_ARTWORK_URL = "flowtone.logical.large_artwork_url"
    private const val EXTRA_SOURCE_TYPE = "flowtone.logical.context_type"
    private const val EXTRA_SOURCE_KEY = "flowtone.logical.context_key"
    private const val EXTRA_SOURCE_ID = "flowtone.logical.context_id"
    private const val EXTRA_SOURCE_NAME = "flowtone.logical.context_name"

    fun encode(item: PlaybackQueueItem, artworkData: ByteArray? = null): MediaItem {
        val song = item.presentation
        val extras = Bundle().apply {
            putInt(EXTRA_VERSION, VERSION)
            putString(EXTRA_QUEUE_ID, item.queueId)
            putString(EXTRA_STABLE_ID, item.stableIdentity)
            putInt(EXTRA_SOURCE_INDEX, item.sourceIndex)
            putLong(EXTRA_SONG_ID, song.id)
            putString(EXTRA_SONG_SOURCE, song.sourceType.name)
            putString(EXTRA_SONG_URI, song.uri.toString())
            putLong(EXTRA_DURATION, song.durationMs)
            song.artworkUri?.let { putString(EXTRA_ARTWORK_URI, it.toString()) }
            song.albumId?.let { putLong(EXTRA_ALBUM_ID, it) }
            song.albumTitle?.let { putString(EXTRA_ALBUM_TITLE, it) }
            song.displayName?.let { putString(EXTRA_DISPLAY_NAME, it) }
            putPersistentTrack(item.persistentTrack)
            putProviderSong(item.runtimeProviderSong)
            putString(EXTRA_SOURCE_TYPE, item.source.type.name)
            putString(EXTRA_SOURCE_KEY, item.source.key)
            item.source.sourceId?.let { putString(EXTRA_SOURCE_ID, it) }
            putString(EXTRA_SOURCE_NAME, item.source.displayName)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.albumTitle)
            .setArtworkUri(song.artworkUri)
            .setExtras(extras)
            .apply { if (artworkData != null) setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            .build()
        return MediaItem.Builder()
            .setMediaId(item.queueId)
            .setMediaMetadata(metadata)
            .apply {
                if (!item.isOnline) {
                    setUri(song.uri)
                }
            }
            .build()
    }

    fun decode(mediaItem: MediaItem): PlaybackQueueItem? {
        val extras = mediaItem.mediaMetadata.extras ?: return null
        if (extras.getInt(EXTRA_VERSION, 0) != VERSION) return null
        val queueId = extras.getString(EXTRA_QUEUE_ID)?.takeIf(String::isNotBlank) ?: return null
        val stableIdentity = extras.getString(EXTRA_STABLE_ID)?.takeIf(String::isNotBlank) ?: return null
        val sourceIndex = extras.getInt(EXTRA_SOURCE_INDEX, 0).coerceAtLeast(0)
        val songUri = extras.getString(EXTRA_SONG_URI)?.let(Uri::parse) ?: Uri.EMPTY
        val sourceType = extras.getString(EXTRA_SONG_SOURCE)
            ?.let { runCatching { SourceType.valueOf(it) }.getOrNull() }
            ?: SourceType.Local
        val song = Song(
            id = extras.getLong(EXTRA_SONG_ID),
            sourceType = sourceType,
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            durationMs = extras.getLong(EXTRA_DURATION).coerceAtLeast(0L),
            uri = songUri,
            albumId = extras.getLong(EXTRA_ALBUM_ID).takeIf { extras.containsKey(EXTRA_ALBUM_ID) },
            albumTitle = extras.getString(EXTRA_ALBUM_TITLE),
            artworkUri = extras.getString(EXTRA_ARTWORK_URI)?.let(Uri::parse),
            displayName = extras.getString(EXTRA_DISPLAY_NAME)
        )
        val source = PlaybackSource(
            type = extras.getString(EXTRA_SOURCE_TYPE)
                ?.let { runCatching { PlaybackSourceType.valueOf(it) }.getOrNull() }
                ?: PlaybackSourceType.Unknown,
            key = extras.getString(EXTRA_SOURCE_KEY).orEmpty().ifBlank { PlaybackSource.Unknown.key },
            sourceId = extras.getString(EXTRA_SOURCE_ID)?.takeIf(String::isNotBlank),
            displayName = extras.getString(EXTRA_SOURCE_NAME).orEmpty()
                .ifBlank { PlaybackSource.Unknown.displayName }
        )
        return PlaybackQueueItem(
            queueId = queueId,
            stableIdentity = stableIdentity,
            presentation = song,
            persistentTrack = extras.readPersistentTrack(song),
            runtimeProviderSong = extras.readProviderSong(song),
            source = source,
            sourceIndex = sourceIndex
        )
    }

    fun isLogicalQueueItem(mediaItem: MediaItem): Boolean =
        mediaItem.mediaMetadata.extras?.getInt(EXTRA_VERSION, 0) == VERSION

    fun isLogicalQueue(mediaItems: List<MediaItem>): Boolean =
        mediaItems.isNotEmpty() && mediaItems.all(::isLogicalQueueItem)

    private fun Bundle.putPersistentTrack(track: PersistentTrack?) {
        when (track) {
            is PersistentTrack.Local -> {
                putString(EXTRA_TRACK_KIND, "local")
                putString(EXTRA_TRACK_LOCAL_ID, track.songId)
            }
            is PersistentTrack.Online -> {
                putString(EXTRA_TRACK_KIND, "online")
                putString(EXTRA_TRACK_HOST, track.sourceHost)
                putString(EXTRA_TRACK_PERSISTENT_ID, track.persistentId)
            }
            null -> putString(EXTRA_TRACK_KIND, "none")
        }
    }

    private fun Bundle.readPersistentTrack(song: Song): PersistentTrack? = when (getString(EXTRA_TRACK_KIND)) {
        "local" -> getString(EXTRA_TRACK_LOCAL_ID)?.let { id ->
            PersistentTrack.Local(id, song.title, song.artist, song.durationMs)
        }
        "online" -> {
            val host = getString(EXTRA_TRACK_HOST)
            val id = getString(EXTRA_TRACK_PERSISTENT_ID)
            if (host.isNullOrBlank() || id.isNullOrBlank()) null else PersistentTrack.Online(
                sourceHost = host,
                persistentId = id,
                cachedTitle = song.title,
                cachedArtist = song.artist,
                cachedDurationMs = song.durationMs
            )
        }
        else -> null
    }

    private fun Bundle.putProviderSong(song: ProviderSong?) {
        song ?: return
        putString(EXTRA_PROVIDER_EXTENSION, song.trackRef.extensionId)
        putString(EXTRA_PROVIDER_OPAQUE_ID, song.trackRef.opaqueId)
        song.persistentId?.let { putString(EXTRA_PROVIDER_PERSISTENT_ID, it) }
        song.sourceHost?.let { putString(EXTRA_PROVIDER_SOURCE_HOST, it) }
        song.artwork?.let {
            putString(EXTRA_ARTWORK_EXTENSION, it.extensionId)
            putString(EXTRA_ARTWORK_URL, it.url)
        }
        song.largeArtwork?.let {
            putString(EXTRA_LARGE_ARTWORK_EXTENSION, it.extensionId)
            putString(EXTRA_LARGE_ARTWORK_URL, it.url)
        }
    }

    private fun Bundle.readProviderSong(song: Song): ProviderSong? {
        val extensionId = getString(EXTRA_PROVIDER_EXTENSION)?.takeIf(String::isNotBlank) ?: return null
        val opaqueId = getString(EXTRA_PROVIDER_OPAQUE_ID)?.takeIf(String::isNotBlank) ?: return null
        return ProviderSong(
            trackRef = ExtensionTrackRef(extensionId, opaqueId),
            title = song.title,
            artist = song.artist,
            durationMs = song.durationMs.takeIf { it > 0L },
            artwork = readImage(EXTRA_ARTWORK_EXTENSION, EXTRA_ARTWORK_URL),
            largeArtwork = readImage(EXTRA_LARGE_ARTWORK_EXTENSION, EXTRA_LARGE_ARTWORK_URL),
            persistentId = getString(EXTRA_PROVIDER_PERSISTENT_ID),
            sourceHost = getString(EXTRA_PROVIDER_SOURCE_HOST)
        )
    }

    private fun Bundle.readImage(extensionKey: String, urlKey: String): ExtensionImage? {
        val extensionId = getString(extensionKey)?.takeIf(String::isNotBlank) ?: return null
        val url = getString(urlKey)?.takeIf(String::isNotBlank) ?: return null
        return ExtensionImage(extensionId, url)
    }
}
