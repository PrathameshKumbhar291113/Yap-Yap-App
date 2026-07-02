package com.yapyap.feature.call.domain.repository

import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import kotlinx.coroutines.flow.StateFlow

interface CallMediaRepository {
    val isMuted: StateFlow<Boolean>
    val isCameraEnabled: StateFlow<Boolean>
    val audioRoute: StateFlow<CallAudioRoute>
    val connectionState: StateFlow<CallMediaConnectionState>
    val localVideoTrack: StateFlow<CallRenderableVideoTrack?>
    val remoteVideoTrack: StateFlow<CallRenderableVideoTrack?>
    val remoteParticipantConnected: StateFlow<Boolean>

    suspend fun prepareForCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
    ): Result<Unit>

    suspend fun reconnectForCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
        restoreMuted: Boolean,
        restoreCameraEnabled: Boolean,
    ): Result<Unit>

    suspend fun releaseCall(callSessionId: String): Result<Unit>

    suspend fun setMuted(muted: Boolean): Result<Unit>

    suspend fun setCameraEnabled(enabled: Boolean): Result<Unit>

    suspend fun switchCamera(): Result<Unit>

    suspend fun setAudioRoute(route: CallAudioRoute): Result<Unit>
}
