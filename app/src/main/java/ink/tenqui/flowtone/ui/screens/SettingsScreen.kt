package ink.tenqui.flowtone.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
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

private enum class SettingsSection(val title: String) {
    Appearance("外观"),
    Playback("播放"),
    Record("记录"),
    Advanced("高级"),
    General("通用")
}

private const val MinSongRecordThresholdSeconds = 1
private const val MaxSongRecordThresholdSeconds = 60

@Composable
internal fun SettingsScreen(
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    disablePausedArtworkTilt: Boolean,
    onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onBackActionChange: ((() -> Unit)?) -> Unit,
    onPathSegmentsChange: (List<String>) -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    songRecordThresholdSeconds: Int,
    onSongRecordThresholdSecondsChange: (Int) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    var selectedSection by rememberSaveable {
        mutableStateOf<SettingsSection?>(null)
    }
    var selectedStartPage by rememberSaveable {
        mutableStateOf(appPreferences.getDefaultStartPage())
    }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnBackActionChange by rememberUpdatedState(onBackActionChange)
    val currentOnPathSegmentsChange by rememberUpdatedState(onPathSegmentsChange)
    val handleBack = remember(selectedSection) {
        {
            if (selectedSection == null) {
                currentOnBack()
            } else {
                selectedSection = null
            }
        }
    }

    DisposableEffect(handleBack) {
        currentOnBackActionChange(handleBack)
        onDispose { currentOnBackActionChange(null) }
    }
    SideEffect {
        currentOnPathSegmentsChange(selectedSection?.let { listOf(it.title) } ?: emptyList())
    }
    DisposableEffect(Unit) {
        onDispose { currentOnPathSegmentsChange(emptyList()) }
    }
    BackHandler(onBack = handleBack)

    AnimatedContent(
        targetState = selectedSection,
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        label = "SettingsContent",
        modifier = modifier
            .fillMaxSize()
            .rightSwipeBackGesture(handleBack)
    ) { section ->
        fun viewElementModifier(index: Int): Modifier {
            return elementModifier(index).then(staggeredPageElementModifier(index))
        }

        when (section) {
            null -> SettingsSectionList(
                onSectionClick = { selectedSection = it },
                elementModifier = ::viewElementModifier
            )

            SettingsSection.Appearance -> AppearanceSettingsPage(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                disablePausedArtworkTilt = disablePausedArtworkTilt,
                onDisablePausedArtworkTiltChange = onDisablePausedArtworkTiltChange,
                elementModifier = ::viewElementModifier
            )

            SettingsSection.Playback -> PlaybackSettingsPage(
                resumePlaybackAfterCall = resumePlaybackAfterCall,
                onResumePlaybackAfterCallChange = onResumePlaybackAfterCallChange,
                elementModifier = ::viewElementModifier
            )

            SettingsSection.Record -> RecordSettingsPage(
                songRecordThresholdSeconds = songRecordThresholdSeconds,
                onSongRecordThresholdSecondsChange = onSongRecordThresholdSecondsChange,
                elementModifier = ::viewElementModifier
            )

            SettingsSection.Advanced -> AdvancedSettingsPage(
                preloadSongMetadataCount = preloadSongMetadataCount,
                onPreloadSongMetadataCountChange = onPreloadSongMetadataCountChange,
                elementModifier = ::viewElementModifier
            )

            SettingsSection.General -> GeneralSettingsPage(
                selectedStartPage = selectedStartPage,
                onStartPageSelected = { page ->
                    selectedStartPage = page
                    appPreferences.setDefaultStartPage(page)
                },
                hideSecondaryBackButton = hideSecondaryBackButton,
                onHideSecondaryBackButtonChange = onHideSecondaryBackButtonChange,
                allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                onAllowFullscreenFromCollapsedChange = onAllowFullscreenFromCollapsedChange,
                elementModifier = ::viewElementModifier
            )
        }
    }
}

@Composable
private fun SettingsSectionList(
    onSectionClick: (SettingsSection) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SettingsSectionRow(
            title = "外观",
            subtitle = "主题模式",
            icon = Icons.Rounded.Palette,
            onClick = { onSectionClick(SettingsSection.Appearance) },
            modifier = elementModifier(0)
        )
        SettingsSectionRow(
            title = "播放",
            subtitle = "播放恢复行为",
            icon = Icons.Rounded.PlayCircle,
            onClick = { onSectionClick(SettingsSection.Playback) },
            modifier = elementModifier(1).padding(top = 12.dp)
        )
        SettingsSectionRow(
            title = "记录",
            subtitle = "听歌记录规则",
            icon = Icons.Rounded.History,
            onClick = { onSectionClick(SettingsSection.Record) },
            modifier = elementModifier(2).padding(top = 12.dp)
        )
        SettingsSectionRow(
            title = "高级",
            subtitle = "预载与性能",
            icon = Icons.Rounded.Tune,
            onClick = { onSectionClick(SettingsSection.Advanced) },
            modifier = elementModifier(3).padding(top = 12.dp)
        )
        SettingsSectionRow(
            title = "通用",
            subtitle = "启动与界面行为",
            icon = Icons.Rounded.Settings,
            onClick = { onSectionClick(SettingsSection.General) },
            modifier = elementModifier(4).padding(top = 12.dp)
        )
    }
}

@Composable
private fun AppearanceSettingsPage(
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
private fun PlaybackSettingsPage(
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
private fun RecordSettingsPage(
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
private fun AdvancedSettingsPage(
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
private fun GeneralSettingsPage(
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

@Composable
private fun SettingsPageColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsSectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SongRecordThresholdRow(
    selectedSeconds: Int,
    onSelectedSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }
    val seconds = selectedSeconds.coerceSongRecordThreshold()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "歌曲记录阈值",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "当前：$seconds 秒，播放满后才计入今日听歌",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "设置歌曲记录阈值",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDialog) {
        SongRecordThresholdDialog(
            selectedSeconds = seconds,
            onDismiss = { showDialog = false },
            onConfirm = { value ->
                onSelectedSecondsChange(value)
                showDialog = false
            }
        )
    }
}

@Composable
private fun SongRecordThresholdDialog(
    selectedSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var draftSeconds by rememberSaveable(selectedSeconds) {
        mutableStateOf(selectedSeconds.coerceSongRecordThreshold())
    }
    var inputText by rememberSaveable(selectedSeconds) {
        mutableStateOf(draftSeconds.toString())
    }
    val inputSeconds = inputText.toIntOrNull()
    val inputInvalid = inputSeconds == null ||
        inputSeconds !in MinSongRecordThresholdSeconds..MaxSongRecordThresholdSeconds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "歌曲记录阈值")
        },
        text = {
            Column {
                Text(
                    text = "同一首歌真实播放达到这个时间后，才会计入今日听歌。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$draftSeconds 秒",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Slider(
                    value = draftSeconds.toFloat(),
                    onValueChange = { value ->
                        val seconds = value
                            .roundToInt()
                            .coerceSongRecordThreshold()
                        draftSeconds = seconds
                        inputText = seconds.toString()
                    },
                    valueRange = MinSongRecordThresholdSeconds.toFloat()..
                        MaxSongRecordThresholdSeconds.toFloat(),
                    steps = MaxSongRecordThresholdSeconds - MinSongRecordThresholdSeconds - 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${MinSongRecordThresholdSeconds} 秒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${MaxSongRecordThresholdSeconds} 秒",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(2)
                        inputText = digits
                        digits.toIntOrNull()
                            ?.takeIf {
                                it in MinSongRecordThresholdSeconds..MaxSongRecordThresholdSeconds
                            }
                            ?.let { draftSeconds = it }
                    },
                    label = { Text("秒数") },
                    isError = inputInvalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = if (inputInvalid) {
                                "请输入 1-60 秒"
                            } else {
                                "也可以直接输入数字"
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !inputInvalid,
                onClick = {
                    val confirmedSeconds = inputSeconds
                        ?.coerceSongRecordThreshold()
                        ?: draftSeconds
                    onConfirm(confirmedSeconds)
                }
            ) {
                Text(text = "确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}

@Composable
private fun DefaultStartPageRow(
    selectedPage: TopLevelPage,
    onPageSelected: (TopLevelPage) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    val expandIconRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(280, easing = FlowtonePageEasing),
        label = "StartPageExpandIconRotation"
    )
    val pages = listOf(
        TopLevelPage.Home,
        TopLevelPage.Library,
        TopLevelPage.Mine
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "启动时默认进入",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "下次打开应用时生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = expandIconRotation }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) + fadeIn(tween(220, easing = FlowtonePageEasing)),
            exit = shrinkVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) + fadeOut(tween(180, easing = FlowtonePageEasing))
        ) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                pages.forEach { page ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPageSelected(page) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPage == page,
                            onClick = { onPageSelected(page) }
                        )
                        Text(
                            text = page.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreloadStrengthRow(
    selectedCount: Int,
    onSelectedCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(1, 3, 5, 7, 10)
    val selectedIndex = options.indexOf(selectedCount).takeIf { it != -1 } ?: 2
    var expanded by rememberSaveable {
        mutableStateOf(false)
    }
    val expandIconRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(280, easing = FlowtonePageEasing),
        label = "PreloadStrengthExpandIconRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "预载歌曲元信息强度",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "当前：$selectedCount 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = expandIconRotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) + fadeIn(tween(220, easing = FlowtonePageEasing)),
            exit = shrinkVertically(
                animationSpec = tween(300, easing = FlowtonePageEasing)
            ) + fadeOut(tween(180, easing = FlowtonePageEasing))
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
            ) {
                Text(
                    text = "提前加载接下来歌曲的封面与元信息，减少切歌时的封面闪烁。强度越高，占用的内存与后台加载越多。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = selectedIndex.toFloat(),
                    onValueChange = { value ->
                        val index = value
                            .roundToInt()
                            .coerceIn(options.indices)
                        onSelectedCountChange(options[index])
                    },
                    valueRange = 0f..(options.size - 1).toFloat(),
                    steps = options.size - 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    options.forEach { count ->
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (count == selectedCount) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "低",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "高",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun Int.coerceSongRecordThreshold(): Int {
    return coerceIn(
        MinSongRecordThresholdSeconds,
        MaxSongRecordThresholdSeconds
    )
}
