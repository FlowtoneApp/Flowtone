package ink.tenqui.flowtone.ui.screens

import android.text.method.LinkMovementMethod
import android.text.format.Formatter
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import ink.tenqui.flowtone.ui.components.PageTransitionScope
import ink.tenqui.flowtone.ui.components.rightSwipeBackGesture
import ink.tenqui.flowtone.ui.components.settingsOptionPalette
import ink.tenqui.flowtone.viewmodel.AppUpdateViewModel
import ink.tenqui.flowtone.viewmodel.UpdateVersionStatus
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.image.ImagesPlugin
import org.commonmark.node.SoftLineBreak
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun UpdatesScreen(
    onBack: () -> Unit,
    pageScope: PageTransitionScope,
    modifier: Modifier = Modifier,
    updateViewModel: AppUpdateViewModel = viewModel()
) {
    val state by updateViewModel.state.collectAsState()
    val context = LocalContext.current
    val cardColor = settingsOptionPalette().surfaceColor

    LazyColumn(
        modifier = modifier.fillMaxSize().rightSwipeBackGesture(onBack),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "status") {
            Column(
                modifier = pageScope.elementModifier(0, 2)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(cardColor)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = when {
                            state.checking -> "正在检查更新…"
                            state.error != null -> "检查更新失败"
                            else -> when (state.versionStatus) {
                                UpdateVersionStatus.Available -> "发现新版本"
                                UpdateVersionStatus.Latest -> "已是最新版本"
                                UpdateVersionStatus.Ahead -> "当前版本较新"
                                UpdateVersionStatus.Unknown -> "版本信息"
                            }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "当前版本 ${updateViewModel.currentVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.release?.let { release ->
                    Text(
                        (if (state.error != null) "上次获取 ${release.tag}" else "最新发布 ${release.tag}") +
                            if (release.prerelease) " · 预发布" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = updateViewModel::checkForUpdates,
                        enabled = !state.checking
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(if (state.error != null) "重试" else "重新检查")
                    }
                    state.release?.let { release ->
                        if (state.versionStatus == UpdateVersionStatus.Available) {
                            Button(onClick = { context.openExternalUrl(release.url) }) {
                                Text("前往下载")
                            }
                        }
                    }
                }
            }
        }
        state.release?.let { release ->
            item(key = "release") {
                val publishedDate = remember(release.publishedAt) {
                    runCatching {
                        Instant.parse(release.publishedAt).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    }.getOrDefault(release.publishedAt)
                }
                Column(
                    modifier = pageScope.elementModifier(1, 2)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardColor)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        release.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        listOf(release.tag, publishedDate, release.author)
                            .filter(String::isNotBlank).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    ReleaseMarkdown(release.body.ifBlank { "此版本暂无更新说明。" })
                    HorizontalDivider()
                    Text(
                        "安装包",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    release.assets.forEach { asset ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { context.openExternalUrl(asset.url) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    asset.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    Formatter.formatShortFileSize(context, asset.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { context.openExternalUrl(release.url) }) {
                        Text("在 GitHub 查看完整发布")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseMarkdown(markdown: String) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    builder.on(SoftLineBreak::class.java) { visitor, _ -> visitor.forceNewLine() }
                }
            })
            .build()
    }
    val rendered = remember(markwon, markdown) { markwon.toMarkdown(markdown) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
    val spacing = with(LocalDensity.current) { 4.dp.toPx() }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = false
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            view.setLineSpacing(spacing, 1f)
            // 页面动画会重组，但只在正文改变时重新绑定 Span 和图片请求。
            if (view.tag !== rendered) {
                markwon.setParsedMarkdown(view, rendered)
                view.tag = rendered
            }
        }
    )
}
