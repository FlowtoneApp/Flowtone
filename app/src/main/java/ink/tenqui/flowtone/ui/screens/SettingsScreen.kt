package ink.tenqui.flowtone.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.app.FlowtonePageEasing
import ink.tenqui.flowtone.app.TopLevelPage
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.ThemeModeSelector
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.theme.AppThemeMode

@Composable
internal fun SettingsScreen(
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    var selectedStartPage by rememberSaveable {
        mutableStateOf(appPreferences.getDefaultStartPage())
    }
    var startPageExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    val expandIconRotation by animateFloatAsState(
        targetValue = if (startPageExpanded) 180f else 0f,
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
            .fillMaxSize()
            .rightSwipeBackGesture(onBack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        OptionGroup(
            title = "\u5916\u89c2",
            modifier = elementModifier(0)
        ) {
            ThemeModeSelector(
                selectedMode = themeMode,
                onModeSelected = onThemeModeChange
            )
        }
        OptionGroup(
            title = "\u5e94\u7528\u884c\u4e3a",
            modifier = elementModifier(1).padding(top = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { startPageExpanded = !startPageExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "\u542f\u52a8\u65f6\u9ed8\u8ba4\u8fdb\u5165",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "\u4e0b\u6b21\u6253\u5f00\u5e94\u7528\u65f6\u751f\u6548",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = if (startPageExpanded) "\u6536\u8d77" else "\u5c55\u5f00",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = expandIconRotation }
                    )
                }
                AnimatedVisibility(
                    visible = startPageExpanded,
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
                                    .clickable {
                                        selectedStartPage = page
                                        appPreferences.setDefaultStartPage(page)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedStartPage == page,
                                    onClick = {
                                        selectedStartPage = page
                                        appPreferences.setDefaultStartPage(page)
                                    }
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
            SettingSwitchRow(
                title = "\u5173\u95ed\u5b50\u83dc\u5355\u8fd4\u56de\u6309\u94ae",
                subtitle = "\u53f3\u6ed1\u5c4f\u5e55\u5373\u53ef\u8fd4\u56de\u4e0a\u4e00\u7ea7",
                checked = hideSecondaryBackButton,
                onCheckedChange = onHideSecondaryBackButtonChange,
                modifier = Modifier.padding(top = 12.dp)
            )
            SettingSwitchRow(
                title = "\u6765\u7535\u540e\u6062\u590d\u64ad\u653e",
                subtitle = "\u4ec5\u5728\u6765\u7535\u524d\u6b63\u5728\u64ad\u653e\u4e14\u97f3\u9891\u7126\u70b9\u77ed\u6682\u4e22\u5931\u65f6\u6062\u590d",
                checked = resumePlaybackAfterCall,
                onCheckedChange = onResumePlaybackAfterCallChange,
                modifier = Modifier.padding(top = 12.dp)
            )
            SettingSwitchRow(
                title = "\u6b63\u5e38\u6001\u4e0a\u6ed1\u76f4\u8fbe\u5168\u5c4f",
                subtitle = "\u5173\u95ed\u540e\u9700\u8981\u5148\u5c55\u5f00 MiniPlayer\uff0c\u518d\u4e0a\u6ed1\u8fdb\u5165\u5168\u5c4f",
                checked = allowFullscreenFromCollapsed,
                onCheckedChange = onAllowFullscreenFromCollapsedChange,
                modifier = Modifier.padding(top = 12.dp)
            )
            PreloadStrengthRow(
                selectedCount = preloadSongMetadataCount,
                onSelectedCountChange = onPreloadSongMetadataCountChange,
                modifier = Modifier.padding(top = 12.dp)
            )
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "\u9884\u8f7d\u6b4c\u66f2\u5143\u4fe1\u606f\u5f3a\u5ea6",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "\u63d0\u524d\u52a0\u8f7d\u63a5\u4e0b\u6765\u6b4c\u66f2\u7684\u5c01\u9762\u4e0e\u5143\u4fe1\u606f\uff0c\u51cf\u5c11\u5207\u6b4c\u65f6\u7684\u5c01\u9762\u95ea\u70c1\u3002\u5f3a\u5ea6\u8d8a\u9ad8\uff0c\u5360\u7528\u7684\u5185\u5b58\u4e0e\u540e\u53f0\u52a0\u8f7d\u8d8a\u591a\u3002",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = "\u5f53\u524d\uff1a$selectedCount \u9996",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { count ->
                val selected = count == selectedCount
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .clickable { onSelectedCountChange(count) }
                        .padding(vertical = 9.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                text = "\u4f4e",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\u4e2d",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\u9ad8",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
