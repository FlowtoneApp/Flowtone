package ink.tenqui.flowtone.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarNavigationTitleShift
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.library.ArtistHeroBackgroundKind
import ink.tenqui.flowtone.ui.library.ArtistIdentityTopBarRow
import ink.tenqui.flowtone.ui.library.artistTopBarVisualPresentation

internal val ArtistTopBarIdentityMotionDistance = 16.dp

internal enum class ArtistTopBarSurfaceTreatment { Transparent }

internal fun artistTopBarSurfaceTreatment(
    backgroundKind: ArtistHeroBackgroundKind
): ArtistTopBarSurfaceTreatment = ArtistTopBarSurfaceTreatment.Transparent

internal data class ArtistTopBarIdentityPresentation(
    val alpha: Float,
    val translationYFraction: Float
)

internal enum class ArtistTopBarStableShellElement { Back, Avatar }

internal val ArtistTopBarStableShellElements = ArtistTopBarStableShellElement.entries.toSet()

internal fun artistTopBarIdentityPresentation(
    progress: Float
): ArtistTopBarIdentityPresentation {
    val presentation = artistTopBarVisualPresentation(progress)
    return ArtistTopBarIdentityPresentation(
        alpha = presentation.foregroundAlpha,
        translationYFraction = presentation.foregroundTranslationYFraction
    )
}

@Composable
internal fun ArtistIdentityTopBar(
    artistName: String,
    avatarImage: ExtensionImage?,
    backgroundKind: ArtistHeroBackgroundKind,
    identityVisible: Boolean,
    identityProgress: Float,
    pathSegments: List<String>,
    onBack: () -> Unit,
    onFullTitleRequest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val identityPresentation = artistTopBarIdentityPresentation(identityProgress)
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val identityMotionDistancePx = with(density) { ArtistTopBarIdentityMotionDistance.toPx() }
    val contentColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarTop + FlowtoneTopBarContentHeight)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(FlowtoneTopBarContentHeight)
                .graphicsLayer {
                    alpha = identityPresentation.alpha
                    translationY = identityMotionDistancePx *
                        identityPresentation.translationYFraction
                },
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = onBack,
                enabled = identityVisible,
                modifier = Modifier
                    .offset(x = (-8).dp)
                    .padding(start = 12.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = contentColor
                )
            }
            ArtistIdentityTopBarRow(
                artistName = artistName,
                avatarImage = avatarImage,
                pathSegments = pathSegments,
                contentColor = contentColor,
                onFullTitleRequest = onFullTitleRequest,
                interactionEnabled = identityVisible,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = FlowtoneTopBarTitleStartPadding +
                            FlowtoneTopBarNavigationTitleShift,
                        end = 24.dp
                    )
            )
        }
    }
}
