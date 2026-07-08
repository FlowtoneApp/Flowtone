package ink.tenqui.flowtone.app

internal enum class SearchReturnStage {
    Idle,
    SearchExitingForArtist,
    ArtistVisible,
    ArtistExitingToSearch,
    SearchPreparing,
    SearchReentering
}

internal data class SearchListPosition(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
)

internal fun searchReturnStageForArtistOpen(
    searchActive: Boolean,
    navigationMode: ArtistRootNavigationMode
): SearchReturnStage {
    return if (searchActive && navigationMode == ArtistRootNavigationMode.NormalPage) {
        SearchReturnStage.SearchExitingForArtist
    } else {
        SearchReturnStage.Idle
    }
}

internal fun shouldRestoreSearchAfterArtistClose(
    searchActive: Boolean,
    navigationMode: ArtistRootNavigationMode?,
    currentStage: SearchReturnStage
): Boolean {
    return searchActive &&
        navigationMode == ArtistRootNavigationMode.NormalPage &&
        currentStage == SearchReturnStage.ArtistVisible
}

internal fun searchReturnStageAfterArtistExit(
    currentStage: SearchReturnStage
): SearchReturnStage {
    return if (currentStage == SearchReturnStage.ArtistExitingToSearch) {
        SearchReturnStage.SearchPreparing
    } else {
        currentStage
    }
}

internal fun searchReturnStageAfterPositionRestored(
    currentStage: SearchReturnStage
): SearchReturnStage {
    return if (currentStage == SearchReturnStage.SearchPreparing) {
        SearchReturnStage.SearchReentering
    } else {
        currentStage
    }
}

internal fun isSearchReturnAnimationStage(stage: SearchReturnStage): Boolean {
    return stage == SearchReturnStage.SearchExitingForArtist ||
        stage == SearchReturnStage.ArtistExitingToSearch ||
        stage == SearchReturnStage.SearchPreparing ||
        stage == SearchReturnStage.SearchReentering
}

internal fun isSearchForegroundHiddenStage(stage: SearchReturnStage): Boolean {
    return stage == SearchReturnStage.ArtistVisible ||
        stage == SearchReturnStage.ArtistExitingToSearch ||
        stage == SearchReturnStage.SearchPreparing
}
