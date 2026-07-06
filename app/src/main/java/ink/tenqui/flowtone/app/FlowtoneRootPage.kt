package ink.tenqui.flowtone.app

internal sealed interface FlowtoneRootPage {
    data object MainTabs : FlowtoneRootPage
    data class ArtistRootPage(val artistName: String) : FlowtoneRootPage
}
