package com.yapyap.feature.call.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.yapyap.core.theme.YapCallGreen
import com.yapyap.core.ui.YapInsets
import com.yapyap.feature.call.domain.entity.CallType
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.call_ongoing_video
import yapyap.shared.generated.resources.call_ongoing_voice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YapCallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: androidx.compose.foundation.layout.WindowInsets = YapInsets.topAppBarNested,
) {
    val chrome = LocalOngoingCallChrome.current
    val isCallBar = chrome.showGreenAppBar
    val barTitle = if (isCallBar) {
        when (chrome.callType) {
            CallType.Video -> stringResource(Res.string.call_ongoing_video)
            CallType.Voice, null -> stringResource(Res.string.call_ongoing_voice)
        }
    } else {
        title
    }
    val interactionSource = remember { MutableInteractionSource() }
    TopAppBar(
        modifier = modifier.then(
            if (isCallBar) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White.copy(alpha = 0.2f)),
                    role = Role.Button,
                    onClick = chrome.onReturnToCall,
                )
            } else {
                Modifier
            },
        ),
        title = {
            Text(
                text = barTitle,
                color = if (isCallBar) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = if (isCallBar) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = YapCallGreen,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
    )
}
