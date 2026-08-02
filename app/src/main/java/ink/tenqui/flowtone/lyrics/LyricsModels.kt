package ink.tenqui.flowtone.lyrics

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data object DirectoryNotSelected : LyricsState
    data object DirectoryPermissionLost : LyricsState
    data object OutsideSelectedDirectory : LyricsState
    data object NotFound : LyricsState

    data class Available(
        val lines: List<LyricLine>
    ) : LyricsState

    data class Error(
        val message: String? = null
    ) : LyricsState
}
