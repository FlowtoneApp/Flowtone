package ink.tenqui.flowtone.ui.library

import ink.tenqui.flowtone.core.model.Song
import java.text.Collator
import java.util.Locale

internal enum class PlaylistSongSortCriterion(val label: String) {
    Alphabetical("按字母排序"),
    ChineseFirst("中文优先排序"),
    OtherCharactersFirst("其他字符优先排序"),
    Duration("歌曲时长排序")
}

internal enum class PlaylistSongSortOrder(val label: String) {
    Ascending("正序"),
    Descending("倒序"),
    DateAdded("添加时间"),
    FileDate("文件时间")
}

internal data class PlaylistSongSort(
    val criterion: PlaylistSongSortCriterion = PlaylistSongSortCriterion.Alphabetical,
    val order: PlaylistSongSortOrder = PlaylistSongSortOrder.Ascending
)

internal fun List<SelectablePlaylistSong>.sortedForPlaylist(
    sort: PlaylistSongSort
): List<SelectablePlaylistSong> {
    val titleCollator = Collator.getInstance(Locale.CHINA)
    val comparator = Comparator<SelectablePlaylistSong> { first, second ->
        when (sort.order) {
            PlaylistSongSortOrder.DateAdded ->
                second.song.dateAddedSeconds.compareTo(first.song.dateAddedSeconds)
            PlaylistSongSortOrder.FileDate ->
                second.song.dateModifiedSeconds.compareTo(first.song.dateModifiedSeconds)
            PlaylistSongSortOrder.Ascending,
            PlaylistSongSortOrder.Descending -> compareUsingCriterion(
                first = first,
                second = second,
                criterion = sort.criterion,
                titleCollator = titleCollator
            )
        }.takeIf { it != 0 } ?: first.selectionKey.compareTo(second.selectionKey)
    }
    return if (sort.order == PlaylistSongSortOrder.Descending) {
        sortedWith(comparator.reversed())
    } else {
        sortedWith(comparator)
    }
}

private fun compareUsingCriterion(
    first: SelectablePlaylistSong,
    second: SelectablePlaylistSong,
    criterion: PlaylistSongSortCriterion,
    titleCollator: Collator
): Int {
    return when (criterion) {
        PlaylistSongSortCriterion.Alphabetical ->
            titleCollator.compare(first.song.title, second.song.title)
        PlaylistSongSortCriterion.ChineseFirst -> comparePriorityGroup(
            first = first.song,
            second = second.song,
            firstHasPriority = { song ->
                song.title.firstOrNull()?.isChineseCharacter() == true
            },
            titleCollator = titleCollator
        )
        PlaylistSongSortCriterion.OtherCharactersFirst -> comparePriorityGroup(
            first = first.song,
            second = second.song,
            firstHasPriority = { song ->
                song.title.firstOrNull()?.isSymbolCharacter() == true
            },
            titleCollator = titleCollator
        )
        PlaylistSongSortCriterion.Duration ->
            first.song.durationMs.compareTo(second.song.durationMs)
    }
}

private fun comparePriorityGroup(
    first: Song,
    second: Song,
    firstHasPriority: (Song) -> Boolean,
    titleCollator: Collator
): Int {
    val firstIsPriority = firstHasPriority(first)
    val secondIsPriority = firstHasPriority(second)
    if (firstIsPriority != secondIsPriority) {
        return if (firstIsPriority) -1 else 1
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
