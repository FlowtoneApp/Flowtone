package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.data.local.localArtistStableId

internal enum class ArtistRootNavigationMode {
    MiniPlayer,
    NormalPage
}

internal enum class ArtistRootReturnTarget {
    MiniPlayerFullscreen,
    PreviousPage
}

internal enum class MiniPlayerArtistOpenDecision {
    OpenArtistPage,
    CollapseMiniPlayer,
    Ignore
}

internal fun artistRootReturnTarget(
    navigationMode: ArtistRootNavigationMode?
): ArtistRootReturnTarget {
    return when (navigationMode) {
        ArtistRootNavigationMode.MiniPlayer -> ArtistRootReturnTarget.MiniPlayerFullscreen
        ArtistRootNavigationMode.NormalPage,
        null -> ArtistRootReturnTarget.PreviousPage
    }
}

internal fun miniPlayerArtistOpenDecision(
    currentArtistName: String?,
    targetArtistName: String,
    searchReturnStage: SearchReturnStage,
    artistRootReturnInProgress: Boolean
): MiniPlayerArtistOpenDecision {
    val targetStableId = targetArtistName
        .trim()
        .takeIf { artistName -> artistName.isNotEmpty() }
        ?.let(::localArtistStableId)
        ?: return MiniPlayerArtistOpenDecision.Ignore
    val currentStableId = currentArtistName
        ?.trim()
        ?.takeIf { artistName -> artistName.isNotEmpty() }
        ?.let(::localArtistStableId)
        ?: return MiniPlayerArtistOpenDecision.OpenArtistPage

    if (currentStableId != targetStableId) {
        return MiniPlayerArtistOpenDecision.OpenArtistPage
    }

    return if (
        artistRootReturnInProgress ||
        searchReturnStage == SearchReturnStage.ArtistExitingToSearch
    ) {
        MiniPlayerArtistOpenDecision.Ignore
    } else {
        MiniPlayerArtistOpenDecision.CollapseMiniPlayer
    }
}
