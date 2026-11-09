package ink.tenqui.flowtone.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import ink.tenqui.flowtone.data.online.ProviderSongLikedStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavoriteManagementInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortAndLongPressInvokeOnlyTheirOwnActions() {
        var taps = 0
        var longPresses = 0
        var showDialog by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                FavoriteButton(liked = false, enabled = true, onClick = { taps++ }, onLongClick = { longPresses++; showDialog = true })
                if (showDialog) FavoriteManagementDialog(false, true, null, null, null, false, {}, { false }, { showDialog = false })
            }
        }
        compose.onNodeWithContentDescription("声流未喜欢").performClick()
        compose.runOnIdle { assertEquals(1, taps); assertEquals(0, longPresses) }
        compose.onNodeWithContentDescription("声流未喜欢").performTouchInput { longClick() }
        compose.onNodeWithText("收藏这首歌曲").assertExists()
        compose.runOnIdle { assertEquals(1, taps); assertEquals(1, longPresses) }
    }

    @Test fun dialogKeepsLocalSuccessAndRetriesFailedProviderChange() {
        var localCalls = 0
        var remoteCalls = 0
        var dismissed = false
        var providerSucceeds by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                FavoriteManagementDialog(
                    localLiked = true, localAvailable = true, providerName = "来源", providerColor = "#667788",
                    providerStatus = ProviderSongLikedStatus.NotLiked, providerWritable = true,
                    onSetLocal = { assertEquals(false, it); localCalls++ },
                    onSetProvider = { assertEquals(true, it); remoteCalls++; providerSucceeds },
                    onDismiss = { dismissed = true }
                )
            }
        }
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("已选择").assertCountEquals(1)
        compose.onNodeWithText("声流").performClick()
        compose.onNodeWithText("来源").performClick()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("来源收藏更新失败，请重试").assertExists()
        compose.runOnIdle {
            assertEquals(1, localCalls)
            assertEquals(1, remoteCalls)
            assertEquals(false, dismissed)
            providerSucceeds = true
        }
        compose.onNodeWithText("确定").performClick()
        compose.runOnIdle { assertEquals(1, localCalls); assertEquals(2, remoteCalls); assertEquals(true, dismissed) }
    }

    @Test fun unknownAndReadOnlyProviderCannotBeChanged() {
        var status by mutableStateOf<ProviderSongLikedStatus?>(ProviderSongLikedStatus.Unknown)
        var writable by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                FavoriteManagementDialog(false, true, "来源", null, status, writable, {}, { true }, {})
            }
        }
        compose.onNodeWithText("来源喜欢状态未知").assertExists()
        compose.onNodeWithText("确定").assertIsNotEnabled()
        compose.runOnIdle { status = ProviderSongLikedStatus.Liked; writable = false }
        compose.onNodeWithText("来源喜欢状态为只读").assertExists()
        compose.onNodeWithText("确定").assertIsNotEnabled()
    }
}
