package ink.tenqui.flowtone.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val ArtistSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val ArtistCandidateRowHeight = 52.dp
private val ArtistSheetFixedContentHeight = 126.dp

@Composable
internal fun ArtistSelectionSheet(
    rawArtist: String,
    candidates: List<String>,
    overlayHeight: Dp,
    fullscreen: Boolean,
    onArtistSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sheetVisible by remember { mutableStateOf(false) }
    var dismissStarted by remember { mutableStateOf(false) }
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val scrimProgress by animateFloatAsState(
        targetValue = if (sheetVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
            easing = LinearEasing
        ),
        label = "ArtistSelectionScrimProgress"
    )

    fun requestDismiss() {
        if (!dismissStarted) {
            dismissStarted = true
            sheetVisible = false
        }
    }

    BackHandler(enabled = true) {
        requestDismiss()
    }

    LaunchedEffect(Unit) {
        sheetVisible = true
    }
    LaunchedEffect(sheetVisible, dismissStarted) {
        if (dismissStarted && !sheetVisible) {
            delay(MINI_PLAYER_ANIMATION_DURATION_MS.toLong())
            onDismiss()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(overlayHeight)
            .clickable(
                interactionSource = noRippleInteractionSource,
                indication = null,
                onClick = { requestDismiss() }
            )
    ) {
        val scrimAlpha = if (fullscreen) 0.20f else 0.10f
        val maxListHeight = (maxHeight * 0.58f - ArtistSheetFixedContentHeight)
            .coerceAtLeast(ArtistCandidateRowHeight * 2f)
        val listHeight = (ArtistCandidateRowHeight * candidates.size)
            .coerceAtMost(maxListHeight)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha * scrimProgress))
        )

        AnimatedVisibility(
            visible = sheetVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                ),
                initialAlpha = 0f
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                ),
                initialOffsetY = { fullHeight -> fullHeight + 56 }
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                )
            ) + slideOutVertically(
                animationSpec = tween(
                    durationMillis = MINI_PLAYER_ANIMATION_DURATION_MS,
                    easing = MiniPlayerEasing
                ),
                targetOffsetY = { fullHeight -> fullHeight + 56 }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(ArtistSheetShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        interactionSource = noRippleInteractionSource,
                        indication = null,
                        onClick = {}
                    )
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 18.dp)
            ) {
                Text(
                    text = "选择曲师",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listHeight)
                        .padding(top = 12.dp)
                ) {
                    items(
                        items = candidates,
                        key = { artist -> artist }
                    ) { artist ->
                        ArtistCandidateItem(
                            artist = artist,
                            onClick = {
                                onArtistSelected(artist)
                            }
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    text = "原始信息：$rawArtist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistCandidateItem(
    artist: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = artist,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .height(ArtistCandidateRowHeight)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    )
}
