package ink.tenqui.flowtone.playback

import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.model.PersistentTrack
import ink.tenqui.flowtone.core.online.ExtensionImage

data class PlaybackState(
    val currentSong: Song? = null,
    /** 用于 Likes/歌单等 Flowtone collection 的长期身份，Song 仅保留展示兼容职责。 */
    val currentTrack: PersistentTrack? = null,
    /** 在线歌曲的封面必须保留为受 Host 约束的图片模型，不能退化为裸 URL。 */
    val extensionArtwork: ExtensionImage? = null,
    val extensionLargeArtwork: ExtensionImage? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackOrderMode: PlaybackOrderMode = PlaybackOrderMode.Sequence,
    val errorMessage: String? = null
)

data class PlaybackPositionSnapshot(
    val mediaId: String? = null,
    val positionMs: Long = 0L
)
