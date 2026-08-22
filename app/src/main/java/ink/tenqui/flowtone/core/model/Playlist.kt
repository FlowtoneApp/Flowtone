package ink.tenqui.flowtone.core.model

import kotlin.random.Random

enum class PlaylistCardStyle {
    SQUARE,
    WIDE,
    LARGE
}

enum class PlaylistAppearanceColorKey {
    ROSE,
    PURPLE,
    INDIGO,
    BLUE,
    TEAL,
    GREEN,
    AMBER,
    ORANGE;

    companion object {
        fun fromStorageValue(value: String?): PlaylistAppearanceColorKey? {
            val normalizedValue = value?.trim().orEmpty()
            return entries.firstOrNull { key ->
                key.name.equals(normalizedValue, ignoreCase = true)
            }
        }
    }
}

// 旧歌单的兼容映射依赖该顺序和数量；新增颜色时不要修改这组既有槽位。
val StablePlaylistAppearanceColorKeys: List<PlaylistAppearanceColorKey> = listOf(
    PlaylistAppearanceColorKey.ROSE,
    PlaylistAppearanceColorKey.PURPLE,
    PlaylistAppearanceColorKey.INDIGO,
    PlaylistAppearanceColorKey.BLUE,
    PlaylistAppearanceColorKey.TEAL,
    PlaylistAppearanceColorKey.GREEN,
    PlaylistAppearanceColorKey.AMBER,
    PlaylistAppearanceColorKey.ORANGE
)

fun playlistAppearanceColorKeyForStableId(id: String): PlaylistAppearanceColorKey {
    val index = Math.floorMod(id.hashCode(), StablePlaylistAppearanceColorKeys.size)
    return StablePlaylistAppearanceColorKeys[index]
}

fun randomPlaylistAppearanceColorKey(
    avoiding: PlaylistAppearanceColorKey? = null,
    random: Random = Random.Default
): PlaylistAppearanceColorKey {
    val candidates = StablePlaylistAppearanceColorKeys.filterNot { key -> key == avoiding }
    return candidates[random.nextInt(candidates.size)]
}

data class Playlist(
    val id: String,
    val title: String,
    val subtitle: String = "0 首歌曲",
    val cardStyle: PlaylistCardStyle = PlaylistCardStyle.SQUARE,
    val appearanceColorKey: PlaylistAppearanceColorKey =
        playlistAppearanceColorKeyForStableId(id),
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 用户或 Provider 明确指定的歌单封面。为空时，界面使用歌单第一首歌曲的封面。
     */
    val customArtworkUri: String? = null,
    val creatorName: String? = null,
    val description: String? = null
)

const val LocalPlaylistCreatorName = "我"

data class PlaylistSongEntry(
    val id: String,
    val playlistId: String,
    val track: PersistentTrack,
    val addedAt: Long
)
