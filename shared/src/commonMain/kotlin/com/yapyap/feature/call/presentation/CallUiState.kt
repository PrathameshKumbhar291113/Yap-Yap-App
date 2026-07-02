package com.yapyap.feature.call.presentation

import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack

data class CallUiState(
    val callState: CallState = CallState.Idle,
    val remoteDisplayName: String = "",
    val statusMessage: String = "",
    val callType: CallType? = null,
    val isMuted: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val audioRoute: CallAudioRoute = CallAudioRoute.Earpiece,
    val connectionState: CallMediaConnectionState = CallMediaConnectionState.Idle,
    val elapsedSeconds: Long? = null,
    val localVideoTrack: CallRenderableVideoTrack? = null,
    val remoteVideoTrack: CallRenderableVideoTrack? = null,
    val remoteParticipantConnected: Boolean = false,
    val actionError: String? = null,
    val isCallScreenExpanded: Boolean = false,
    val pendingPermissionStatus: com.yapyap.core.call.CallPermissionStatus? = null,
    val pendingPermissionCallType: CallType? = null,
    val isAcceptInProgress: Boolean = false,
) {
    val isActiveCall: Boolean = callState is CallState.Outgoing ||
        callState is CallState.Incoming ||
        callState is CallState.Connecting ||
        callState is CallState.Connected ||
        callState is CallState.Reconnecting

    val showTimer: Boolean = callState is CallState.Connected && elapsedSeconds != null

    val isVideoCall: Boolean = callType == CallType.Video

    val showsFullScreenCallOverlay: Boolean = when (callState) {
        CallState.Idle -> false
        is CallState.Incoming,
        is CallState.Outgoing,
        is CallState.Ended,
        is CallState.Failed,
        -> true
        is CallState.Connecting,
        is CallState.Connected,
        is CallState.Reconnecting,
        -> isCallScreenExpanded
    }

    val showsOngoingCallBanner: Boolean = isActiveCall && !showsFullScreenCallOverlay

    val tintsSystemChromeGreen: Boolean = isActiveCall
}
