package com.yapyap.feature.call.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.yapyap.core.di.koinViewModel
import com.yapyap.feature.call.domain.entity.CallState

@Composable
fun CallOverlayHost(
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    CallActiveChromeEffect(isActive = uiState.tintsSystemChromeGreen)

    if (!uiState.showsFullScreenCallOverlay) return

    val overlayModifier = modifier.fillMaxSize()

    val canMinimize = uiState.callState is CallState.Connecting ||
        uiState.callState is CallState.Connected ||
        uiState.callState is CallState.Reconnecting

    CallBackHandler(enabled = canMinimize) {
        viewModel.minimizeCallScreen()
    }

    when (val callState = uiState.callState) {
        is CallState.Incoming -> {
            IncomingCallOverlay(
                uiState = uiState,
                onAccept = viewModel::answerIncoming,
                onDecline = viewModel::rejectIncoming,
                modifier = overlayModifier,
            )
        }
        is CallState.Outgoing -> {
            if (uiState.isVideoCall) {
                ActiveCallScreen(
                    uiState = uiState,
                    onMinimize = null,
                    controls = {
                        CallControlsBar(
                            uiState = uiState,
                            onToggleMute = viewModel::toggleMute,
                            onToggleCamera = viewModel::toggleCamera,
                            onSwitchCamera = viewModel::switchCamera,
                            onToggleSpeaker = viewModel::toggleSpeaker,
                            onEndCall = viewModel::endCall,
                        )
                    },
                    modifier = overlayModifier,
                )
            } else {
                OutgoingCallOverlay(
                    uiState = uiState,
                    onCancel = viewModel::endCall,
                    modifier = overlayModifier,
                )
            }
        }
        is CallState.Connecting,
        is CallState.Connected,
        is CallState.Reconnecting,
        -> {
            ActiveCallScreen(
                uiState = uiState,
                onMinimize = viewModel::minimizeCallScreen,
                controls = {
                    CallControlsBar(
                        uiState = uiState,
                        onToggleMute = viewModel::toggleMute,
                        onToggleCamera = viewModel::toggleCamera,
                        onSwitchCamera = viewModel::switchCamera,
                        onToggleSpeaker = viewModel::toggleSpeaker,
                        onEndCall = viewModel::endCall,
                    )
                },
                modifier = overlayModifier,
            )
        }
        is CallState.Ended -> {
            ActiveCallScreen(
                uiState = uiState,
                onMinimize = null,
                controls = {
                    CallEndedControlsBar(onDismiss = viewModel::dismissOverlay)
                },
                modifier = overlayModifier,
            )
        }
        is CallState.Failed -> {
            CallFailedOverlay(
                uiState = uiState,
                onEndCall = viewModel::endCall,
                onDismiss = viewModel::dismissOverlay,
                modifier = overlayModifier,
            )
        }
        CallState.Idle -> Unit
    }
}
