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
internal fun SettingsSectionRow(
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
internal fun SongRecordThresholdRow(
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
internal fun SongRecordThresholdDialog(
    selectedSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var draftSeconds by rememberSaveable(selectedSeconds) {
        mutableStateOf(selectedSeconds.coerceSongRecordThreshold())
    }
    var visible by remember {
        mutableStateOf(false)
    }
    var closing by remember {
        mutableStateOf(false)
    }
    val scope = rememberCoroutineScope()

    fun closeAfterAnimation(action: () -> Unit) {
        if (closing) {
            return
        }

        closing = true
        visible = false
        scope.launch {
            delay(DialogScaleExitDurationMillis.toLong())
            action()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = {
            closeAfterAnimation(onDismiss)
        },
        properties = PopupProperties(focusable = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = DialogScaleEnterDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = DialogScaleExitDurationMillis,
                        easing = FlowtonePageEasing
                    )
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable {
                            closeAfterAnimation(onDismiss)
                        }
                )
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = DialogScaleEnterDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = DialogScaleEnterDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = DialogScaleExitDurationMillis,
                        easing = FlowtonePageEasing
                    )
                ) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = tween(
                        durationMillis = DialogScaleExitDurationMillis,
                        easing = FlowtonePageEasing
                    )
                )
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "歌曲记录阈值",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "同一首歌真实播放达到这个时间后，才会计入今日听歌。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
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
                            },
                            valueRange = MinSongRecordThresholdSeconds.toFloat()..
                                MaxSongRecordThresholdSeconds.toFloat(),
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    closeAfterAnimation(onDismiss)
                                }
                            ) {
                                Text(text = "取消")
                            }
                            TextButton(
                                onClick = {
                                    val confirmedSeconds = draftSeconds
                                    closeAfterAnimation {
                                        onConfirm(confirmedSeconds)
                                    }
                                }
                            ) {
                                Text(text = "确定")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DefaultStartPageRow(
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
internal fun PreloadStrengthRow(
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
internal fun SettingSwitchRow(
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

internal fun Int.coerceSongRecordThreshold(): Int {
    return coerceIn(
        MinSongRecordThresholdSeconds,
        MaxSongRecordThresholdSeconds
    )
}
