package ink.tenqui.flowtone.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ink.tenqui.flowtone.BuildConfig
import java.util.Locale

internal const val FullscreenDiagnosticsRoot = "root"

internal fun isFullscreenLayoutDiagnosticsAvailable(): Boolean {
    return BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "benchmark"
}

@Stable
internal class FullscreenLayoutDiagnostics {
    private val nodes = mutableStateMapOf<String, FullscreenLayoutDiagnosticNode>()

    fun record(
        key: String,
        coordinates: LayoutCoordinates,
        transform: FullscreenLayerTransform = FullscreenLayerTransform()
    ) {
        if (!isFullscreenLayoutDiagnosticsAvailable()) return
        nodes[key] = FullscreenLayoutDiagnosticNode(
            layoutBounds = coordinates.boundsInRoot(),
            transform = transform
        )
    }

    fun nodeOf(key: String): FullscreenLayoutDiagnosticNode? = nodes[key]
}

internal data class FullscreenLayoutDiagnosticNode(
    val layoutBounds: Rect,
    val transform: FullscreenLayerTransform
)

internal data class FullscreenLayerTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationZ: Float = 0f
) {
    val isPureTranslation: Boolean
        get() = scaleX == 1f && scaleY == 1f && rotationZ == 0f
}

@Composable
internal fun FullscreenLayoutDiagnosticsOverlay(
    diagnostics: FullscreenLayoutDiagnostics,
    animationProgress: Float,
    fullscreenProgress: Float,
    layoutScale: Float,
    artworkScale: Float,
    artworkRotationZ: Float,
    artworkTranslationX: Float = 0f,
    artworkTranslationY: Float = 0f,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    val navigationBottomPx = WindowInsets.navigationBars.getBottom(density)
    val cutoutTopPx = WindowInsets.displayCutout.getTop(density)
    val cutoutBottomPx = WindowInsets.displayCutout.getBottom(density)
    val safeTopPx = WindowInsets.safeDrawing.getTop(density)
    val safeBottomPx = WindowInsets.safeDrawing.getBottom(density)
    val report = fullscreenDiagnosticsReport(
        diagnostics = diagnostics,
        windowWidthPx = view.width,
        windowHeightPx = view.height,
        windowWidthDp = view.width / density.density,
        windowHeightDp = view.height / density.density,
        density = density.density,
        densityDpi = configuration.densityDpi,
        fontScale = density.fontScale,
        orientation = configuration.orientation,
        statusTopPx = statusTopPx,
        navigationBottomPx = navigationBottomPx,
        cutoutTopPx = cutoutTopPx,
        cutoutBottomPx = cutoutBottomPx,
        safeTopPx = safeTopPx,
        safeBottomPx = safeBottomPx,
        animationProgress = animationProgress,
        fullscreenProgress = fullscreenProgress,
        layoutScale = layoutScale,
        artworkScale = artworkScale,
        artworkRotationZ = artworkRotationZ,
        artworkTranslationX = artworkTranslationX,
        artworkTranslationY = artworkTranslationY
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.TopStart
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.82f),
            contentColor = Color.White,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Button(
                    onClick = { clipboard.setText(AnnotatedString(report)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制诊断信息")
                }
                Text(
                    text = report,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun fullscreenDiagnosticsReport(
    diagnostics: FullscreenLayoutDiagnostics,
    windowWidthPx: Int,
    windowHeightPx: Int,
    windowWidthDp: Float,
    windowHeightDp: Float,
    density: Float,
    densityDpi: Int,
    fontScale: Float,
    orientation: Int,
    statusTopPx: Int,
    navigationBottomPx: Int,
    cutoutTopPx: Int,
    cutoutBottomPx: Int,
    safeTopPx: Int,
    safeBottomPx: Int,
    animationProgress: Float,
    fullscreenProgress: Float,
    layoutScale: Float,
    artworkScale: Float,
    artworkRotationZ: Float,
    artworkTranslationX: Float,
    artworkTranslationY: Float
): String = buildString {
    fun number(value: Float) = String.format(Locale.US, "%.1f", value)
    fun pxDp(prefix: String, px: Int) {
        appendLine("$prefix.px=$px")
        appendLine("$prefix.dp=${number(px / density)}")
    }
    fun node(key: String) {
        val root = diagnostics.nodeOf(FullscreenDiagnosticsRoot)?.layoutBounds
        val value = diagnostics.nodeOf(key)
        if (root == null || value == null) {
            appendLine("$key.layoutBounds=unavailable")
            return
        }
        val layout = value.layoutBounds
        val transform = value.transform
        val left = layout.left - root.left
        val top = layout.top - root.top
        val right = layout.right - root.left
        val bottom = layout.bottom - root.top
        appendLine("$key.layoutLeftPx=${number(left)}")
        appendLine("$key.layoutTopPx=${number(top)}")
        appendLine("$key.layoutRightPx=${number(right)}")
        appendLine("$key.layoutBottomPx=${number(bottom)}")
        appendLine("$key.layoutWidthPx=${number(right - left)}")
        appendLine("$key.layoutHeightPx=${number(bottom - top)}")
        appendLine("$key.layoutLeftDp=${number(left / density)}")
        appendLine("$key.layoutTopDp=${number(top / density)}")
        appendLine("$key.layoutRightDp=${number(right / density)}")
        appendLine("$key.layoutBottomDp=${number(bottom / density)}")
        appendLine("$key.layoutWidthDp=${number((right - left) / density)}")
        appendLine("$key.layoutHeightDp=${number((bottom - top) / density)}")
        appendLine("$key.translationXPx=${number(transform.translationX)}")
        appendLine("$key.translationYPx=${number(transform.translationY)}")
        appendLine("$key.translationXDp=${number(transform.translationX / density)}")
        appendLine("$key.translationYDp=${number(transform.translationY / density)}")
        appendLine("$key.scaleX=${number(transform.scaleX)}")
        appendLine("$key.scaleY=${number(transform.scaleY)}")
        appendLine("$key.rotationZ=${number(transform.rotationZ)}")
        if (transform.isPureTranslation) {
            appendLine("$key.visualLeftPx=${number(left + transform.translationX)}")
            appendLine("$key.visualTopPx=${number(top + transform.translationY)}")
            appendLine("$key.visualRightPx=${number(right + transform.translationX)}")
            appendLine("$key.visualBottomPx=${number(bottom + transform.translationY)}")
            appendLine("$key.visualLeftDp=${number((left + transform.translationX) / density)}")
            appendLine("$key.visualTopDp=${number((top + transform.translationY) / density)}")
            appendLine("$key.visualRightDp=${number((right + transform.translationX) / density)}")
            appendLine("$key.visualBottomDp=${number((bottom + transform.translationY) / density)}")
        }
    }

    appendLine("device.manufacturer=${Build.MANUFACTURER}")
    appendLine("device.model=${Build.MODEL}")
    appendLine("android.version=${Build.VERSION.RELEASE}")
    appendLine("android.sdkInt=${Build.VERSION.SDK_INT}")
    appendLine("window.widthPx=$windowWidthPx")
    appendLine("window.heightPx=$windowHeightPx")
    appendLine("window.widthDp=${number(windowWidthDp)}")
    appendLine("window.heightDp=${number(windowHeightDp)}")
    appendLine("density=${number(density)}")
    appendLine("densityDpi=$densityDpi")
    appendLine("fontScale=${number(fontScale)}")
    appendLine("orientation=${if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"}")
    pxDp("insets.statusTop", statusTopPx)
    pxDp("insets.navigationBottom", navigationBottomPx)
    pxDp("insets.cutoutTop", cutoutTopPx)
    pxDp("insets.cutoutBottom", cutoutBottomPx)
    pxDp("insets.safeDrawingTop", safeTopPx)
    pxDp("insets.safeDrawingBottom", safeBottomPx)
    listOf(
        FullscreenDiagnosticsRoot,
        "collapseArrow",
        "artworkOuter",
        "artworkImage",
        "title",
        "artist",
        "actions",
        "progress",
        "timeLabels",
        "controls"
    ).forEach(::node)
    appendLine("miniPlayerExpandedProgress=${number(animationProgress)}")
    appendLine("fullscreenProgress=${number(fullscreenProgress)}")
    appendLine("fullscreen.referenceWidthDp=${number(FullscreenPlayerReferenceWidthDp)}")
    appendLine("fullscreen.layoutScale=${number(layoutScale)}")
    appendLine("fullscreen.contentDensity=${number(density * layoutScale)}")
    appendLine("artworkScaleX=${number(artworkScale)}")
    appendLine("artworkScaleY=${number(artworkScale)}")
    appendLine("artworkRotationZ=${number(artworkRotationZ)}")
    appendLine("artworkTranslationX=${number(artworkTranslationX)}")
    appendLine("artworkTranslationY=${number(artworkTranslationY)}")
}
