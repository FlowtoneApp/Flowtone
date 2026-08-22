package ink.tenqui.flowtone.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.app.AppPreferences
import ink.tenqui.flowtone.app.FlowtonePageEasing
import ink.tenqui.flowtone.app.TopLevelPage
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.PageTransitionHost
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.ThemeModeSelector
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.theme.AppThemeMode
import ink.tenqui.flowtone.ui.player.lyrics.LyricsBackgroundStyle
import ink.tenqui.flowtone.viewmodel.MusicViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun SettingsScreen(
    appPreferences: AppPreferences,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    disablePausedArtworkTilt: Boolean,
    onDisablePausedArtworkTiltChange: (Boolean) -> Unit,
    strictProgressBar: Boolean,
    onStrictProgressBarChange: (Boolean) -> Unit,
    allowScreenOffOnLyricsPage: Boolean,
    onAllowScreenOffOnLyricsPageChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onBackActionChange: ((() -> Unit)?) -> Unit,
    onPathSegmentsChange: (List<String>) -> Unit,
    hideSecondaryBackButton: Boolean,
    onHideSecondaryBackButtonChange: (Boolean) -> Unit,
    resumePlaybackAfterCall: Boolean,
    onResumePlaybackAfterCallChange: (Boolean) -> Unit,
    allowFullscreenFromCollapsed: Boolean,
    onAllowFullscreenFromCollapsedChange: (Boolean) -> Unit,
    pageScope: PageTransitionScope,
    openExpandedMiniPlayerOnMediaClick: Boolean,
    onOpenExpandedMiniPlayerOnMediaClickChange: (Boolean) -> Unit,
    preloadSongMetadataCount: Int,
    onPreloadSongMetadataCountChange: (Int) -> Unit,
    preloadLyricsCount: Int,
    onPreloadLyricsCountChange: (Int) -> Unit,
    songRecordThresholdSeconds: Int,
    onOpenSongRecordThresholdDialog: () -> Unit,
    flowCloudSpeed: Float,
    onOpenFlowCloudSpeedDialog: () -> Unit,
    darkFlowCloudOverlayEnabled: Boolean,
    onDarkFlowCloudOverlayChange: (Boolean) -> Unit,
    lyricsBackgroundStyle: LyricsBackgroundStyle,
    onLyricsBackgroundStyleChange: (LyricsBackgroundStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by rememberSaveable {
        mutableStateOf<SettingsSection?>(null)
    }
    var showingOnlineSettings by rememberSaveable { mutableStateOf(false) }
    var showingLyricsSettings by rememberSaveable { mutableStateOf(false) }
    var managingLyricsFolders by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val extensionManager = remember(context) { ExtensionManager.get(context) }
    val extensionScope = rememberCoroutineScope()
    var installedExtensions by remember { mutableStateOf<List<InstalledExtension>>(emptyList()) }
    fun refreshExtensions() {
        installedExtensions = extensionManager.installedExtensions()
    }
    val extensionPackageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        extensionScope.launch {
            val result = runCatching { extensionManager.install(uri) }
            refreshExtensions()
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { "扩展 ${it.manifest.name} 已安装" },
                    onFailure = { it.message ?: "扩展安装失败" }
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val musicViewModel: MusicViewModel = viewModel()
    val lyricsFolders by musicViewModel.lyricsFolders.collectAsState()
    val lyricsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        val saved = runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            musicViewModel.addLyricsFolder(treeUri)
        }.getOrElse { false }
        Toast.makeText(
            context,
            if (saved) "已添加歌词文件夹" else "该文件夹已添加或无法授权",
            Toast.LENGTH_SHORT
        ).show()
    }
    var selectedStartPage by rememberSaveable {
        mutableStateOf(appPreferences.getDefaultStartPage())
    }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnBackActionChange by rememberUpdatedState(onBackActionChange)
    val currentOnPathSegmentsChange by rememberUpdatedState(onPathSegmentsChange)
    LaunchedEffect(managingLyricsFolders) {
        if (managingLyricsFolders) musicViewModel.refreshLyricsFolders()
    }
    LaunchedEffect(showingOnlineSettings) {
        if (showingOnlineSettings) refreshExtensions()
    }
    val handleBack = remember(
        selectedSection,
        showingOnlineSettings,
        showingLyricsSettings,
        managingLyricsFolders
    ) {
        {
            if (managingLyricsFolders) {
                managingLyricsFolders = false
            } else if (showingLyricsSettings) {
                showingLyricsSettings = false
            } else if (showingOnlineSettings) {
                showingOnlineSettings = false
            } else if (selectedSection == null) {
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
        currentOnPathSegmentsChange(
            selectedSection?.let { section ->
                buildList {
                    add(section.title)
                    if (section == SettingsSection.General && showingOnlineSettings) {
                        add("在线")
                    }
                    if (
                        section == SettingsSection.General &&
                        (showingLyricsSettings || managingLyricsFolders)
                    ) {
                        add("歌词")
                    }
                    if (managingLyricsFolders) {
                        add("歌词文件夹")
                    }
                }
            } ?: emptyList()
        )
    }
    DisposableEffect(Unit) {
        onDispose { currentOnPathSegmentsChange(emptyList()) }
    }
    BackHandler(onBack = handleBack)

    PageTransitionHost(
        targetState = SettingsPageState(
            section = selectedSection,
            showingOnlineSettings = showingOnlineSettings,
            showingLyricsSettings = showingLyricsSettings,
            managingLyricsFolders = managingLyricsFolders
        ),
        parentScope = pageScope,
        modifier = modifier
            .fillMaxSize()
            .rightSwipeBackGesture(handleBack)
    ) { state ->
        val localScope = this
        val elementCount = when {
            state.section == null -> 5
            state.section == SettingsSection.Appearance -> 3
            state.section == SettingsSection.General && state.managingLyricsFolders -> 1
            state.section == SettingsSection.General && state.showingLyricsSettings -> 1
            state.section == SettingsSection.General && state.showingOnlineSettings ->
                (installedExtensions.size + 1).coerceAtLeast(1)
            state.section == SettingsSection.General -> 3
            else -> 1
        }
        fun viewElementModifier(index: Int): Modifier {
            return localScope.elementModifier(index, elementCount)
        }

        when (state.section) {
            null -> SettingsSectionList(
                onSectionClick = { sectionToOpen ->
                    selectedSection = sectionToOpen
                    showingOnlineSettings = false
                    showingLyricsSettings = false
                    managingLyricsFolders = false
                },
                elementModifier = ::viewElementModifier
            )

            SettingsSection.Appearance -> AppearanceSettingsPage(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    lyricsBackgroundStyle = lyricsBackgroundStyle,
                    onLyricsBackgroundStyleChange = onLyricsBackgroundStyleChange,
                    disablePausedArtworkTilt = disablePausedArtworkTilt,
                    onDisablePausedArtworkTiltChange = onDisablePausedArtworkTiltChange,
                    strictProgressBar = strictProgressBar,
                    onStrictProgressBarChange = onStrictProgressBarChange,
                    flowCloudSpeed = flowCloudSpeed,
                    onOpenFlowCloudSpeedDialog = onOpenFlowCloudSpeedDialog,
                    darkFlowCloudOverlayEnabled = darkFlowCloudOverlayEnabled,
                    onDarkFlowCloudOverlayChange = onDarkFlowCloudOverlayChange,
                elementModifier = ::viewElementModifier
                )

            SettingsSection.Playback -> PlaybackSettingsPage(
                resumePlaybackAfterCall = resumePlaybackAfterCall,
                onResumePlaybackAfterCallChange = onResumePlaybackAfterCallChange,
                elementModifier = ::viewElementModifier
            )

            SettingsSection.Record -> RecordSettingsPage(
                songRecordThresholdSeconds = songRecordThresholdSeconds,
                onOpenSongRecordThresholdDialog = onOpenSongRecordThresholdDialog,
                elementModifier = ::viewElementModifier
            )

            SettingsSection.Advanced -> AdvancedSettingsPage(
                preloadSongMetadataCount = preloadSongMetadataCount,
                onPreloadSongMetadataCountChange = onPreloadSongMetadataCountChange,
                preloadLyricsCount = preloadLyricsCount,
                onPreloadLyricsCountChange = onPreloadLyricsCountChange,
                elementModifier = ::viewElementModifier
            )

            SettingsSection.General -> when {
                state.managingLyricsFolders -> LyricsFolderSettingsPage(
                    folders = lyricsFolders,
                    onAddFolder = { lyricsFolderLauncher.launch(null) },
                    onRemoveFolder = { folder -> musicViewModel.removeLyricsFolder(folder.uri) },
                elementModifier = ::viewElementModifier
                )

                state.showingLyricsSettings -> LyricsSettingsPage(
                    allowScreenOffOnLyricsPage = allowScreenOffOnLyricsPage,
                    onAllowScreenOffOnLyricsPageChange =
                        onAllowScreenOffOnLyricsPageChange,
                    lyricsFolders = lyricsFolders,
                    onOpenLyricsFolders = { managingLyricsFolders = true },
                elementModifier = ::viewElementModifier
                )

                state.showingOnlineSettings -> OnlineSettingsPage(
                    installedExtensions = installedExtensions,
                    onInstall = {
                        extensionPackageLauncher.launch(FlowtoneExtensionMimeTypes)
                    },
                    onUninstall = { id ->
                        extensionScope.launch {
                            extensionManager.uninstall(id)
                            refreshExtensions()
                            Toast.makeText(context, "扩展已删除", Toast.LENGTH_SHORT).show()
                        }
                    },
                    elementModifier = ::viewElementModifier
                )
                else -> GeneralSettingsPage(
                selectedStartPage = selectedStartPage,
                onStartPageSelected = { page ->
                    selectedStartPage = page
                    appPreferences.setDefaultStartPage(page)
                },
                hideSecondaryBackButton = hideSecondaryBackButton,
                onHideSecondaryBackButtonChange = onHideSecondaryBackButtonChange,
                allowFullscreenFromCollapsed = allowFullscreenFromCollapsed,
                onAllowFullscreenFromCollapsedChange = onAllowFullscreenFromCollapsedChange,
                openExpandedMiniPlayerOnMediaClick = openExpandedMiniPlayerOnMediaClick,
                onOpenExpandedMiniPlayerOnMediaClickChange =
                    onOpenExpandedMiniPlayerOnMediaClickChange,
                onOpenOnlineSettings = { showingOnlineSettings = true },
                onOpenLyricsSettings = { showingLyricsSettings = true },
                elementModifier = ::viewElementModifier
                )
        }
    }
}
}

// Android DocumentsProvider 往往把自定义 .flowtone 包标成 ZIP 或通用二进制流，
// 因而不能只请求自定义 MIME；实际后缀与包内容仍由 Host 安装器校验。
private val FlowtoneExtensionMimeTypes = arrayOf(
    "application/x-flowtone",
    "application/zip",
    "application/octet-stream"
)

private data class SettingsPageState(
    val section: SettingsSection?,
    val showingOnlineSettings: Boolean,
    val showingLyricsSettings: Boolean,
    val managingLyricsFolders: Boolean
)

