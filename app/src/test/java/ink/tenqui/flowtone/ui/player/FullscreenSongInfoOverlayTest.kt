package ink.tenqui.flowtone.ui.player

import ink.tenqui.flowtone.data.online.ProviderSongLikedStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenSongInfoOverlayTest {
    @Test
    fun onlineLikedStatusLabelsKeepUnknownDistinctFromNotLiked() {
        assertEquals("来源已喜欢", providerLikedSongStatusText(ProviderSongLikedStatus.Liked))
        assertEquals("来源未喜欢", providerLikedSongStatusText(ProviderSongLikedStatus.NotLiked))
        assertEquals("来源喜欢状态未知", providerLikedSongStatusText(ProviderSongLikedStatus.Unknown))
    }
}
