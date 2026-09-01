package ink.tenqui.flowtone.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.core.online.ExtensionImage
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarContentHeight
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarNavigationTitleShift
import ink.tenqui.flowtone.ui.components.FlowtoneTopBarTitleStartPadding
import ink.tenqui.flowtone.ui.library.ArtistAvatar
import ink.tenqui.flowtone.ui.library.rememberExperimentalArtistAvatarImage
import ink.tenqui.flowtone.ui.player.localSongsForArtist

private val ArtistIdentityAvatarSize = 36.dp
private val ArtistIdentityTitleGap = 10.dp
private val ArtistIdentityMotionDistance = 8.dp
private val ArtistPathMotionDistance = 10.dp
private const val ArtistAvatarEnterDelayMillis = 64
private const val ArtistTitleEnterDelayMillis = 128
private const val ArtistAvatarExitDelayMillis = 64
private const val ArtistPathTitleStaggerFraction = 0.18f

@Composable
internal fun ArtistIdentityTopBar(
    artistName: String,
    hasLocalContent: Boolean,
    providedAvatar: ExtensionImage?,
    destinationTitle: String?,
    identityVisible: Boolean,
    artistSurfaceVisible: Boolean,
    allSongs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    playlistSortProgress: Float,
    modifier: Modifier = Modifier
) {
    val artistSongs = remember(artistName, allSongs, hasLocalContent) {
        if (hasLocalContent) localSongsForArtist(allSongs, artistName) else emptyList()
    }
    val avatarLookupSongTitle = remember(artistSongs, currentSong) {
        val currentArtistSong = currentSong?.takeIf { playingSong ->
            artistSongs.any { artistSong ->
                artistSong.id == playingSong.id || artistSong.uri == playingSong.uri
            }
        }
        (currentArtistSong ?: artistSongs.firstOrNull())?.title.orEmpty()
    }
    val resolvedLocalAvatar = if (hasLocalContent) {
        rememberExperimentalArtistAvatarImage(
            songTitle = avatarLookupSongTitle,
            artistName = artistName
        )
    } else {
        null
    }
    val avatarImage = providedAvatar ?: resolvedLocalAvatar
    var retainedDestinationTitle by remember { mutableStateOf(destinationTitle) }
    val pathProgress = remember {
        Animatable(if (destinationTitle == null) 0f else 1f)
    }

    LaunchedEffect(destinationTitle) {
        if (destinationTitle != null) {
            retainedDestinationTitle = destinationTitle
            withFrameNanos { }
            pathProgress.animateTo(1f, flowtonePageTextTween())
        } else {
            pathProgress.animateTo(0f, flowtonePageTextTween())
            retainedDestinationTitle = null
        }
    }

    val enteringPath = destinationTitle != null
    val separatorProgress = if (enteringPath) {
        pathProgress.value
    } else {
        (pathProgress.value / (1f - ArtistPathTitleStaggerFraction)).coerceIn(0f, 1f)
    }
    val titleProgress = if (enteringPath) {
        ((pathProgress.value - ArtistPathTitleStaggerFraction) /
            (1f - ArtistPathTitleStaggerFraction)).coerceIn(0f, 1f)
    } else {
        pathProgress.value
    }
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val identityMotionDistancePx = with(density) { ArtistIdentityMotionDistance.toPx() }
    val pathMotionDistancePx = with(density) { ArtistPathMotionDistance.toPx() }
    val pathDirection = if (enteringPath) -1f else 1f
    val sortExitDistancePx = with(density) { 12.dp.toPx() }
    val artistSurfaceProgress by animateFloatAsState(
        targetValue = if (artistSurfaceVisible) 1f else 0f,
        animationSpec = tween(180, easing = FlowtonePageEasing),
        label = "ArtistTopBarSurfaceProgress"
    )
    val artistSurfaceAlpha = artistSurfaceProgress * (1f - pathProgress.value)
    val identityTransition = updateTransition(
        targetState = identityVisible,
        label = "ArtistTopBarIdentity"
    )
    val avatarProgress by identityTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(
                    durationMillis = 180,
                    delayMillis = ArtistAvatarEnterDelayMillis,
                    easing = FlowtonePageEasing
                )
            } else {
                tween(
                    durationMillis = 160,
                    delayMillis = ArtistAvatarExitDelayMillis,
                    easing = FlowtonePageEasing
                )
            }
        },
        label = "ArtistTopBarAvatarProgress"
    ) { visible ->
        if (visible) 1f else 0f
    }
    val artistTitleProgress by identityTransition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(
                    durationMillis = 180,
                    delayMillis = ArtistTitleEnterDelayMillis,
                    easing = FlowtonePageEasing
                )
            } else {
                tween(durationMillis = 160, easing = FlowtonePageEasing)
            }
        },
        label = "ArtistTopBarTitleProgress"
    ) { visible ->
        if (visible) 1f else 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlowtoneTopBarContentHeight + statusBarTop)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = artistSurfaceAlpha }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(FlowtoneTopBarContentHeight),
            contentAlignment = Alignment.CenterStart
        ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .offset(x = (-8).dp)
                .padding(start = 12.dp)
                .size(40.dp)
                .graphicsLayer { alpha = 1f - playlistSortProgress }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlowtoneTopBarContentHeight)
                .padding(
                    start = FlowtoneTopBarTitleStartPadding +
                        FlowtoneTopBarNavigationTitleShift,
                    end = 24.dp
                )
                .clipToBounds()
                .graphicsLayer {
                    alpha = 1f - playlistSortProgress
                    translationX = -sortExitDistancePx * playlistSortProgress
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val artistTitleMaxWidth = if (retainedDestinationTitle == null) {
                maxWidth - ArtistIdentityAvatarSize - ArtistIdentityTitleGap
            } else {
                maxWidth * 0.42f
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ArtistAvatar(
                    size = ArtistIdentityAvatarSize,
                    image = avatarImage,
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.graphicsLayer {
                        alpha = avatarProgress
                        translationY = -identityMotionDistancePx * (1f - avatarProgress)
                    }
                )
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = ArtistIdentityTitleGap)
                        .widthIn(max = artistTitleMaxWidth)
                        .graphicsLayer {
                            alpha = artistTitleProgress
                            translationY = -identityMotionDistancePx *
                                (1f - artistTitleProgress)
                        }
                )
                retainedDestinationTitle?.let { title ->
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = separatorProgress
                            translationX = pathDirection * pathMotionDistancePx *
                                (1f - separatorProgress)
                        }
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                alpha = titleProgress
                                translationX = pathDirection * pathMotionDistancePx *
                                    (1f - titleProgress)
                            }
                    )
                }
            }
        }
        }
    }
}
