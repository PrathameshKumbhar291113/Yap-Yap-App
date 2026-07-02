package com.yapyap.feature.call.data.repository

import com.yapyap.core.call.CallMediaController
import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import com.yapyap.feature.call.domain.repository.CallMediaRepository
import kotlinx.coroutines.flow.StateFlow

class CallMediaRepositoryImpl(
    private val mediaController: CallMediaController,
) : CallMediaRepository {
    override val isMuted: StateFlow<Boolean> = mediaController.isMuted
    override val isCameraEnabled: StateFlow<Boolean> = mediaController.isCameraEnabled
    override val audioRoute: StateFlow<CallAudioRoute> = mediaController.audioRoute
    override val connectionState: StateFlow<CallMediaConnectionState> = mediaController.connectionState
    override val localVideoTrack: StateFlow<CallRenderableVideoTrack?> = mediaController.localVideoTrack
    override val remoteVideoTrack: StateFlow<CallRenderableVideoTrack?> = mediaController.remoteVideoTrack
    override val remoteParticipantConnected: StateFlow<Boolean> = mediaController.remoteParticipantConnected

    override suspend fun prepareForCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
    ): Result<Unit> = mediaController.prepareCall(
        callSessionId = callSessionId,
        videoEnabled = videoEnabled,
        credentials = credentials,
    )

    override suspend fun reconnectForCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
        restoreMuted: Boolean,
        restoreCameraEnabled: Boolean,
    ): Result<Unit> = mediaController.reconnectCall(
        callSessionId = callSessionId,
        videoEnabled = videoEnabled,
        credentials = credentials,
        restoreMuted = restoreMuted,
        restoreCameraEnabled = restoreCameraEnabled,
    )

    override suspend fun releaseCall(callSessionId: String): Result<Unit> =
        mediaController.releaseCall(callSessionId)

    override suspend fun setMuted(muted: Boolean): Result<Unit> =
        mediaController.setMuted(muted)

    override suspend fun setCameraEnabled(enabled: Boolean): Result<Unit> =
        mediaController.setCameraEnabled(enabled)

    override suspend fun switchCamera(): Result<Unit> = mediaController.switchCamera()

    override suspend fun setAudioRoute(route: CallAudioRoute): Result<Unit> =
        mediaController.setAudioRoute(route)
}
