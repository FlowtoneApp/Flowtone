package ink.tenqui.flowtone.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.FlowtoneMotion

@Composable
internal fun FavoriteButton(
    liked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualEnabled: Boolean = enabled
) {
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (visualEnabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = if (liked) {
                Icons.Rounded.Favorite
            } else {
                Icons.Outlined.FavoriteBorder
            },
            contentDescription = if (liked) {
                "\u5df2\u559c\u6b22"
            } else {
                "\u6dfb\u52a0\u559c\u6b22"
            },
            tint = if (liked) {
                Color(0xFFFF4D67)
            } else {
                Color.White.copy(alpha = 0.92f)
            },
            modifier = Modifier.size(PlayerSideButtonIconSize)
        )
    }
}

@Composable
internal fun QueueButton(
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualEnabled: Boolean = enabled
) {
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (visualEnabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = "\u64ad\u653e\u961f\u5217",
            tint = iconColor,
            modifier = Modifier.size(PlayerSideButtonIconSize)
        )
    }
}

@Composable
internal fun FullscreenMoreMenu(
    visible: Boolean,
    iconColor: Color,
    alpha: Float,
    enabled: Boolean,
    onCollapse: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenSongInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capsuleHeight by animateDpAsState(
        targetValue = if (visible) FullscreenMoreMenuCapsuleHeight else 0.dp,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "FullscreenMoreMenuCapsuleHeight"
    )
    val capsuleIconAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "FullscreenMoreMenuIconAlpha"
    )
    val capsuleIconScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.82f,
        animationSpec = tween(
            durationMillis = FlowtoneMotion.DurationMillis,
            easing = FlowtoneMotion.Easing
        ),
        label = "FullscreenMoreMenuIconScale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fullscreenMenuEnterTransition(),
        exit = fullscreenMenuExitTransition(),
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
            }
    ) {
        Box(
            modifier = Modifier
                .width(PlayerSideButtonSize)
                .height(FullscreenMoreMenuHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier.width(PlayerSideButtonSize),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FullscreenMoreMenuAction(
                    icon = Icons.Rounded.ExpandMore,
                    contentDescription = "\u6536\u8d77\u66f4\u591a\u64cd\u4f5c",
                    iconColor = iconColor,
                    iconSize = FullscreenMoreMenuCollapseIconSize,
                    enabled = enabled,
                    onClick = onCollapse
                )
                Column(
                    modifier = Modifier
                        .width(PlayerSideButtonSize)
                        .height(capsuleHeight)
                        .clip(RoundedCornerShape(FullscreenMoreMenuCornerRadius))
                        .background(Color.Gray.copy(alpha = 0.26f)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FullscreenMoreMenuAction(
                        icon = Icons.Outlined.CreateNewFolder,
                        contentDescription = "\u6dfb\u52a0\u5230\u6b4c\u5355",
                        iconColor = iconColor,
                        iconAlpha = capsuleIconAlpha,
                        iconScale = capsuleIconScale,
                        enabled = enabled,
                        onClick = onAddToPlaylist
                    )
                    FullscreenMoreMenuAction(
                        icon = Icons.Outlined.Info,
                        contentDescription = "\u4fe1\u606f",
                        iconColor = iconColor,
                        iconAlpha = capsuleIconAlpha,
                        iconScale = capsuleIconScale,
                        enabled = enabled,
                        onClick = onOpenSongInfo
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenMoreMenuAction(
    icon: ImageVector,
    contentDescription: String,
    iconColor: Color,
    onClick: () -> Unit,
    iconSize: Dp = PlayerSideButtonIconSize,
    iconAlpha: Float = 1f,
    iconScale: Float = 1f,
    enabled: Boolean = true
) {
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(PlayerSideButtonSize)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    alpha = iconAlpha
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
    }
}

@Composable
internal fun MoreMenuButton(
    iconColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualEnabled: Boolean = enabled
) {
    TransparentControlButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                alpha = if (visualEnabled) 1f else 0.45f
            }
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = "\u66f4\u591a",
            tint = iconColor,
            modifier = Modifier.size(PlayerSideButtonIconSize)
        )
    }
}
