package com.yapyap.feature.call.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yapyap.core.config.AppBuildConfig
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState

@Composable
fun CallDiagnosticsOverlay(
    uiState: CallUiState,
    modifier: Modifier = Modifier,
) {
    if (!AppBuildConfig.IS_DEBUG) return

    val roomConnected = uiState.connectionState == CallMediaConnectionState.Connected
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(8.dp),
    ) {
        Text("Room Connected: ${if (roomConnected) "Yes" else "No"}", style = MaterialTheme.typography.labelSmall, color = Color.White)
        Text(
            "Participants: ${if (uiState.remoteParticipantConnected) "2" else "1"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Text(
            "Camera: ${if (uiState.isCameraEnabled) "Active" else "Off"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Text(
            "Mic: ${if (uiState.isMuted) "Muted" else "Active"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Text(
            "Video Published: ${if (uiState.localVideoTrack != null) "Yes" else "No"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Text(
            "Video Subscribed: ${if (uiState.remoteVideoTrack != null) "Yes" else "No"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Text(
            "Renderer Attached: ${if (uiState.localVideoTrack != null || uiState.remoteVideoTrack != null) "Yes" else "No"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Text(
            "Frames Received: ${if (uiState.remoteVideoTrack != null) "Yes" else "No"}",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
