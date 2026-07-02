package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Simulates LiveKit media when backend runs with LIVEKIT_DISABLED (stub tokens).
 * Allows full call UX testing without a real media server.
 */
class FakeCallMediaController : CallMediaController {
    private val mutex = Mutex()
    private val _isMuted = MutableStateFlow(false)
    private val _isCameraEnabled = MutableStateFlow(false)
    private val _audioRoute = MutableStateFlow(CallAudioRoute.Earpiece)
    private val _connectionState = MutableStateFlow(CallMediaConnectionState.Idle)
    private val _localVideoTrack = MutableStateFlow<CallRenderableVideoTrack?>(null)
    private val _remoteVideoTrack = MutableStateFlow<CallRenderableVideoTrack?>(null)
    private val _remoteParticipantConnected = MutableStateFlow(false)

    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    override val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()
    override val audioRoute: StateFlow<CallAudioRoute> = _audioRoute.asStateFlow()
    override val connectionState: StateFlow<CallMediaConnectionState> = _connectionState.asStateFlow()
    override val localVideoTrack: StateFlow<CallRenderableVideoTrack?> = _localVideoTrack.asStateFlow()
    override val remoteVideoTrack: StateFlow<CallRenderableVideoTrack?> = _remoteVideoTrack.asStateFlow()
    override val remoteParticipantConnected: StateFlow<Boolean> = _remoteParticipantConnected.asStateFlow()

    override suspend fun prepareCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            CallDiagnostics.stage(
                stage = "CALL_ROOM_CONNECTING",
                callSessionId = callSessionId,
                extra = mapOf("mode" to "stub"),
            )
            _connectionState.value = CallMediaConnectionState.Connecting
            _isMuted.value = false
            _isCameraEnabled.value = videoEnabled
            _audioRoute.value = CallAudioRoute.Earpiece
            CallDiagnostics.stage(stage = "CALL_ROOM_CONNECTED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            CallDiagnostics.stage(stage = "CALL_LOCAL_AUDIO_CREATED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            CallDiagnostics.stage(stage = "CALL_LOCAL_AUDIO_PUBLISHED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            if (videoEnabled) {
                CallDiagnostics.stage(stage = "CALL_LOCAL_VIDEO_CREATED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
                CallDiagnostics.stage(stage = "CALL_LOCAL_VIDEO_PUBLISHED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            }
            CallDiagnostics.stage(stage = "CALL_REMOTE_PARTICIPANT_JOINED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            CallDiagnostics.stage(stage = "CALL_REMOTE_AUDIO_RECEIVED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            if (videoEnabled) {
                CallDiagnostics.stage(stage = "CALL_REMOTE_VIDEO_RECEIVED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            }
            _remoteParticipantConnected.value = true
            _connectionState.value = CallMediaConnectionState.Connected
            CallDiagnostics.stage(stage = "CALL_MEDIA_PREPARED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
        }
    }

    override suspend fun reconnectCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
        restoreMuted: Boolean,
        restoreCameraEnabled: Boolean,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            _connectionState.value = CallMediaConnectionState.Reconnecting
            _isMuted.value = restoreMuted
            _isCameraEnabled.value = videoEnabled && restoreCameraEnabled
            _remoteParticipantConnected.value = true
            _connectionState.value = CallMediaConnectionState.Connected
            CallDiagnostics.stage(stage = "CALL_MEDIA_RECONNECTED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
        }
    }

    override suspend fun releaseCall(callSessionId: String): Result<Unit> = mutex.withLock {
        runCatching {
            CallDiagnostics.stage(stage = "CALL_ROOM_DISCONNECTED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
            resetInternal()
            CallDiagnostics.stage(stage = "CALL_MEDIA_RELEASED", callSessionId = callSessionId, extra = mapOf("mode" to "stub"))
        }
    }

    override suspend fun setMuted(muted: Boolean): Result<Unit> = mutex.withLock {
        runCatching { _isMuted.value = muted }
    }

    override suspend fun setCameraEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        runCatching { _isCameraEnabled.value = enabled }
    }

    override suspend fun switchCamera(): Result<Unit> = mutex.withLock {
        runCatching { Unit }
    }

    override suspend fun setAudioRoute(route: CallAudioRoute): Result<Unit> = mutex.withLock {
        runCatching { _audioRoute.value = route }
    }

    private fun resetInternal() {
        _isMuted.value = false
        _isCameraEnabled.value = false
        _audioRoute.value = CallAudioRoute.Earpiece
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _remoteParticipantConnected.value = false
        _connectionState.value = CallMediaConnectionState.Idle
    }
}
