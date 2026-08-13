package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ArtistAvatar
import ink.tenqui.flowtone.core.online.ArtistAvatarExtension
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger

/** Flowtone 核心持有的歌手头像扩展注册表。没有安装扩展时会正常返回 null。 */
class ArtistAvatarExtensionRegistry(
    private val logger: ExtensionCoreLogger = ExtensionCoreLogger { event, details ->
        android.util.Log.d("FlowtoneExtension", "$event $details")
    }
) {
    private val extensions = mutableListOf<ArtistAvatarExtension>()

    @Synchronized
    fun install(extension: ArtistAvatarExtension) {
        extensions.removeAll { it.id == extension.id }
        extensions += extension
    }

    @Synchronized
    fun uninstall(extensionId: String) {
        extensions.removeAll { it.id == extensionId }
    }

    @Synchronized
    fun installedExtensions(): List<ArtistAvatarExtension> = extensions.toList()

    suspend fun findArtistAvatar(songTitle: String, artistName: String): ArtistAvatar? {
        val title = songTitle.trim()
        val artist = artistName.trim()
        if (title.isEmpty() || artist.isEmpty()) return null
        logger.log("artist_avatar.lookup.started", "title=\"$title\" artist=\"$artist\"")
        installedExtensions().forEach { extension ->
            val startedAt = System.nanoTime()
            logger.log("extension.invoke.started", "extension=${extension.id} capability=artist_avatar")
            try {
                val avatar = extension.findArtistAvatar(title, artist)
                logger.log(
                    "extension.invoke.completed",
                    "extension=${extension.id} capability=artist_avatar result=${if (avatar == null) "not_found" else "found"} " +
                        "durationMs=${(System.nanoTime() - startedAt) / 1_000_000}"
                )
                if (avatar != null) {
                    logger.log("artist_avatar.lookup.completed", "result=found provider=${extension.id}")
                    return avatar
                }
            } catch (error: Exception) {
                logger.log(
                    "extension.invoke.failed",
                    "extension=${extension.id} capability=artist_avatar exception=${error.javaClass.simpleName}"
                )
            }
        }
        logger.log("artist_avatar.lookup.completed", "result=not_found")
        return null
    }
}
