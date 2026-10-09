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
import ink.tenqui.flowtone.data.online.packageformat.ExtensionProviderVisualResolver
import ink.tenqui.flowtone.data.online.packageformat.InstalledExtension
import ink.tenqui.flowtone.data.search.SearchProviderVisual
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = SettingsRowSubtitleTopPadding)
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
