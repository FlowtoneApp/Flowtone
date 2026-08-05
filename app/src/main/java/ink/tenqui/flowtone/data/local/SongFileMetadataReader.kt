package ink.tenqui.flowtone.data.local

import android.content.ContentResolver
import android.net.Uri
import com.kyant.taglib.TagLib

internal data class SongFileMetadata(
    val title: String? = null,
    val artist: String? = null
)

internal fun interface SongFileMetadataReader {
    fun read(uri: Uri): SongFileMetadata?
}

internal class AndroidSongFileMetadataReader(
    private val contentResolver: ContentResolver
) : SongFileMetadataReader {
    override fun read(uri: Uri): SongFileMetadata? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val metadata = TagLib.getMetadata(
                    fd = descriptor.dup().detachFd(),
                    readPictures = false
                ) ?: return@use null
                songFileMetadataFrom(metadata.propertyMap)
            }
        } catch (_: Exception) {
            // 损坏或不受支持的文件交由 MediaStore 元信息兜底。
            null
        } catch (_: LinkageError) {
            null
        }
    }
}

internal fun songFileMetadataFrom(properties: Map<String, Array<String>>): SongFileMetadata {
    return SongFileMetadata(
        title = properties.firstValue("TITLE"),
        artist = properties.values("ARTIST").joinToString("/").ifBlank { null }
    )
}

private fun Map<String, Array<String>>.firstValue(key: String): String? {
    return values(key).firstOrNull()
}

private fun Map<String, Array<String>>.values(key: String): List<String> {
    return entries
        .firstOrNull { (candidate, _) -> candidate.equals(key, ignoreCase = true) }
        ?.value
        .orEmpty()
        .map(String::trim)
        .filter(String::isNotEmpty)
}
