package ink.tenqui.flowtone.data.online.runtime

import ink.tenqui.flowtone.core.online.ArtistAvatar
import java.util.Locale

/** 宿主拥有的进程内实验缓存。扩展脚本没有存储 API。 */
class ExtensionResultCache {
    private val values = mutableMapOf<String, ArtistAvatar>()

    @Synchronized
    fun get(extensionId: String, songTitle: String, artistName: String): ArtistAvatar? =
        values[key(extensionId, songTitle, artistName)]

    @Synchronized
    fun put(extensionId: String, songTitle: String, artistName: String, avatar: ArtistAvatar) {
        values[key(extensionId, songTitle, artistName)] = avatar
    }

    @Synchronized
    fun clear(extensionId: String) {
        values.keys.removeAll { it.startsWith("$extensionId\n") }
    }

    private fun key(extensionId: String, title: String, artist: String): String =
        "$extensionId\n${title.trim().lowercase(Locale.ROOT)}\n${artist.trim().lowercase(Locale.ROOT)}"
}
