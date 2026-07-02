package com.yapyap.feature.call.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yapyap.core.theme.YapCallControlBackground
import com.yapyap.core.theme.YapCallHangupRed
import com.yapyap.core.theme.YapSpacing
import com.yapyap.core.ui.YapDrawable
import com.yapyap.core.ui.YapIcon
import com.yapyap.feature.call.domain.entity.CallAudioRoute
import org.jetbrains.compose.resources.stringResource
import yapyap.shared.generated.resources.Res
import yapyap.shared.generated.resources.call_camera
import yapyap.shared.generated.resources.call_end
import yapyap.shared.generated.resources.call_flip_camera
import yapyap.shared.generated.resources.call_mute
import yapyap.shared.generated.resources.call_speaker

@Composable
fun CallControlsBar(
    uiState: CallUiState,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YapSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallRoundControl(
                icon = YapDrawable.Mic,
                label = stringResource(Res.string.call_mute),
                active = uiState.isMuted,
                onClick = onToggleMute,
            )
            if (uiState.isVideoCall) {
                CallRoundControl(
                    icon = YapDrawable.Videocam,
                    label = stringResource(Res.string.call_camera),
                    active = !uiState.isCameraEnabled,
                    onClick = onToggleCamera,
                )
                CallRoundControl(
                    icon = YapDrawable.FlipCamera,
                    label = stringResource(Res.string.call_flip_camera),
                    active = false,
                    onClick = onSwitchCamera,
                )
                CallRoundControl(
                    icon = YapDrawable.Speaker,
                    label = stringResource(Res.string.call_speaker),
                    active = uiState.audioRoute == CallAudioRoute.Speaker,
                    onClick = onToggleSpeaker,
                )
            } else {
                CallRoundControl(
                    icon = YapDrawable.Speaker,
                    label = stringResource(Res.string.call_speaker),
                    active = uiState.audioRoute == CallAudioRoute.Speaker,
                    onClick = onToggleSpeaker,
                )
            }
        }
        HangupButton(onClick = onEndCall)
    }
}

@Composable
private fun CallRoundControl(
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else YapCallControlBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 28.dp),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        YapIcon(
            resource = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (active) Color.Black else Color.White,
        )
    }
}

@Composable
fun HangupButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
            .size(72.dp)
            .clip(CircleShape)
            .background(YapCallHangupRed)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 36.dp, color = Color.White),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        YapIcon(
            resource = YapDrawable.Call,
            contentDescription = stringResource(Res.string.call_end),
            modifier = Modifier
                .size(32.dp)
                .rotate(135f),
            tint = Color.White,
        )
    }
}

@Composable
fun CallEndedControlsBar(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        HangupButton(onClick = onDismiss)
    }
}
