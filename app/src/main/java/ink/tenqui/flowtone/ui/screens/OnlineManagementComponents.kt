package ink.tenqui.flowtone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ink.tenqui.flowtone.R
import ink.tenqui.flowtone.data.online.ExtensionManager
import ink.tenqui.flowtone.data.online.capability.SummaryCapability
import ink.tenqui.flowtone.data.online.capability.SummaryCapabilityStatus
import ink.tenqui.flowtone.data.online.packageformat.ExtensionProviderVisualResolver
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.search.SearchProviderVisual
import ink.tenqui.flowtone.ui.components.OptionGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The same installed icon and image loader are used on the list and detail page. */
@Composable
internal fun ExtensionInstalledIcon(installed: InstalledExtension, modifier: Modifier = Modifier) {
    val visual by produceState<SearchProviderVisual?>(initialValue = null, key1 = installed) {
        value = withContext(Dispatchers.IO) { ExtensionProviderVisualResolver.resolve(installed) }
    }
    val iconFile = visual?.iconFile
    val iconColor = visual?.iconColor
    val fallbackTint = MaterialTheme.colorScheme.onSurfaceVariant
    if (iconFile == null) {
        Icon(
            painter = painterResource(R.drawable.ic_deployed_code_update),
            contentDescription = null,
            tint = fallbackTint,
            modifier = modifier
        )
        return
    }
    val context = LocalContext.current
    val imageLoader = remember(context) { ExtensionManager.get(context).extensionImageLoader }
    var imageLoaded by remember(iconFile) { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!imageLoaded) {
            Icon(
                painter = painterResource(R.drawable.ic_deployed_code_update),
                contentDescription = null,
                tint = fallbackTint,
                modifier = Modifier.fillMaxSize()
            )
        }
        AsyncImage(
            model = iconFile,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = iconColor?.let { hex ->
                hex.removePrefix("#").toLongOrNull(16)?.let {
                    ColorFilter.tint(Color((0xFF000000L or it).toInt()))
                }
            },
            onSuccess = { imageLoaded = true },
            onError = { imageLoaded = false },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** A single full-width destination row for online management pages. */
@Composable
internal fun OnlineManagementEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 2,
    leading: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(SettingsRowCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SettingsRowHorizontalPadding,
                vertical = SettingsRowVerticalPadding
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(SettingsRowIconSize), contentAlignment = Alignment.Center) { leading() }
            Column(modifier = Modifier.weight(1f).padding(start = SettingsSectionRowIconGap)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OnlineCapabilitySection(
    capabilities: List<SummaryCapability>,
    modifier: Modifier = Modifier
) {
    OptionGroup(title = "功能支持", modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SettingsRowCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                capabilities.forEachIndexed { index, capability ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(capability.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            when (capability.status) {
                                SummaryCapabilityStatus.Supported -> "支持"
                                SummaryCapabilityStatus.Partial -> "部分支持"
                                SummaryCapabilityStatus.Unsupported -> "不支持"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    if (index != capabilities.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f))
                    }
                }
            }
        }
    }
}
