package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallRoomCredentials
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Routes media operations to [FakeCallMediaController] for stub credentials or the platform LiveKit controller.
 * Exposes stable [StateFlow]s that follow whichever controller is active.
 */
class RoutedCallMediaController(
    private val liveMediaController: CallMediaController,
    private val stubMediaController: FakeCallMediaController,
    dispatcherProvider: DispatcherProvider,
) : CallMediaController {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.default)
    private var activeController: CallMediaController = stubMediaController
    private var forwardingJob: Job? = null

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

    init {
        bindForwarding(stubMediaController)
    }

    override suspend fun prepareCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
    ): Result<Unit> {
        switchControllerIfNeeded(credentials, callSessionId)
        CallDiagnostics.stage(
            stage = if (activeController === stubMediaController) "CALL_MEDIA_STUB_MODE" else "CALL_MEDIA_LIVE_MODE",
            callSessionId = callSessionId,
        )
        return activeController.prepareCall(callSessionId, videoEnabled, credentials)
    }

    override suspend fun reconnectCall(
        callSessionId: String,
        videoEnabled: Boolean,
        credentials: CallRoomCredentials,
        restoreMuted: Boolean,
        restoreCameraEnabled: Boolean,
    ): Result<Unit> {
        switchControllerIfNeeded(credentials, callSessionId)
        return activeController.reconnectCall(
            callSessionId = callSessionId,
            videoEnabled = videoEnabled,
            credentials = credentials,
            restoreMuted = restoreMuted,
            restoreCameraEnabled = restoreCameraEnabled,
        )
    }

    override suspend fun releaseCall(callSessionId: String): Result<Unit> {
        stubMediaController.releaseCall(callSessionId)
        liveMediaController.releaseCall(callSessionId)
        activeController = stubMediaController
        bindForwarding(stubMediaController)
        return Result.success(Unit)
    }

    override suspend fun setMuted(muted: Boolean): Result<Unit> = activeController.setMuted(muted)

    override suspend fun setCameraEnabled(enabled: Boolean): Result<Unit> = activeController.setCameraEnabled(enabled)

    override suspend fun switchCamera(): Result<Unit> = activeController.switchCamera()

    override suspend fun setAudioRoute(route: CallAudioRoute): Result<Unit> = activeController.setAudioRoute(route)

    private suspend fun switchControllerIfNeeded(credentials: CallRoomCredentials, callSessionId: String) {
        val next = controllerFor(credentials)
        if (next === activeController) return
        inactiveController().releaseCall(callSessionId)
        activeController = next
        bindForwarding(next)
    }

    private fun inactiveController(): CallMediaController {
        return if (activeController === stubMediaController) liveMediaController else stubMediaController
    }

    private fun controllerFor(credentials: CallRoomCredentials): CallMediaController {
        return if (CallMediaMode.isStub(credentials)) stubMediaController else liveMediaController
    }

    private fun bindForwarding(controller: CallMediaController) {
        forwardingJob?.cancel()
        _isMuted.value = controller.isMuted.value
        _isCameraEnabled.value = controller.isCameraEnabled.value
        _audioRoute.value = controller.audioRoute.value
        _connectionState.value = controller.connectionState.value
        _localVideoTrack.value = controller.localVideoTrack.value
        _remoteVideoTrack.value = controller.remoteVideoTrack.value
        _remoteParticipantConnected.value = controller.remoteParticipantConnected.value
        forwardingJob = scope.launch {
            coroutineScope {
                launch { controller.isMuted.collect { _isMuted.value = it } }
                launch { controller.isCameraEnabled.collect { _isCameraEnabled.value = it } }
                launch { controller.audioRoute.collect { _audioRoute.value = it } }
                launch { controller.connectionState.collect { _connectionState.value = it } }
                launch { controller.localVideoTrack.collect { _localVideoTrack.value = it } }
                launch { controller.remoteVideoTrack.collect { _remoteVideoTrack.value = it } }
                launch { controller.remoteParticipantConnected.collect { _remoteParticipantConnected.value = it } }
            }
        }
    }
}
