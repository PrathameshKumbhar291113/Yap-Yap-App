package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.remoteDisplayName
import com.yapyap.feature.call.domain.entity.remoteParticipant
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.reader.CallSessionReader
import com.yapyap.feature.call.domain.repository.CallMediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Observes call lifecycle and prepares/releases LiveKit media without involving CallCoordinator.
 */
class CallMediaSessionBinder(
    private val callSessionReader: CallSessionReader,
    private val callMediaRepository: CallMediaRepository,
    private val credentialsStore: CallMediaCredentialsStore,
    private val entryHandler: CallEntryHandler,
    private val dispatcherProvider: DispatcherProvider,
) {
    private var boundSessionId: String? = null
    private var mediaPreparedSessionId: String? = null
    private var foregroundSessionId: String? = null

    fun start(parentScope: CoroutineScope) {
        parentScope.launch(dispatcherProvider.default) {
            callSessionReader.state
                .distinctUntilChangedBy { mediaBindingKey(it) }
                .collect { state -> handleState(state) }
        }
        parentScope.launch(dispatcherProvider.default) {
            credentialsStore.revision.collect {
                retryPrepareForCurrentState()
            }
        }
    }

    suspend fun releaseBoundIfAny() {
        boundSessionId?.let { sessionId ->
            callMediaRepository.releaseCall(sessionId)
            if (foregroundSessionId != null) {
                CallMediaPlatformHooks.onActiveCallStopped(foregroundSessionId)
                foregroundSessionId = null
            }
            boundSessionId = null
            mediaPreparedSessionId = null
            credentialsStore.clear()
        }
    }

    private suspend fun handleState(state: CallState) {
        when (state) {
            is CallState.Connected -> prepareMediaIfReady(state.session, state.isMuted, state.isCameraEnabled)
            is CallState.Connecting -> prepareMediaIfReady(
                session = state.session,
                isMuted = false,
                isCameraEnabled = state.session.callType == CallType.Video,
            )
            is CallState.Outgoing -> {
                if (state.session.callType == CallType.Video) {
                    prepareMediaIfReady(
                        session = state.session,
                        isMuted = false,
                        isCameraEnabled = true,
                    )
                }
            }
            is CallState.Reconnecting -> onReconnecting(state)
            is CallState.Idle,
            is CallState.Ended,
            is CallState.Failed,
            -> releaseBoundIfAny()
            else -> Unit
        }
    }

    private suspend fun retryPrepareForCurrentState() {
        when (val state = callSessionReader.state.value) {
            is CallState.Connected -> prepareMediaIfReady(state.session, state.isMuted, state.isCameraEnabled)
            is CallState.Connecting -> prepareMediaIfReady(
                session = state.session,
                isMuted = false,
                isCameraEnabled = state.session.callType == CallType.Video,
            )
            is CallState.Outgoing -> {
                if (state.session.callType == CallType.Video) {
                    prepareMediaIfReady(
                        session = state.session,
                        isMuted = false,
                        isCameraEnabled = true,
                    )
                }
            }
            else -> Unit
        }
    }

    private suspend fun prepareMediaIfReady(
        session: CallSession,
        isMuted: Boolean,
        isCameraEnabled: Boolean,
    ) {
        val credentials = credentialsStore.get(session.id) ?: run {
            CallDiagnostics.stage(
                stage = "CALL_MEDIA_JOIN_DEFERRED",
                callSessionId = session.id,
                extra = mapOf("reason" to "credentials_missing"),
            )
            return
        }
        if (!credentialsStore.canJoinMedia(session.id)) {
            CallDiagnostics.stage(
                stage = "CALL_MEDIA_JOIN_DEFERRED",
                callSessionId = session.id,
                extra = mapOf("reason" to "canJoinMedia=false"),
            )
            return
        }
        if (mediaPreparedSessionId == session.id) return
        val videoEnabled = session.callType == CallType.Video
        boundSessionId = session.id
        if (foregroundSessionId != session.id) {
            val localUserId = entryHandler.localParticipantOrNull()?.userId
            val remoteName = when (val state = callSessionReader.state.value) {
                is CallState.Connected -> state.remoteDisplayName(localUserId)
                is CallState.Outgoing -> state.remoteUser.displayName
                else -> localUserId?.let { session.remoteParticipant(it).displayName } ?: ""
            }
            CallMediaPlatformHooks.onActiveCallStarted(
                callSessionId = session.id,
                remoteDisplayName = remoteName,
                isVideoCall = videoEnabled,
            )
            foregroundSessionId = session.id
        }
        CallDiagnostics.stage(
            stage = "CALL_CONNECT_START",
            callSessionId = session.id,
            extra = mapOf(
                "videoEnabled" to videoEnabled.toString(),
                "roomName" to credentials.roomName,
                "isStub" to CallMediaMode.isStub(credentials).toString(),
            ),
        )
        CallLiveKitTokenDiagnostics.logCredentials(
            callSessionId = session.id,
            credentials = credentials,
            source = "media_binder_prepare",
            canJoinMedia = true,
        )
        callMediaRepository.prepareForCall(
            callSessionId = session.id,
            videoEnabled = videoEnabled,
            credentials = credentials,
        ).onSuccess {
            mediaPreparedSessionId = session.id
            CallDiagnostics.stage(stage = "CALL_CONNECT_SUCCESS", callSessionId = session.id)
            if (isMuted) callMediaRepository.setMuted(true)
            if (videoEnabled && !isCameraEnabled) {
                callMediaRepository.setCameraEnabled(false)
            }
        }.onFailure { error ->
            CallDiagnostics.stage(
                stage = "CALL_ROOM_FAILED",
                callSessionId = session.id,
                extra = mapOf(
                    "error" to (error.message ?: "unknown"),
                    "stack" to error.stackTraceToString().take(500),
                ),
            )
            callMediaRepository.releaseCall(session.id)
            boundSessionId = null
            mediaPreparedSessionId = null
            if (foregroundSessionId == session.id) {
                CallMediaPlatformHooks.onActiveCallStopped(session.id)
                foregroundSessionId = null
            }
        }
    }

    private suspend fun onReconnecting(state: CallState.Reconnecting) {
        val credentials = credentialsStore.get(state.session.id) ?: return
        if (!credentialsStore.canJoinMedia(state.session.id)) return
        callMediaRepository.reconnectForCall(
            callSessionId = state.session.id,
            videoEnabled = state.session.callType == CallType.Video,
            credentials = credentials,
            restoreMuted = callMediaRepository.isMuted.value,
            restoreCameraEnabled = callMediaRepository.isCameraEnabled.value,
        )
    }

    private fun mediaBindingKey(state: CallState): String = when (state) {
        is CallState.Connected -> "connected:${state.session.id}"
        is CallState.Connecting -> "connecting:${state.session.id}"
        is CallState.Outgoing -> "outgoing:${state.session.id}:${state.session.callType.name}"
        is CallState.Reconnecting -> "reconnecting:${state.session.id}:${state.attempt}"
        is CallState.Ended -> "ended:${state.session.id}"
        is CallState.Failed -> "failed:${state.session?.id.orEmpty()}"
        CallState.Idle -> "idle"
        else -> state::class.simpleName.orEmpty()
    }
}
