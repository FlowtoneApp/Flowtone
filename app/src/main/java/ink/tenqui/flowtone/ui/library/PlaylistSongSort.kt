package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.core.model.Song
import java.text.Collator
import java.util.Locale

internal enum class PlaylistSongSortCriterion(val label: String) {
    Title("标题"),
    DateAdded("添加时间"),
    FileTime("文件时间"),
    Duration("歌曲时长")
}

internal enum class PlaylistSongSortDirection(val label: String) {
    Ascending("正序"),
    Descending("倒序")
}

internal enum class PlaylistSongTitleCharacterPriority(val label: String) {
    Default("默认"),
    ChineseFirst("中文优先"),
    OtherFirst("其他字符优先")
}

/**
 * 排序条件被明确拆成依据、方向和标题字符优先级三部分。
 * 标题以外的依据保留 [titleCharacterPriority]，便于切回标题时恢复用户的上次选择。
 */
internal data class PlaylistSongSort(
    val criterion: PlaylistSongSortCriterion = PlaylistSongSortCriterion.Title,
    val direction: PlaylistSongSortDirection = PlaylistSongSortDirection.Ascending,
    val titleCharacterPriority: PlaylistSongTitleCharacterPriority =
        PlaylistSongTitleCharacterPriority.Default
)

internal fun List<SelectablePlaylistSong>.sortedForPlaylist(
    sort: PlaylistSongSort
): List<SelectablePlaylistSong> {
    val titleCollator = Collator.getInstance(Locale.CHINA)
    val comparator = Comparator<SelectablePlaylistSong> { first, second ->
        compareUsingCriterion(
            first = first,
            second = second,
            sort = sort,
            titleCollator = titleCollator
        ).takeIf { it != 0 } ?: first.selectionKey.compareTo(second.selectionKey)
    }
    return when (sort.direction) {
        PlaylistSongSortDirection.Ascending -> sortedWith(comparator)
        PlaylistSongSortDirection.Descending -> sortedWith(comparator.reversed())
    }
}

private fun compareUsingCriterion(
    first: SelectablePlaylistSong,
    second: SelectablePlaylistSong,
    sort: PlaylistSongSort,
    titleCollator: Collator
): Int {
    return when (sort.criterion) {
        PlaylistSongSortCriterion.Title -> compareTitle(
            first = first.song,
            second = second.song,
            priority = sort.titleCharacterPriority,
            titleCollator = titleCollator
        )
        PlaylistSongSortCriterion.DateAdded ->
            (first.playlistAddedAtSeconds ?: first.song.dateAddedSeconds).compareTo(
                second.playlistAddedAtSeconds ?: second.song.dateAddedSeconds
            )
        PlaylistSongSortCriterion.FileTime ->
            first.song.dateModifiedSeconds.compareTo(second.song.dateModifiedSeconds)
        PlaylistSongSortCriterion.Duration ->
            first.song.durationMs.compareTo(second.song.durationMs)
    }
}

private fun compareTitle(
    first: Song,
    second: Song,
    priority: PlaylistSongTitleCharacterPriority,
    titleCollator: Collator
): Int {
    val firstHasPriority = when (priority) {
        PlaylistSongTitleCharacterPriority.Default -> false
        PlaylistSongTitleCharacterPriority.ChineseFirst ->
            first.title.firstOrNull()?.isChineseCharacter() == true
        PlaylistSongTitleCharacterPriority.OtherFirst ->
            first.title.firstOrNull()?.isSymbolCharacter() == true
    }
    val secondHasPriority = when (priority) {
        PlaylistSongTitleCharacterPriority.Default -> false
        PlaylistSongTitleCharacterPriority.ChineseFirst ->
            second.title.firstOrNull()?.isChineseCharacter() == true
        PlaylistSongTitleCharacterPriority.OtherFirst ->
            second.title.firstOrNull()?.isSymbolCharacter() == true
    }
    if (firstHasPriority != secondHasPriority) {
        return if (firstHasPriority) -1 else 1
    }
    return titleCollator.compare(first.title, second.title)
}

private fun Char.isChineseCharacter(): Boolean {
    return Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN
}

private fun Char.isSymbolCharacter(): Boolean {
    return when (Character.getType(this)) {
        Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(),
        Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        Character.OTHER_SYMBOL.toInt() -> true
        else -> false
    }
}
