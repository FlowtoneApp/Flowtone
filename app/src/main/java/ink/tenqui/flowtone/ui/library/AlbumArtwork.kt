package ink.tenqui.flowtone.ui.library

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneArtwork

private val AlbumArtworkCoverSize = 128.dp
private val AlbumVinylDiscSize = 112.dp
private val AlbumVinylExposedWidth = 28.dp

@Composable
internal fun AlbumArtwork(
    artworkUri: Uri?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(AlbumArtworkCoverSize + AlbumVinylExposedWidth)
            .height(AlbumArtworkCoverSize)
    ) {
        VinylDisc(
            modifier = Modifier
                .size(AlbumVinylDiscSize)
                .align(Alignment.CenterStart)
        )
        FlowtoneArtwork(
            artworkUri = artworkUri,
            modifier = Modifier
                .size(AlbumArtworkCoverSize)
                .align(Alignment.CenterEnd)
        )
    }
}

@Composable
internal fun VinylDisc(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF24242A),
                    Color(0xFF15151A),
                    Color(0xFF0D0D11)
                ),
                center = center,
                radius = diameter / 2f
            ),
            radius = diameter / 2f,
            center = center
        )

        listOf(0.34f, 0.46f, 0.58f, 0.70f, 0.82f, 0.92f).forEach { scale ->
            drawCircle(
                color = Color.White.copy(alpha = 0.045f),
                radius = diameter * scale / 2f,
                center = center,
                style = Stroke(width = 0.75.dp.toPx())
            )
        }

        val highlightInset = diameter * 0.11f
        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = 205f,
            sweepAngle = 54f,
            useCenter = false,
            topLeft = Offset(highlightInset, highlightInset),
            size = Size(
                width = diameter - highlightInset * 2f,
                height = diameter - highlightInset * 2f
            ),
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = Color.White.copy(alpha = 0.055f),
            startAngle = 22f,
            sweepAngle = 38f,
            useCenter = false,
            topLeft = Offset(highlightInset * 1.75f, highlightInset * 1.75f),
            size = Size(
                width = diameter - highlightInset * 3.5f,
                height = diameter - highlightInset * 3.5f
            ),
            style = Stroke(width = 0.9.dp.toPx(), cap = StrokeCap.Round)
        )

        drawCircle(
            color = Color(0xFF34343A),
            radius = diameter * 0.095f,
            center = center
        )
        drawCircle(
            color = Color(0xFF111116),
            radius = 1.6.dp.toPx(),
            center = center
        )
    }
}
