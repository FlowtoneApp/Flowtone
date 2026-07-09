package ink.tenqui.flowtone.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.delay

@Composable
internal fun FlowtoneTopBar(
    selectedTopLevelPage: TopLevelPage,
    pagerState: PagerState,
    secondaryPage: SecondaryPage?,
    additionalPathSegments: List<String>,
    backgroundAlpha: Float,
    titleVisible: Boolean,
    hideBackButton: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    searchColors: TopLevelSearchColors,
    searchFocusRequest: Int,
    searchKeyboardDismissRequest: Int,
    searchReentryProgress: Float,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onExitSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onSearchFocusRequestConsumed: () -> Unit,
    onSearchKeyboardDismissRequestConsumed: () -> Unit,
    onSearchInputFocusChange: (Boolean) -> Unit,
    onSearchImeAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pathSegments = when (secondaryPage) {
        SecondaryPage.Settings -> listOf(SecondaryPage.Settings.title) + additionalPathSegments
        SecondaryPage.About -> listOf(SecondaryPage.About.title)
        SecondaryPage.LocalLibrary -> listOf(SecondaryPage.LocalLibrary.title)
        SecondaryPage.Playlist -> additionalPathSegments.ifEmpty {
            listOf(SecondaryPage.Playlist.title)
        }
        SecondaryPage.Artist -> listOf(SecondaryPage.Artist.title) + additionalPathSegments
        SecondaryPage.ListeningRecords -> listOf(SecondaryPage.ListeningRecords.title)
        SecondaryPage.OpenSource -> listOf(
            SecondaryPage.About.title,
            SecondaryPage.OpenSource.title
        ) + additionalPathSegments
        null -> emptyList()
    }
    val showBackButton = secondaryPage != null && !hideBackButton && !searchActive
    val backButtonProgress by animateFloatAsState(
        targetValue = if (showBackButton) 1f else 0f,
        animationSpec = tween(280, easing = FlowtonePageEasing),
        label = "SecondaryBackButtonProgress"
    )
    val searchProgress by animateFloatAsState(
        targetValue = if (searchActive) 1f else 0f,
        animationSpec = tween(360, easing = FlowtonePageEasing),
        label = "GlobalSearchTopBarProgress"
    )
    val titleVisibilityAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(160, easing = FlowtonePageEasing),
        label = "TopBarTitleVisibilityAlpha"
    )
    val density = LocalDensity.current
    val navigationShiftPx = with(density) { 40.dp.toPx() } * backButtonProgress
    val searchAvailable = secondaryPage == null || searchActive
    val titleEndPadding = if (searchAvailable) 84.dp else 24.dp
    val titleExitDistancePx = with(density) { 12.dp.toPx() }
    val searchReentryLayerProgress = searchReentryProgress.coerceIn(0f, 1f)
    val searchReentryTranslationY = with(density) {
        -56.dp.toPx() * (1f - searchReentryLayerProgress)
    }
    val topBarBackgroundAlpha = backgroundAlpha
    val topBarBaseBackground = if (searchActive) {
        MaterialTheme.colorScheme.background.copy(alpha = 0.97f)
    } else {
        Color.Transparent
    }
    val rootTopBarBackgroundAlpha = if (searchActive) 0f else topBarBackgroundAlpha

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(topBarBaseBackground)
            .background(
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = rootTopBarBackgroundAlpha)
            )
            .statusBarsPadding()
            .height(56.dp)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = showBackButton,
            enter = fadeIn(tween(180, easing = FlowtonePageEasing)) +
                slideInHorizontally(tween(260, easing = FlowtonePageEasing)) { -it * 2 },
            exit = fadeOut(tween(140, easing = FlowtonePageEasing)) +
                slideOutHorizontally(tween(260, easing = FlowtonePageEasing)) { -it * 2 },
            modifier = Modifier.offset(x = (-8).dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "\u8fd4\u56de",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = titleEndPadding)
                .clipToBounds()
                .graphicsLayer {
                    alpha = (1f - searchProgress) * titleVisibilityAlpha
                    translationX = -titleExitDistancePx * searchProgress
                    translationY = 0f
                }
        ) {
            FlowtonePathTitle(
                pagerState = pagerState,
                rootPage = selectedTopLevelPage,
                segments = pathSegments,
                navigationShiftPx = navigationShiftPx,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (searchAvailable && searchActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = searchReentryLayerProgress
                        translationY = searchReentryTranslationY
                    }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer.copy(
                            alpha = topBarBackgroundAlpha
                        )
                    )
            ) {
                GlobalSearchTopBarControl(
                    progress = searchProgress,
                    active = true,
                    query = searchQuery,
                    colors = searchColors,
                    focusRequest = searchFocusRequest,
                    keyboardDismissRequest = searchKeyboardDismissRequest,
                    onSearchClick = onSearchClick,
                    onQueryChange = onSearchQueryChange,
                    onExitSearch = onExitSearch,
                    onClearSearch = onClearSearch,
                    onFocusRequestConsumed = onSearchFocusRequestConsumed,
                    onKeyboardDismissRequestConsumed = onSearchKeyboardDismissRequestConsumed,
                    onInputFocusChange = onSearchInputFocusChange,
                    onImeAction = onSearchImeAction,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (searchAvailable) {
            GlobalSearchTopBarControl(
                progress = searchProgress,
                active = false,
                query = searchQuery,
                colors = searchColors,
                focusRequest = searchFocusRequest,
                keyboardDismissRequest = searchKeyboardDismissRequest,
                onSearchClick = onSearchClick,
                onQueryChange = onSearchQueryChange,
                onExitSearch = onExitSearch,
                onClearSearch = onClearSearch,
                onFocusRequestConsumed = onSearchFocusRequestConsumed,
                onKeyboardDismissRequestConsumed = onSearchKeyboardDismissRequestConsumed,
                onInputFocusChange = onSearchInputFocusChange,
                onImeAction = onSearchImeAction,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun GlobalSearchTopBarControl(
    progress: Float,
    active: Boolean,
    query: String,
    colors: TopLevelSearchColors,
    focusRequest: Int,
    keyboardDismissRequest: Int,
    onSearchClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onExitSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onFocusRequestConsumed: () -> Unit,
    onKeyboardDismissRequestConsumed: () -> Unit,
    onInputFocusChange: (Boolean) -> Unit,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val noRippleInteractionSource = remember { MutableInteractionSource() }
    val backAlpha = ((progress - 0.18f) / 0.82f).coerceIn(0f, 1f)
    val contentAlpha = ((progress - 0.28f) / 0.72f).coerceIn(0f, 1f)

    LaunchedEffect(focusRequest) {
        if (active && focusRequest > 0) {
            delay(240)
            focusRequester.requestFocus()
            keyboardController?.show()
            onFocusRequestConsumed()
        }
    }

    LaunchedEffect(keyboardDismissRequest) {
        if (active && keyboardDismissRequest > 0) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onKeyboardDismissRequestConsumed()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val fieldMaxWidth = (maxWidth - 76.dp).coerceAtLeast(42.dp)
        val fieldWidth = lerpDp(42.dp, fieldMaxWidth, progress)
        val fieldHeight = lerpDp(42.dp, 52.dp, progress)
        val fieldCorner = lerpDp(17.dp, 26.dp, progress)
        val iconSize = lerpDp(23.dp, 22.dp, progress)
        val searchStartPadding = lerpDp(9.5.dp, 17.dp, progress)

        IconButton(
            onClick = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                onExitSearch()
            },
            enabled = active,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .graphicsLayer {
                    alpha = backAlpha
                }
                .semantics {
                    contentDescription = "\u9000\u51fa\u641c\u7d22"
                }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .width(fieldWidth)
                .height(fieldHeight)
                .clip(RoundedCornerShape(fieldCorner))
                .background(colors.container)
                .clickable(
                    enabled = !active,
                    interactionSource = noRippleInteractionSource,
                    indication = null,
                    onClick = onSearchClick
                )
                .semantics {
                    contentDescription = "\u641c\u7d22"
                }
                .padding(start = searchStartPadding, end = if (active) 6.dp else 9.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(iconSize)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = lerpDp(0.dp, 10.dp, progress))
                    .graphicsLayer {
                        alpha = contentAlpha
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isBlank()) {
                    Text(
                        text = "\u641c\u7d22\u6b4c\u66f2\u6216\u827a\u672f\u5bb6",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    enabled = active,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            onImeAction()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            onInputFocusChange(state.isFocused)
                        }
                )
            }
            if (active && query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onClearSearch()
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            alpha = contentAlpha
                        }
                        .semantics {
                            contentDescription = "\u6e05\u9664\u641c\u7d22\u5185\u5bb9"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = colors.content,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

private fun lerpDp(
    start: androidx.compose.ui.unit.Dp,
    stop: androidx.compose.ui.unit.Dp,
    fraction: Float
): androidx.compose.ui.unit.Dp {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}
