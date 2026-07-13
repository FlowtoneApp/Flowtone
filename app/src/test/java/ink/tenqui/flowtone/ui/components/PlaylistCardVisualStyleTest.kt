package ink.tenqui.flowtone.ui.components

import androidx.compose.ui.graphics.Color
import ink.tenqui.flowtone.core.model.LibraryPlaylistCard
import ink.tenqui.flowtone.core.model.LikedSongsPlaylistId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCardVisualStyleTest {
    @Test
    fun likedMusicUsesStableSystemIdentityInsteadOfDisplayName() {
        val playlist = playlist(
            id = LikedSongsPlaylistId,
            title = "Renamed",
            isSystem = true
        )

        assertEquals(
            PlaylistCardVisualType.LikedMusic,
            playlistCardVisualTypeFor(playlist)
        )
    }

    @Test
    fun matchingDisplayNameDoesNotMakeUserPlaylistLikedMusic() {
        val playlist = playlist(
            id = "user_playlist",
            title = "\u6211\u559c\u6b22\u7684\u97f3\u4e50",
            isSystem = false
        )

        assertEquals(
            PlaylistCardVisualType.UserPlaylist,
            playlistCardVisualTypeFor(playlist)
        )
    }

    @Test
    fun fixedLikedIdWithoutSystemFlagRemainsUserPlaylist() {
        assertEquals(
            PlaylistCardVisualType.UserPlaylist,
            playlistCardVisualTypeFor(
                playlist(
                    id = LikedSongsPlaylistId,
                    title = "User playlist",
                    isSystem = false
                )
            )
        )
    }

    @Test
    fun unknownSystemPlaylistUsesDefaultVisuals() {
        assertEquals(
            PlaylistCardVisualType.Default,
            playlistCardVisualTypeFor(
                playlist(
                    id = "future_system_playlist",
                    title = "Future system playlist",
                    isSystem = true
                )
            )
        )
    }

    @Test
    fun onlyLikedMusicUsesFlowCloudAndSpeedSetting() {
        PlaylistCardVisualType.entries.forEach { type ->
            val style = playlistCardVisualStyleFor(type)
            if (type == PlaylistCardVisualType.LikedMusic) {
                assertEquals(PlaylistCardBackgroundType.FlowCloud, style.backgroundType)
                assertTrue(style.usesFlowCloudSpeed)
            } else {
                assertEquals(PlaylistCardBackgroundType.Static, style.backgroundType)
                assertFalse(style.usesFlowCloudSpeed)
            }
        }
    }

    @Test
    fun specialPaletteConstantsRemainThemeSpecific() {
        assertEquals(Color(0xFFD9CDF2), LocalLibraryLightBackground)
        assertEquals(Color(0xFF332A47), LocalLibraryDarkBackground)
        assertEquals(Color(0xFF50396D), CreatePlaylistLightBackground)
        assertEquals(Color(0xFF2D223D), CreatePlaylistDarkBackground)
        assertEquals(
            listOf(
                Color(0xFFE6A1BE),
                Color(0xFFB9A3DD),
                Color(0xFFA6B9E1)
            ),
            LikedMusicLightCloudColors
        )
        assertEquals(
            listOf(
                Color(0xFF8F496B),
                Color(0xFF66518E),
                Color(0xFF4D608D)
            ),
            LikedMusicDarkCloudColors
        )
    }

    @Test
    fun userPlaylistPaletteHasEightDistinctThemeAwareColors() {
        val lightColors = PlaylistAppearanceColorKeys.map { key ->
            playlistAppearanceColors(key, isDarkTheme = false).backgroundColor
        }
        val darkColors = PlaylistAppearanceColorKeys.map { key ->
            playlistAppearanceColors(key, isDarkTheme = true).backgroundColor
        }

        assertEquals(8, PlaylistAppearanceColorKeys.size)
        assertEquals(8, lightColors.distinct().size)
        assertEquals(8, darkColors.distinct().size)
        PlaylistAppearanceColorKeys.indices.forEach { index ->
            assertFalse(lightColors[index] == darkColors[index])
        }
    }

    private fun playlist(
        id: String,
        title: String,
        isSystem: Boolean
    ): LibraryPlaylistCard {
        return LibraryPlaylistCard(
            id = id,
            title = title,
            order = 0,
            isSystem = isSystem
        )
    }
}
