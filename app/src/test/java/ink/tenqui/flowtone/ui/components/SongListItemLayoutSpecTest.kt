package ink.tenqui.flowtone.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SongListItemLayoutSpecTest {
    @Test fun standardArtistAndAlbumRowsShareOneGeometryContract() {
        val artistSpec = songListItemLayoutSpec(compact = false)
        val albumSpec = songListItemLayoutSpec(compact = false)

        assertEquals(artistSpec, albumSpec)
        assertEquals(72.dp, artistSpec.rowMinHeight)
        assertEquals(72.dp, artistSpec.outerMinHeight)
        assertEquals(56.dp, artistSpec.artworkSize)
        assertEquals(8.dp, artistSpec.verticalPadding)
        assertEquals(12.dp, artistSpec.horizontalPadding)
        assertEquals(2.dp, artistSpec.titleToArtistSpacing)
        assertEquals(12.dp, artistSpec.artworkToTextSpacing)
        assertEquals(12.dp, artistSpec.textToTrailingSpacing)
        assertEquals(96.dp, artistSpec.trailingWidth)
        assertEquals(4.dp, StandardSongListItemSpacing)
    }

    @Test fun pagePresentationCannotChangeRowOrArtworkGeometry() {
        val samples = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val specs = samples.map { songListItemLayoutSpec(compact = false) }

        assertEquals(1, specs.distinct().size)
        specs.forEach { spec ->
            assertEquals(72.dp, spec.rowMinHeight)
            assertEquals(56.dp, spec.artworkSize)
        }
    }

    @Test fun compactGeometryRemainsAnExplicitSongListItemVariant() {
        val compact = songListItemLayoutSpec(compact = true)

        assertEquals(64.dp, compact.rowMinHeight)
        assertEquals(48.dp, compact.artworkSize)
        assertEquals(6.dp, compact.verticalPadding)
        assertEquals(0.dp, compact.titleToArtistSpacing)
    }
}
