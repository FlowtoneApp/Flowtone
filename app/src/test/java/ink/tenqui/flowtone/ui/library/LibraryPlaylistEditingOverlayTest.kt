package ink.tenqui.flowtone.ui.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryPlaylistEditingOverlayTest {
    @Test
    fun longPressHitTestSelectsAnotherVisiblePlaylist() {
        val bounds = linkedMapOf(
            "selected" to Rect(20f, 100f, 180f, 240f),
            "other" to Rect(200f, 100f, 360f, 240f)
        )

        assertEquals(
            "other",
            playlistIdAtPosition(
                boundsByPlaylistId = bounds,
                excludedPlaylistId = "selected",
                position = Offset(240f, 160f)
            )
        )
        assertEquals(
            null,
            playlistIdAtPosition(
                boundsByPlaylistId = bounds,
                excludedPlaylistId = "selected",
                position = Offset(100f, 160f)
            )
        )
    }

    @Test
    fun actionsAreRightAlignedAboveTheCard() {
        val placement = calculatePlaylistEditActionsPlacement(
            cardBounds = Rect(20f, 180f, 180f, 320f),
            safeBounds = Rect(12f, 12f, 388f, 700f),
            buttonSizePx = 50f,
            buttonGapPx = 10f,
            cardGapPx = 12f
        )

        assertEquals(12f, placement.left, 0.001f)
        assertEquals(118f, placement.top, 0.001f)
    }

    @Test
    fun actionsStayInsideHorizontalSafeBounds() {
        val placement = calculatePlaylistEditActionsPlacement(
            cardBounds = Rect(300f, 180f, 380f, 320f),
            safeBounds = Rect(12f, 12f, 388f, 700f),
            buttonSizePx = 50f,
            buttonGapPx = 10f,
            cardGapPx = 12f
        )

        assertEquals(210f, placement.left, 0.001f)
    }

    @Test
    fun actionsStayInsideVerticalSafeBounds() {
        val topPlacement = calculatePlaylistEditActionsPlacement(
            cardBounds = Rect(20f, 0f, 180f, 80f),
            safeBounds = Rect(12f, 24f, 388f, 600f),
            buttonSizePx = 50f,
            buttonGapPx = 10f,
            cardGapPx = 12f
        )
        val bottomPlacement = calculatePlaylistEditActionsPlacement(
            cardBounds = Rect(20f, 520f, 180f, 600f),
            safeBounds = Rect(12f, 24f, 388f, 600f),
            buttonSizePx = 50f,
            buttonGapPx = 10f,
            cardGapPx = 12f
        )

        assertEquals(92f, topPlacement.top, 0.001f)
        assertEquals(458f, bottomPlacement.top, 0.001f)
    }

    @Test
    fun appearancePickerIsPlacedAboveTheActionButtonsByDefault() {
        val placement = calculatePlaylistAppearancePickerPlacement(
            cardBounds = Rect(20f, 180f, 180f, 320f),
            actionsPlacement = PlaylistEditActionsPlacement(left = 15f, top = 118f),
            safeBounds = Rect(12f, 12f, 388f, 700f),
            pickerWidthPx = 300f,
            pickerHeightPx = 52f,
            gapPx = 8f
        )

        assertEquals(12f, placement.left, 0.001f)
        assertEquals(58f, placement.top, 0.001f)
    }

    @Test
    fun appearancePickerMovesBelowTheCardWhenTopSpaceIsInsufficient() {
        val placement = calculatePlaylistAppearancePickerPlacement(
            cardBounds = Rect(20f, 24f, 180f, 124f),
            actionsPlacement = PlaylistEditActionsPlacement(left = 15f, top = 24f),
            safeBounds = Rect(12f, 24f, 388f, 700f),
            pickerWidthPx = 300f,
            pickerHeightPx = 52f,
            gapPx = 8f
        )

        assertEquals(132f, placement.top, 0.001f)
    }

    @Test
    fun appearancePickerStaysInsideHorizontalSafeBounds() {
        val placement = calculatePlaylistAppearancePickerPlacement(
            cardBounds = Rect(300f, 180f, 380f, 320f),
            actionsPlacement = PlaylistEditActionsPlacement(left = 218f, top = 118f),
            safeBounds = Rect(12f, 12f, 388f, 700f),
            pickerWidthPx = 344f,
            pickerHeightPx = 52f,
            gapPx = 8f
        )

        assertEquals(44f, placement.left, 0.001f)
    }
}
