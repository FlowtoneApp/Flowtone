package ink.tenqui.flowtone.app

import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.data.online.ProviderAlbum
import ink.tenqui.flowtone.data.online.ProviderEntityIdentity
import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.ui.components.AlbumDetailCloudPlacement
import ink.tenqui.flowtone.ui.components.HomeBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.LibraryBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.MineBackgroundCloudPlacement
import ink.tenqui.flowtone.ui.components.albumDetailCloudPalette
import ink.tenqui.flowtone.ui.components.resolvedAlbumArtworkCloudColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AlbumCloudArtworkSourceTest {
    @Test
    fun localAlbumUsesTheResolvedLocalArtwork() {
        val localArtwork = Any()
        val destination = SecondaryDestination.Album(albumId = 7L, title = "Local")

        assertSame(localArtwork, albumCloudArtworkData(destination, localArtwork))
    }

    @Test
    fun providerAlbumUsesItsExtensionArtworkFromEveryArtistPath() {
        val artwork = ExtensionImage("provider", "https://example.test/album.jpg")
        val album = ProviderAlbum(
            identity = ProviderEntityIdentity("provider", "album"),
            title = "Album",
            artwork = artwork
        )
        val destination = SecondaryDestination.Album(album)

        assertEquals(artwork, albumCloudArtworkData(destination, localArtwork = null))
        assertEquals(artwork, albumCloudArtworkData(destination, localArtwork = Any()))
    }

    @Test
    fun missingArtworkKeepsTheCanonicalPaletteFallbackPath() {
        val album = ProviderAlbum(
            identity = ProviderEntityIdentity("provider", "album"),
            title = "Album"
        )

        assertNull(
            albumCloudArtworkData(
                destination = SecondaryDestination.Album(album),
                localArtwork = null
            )
        )
        assertNull(albumCloudArtworkData(destination = null, localArtwork = null))
    }

    @Test
    fun albumCloudUsesOneAccentForEveryRendererChannel() {
        val accent = Color(0xFF345678)
        val palette = albumDetailCloudPalette(accent)

        assertEquals(accent, palette.primary)
        assertEquals(accent, palette.secondary)
        assertEquals(accent, palette.tertiary)
    }

    @Test
    fun missingOrFailedArtworkUsesTheCanonicalFallbackAccent() {
        val fallback = Color(0xFF604675)

        assertEquals(fallback, resolvedAlbumArtworkCloudColor(null, fallback))
    }

    @Test
    fun everyAlbumEntryUsesTheSameTopCenterPlacement() {
        listOf(
            HomeBackgroundCloudPlacement,
            LibraryBackgroundCloudPlacement,
            MineBackgroundCloudPlacement
        ).forEach { underlyingPlacement ->
            assertEquals(
                AlbumDetailCloudPlacement,
                sharedCloudPlacementForSecondaryPage(
                    secondaryPage = SecondaryPage.Album,
                    topLevelPlacement = underlyingPlacement
                )
            )
        }
        assertEquals(0.5f, AlbumDetailCloudPlacement.cloudCenterWidthFraction, 0f)
        assertEquals(0f, AlbumDetailCloudPlacement.cloudCenterRadiusOffsetXFactor, 0f)
        assertEquals(-0.12f, AlbumDetailCloudPlacement.cloudCenterRadiusOffsetYFactor, 0f)
    }

    @Test
    fun nonAlbumPagesKeepTheirOwnPlacement() {
        assertEquals(
            HomeBackgroundCloudPlacement,
            sharedCloudPlacementForSecondaryPage(
                secondaryPage = SecondaryPage.Artist,
                topLevelPlacement = HomeBackgroundCloudPlacement
            )
        )
    }

    @Test
    fun albumTargetDependsOnTheAlbumInsteadOfThePreviousRoute() {
        val album = Color(0xFF123456)
        val artist = Color(0xFF654321)
        val library = Color(0xFFABCDEF)

        assertEquals(album, albumDetailCloudColorTarget(true, album, artist))
        assertEquals(album, albumDetailCloudColorTarget(true, album, library))
        assertEquals(artist, albumDetailCloudColorTarget(false, album, artist))
    }

    @Test
    fun artistPathColorIsTheSharedTransitionBaseInBothDirections() {
        val main = Color(0xFF112233)
        val artist = Color(0xFF445566)

        assertEquals(artist, resolveSharedCloudBaseAccent(main, artist))
        assertEquals(main, resolveSharedCloudBaseAccent(main, artistPathAccent = null))
    }
}
