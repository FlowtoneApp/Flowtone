package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.data.local.localArtistStableId

/**
 * 页面级 Secondary 导航的稳定 payload。页面离场时仍由该值提供 identity，
 * 不依赖会在当前导航改变后更新的选择状态。
 */
internal sealed interface SecondaryDestination {
    val page: SecondaryPage

    data class Standard(
        override val page: SecondaryPage
    ) : SecondaryDestination

    data class Playlist(
        val playlistId: String?,
        val title: String
    ) : SecondaryDestination {
        override val page: SecondaryPage = SecondaryPage.Playlist
    }

    data class Album(
        val albumId: Long,
        val title: String
    ) : SecondaryDestination {
        override val page: SecondaryPage = SecondaryPage.Album
    }

    class Artist(
        val name: String
    ) : SecondaryDestination {
        override val page: SecondaryPage = SecondaryPage.Artist
        val stableId: String = localArtistStableId(name)

        override fun equals(other: Any?): Boolean {
            return other is Artist && stableId == other.stableId
        }

        override fun hashCode(): Int = stableId.hashCode()

        override fun toString(): String = "Artist(name=$name)"
    }
}

internal data class SecondaryNavigationState(
    val entries: List<SecondaryDestination> = emptyList()
) {
    val current: SecondaryDestination?
        get() = entries.lastOrNull()

    val previous: SecondaryDestination?
        get() = entries.getOrNull(entries.lastIndex - 1)

    fun push(destination: SecondaryDestination): SecondaryNavigationState {
        return if (current == destination) this else copy(entries = entries + destination)
    }

    fun pop(): SecondaryNavigationState {
        return if (entries.isEmpty()) this else copy(entries = entries.dropLast(1))
    }
}

/** Keeps page-local saveable UI state out of navigation payload and history. */
internal fun SecondaryDestination.uiStateKey(): String {
    return when (this) {
        is SecondaryDestination.Standard -> "standard:${page.name}"
        is SecondaryDestination.Playlist -> "playlist:$playlistId"
        is SecondaryDestination.Album -> "album:$albumId"
        is SecondaryDestination.Artist -> "artist:$stableId"
    }
}

internal fun secondaryDestinationBreadcrumbs(
    current: SecondaryDestination?,
    previous: SecondaryDestination?,
    nestedSegments: List<String>
): List<String> {
    return when (current) {
        is SecondaryDestination.Album -> listOfNotNull(
            (previous as? SecondaryDestination.Artist)?.name,
            current.title
        )
        is SecondaryDestination.Playlist -> listOf(current.title)
        is SecondaryDestination.Standard -> nestedSegments
        is SecondaryDestination.Artist,
        null -> emptyList()
    }
}
