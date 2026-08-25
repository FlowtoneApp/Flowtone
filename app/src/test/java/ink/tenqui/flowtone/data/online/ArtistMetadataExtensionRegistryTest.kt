package ink.tenqui.flowtone.data.online

import ink.tenqui.flowtone.core.online.ArtistMetadata
import ink.tenqui.flowtone.core.online.ArtistMetadataExtension
import ink.tenqui.flowtone.data.online.network.ExtensionCoreLogger
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistMetadataExtensionRegistryTest {
    @Test
    fun blankDuplicateAndCanonicalAliasesAreFilteredInStableOrder() = runBlocking {
        val registry = testRegistry().apply {
            install(extension("first") {
                ArtistMetadata(aliases = listOf(" ", "Alias B", "Artist", "Alias A", "alias b"))
            })
            install(extension("second") {
                ArtistMetadata(aliases = listOf("Alias C", "Alias A"))
            })
        }

        val metadata = registry.findArtistMetadata(" Artist ")

        assertEquals(listOf("Alias B", "Alias A", "Alias C"), metadata?.aliases)
    }

    @Test
    fun partialProviderResultsMergeAliasesAndFirstBiography() = runBlocking {
        val registry = testRegistry().apply {
            install(extension("aliases") { ArtistMetadata(aliases = listOf("Alias")) })
            install(extension("biography") { ArtistMetadata(biography = "Biography") })
            install(extension("laterBiography") { ArtistMetadata(biography = "Ignored biography") })
        }

        val metadata = registry.findArtistMetadata("Artist")

        assertEquals(listOf("Alias"), metadata?.aliases)
        assertEquals("Biography", metadata?.biography)
    }

    @Test
    fun blankBiographyBecomesNullAndEmptyResultStaysEmpty() = runBlocking {
        val metadata = testRegistry().apply {
            install(extension("empty") { ArtistMetadata(aliases = listOf(" "), biography = " ") })
        }.findArtistMetadata("Artist")

        assertNull(metadata)
    }

    @Test
    fun providerExceptionDoesNotPreventLaterProvider() = runBlocking {
        val registry = testRegistry().apply {
            install(extension("failing") { error("unavailable") })
            install(extension("working") { ArtistMetadata(biography = "Biography") })
        }

        assertEquals("Biography", registry.findArtistMetadata("Artist")?.biography)
    }

    @Test
    fun identicalConcurrentLookupsShareOneProviderInvocation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val extension = extension("concurrent") {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            ArtistMetadata(biography = "Biography")
        }
        val registry = testRegistry().apply { install(extension) }

        val requests = List(3) { async { registry.findArtistMetadata(" Artist ") } }
        started.await()
        assertEquals(1, calls.get())
        release.complete(Unit)

        assertTrue(requests.awaitAll().all { it?.biography == "Biography" })
        assertEquals(1, calls.get())
    }

    @Test
    fun persistentHitSurvivesRegistryRecreation() = runBlocking {
        val root = Files.createTempDirectory("artist-metadata-results").toFile()
        val calls = AtomicInteger()
        val extension = extension("persistent") {
            calls.incrementAndGet()
            ArtistMetadata(listOf("Alias"), "Biography")
        }

        persistentRegistry(root).apply { install(extension) }.findArtistMetadata("Artist")
        val restored = persistentRegistry(root).apply { install(extension) }
            .findArtistMetadata(" artist ")

        assertEquals("Biography", restored?.biography)
        assertEquals(listOf("Alias"), restored?.aliases)
        assertEquals(1, calls.get())
    }

    private fun extension(
        id: String,
        lookup: suspend () -> ArtistMetadata?
    ): ArtistMetadataExtension = object : ArtistMetadataExtension {
        override val id: String = "test.$id"
        override val displayName: String = "Test"
        override suspend fun findArtistMetadata(artistName: String): ArtistMetadata? = lookup()
    }

    private fun testRegistry() = ArtistMetadataExtensionRegistry(
        logger = ExtensionCoreLogger { _, _ -> }
    )

    private fun persistentRegistry(root: java.io.File) = ArtistMetadataExtensionRegistry(
        logger = ExtensionCoreLogger { _, _ -> },
        persistentCache = ArtistMetadataPersistentCache(root)
    )
}
