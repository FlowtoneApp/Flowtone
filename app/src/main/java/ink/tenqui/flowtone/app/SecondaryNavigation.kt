package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.data.local.localArtistStableId
import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ProviderAlbum

/** Artist destination identity remains source-scoped; local and Provider names are not merged. */
internal sealed interface ArtistDestinationIdentity {
    val displayName: String
    val stableId: String
    val avatar: ExtensionImage?
    val hasLocalContent: Boolean
    val profileMetadata: ArtistMetadata?
        get() = null

    data class Local(
        val name: String
    ) : ArtistDestinationIdentity {
        override val displayName: String
            get() = name.trim()
        override val stableId: String
            get() = "local:${localArtistStableId(displayName)}"
        override val avatar: ExtensionImage? = null
        override val hasLocalContent: Boolean = true
    }

    data class Provider(
        val providerId: String,
        val artistId: String,
        override val displayName: String,
        override val avatar: ExtensionImage?,
        override val profileMetadata: ArtistMetadata? = null
    ) : ArtistDestinationIdentity {
        override val stableId: String
            get() = "provider:${providerId.trim()}\u0000${artistId.trim()}"
        override val hasLocalContent: Boolean = false
    }
}

internal sealed interface AlbumDestinationIdentity {
    val stableId: String

    data class Local(val albumId: Long) : AlbumDestinationIdentity {
        override val stableId: String = "local:$albumId"
    }

    data class Provider(
        val providerId: String,
        val albumId: String
    ) : AlbumDestinationIdentity {
        override val stableId: String
            get() = "provider:${providerId.trim()}\u0000${albumId.trim()}"
    }
}

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

    class Album(
        val identity: AlbumDestinationIdentity,
        val title: String,
        val providerAlbum: ProviderAlbum? = null
    ) : SecondaryDestination {
        override val page: SecondaryPage = SecondaryPage.Album
        val stableId: String get() = identity.stableId

        constructor(albumId: Long, title: String) : this(
            identity = AlbumDestinationIdentity.Local(albumId),
            title = title
        )

        constructor(album: ProviderAlbum) : this(
            identity = AlbumDestinationIdentity.Provider(album.providerId, album.id),
            title = album.title,
            providerAlbum = album
        )

        override fun equals(other: Any?): Boolean = other is Album && stableId == other.stableId

        override fun hashCode(): Int = stableId.hashCode()

        override fun toString(): String = "Album(identity=$identity, title=$title)"
    }

    class Artist(
        val identity: ArtistDestinationIdentity
    ) : SecondaryDestination {
        override val page: SecondaryPage = SecondaryPage.Artist
        val name: String
            get() = identity.displayName
        val stableId: String
            get() = identity.stableId

        constructor(name: String) : this(ArtistDestinationIdentity.Local(name))

        override fun equals(other: Any?): Boolean {
            return other is Artist && stableId == other.stableId
        }

        override fun hashCode(): Int = stableId.hashCode()

        override fun toString(): String = "Artist(identity=$identity)"
    }
}

internal data class SecondaryStackEntry(
    val id: Long,
    val destination: SecondaryDestination
)

internal data class SecondaryNavigationState(
    val entries: List<SecondaryStackEntry> = emptyList(),
    private val nextEntryId: Long = 0L
) {
    val currentEntry: SecondaryStackEntry?
        get() = entries.lastOrNull()

    val current: SecondaryDestination?
        get() = currentEntry?.destination

    val previous: SecondaryDestination?
        get() = entries.getOrNull(entries.lastIndex - 1)?.destination

    fun push(destination: SecondaryDestination): SecondaryNavigationState {
        if (current == destination) return this
        return copy(
            entries = entries + SecondaryStackEntry(nextEntryId, destination),
            nextEntryId = nextEntryId + 1L
        )
    }

    fun replaceWith(destination: SecondaryDestination): SecondaryNavigationState {
        if (entries.size == 1 && current == destination) return this
        return copy(
            entries = listOf(SecondaryStackEntry(nextEntryId, destination)),
            nextEntryId = nextEntryId + 1L
        )
    }

    fun pop(): SecondaryNavigationState {
        return if (entries.isEmpty()) this else copy(entries = entries.dropLast(1))
    }
}

/** Keeps page-local saveable UI state out of navigation payload and history. */
internal fun SecondaryStackEntry.uiStateKey(): String {
    return "secondary-entry:$id:${destination.page.name}"
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
