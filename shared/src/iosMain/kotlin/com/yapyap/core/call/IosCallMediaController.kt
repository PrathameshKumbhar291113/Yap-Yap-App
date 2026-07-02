@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker

/**
 * iOS LiveKit media controller. Room operations delegate to [IosLiveKitBridge] (Swift + LiveKit SDK).
 */
class IosCallMediaController(
    dispatcherProvider: DispatcherProvider,
) : CallMediaController {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    private val bridge = IosLiveKitBridge(scope)
    private val audioSession = AVAudioSession.sharedInstance()

    private val _isMuted = MutableStateFlow(false)
    private val _isCameraEnabled = MutableStateFlow(false)
    private val _audioRoute = MutableStateFlow(CallAudioRoute.Earpiece)
    private val _connectionState = MutableStateFlow(CallMediaConnectionState.Idle)
    private val _localVideoTrack = MutableStateFlow<CallRenderableVideoTrack?>(null)
    private val _remoteVideoTrack = MutableStateFlow<CallRenderableVideoTrack?>(null)
    private val _remoteParticipantConnected = MutableStateFlow(false)

    private var activeSessionId: String? = null

    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()
    override val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()
    override val audioRoute: StateFlow<CallAudioRoute> = _audioRoute.asStateFlow()
    override val connectionState: StateFlow<CallMediaConnectionState> = _connectionState.asStateFlow()
    override val localVideoTrack: StateFlow<CallRenderableVideoTrack?> = _localVideoTrack.asStateFlow()
    override val remoteVideoTrack: StateFlow<CallRenderableVideoTrack?> = _remoteVideoTrack.asStateFlow()
    override val remoteParticipantConnected: StateFlow<Boolean> = _remoteParticipantConnected.asStateFlow()

    init {
        bridge.bindState(
            onConnectionState = { _connectionState.value = it },
            onMuted = { _isMuted.value = it },
            onCameraEnabled = { _isCameraEnabled.value = it },
            onLocalTrack = { _localVideoTrack.value = it?.let(::CallRenderableVideoTrack) },
            onRemoteTrack = { _remoteVideoTrack.value = it?.let(::CallRenderableVideoTrack) },
            onRemoteConnected = { _remoteParticipantConnected.value = it },
        )
    }

    override suspend fun prepareCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            if (activeSessionId == callSessionId && _connectionState.value == CallMediaConnectionState.Connected) {
                return@runCatching
            }
            if (activeSessionId != null) {
                bridge.disconnect(activeSessionId!!)
                resetLocalState()
            }
            configureAudioSession(CallAudioRoute.Earpiece)
            _connectionState.value = CallMediaConnectionState.Connecting
            activeSessionId = callSessionId
            bridge.connect(
                callSessionId = callSessionId,
                credentials = credentials,
                videoEnabled = videoEnabled,
                reconnecting = false,
            ).getOrThrow()
            _connectionState.value = CallMediaConnectionState.Connected
            CallDiagnostics.stage(
                stage = "CALL_MEDIA_PREPARED",
                callSessionId = callSessionId,
                extra = mapOf("platform" to "ios", "videoEnabled" to videoEnabled.toString()),
            )
        }.onFailure { error ->
            CallDiagnostics.stage(
                stage = "CALL_ROOM_FAILED",
                callSessionId = callSessionId,
                extra = mapOf("error" to (error.message ?: error::class.simpleName.orEmpty())),
            )
            bridge.disconnect(callSessionId)
            resetLocalState()
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
            activeSessionId = callSessionId
            bridge.connect(
                callSessionId = callSessionId,
                credentials = credentials,
                videoEnabled = videoEnabled,
                reconnecting = true,
                restoreMuted = restoreMuted,
                restoreCameraEnabled = restoreCameraEnabled,
            ).getOrThrow()
            _connectionState.value = CallMediaConnectionState.Connected
        }.onFailure { error ->
            CallDiagnostics.stage(
                stage = "CALL_ROOM_FAILED",
                callSessionId = callSessionId,
                extra = mapOf("error" to (error.message ?: error::class.simpleName.orEmpty())),
            )
            bridge.disconnect(callSessionId)
            resetLocalState()
        }
    }

    override suspend fun releaseCall(callSessionId: String): Result<Unit> = mutex.withLock {
        runCatching {
            if (activeSessionId != null && activeSessionId != callSessionId) return@runCatching
            bridge.disconnect(callSessionId)
            resetLocalState()
            CallDiagnostics.stage(stage = "CALL_ROOM_RELEASED", callSessionId = callSessionId)
            CallDiagnostics.stage(stage = "CALL_MEDIA_RELEASED", callSessionId = callSessionId)
        }
    }

    override suspend fun setMuted(muted: Boolean): Result<Unit> = mutex.withLock {
        runCatching {
            bridge.setMuted(muted)
            _isMuted.value = muted
        }
    }

    override suspend fun setCameraEnabled(enabled: Boolean): Result<Unit> = mutex.withLock {
        runCatching {
            bridge.setCameraEnabled(enabled)
            _isCameraEnabled.value = enabled
        }
    }

    override suspend fun switchCamera(): Result<Unit> = mutex.withLock {
        runCatching { bridge.switchCamera() }
    }

    override suspend fun setAudioRoute(route: CallAudioRoute): Result<Unit> = mutex.withLock {
        runCatching { configureAudioSession(route) }
    }

    private fun resetLocalState() {
        activeSessionId = null
        _connectionState.value = CallMediaConnectionState.Idle
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _remoteParticipantConnected.value = false
        _isMuted.value = false
        _isCameraEnabled.value = false
        _audioRoute.value = CallAudioRoute.Earpiece
    }

    private fun configureAudioSession(route: CallAudioRoute) {
        val options = AVAudioSessionCategoryOptionAllowBluetooth or
            AVAudioSessionCategoryOptionDefaultToSpeaker
        audioSession.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeVoiceChat,
            options = options,
            error = null,
        )
        when (route) {
            CallAudioRoute.Speaker ->
                audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)
            CallAudioRoute.Earpiece,
            CallAudioRoute.Bluetooth,
            CallAudioRoute.WiredHeadset,
            -> audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
        }
        _audioRoute.value = route
        CallDiagnostics.stage(
            stage = "CALL_AUDIOFOCUS_REQUESTED",
            extra = mapOf("platform" to "ios"),
        )
    }
}
