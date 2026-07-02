package ink.tenqui.flowtone.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ink.tenqui.flowtone.ui.components.OptionGroup
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.staggeredPageElementModifier

private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val GPL_LICENSE_URL = "https://opensource.org/license/gpl-3-0"

internal enum class LicenseType(
    val displayName: String,
    val summary: String,
    val fullTextUrl: String
) {
    Apache20(
        displayName = "Apache License 2.0",
        summary = "允许使用、修改和分发软件，并保留版权与许可证声明。该许可证同时提供明确的专利授权。",
        fullTextUrl = APACHE_LICENSE_URL
    ),
    Gpl30(
        displayName = "GNU General Public License v3.0",
        summary = "允许使用、研究、修改和分发软件；分发修改版本时，需要继续以 GPLv3 提供对应源代码。",
        fullTextUrl = GPL_LICENSE_URL
    )
}

internal data class OpenSourceComponent(
    val id: String,
    val name: String,
    val description: String,
    val projectUrl: String,
    val category: ComponentCategory,
    val licenseType: LicenseType
)

internal enum class ComponentCategory(val title: String) {
    Kotlin("Kotlin 组件"),
    Android("Android 原生组件"),
    Media("媒体播放组件"),
    Flowtone("Flowtone 项目")
}

internal val openSourceComponents = listOf(
    OpenSourceComponent(
        id = "kotlin",
        name = "Kotlin",
        description = "Flowtone 使用的主要编程语言。",
        projectUrl = "https://kotlinlang.org/",
        category = ComponentCategory.Kotlin,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "compose",
        name = "Jetpack Compose",
        description = "用于构建 Flowtone 的声明式用户界面。",
        projectUrl = "https://developer.android.com/compose",
        category = ComponentCategory.Android,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "material3",
        name = "Material Design 3",
        description = "提供界面组件、主题和 Material 设计规范。",
        projectUrl = "https://m3.material.io/",
        category = ComponentCategory.Android,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "androidx-core",
        name = "AndroidX Core",
        description = "提供兼容性 API 与 Android 平台基础扩展。",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/core",
        category = ComponentCategory.Android,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "androidx-lifecycle",
        name = "AndroidX Lifecycle",
        description = "管理界面生命周期、ViewModel 与可观察状态。",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        category = ComponentCategory.Android,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "media3-exoplayer",
        name = "AndroidX Media3 ExoPlayer",
        description = "负责本地音频的解码与播放。",
        projectUrl = "https://developer.android.com/media/media3/exoplayer",
        category = ComponentCategory.Media,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "media3-session",
        name = "AndroidX Media3 Session",
        description = "连接播放器、系统媒体控件与后台播放服务。",
        projectUrl = "https://developer.android.com/media/media3/session",
        category = ComponentCategory.Media,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "coil",
        name = "Coil",
        description = "加载并显示歌曲与专辑封面。",
        projectUrl = "https://github.com/coil-kt/coil",
        category = ComponentCategory.Kotlin,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "androidx-palette",
        name = "AndroidX Palette",
        description = "从封面图像中提取适合界面使用的颜色。",
        projectUrl = "https://developer.android.com/jetpack/androidx/releases/palette",
        category = ComponentCategory.Android,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "material-components",
        name = "Material Components for Android",
        description = "提供 Android Material 组件及相关资源。",
        projectUrl = "https://github.com/material-components/material-components-android",
        category = ComponentCategory.Android,
        licenseType = LicenseType.Apache20
    ),
    OpenSourceComponent(
        id = "flowtone",
        name = "Flowtone",
        description = "本应用自身的源代码与发行项目。",
        projectUrl = "https://github.com/FlowtoneApp/Flowtone",
        category = ComponentCategory.Flowtone,
        licenseType = LicenseType.Gpl30
    )
)
