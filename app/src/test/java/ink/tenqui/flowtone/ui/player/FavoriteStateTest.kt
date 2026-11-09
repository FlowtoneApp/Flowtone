package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.data.online.ProviderSongLikedStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteStateTest {
    @Test fun descriptionsKeepLocalAndProviderStatesDistinct() {
        assertEquals("声流未喜欢，来源未喜欢", favoriteStateDescription(false, ProviderSongLikedStatus.NotLiked))
        assertEquals("声流未喜欢，来源已喜欢", favoriteStateDescription(false, ProviderSongLikedStatus.Liked))
        assertEquals("声流已喜欢，来源未喜欢", favoriteStateDescription(true, ProviderSongLikedStatus.NotLiked))
        assertEquals("声流已喜欢，来源已喜欢", favoriteStateDescription(true, ProviderSongLikedStatus.Liked))
        assertEquals("声流已喜欢", favoriteStateDescription(true, ProviderSongLikedStatus.Unknown))
        assertEquals("声流未喜欢", favoriteStateDescription(false, null))
    }

    @Test fun backgroundsOnlyAppearForKnownDifferentStates() {
        assertNull(favoriteBackgroundLiked(false, ProviderSongLikedStatus.NotLiked))
        assertEquals(true, favoriteBackgroundLiked(false, ProviderSongLikedStatus.Liked))
        assertEquals(false, favoriteBackgroundLiked(true, ProviderSongLikedStatus.NotLiked))
        assertNull(favoriteBackgroundLiked(true, ProviderSongLikedStatus.Liked))
        assertNull(favoriteBackgroundLiked(true, ProviderSongLikedStatus.Unknown))
        assertNull(favoriteBackgroundLiked(false, null))
    }

    @Test fun onlyChangedWritableKnownTargetsAreSubmitted() {
        assertEquals(FavoriteChanges(true, true), favoriteChanges(true, false, true, false, true, true))
        assertEquals(FavoriteChanges(false, false), favoriteChanges(true, true, true, null, true, true))
        assertEquals(FavoriteChanges(false, false), favoriteChanges(false, true, false, false, true, false))
        assertEquals(FavoriteChanges(false, true), favoriteChanges(true, true, true, true, false, true))
    }
}
