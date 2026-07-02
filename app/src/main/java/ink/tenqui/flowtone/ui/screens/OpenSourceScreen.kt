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

@Composable
fun OpenSourceScreen(
    onBack: () -> Unit,
    onBackActionChange: ((() -> Unit)?) -> Unit,
    onPathSegmentsChange: (List<String>) -> Unit,
    elementModifier: (Int) -> Modifier,
    modifier: Modifier = Modifier
) {
    var selectedComponentId by rememberSaveable { mutableStateOf<String?>(null) }
    var showingLicense by rememberSaveable { mutableStateOf(false) }
    val selectedComponent = openSourceComponents.firstOrNull {
        it.id == selectedComponentId
    }
    var retainedComponent by remember { mutableStateOf<OpenSourceComponent?>(null) }
    SideEffect {
        if (selectedComponent != null) {
            retainedComponent = selectedComponent
        }
    }
    val displayedComponent = selectedComponent ?: retainedComponent
    val pathSegments = when {
        showingLicense && selectedComponent != null -> listOf(
            selectedComponent.name,
            selectedComponent.licenseType.displayName
        )
        selectedComponent != null -> listOf(selectedComponent.name)
        else -> emptyList()
    }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnBackActionChange by rememberUpdatedState(onBackActionChange)
    val currentOnPathSegmentsChange by rememberUpdatedState(onPathSegmentsChange)
    val handleBack = remember(selectedComponentId, showingLicense) {
        {
            when {
                showingLicense -> showingLicense = false
                selectedComponentId != null -> selectedComponentId = null
                else -> currentOnBack()
            }
        }
    }

    DisposableEffect(handleBack) {
        currentOnBackActionChange(handleBack)
        onDispose { currentOnBackActionChange(null) }
    }
    SideEffect {
        currentOnPathSegmentsChange(pathSegments)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnPathSegmentsChange(emptyList()) }
    }
    BackHandler(onBack = handleBack)

    AnimatedContent(
        targetState = when {
            showingLicense && selectedComponent != null -> OpenSourceView.License
            selectedComponent != null -> OpenSourceView.Detail
            else -> OpenSourceView.List
        },
        transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        label = "OpenSourceContent",
        modifier = modifier
            .fillMaxSize()
            .rightSwipeBackGesture(handleBack)
    ) { view ->
        fun viewElementModifier(index: Int): Modifier {
            return elementModifier(index).then(staggeredPageElementModifier(index))
        }

        when (view) {
            OpenSourceView.List -> ComponentList(
                onComponentClick = { selectedComponentId = it.id },
                elementModifier = ::viewElementModifier
            )

            OpenSourceView.Detail -> displayedComponent?.let { component ->
                ComponentDetail(
                    component = component,
                    onShowLicense = { showingLicense = true },
                    elementModifier = ::viewElementModifier
                )
            }

            OpenSourceView.License -> displayedComponent?.let { component ->
                LicenseDescription(
                    licenseType = component.licenseType,
                    elementModifier = ::viewElementModifier
                )
            }
        }
    }
}

