package com.yapyap.feature.call.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yapyap.core.call.CallCoordinator
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.call.domain.entity.CallAudioRoute
import com.yapyap.feature.call.domain.entity.CallMediaConnectionState
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.entity.remoteDisplayName
import com.yapyap.feature.call.domain.media.CallRenderableVideoTrack
import com.yapyap.feature.call.domain.repository.CallMediaRepository
import com.yapyap.feature.call.domain.usecase.AnswerIncomingCallUseCase
import com.yapyap.feature.call.domain.usecase.EndCallUseCase
import com.yapyap.feature.call.domain.usecase.ObserveActiveCallUseCase
import com.yapyap.feature.call.domain.usecase.RejectIncomingCallUseCase
import com.yapyap.feature.call.domain.usecase.SwitchCameraUseCase
import com.yapyap.feature.call.domain.usecase.ToggleCameraUseCase
import com.yapyap.feature.call.domain.usecase.ToggleMuteUseCase
import com.yapyap.feature.call.domain.usecase.ToggleSpeakerUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

class CallViewModel(
    observeActiveCallUseCase: ObserveActiveCallUseCase,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val answerIncomingCallUseCase: AnswerIncomingCallUseCase,
    private val rejectIncomingCallUseCase: RejectIncomingCallUseCase,
    private val endCallUseCase: EndCallUseCase,
    private val toggleMuteUseCase: ToggleMuteUseCase,
    private val toggleCameraUseCase: ToggleCameraUseCase,
    private val toggleSpeakerUseCase: ToggleSpeakerUseCase,
    private val switchCameraUseCase: SwitchCameraUseCase,
    private val callCoordinator: CallCoordinator,
    private val permissionPromptController: CallPermissionPromptController,
    private val presentationController: CallUiPresentationController,
    callMediaRepository: CallMediaRepository,
) : ViewModel() {
    private val actionError = MutableStateFlow<String?>(null)
    private val isAcceptInProgress = MutableStateFlow(false)
    private val elapsedSeconds = MutableStateFlow<Long?>(null)
    private var timerJob: Job? = null
    private var timerAnchor: Instant? = null

    init {
        viewModelScope.launch {
            observeActiveCallUseCase().collect { callState ->
                syncTimer(callState)
                syncPresentation(callState)
            }
        }
    }

    private fun syncPresentation(callState: CallState) {
        when (callState) {
            CallState.Idle -> presentationController.reset()
            is CallState.Incoming,
            is CallState.Outgoing,
            is CallState.Ended,
            is CallState.Failed,
            -> presentationController.expandCallScreen()
            is CallState.Connecting,
            is CallState.Connected,
            is CallState.Reconnecting,
            -> Unit
        }
    }

    private data class MediaUi(
        val isMuted: Boolean,
        val isCameraEnabled: Boolean,
        val audioRoute: CallAudioRoute,
        val connectionState: CallMediaConnectionState,
        val localVideoTrack: CallRenderableVideoTrack?,
        val remoteVideoTrack: CallRenderableVideoTrack?,
        val remoteParticipantConnected: Boolean,
    )

    private val mediaUi = combine(
        combine(
            callMediaRepository.isMuted,
            callMediaRepository.isCameraEnabled,
            callMediaRepository.audioRoute,
        ) { muted, camera, route ->
            Triple(muted, camera, route)
        },
        combine(
            callMediaRepository.connectionState,
            callMediaRepository.localVideoTrack,
            callMediaRepository.remoteVideoTrack,
            callMediaRepository.remoteParticipantConnected,
        ) { connection, local, remote, remoteConnected ->
            listOf(connection, local, remote, remoteConnected)
        },
    ) { basics, rest ->
        MediaUi(
            isMuted = basics.first,
            isCameraEnabled = basics.second,
            audioRoute = basics.third,
            connectionState = rest[0] as CallMediaConnectionState,
            localVideoTrack = rest[1] as CallRenderableVideoTrack?,
            remoteVideoTrack = rest[2] as CallRenderableVideoTrack?,
            remoteParticipantConnected = rest[3] as Boolean,
        )
    }

    val uiState: StateFlow<CallUiState> = combine(
        observeActiveCallUseCase(),
        observeAuthStateUseCase(),
        mediaUi,
        elapsedSeconds,
        actionError,
        presentationController.isCallScreenExpanded,
        permissionPromptController.pendingStatus,
        permissionPromptController.pendingCallType,
        isAcceptInProgress,
    ) { values ->
        val callState = values[0] as CallState
        val authState = values[1] as AuthState
        val media = values[2] as MediaUi
        val elapsed = values[3] as Long?
        val error = values[4] as String?
        val expanded = values[5] as Boolean
        val permissionStatus = values[6] as com.yapyap.core.call.CallPermissionStatus?
        val permissionCallType = values[7] as CallType?
        val accepting = values[8] as Boolean
        val localUserId = (authState as? AuthState.Authenticated)?.user?.id
        CallUiState(
            callState = callState,
            remoteDisplayName = callState.remoteDisplayName(localUserId),
            statusMessage = callState.statusMessage(media.connectionState),
            callType = callState.callType(),
            isMuted = media.isMuted,
            isCameraEnabled = media.isCameraEnabled,
            audioRoute = media.audioRoute,
            connectionState = media.connectionState,
            elapsedSeconds = elapsed,
            localVideoTrack = media.localVideoTrack,
            remoteVideoTrack = media.remoteVideoTrack,
            remoteParticipantConnected = media.remoteParticipantConnected,
            actionError = error,
            isCallScreenExpanded = expanded,
            pendingPermissionStatus = permissionStatus,
            pendingPermissionCallType = permissionCallType,
            isAcceptInProgress = accepting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = CallUiState(),
    )

    private fun syncTimer(callState: CallState) {
        when (callState) {
            is CallState.Connected -> {
                if (timerAnchor != callState.connectedAt) {
                    timerAnchor = callState.connectedAt
                    startTimer(callState.connectedAt)
                }
            }
            is CallState.Reconnecting -> Unit
            else -> stopTimer(resetElapsed = callState !is CallState.Ended)
        }
    }

    private fun startTimer(connectedAt: Instant) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                elapsedSeconds.value = (Clock.System.now() - connectedAt).inWholeSeconds.coerceAtLeast(0)
                delay(1_000)
            }
        }
    }

    private fun stopTimer(resetElapsed: Boolean) {
        timerJob?.cancel()
        timerJob = null
        timerAnchor = null
        if (resetElapsed) elapsedSeconds.value = null
    }

    fun expandCallScreen() = presentationController.expandCallScreen()

    fun minimizeCallScreen() = presentationController.minimizeCallScreen()

    fun answerIncoming() {
        if (isAcceptInProgress.value) return
        viewModelScope.launch {
            actionError.value = null
            val callType = (uiState.value.callState as? CallState.Incoming)?.session?.callType ?: CallType.Voice
            if (!permissionPromptController.ensurePermissions(callType)) return@launch
            isAcceptInProgress.value = true
            answerIncomingCallUseCase()
                .onFailure { actionError.value = it.message }
            isAcceptInProgress.value = false
        }
    }

    fun rejectIncoming() {
        viewModelScope.launch {
            actionError.value = null
            rejectIncomingCallUseCase().onFailure { actionError.value = it.message }
        }
    }

    fun endCall() {
        viewModelScope.launch {
            actionError.value = null
            endCallUseCase().onFailure { actionError.value = it.message }
        }
    }

    fun dismissOverlay() = callCoordinator.dismissOverlay()

    fun dismissPermissionPrompt() = permissionPromptController.dismiss()

    fun openPermissionSettings() = permissionPromptController.openSettings()

    fun toggleMute() {
        viewModelScope.launch { toggleMuteUseCase().onFailure { actionError.value = it.message } }
    }

    fun toggleCamera() {
        viewModelScope.launch { toggleCameraUseCase().onFailure { actionError.value = it.message } }
    }

    fun switchCamera() {
        viewModelScope.launch { switchCameraUseCase().onFailure { actionError.value = it.message } }
    }

    fun toggleSpeaker() {
        viewModelScope.launch { toggleSpeakerUseCase().onFailure { actionError.value = it.message } }
    }

    private fun CallState.callType(): CallType? = when (this) {
        is CallState.Outgoing -> session.callType
        is CallState.Incoming -> session.callType
        is CallState.Connecting -> session.callType
        is CallState.Connected -> session.callType
        is CallState.Reconnecting -> session.callType
        is CallState.Ended -> session.callType
        is CallState.Failed -> session?.callType
        CallState.Idle -> null
    }

    private fun CallState.statusMessage(connectionState: CallMediaConnectionState): String = when (this) {
        CallState.Idle -> ""
        is CallState.Outgoing -> "Calling…"
        is CallState.Incoming -> "Ringing…"
        is CallState.Connecting -> "Connecting…"
        is CallState.Connected -> when (connectionState) {
            CallMediaConnectionState.PoorConnection -> "Poor connection"
            CallMediaConnectionState.Reconnecting -> "Reconnecting…"
            else -> ""
        }
        is CallState.Reconnecting -> "Reconnecting…"
        is CallState.Ended -> "Call ended"
        is CallState.Failed -> error.message
    }
}

fun formatCallDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
