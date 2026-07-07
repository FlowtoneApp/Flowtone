package ink.tenqui.flowtone.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.core.model.Song
import ink.tenqui.flowtone.data.local.likedSongStorageKeys
import ink.tenqui.flowtone.ui.components.FlowtoneMotion
import ink.tenqui.flowtone.ui.player.MiniPlayerCollapsedHeight
import ink.tenqui.flowtone.ui.player.MiniPlayerMinimizedHeight

internal const val MINI_PLAYER_EXPAND_ANIMATION_DURATION_MS = 300

private const val FLOWTONE_INSETS_TAG = "FlowtoneInsets"

internal val FlowtonePageEasing = FlowtoneMotion.Easing

internal fun flowtoneRootPage(artistRootPageArtistName: String?): FlowtoneRootPage {
    return artistRootPageArtistName?.let { artistName ->
        FlowtoneRootPage.ArtistRootPage(artistName)
    } ?: FlowtoneRootPage.MainTabs
}

internal fun isMiniPlayerBackgroundBlurActive(
    hasCurrentSong: Boolean,
    miniPlayerExpanded: Boolean,
    miniPlayerFullscreen: Boolean
): Boolean {
    return hasCurrentSong && (miniPlayerExpanded || miniPlayerFullscreen)
}

internal fun resolveFlowtoneNavigationMode(context: Context): Int {
    val resourceId = context.resources.getIdentifier(
        "config_navBarInteractionMode",
        "integer",
        "android"
    )
    val resourceNavMode = if (resourceId > 0) {
        context.resources.getInteger(resourceId)
    } else {
        -1
    }
    val secureNavMode = Settings.Secure.getInt(
        context.contentResolver,
        "navigation_mode",
        -1
    )

    return if (secureNavMode >= 0) {
        secureNavMode
    } else {
        resourceNavMode
    }
}

internal fun isThreeButtonNavigationMode(navMode: Int): Boolean {
    return navMode == 0
}

internal fun isDebuggableApplication(context: Context): Boolean {
    return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

internal fun miniPlayerBottomProtectionPx(
    navMode: Int,
    isThreeButtonNavigation: Boolean,
    navigationBottom: Int,
    tappableBottom: Int,
    isDebuggable: Boolean
): Int {
    val bottomProtection = when {
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> navigationBottom
        isThreeButtonNavigation -> navigationBottom
        else -> tappableBottom
    }
    if (isDebuggable) {
        Log.d(
            FLOWTONE_INSETS_TAG,
            "navMode=$navMode, isThreeButton=$isThreeButtonNavigation, " +
                "navigationBottom=$navigationBottom, tappableBottom=$tappableBottom, " +
                "bottomProtection=$bottomProtection"
        )
    }

    return bottomProtection
}

internal fun miniPlayerContentBottomPaddingTarget(
    hasCurrentSong: Boolean,
    miniPlayerMinimized: Boolean,
    miniPlayerBottomProtection: Dp
): Dp {
    return if (hasCurrentSong) {
        val playerHeight = if (miniPlayerMinimized) {
            MiniPlayerMinimizedHeight
        } else {
            MiniPlayerCollapsedHeight
        }
        playerHeight + miniPlayerBottomProtection
    } else {
        0.dp
    }
}

internal fun nextLikedSongKeys(
    song: Song,
    liked: Boolean,
    currentKeys: List<String>
): List<String> {
    val songKeys = likedSongStorageKeys(song)
    return if (liked) {
        (currentKeys + songKeys).distinct()
    } else {
        val keysToRemove = songKeys.toSet()
        currentKeys.filterNot { key -> key in keysToRemove }
    }
}
