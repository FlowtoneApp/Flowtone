package ink.tenqui.flowtone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.app.FlowtonePageEasing
import ink.tenqui.flowtone.app.TopLevelPage
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.ThemeModeSelector
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun AppearanceSettingsPage(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    disablePausedArtworkTilt: Boolean,
    onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        OptionGroup(
            title = "外观",
            modifier = elementModifier(0)
        ) {
            ThemeModeSelector(
                selectedMode = themeMode,
                onModeSelected = onThemeModeChange
            )
            SettingSwitchRow(
                title = "取消暂停时封面倾斜",
                subtitle = "打开后，暂停时封面仅缩小，不再倾斜",
                checked = disablePausedArtworkTilt,
                onCheckedChange = onDisablePausedArtworkTiltChange,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
internal fun PlaybackSettingsPage(
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        OptionGroup(
            title = "播放",
            modifier = elementModifier(0)
        ) {
            SettingSwitchRow(
                title = "来电后恢复播放",
                subtitle = "仅在来电前正在播放且音频焦点短暂丢失时恢复",
                checked = resumePlaybackAfterCall,
                onCheckedChange = onResumePlaybackAfterCallChange
            )
        }
    }
}

@Composable
internal fun RecordSettingsPage(
    songRecordThresholdSeconds: Int,
    onSongRecordThresholdSecondsChange: (Int) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        OptionGroup(
            title = "记录",
            modifier = elementModifier(0)
        ) {
            SongRecordThresholdRow(
                selectedSeconds = songRecordThresholdSeconds,
                onSelectedSecondsChange = onSongRecordThresholdSecondsChange
            )
        }
    }
}

@Composable
internal fun AdvancedSettingsPage(
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        OptionGroup(
            title = "高级",
            modifier = elementModifier(0)
        ) {
            PreloadStrengthRow(
                selectedCount = preloadSongMetadataCount,
                onSelectedCountChange = onPreloadSongMetadataCountChange
            )
        }
    }
}

@Composable
internal fun GeneralSettingsPage(
    selectedStartPage: TopLevelPage,
    onStartPageSelected: (TopLevelPage) -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    SettingsPageColumn(modifier = modifier) {
        OptionGroup(
            title = "通用",
            modifier = elementModifier(0)
        ) {
            DefaultStartPageRow(
                selectedPage = selectedStartPage,
                onPageSelected = onStartPageSelected
            )
            SettingSwitchRow(
                title = "关闭子菜单返回按钮",
                subtitle = "右滑屏幕即可返回上一级",
                checked = hideSecondaryBackButton,
                onCheckedChange = onHideSecondaryBackButtonChange,
                modifier = Modifier.padding(top = 12.dp)
            )
            SettingSwitchRow(
                title = "正常态上滑直达全屏",
                subtitle = "关闭后需要先展开 MiniPlayer，再上滑进入全屏",
                checked = allowFullscreenFromCollapsed,
                onCheckedChange = onAllowFullscreenFromCollapsedChange,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
