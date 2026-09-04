package ink.tenqui.flowtone.data.online.packageformat

import ink.tenqui.flowtone.app.ArtistDestinationIdentity
import ink.tenqui.flowtone.app.SecondaryDestination
import ink.tenqui.flowtone.app.SecondaryNavigationState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugProviderUiRegressionFixtureTest {
    @Test
    fun manifestDeclaresCurrentProviderCollectionContract() {
        val manifest = ExtensionManifestParser.parse(fixtureFile("manifest.json").readText())

        assertEquals("debug.provider.ui-regression-fixture", manifest.id)
        assertEquals("Flowtone UI Test", manifest.name)
        assertTrue(manifest.supportsMusicProvider)
        assertTrue(manifest.supportsArtistMetadata)
        assertEquals(
            setOf(
                ink.tenqui.flowtone.data.online.ProviderEntityCapability.Song,
                ink.tenqui.flowtone.data.online.ProviderEntityCapability.Album
            ),
            manifest.providerEntityCapabilities
        )
    }

    @Test
    fun fixtureSearchAndProfileCasesUseStableArtistIdentities() {
        val source = fixtureFile("main.js").readText()

        assertArtist(source, "banner-scroll", "Banner Scroll Test")
        assertArtist(source, "no-banner-cloud", "No Banner Cloud Test")
        assertArtist(
            source,
            "long-artist-title",
            "This Is An Extremely Long Artist Name Used To Verify Top Bar Ellipsis Behaviour"
        )
        assertArtist(source, "long-biography", "Long Biography Test")
        assertArtist(source, "exactly-two-line-bio", "Exactly Two Line Biography Test")
        assertArtist(source, "slow-loading-banner", "Slow Loading / 7 Skeleton Test")
        assertTrue(source.contains("async searchPage(request)"))
        assertTrue(source.contains("request.category !== 'user'"))
        assertTrue(source.contains("artist.id.includes(keyword)"))
        val searchResultPayload = source.between(
            "function artistSearchResult(artist)",
            "globalThis.flowtoneExtension"
        )
        assertTrue(searchResultPayload.contains("id: artist.id"))
        assertTrue(searchResultPayload.contains("title: artist.title"))
        assertTrue(searchResultPayload.contains("category: 'user'"))
        assertTrue(searchResultPayload.contains("artworkUrl: artist.artworkUrl"))
        assertFalse(searchResultPayload.contains("...artist,"))
        listOf(
            "banner-scroll",
            "no-banner-cloud",
            "long-artist-title",
            "long-biography",
            "exactly-two-line-bio",
            "slow-loading-banner"
        ).forEach { artistId ->
            assertTrue(source.contains("id: '$artistId'"))
        }
    }

    @Test
    fun fixtureKeepsBannerCloudCollectionAndLongTitleBoundariesExplicit() {
        val source = fixtureFile("main.js").readText()
        val bannerArtist = source.between("id: 'banner-scroll'", "id: 'no-banner-cloud'")
        val cloudArtist = source.between("id: 'no-banner-cloud'", "id: 'long-artist-title'")

        assertTrue(bannerArtist.contains("bannerUrl:"))
        assertFalse(cloudArtist.contains("bannerUrl:"))
        assertTrue(source.contains("'long-album-title'"))
        assertTrue(source.contains("An Extremely Long Album Title Created Specifically"))
        assertTrue(source.contains("addTracks('banner-scroll', 'banner-many-tracks', 15"))
        assertTrue(source.contains("addTracks('long-artist-title', 'long-title-record', 18"))
        val longArtist = source.between("id: 'long-artist-title'", "id: 'long-biography'")
        assertTrue(longArtist.contains("songCount: 18"))
        assertTrue(source.contains("artists: isMultiArtist"))
        assertTrue(source.contains("async getSongs()"))
        assertTrue(source.contains("async getAlbums()"))
    }

    @Test
    fun fixtureDelaysOnlyCollectionReads() {
        val source = fixtureFile("main.js").readText()

        assertTrue(source.contains("const COLLECTION_DELAY_MS = 3000"))
        assertTrue(source.contains("function waitForFirstCollectionRead()"))
        assertTrue(source.contains("waitForFirstCollectionRead();\n    return songs;"))
        assertTrue(source.contains("waitForFirstCollectionRead();\n    return albums;"))
        assertFalse(
            source.between("async searchPage(request)", "async getSongs()")
                .contains("waitForFirstCollectionRead()")
        )
    }

    @Test
    fun everyFixtureArtistPayloadCanCreateANavigableArtistEntry() {
        val cases = listOf(
            "banner-scroll" to "Banner Scroll Test",
            "no-banner-cloud" to "No Banner Cloud Test",
            "long-artist-title" to
                "This Is An Extremely Long Artist Name Used To Verify Top Bar Ellipsis Behaviour",
            "long-biography" to "Long Biography Test",
            "exactly-two-line-bio" to "Exactly Two Line Biography Test",
            "slow-loading-banner" to "Slow Loading / 7 Skeleton Test"
        )

        cases.forEach { (remoteId, title) ->
            val destination = SecondaryDestination.Artist(
                ArtistDestinationIdentity.Provider(
                    providerId = "debug.provider.ui-regression-fixture",
                    artistId = remoteId,
                    displayName = title,
                    avatar = null
                )
            )
            val entry = SecondaryNavigationState().push(destination).currentEntry

            assertEquals(remoteId, (destination.identity as ArtistDestinationIdentity.Provider).artistId)
            assertEquals(destination, entry?.destination)
        }
    }

    private fun assertArtist(source: String, id: String, title: String) {
        assertTrue(source.contains("id: '$id'"))
        assertTrue(source.contains("title: '$title'"))
    }

    private fun fixtureFile(name: String): File = listOf(
        File("src/debug/provider-artist-profile-fixture/$name"),
        File("app/src/debug/provider-artist-profile-fixture/$name")
    ).firstOrNull(File::isFile) ?: error("Debug provider UI fixture file is missing: $name")

    private fun String.between(start: String, end: String): String =
        substringAfter(start).substringBefore(end)
}
