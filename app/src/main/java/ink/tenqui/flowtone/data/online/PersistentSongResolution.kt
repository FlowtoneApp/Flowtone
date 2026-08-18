package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.model.PersistentTrack

/**
 * 歌单条目的恢复结果。Unavailable 保留原条目，供后续以标题和歌手做模糊恢复；本阶段不执行
 * 模糊匹配，也不删除条目。
 */
sealed interface PersistentSongResolution {
    data class Resolved(val song: ProviderSong) : PersistentSongResolution
    data class ProviderMissing(val track: PersistentTrack.Online) : PersistentSongResolution
    data class Unresolved(val track: PersistentTrack.Online) : PersistentSongResolution
}
