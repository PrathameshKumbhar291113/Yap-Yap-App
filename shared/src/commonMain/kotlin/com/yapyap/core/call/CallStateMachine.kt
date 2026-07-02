package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallDirection
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallError
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.entity.ReconnectReason
import kotlin.time.Clock
import kotlin.time.Instant

class CallStateMachine(
    private val callSessionStore: CallSessionStore,
) {
    fun currentSessionOrNull(): CallSession? = callSessionStore.currentSessionOrNull()

    fun currentState(): CallState = callSessionStore.state.value

    fun hasActiveCall(): Boolean = isActiveCallState(callSessionStore.state.value)

    fun isActiveCallState(state: CallState = callSessionStore.state.value): Boolean = when (state) {
        is CallState.Outgoing,
        is CallState.Incoming,
        is CallState.Connecting,
        is CallState.Connected,
        is CallState.Reconnecting,
        -> true
        CallState.Idle,
        is CallState.Ended,
        is CallState.Failed,
        -> false
    }

    fun setOutgoing(session: CallSession, remoteUser: CallParticipant) {
        callSessionStore.setState(
            CallState.Outgoing(
                session = session,
                remoteUser = remoteUser,
            ),
        )
    }

    fun setIncoming(session: CallSession, remoteUser: CallParticipant) {
        CallDiagnostics.stage(
            stage = "CALL_STATE_MACHINE_SET_INCOMING",
            callSessionId = session.id,
            extra = mapOf(
                "remoteUserId" to remoteUser.userId.toString(),
                "callType" to session.callType.name,
            ),
        )
        callSessionStore.setState(
            CallState.Incoming(
                session = session,
                remoteUser = remoteUser,
            ),
        )
    }

    fun setConnecting(session: CallSession, direction: CallDirection) {
        callSessionStore.setState(
            CallState.Connecting(
                session = session,
                direction = direction,
            ),
        )
    }

    fun setConnected(
        session: CallSession,
        connectedAt: Instant = Clock.System.now(),
        isCameraEnabled: Boolean = session.callType == CallType.Video,
        isMuted: Boolean = false,
        audioRoute: com.yapyap.feature.call.domain.entity.CallAudioRoute =
            com.yapyap.feature.call.domain.entity.CallAudioRoute.Earpiece,
    ) {
        callSessionStore.setState(
            CallState.Connected(
                session = session,
                connectedAt = connectedAt,
                isCameraEnabled = isCameraEnabled,
                isMuted = isMuted,
                audioRoute = audioRoute,
            ),
        )
    }

    fun setReconnecting(
        session: CallSession,
        attempt: Int,
        reason: ReconnectReason,
    ) {
        callSessionStore.setState(
            CallState.Reconnecting(
                session = session,
                attempt = attempt,
                reason = reason,
            ),
        )
    }

    fun incrementReconnectAttempt(): Int {
        val current = callSessionStore.state.value as? CallState.Reconnecting ?: return 1
        val nextAttempt = current.attempt + 1
        setReconnecting(
            session = current.session,
            attempt = nextAttempt,
            reason = current.reason,
        )
        return nextAttempt
    }

    fun isRecoverableCallState(): Boolean {
        return when (callSessionStore.state.value) {
            is CallState.Connected,
            is CallState.Connecting,
            is CallState.Reconnecting,
            -> true
            else -> false
        }
    }

    fun shouldReconcileOnSocketReady(): Boolean {
        return when (callSessionStore.state.value) {
            is CallState.Connecting,
            is CallState.Reconnecting,
            -> true
            else -> false
        }
    }

    fun connectedMediaFlagsOrDefaults(session: CallSession): ConnectedMediaFlags {
        return when (val current = callSessionStore.state.value) {
            is CallState.Connected -> ConnectedMediaFlags(
                connectedAt = current.connectedAt,
                isMuted = current.isMuted,
                isCameraEnabled = current.isCameraEnabled,
                audioRoute = current.audioRoute,
            )
            else -> ConnectedMediaFlags(
                connectedAt = Clock.System.now(),
                isMuted = false,
                isCameraEnabled = session.callType == CallType.Video,
                audioRoute = com.yapyap.feature.call.domain.entity.CallAudioRoute.Earpiece,
            )
        }
    }

    fun callDirectionOrDefault(session: CallSession, localUserId: Int?): CallDirection {
        return when (val current = callSessionStore.state.value) {
            is CallState.Connecting -> current.direction
            is CallState.Outgoing -> CallDirection.Outgoing
            is CallState.Incoming -> CallDirection.Incoming
            else -> if (localUserId != null && session.caller.userId == localUserId) {
                CallDirection.Outgoing
            } else {
                CallDirection.Incoming
            }
        }
    }

    data class ConnectedMediaFlags(
        val connectedAt: Instant,
        val isMuted: Boolean,
        val isCameraEnabled: Boolean,
        val audioRoute: com.yapyap.feature.call.domain.entity.CallAudioRoute,
    )

    fun setEnded(
        session: CallSession,
        reason: CallEndReason,
        durationMs: Long? = null,
    ) {
        callSessionStore.setState(
            CallState.Ended(
                session = session,
                reason = reason,
                durationMs = durationMs,
            ),
        )
    }

    fun setFailed(session: CallSession?, error: CallError) {
        callSessionStore.setState(
            CallState.Failed(
                session = session,
                error = error,
            ),
        )
    }

    fun setIdle() {
        callSessionStore.setState(CallState.Idle)
    }

    fun dismissOverlayIfTerminal() {
        when (callSessionStore.state.value) {
            is CallState.Ended,
            is CallState.Failed,
            -> setIdle()
            else -> Unit
        }
    }

    fun shouldIgnoreInviteBecauseBusy(callSessionId: String): Boolean {
        val currentSession = callSessionStore.currentSessionOrNull()
        return currentSession != null && currentSession.id != callSessionId
    }

    fun isDuplicateIncomingInvite(callSessionId: String): Boolean {
        val currentSession = callSessionStore.currentSessionOrNull()
        return currentSession?.id == callSessionId &&
            callSessionStore.state.value is CallState.Incoming
    }

    fun updateSessionCallType(callSessionId: String, callType: CallType) {
        if (callType != CallType.Video) return
        val updated = when (val state = callSessionStore.state.value) {
            is CallState.Incoming if state.session.id == callSessionId && state.session.callType != CallType.Video ->
                state.copy(session = state.session.copy(callType = callType))
            is CallState.Connecting if state.session.id == callSessionId && state.session.callType != CallType.Video ->
                state.copy(session = state.session.copy(callType = callType))
            is CallState.Connected if state.session.id == callSessionId && state.session.callType != CallType.Video ->
                state.copy(
                    session = state.session.copy(callType = callType),
                    isCameraEnabled = true,
                )
            is CallState.Reconnecting if state.session.id == callSessionId && state.session.callType != CallType.Video ->
                state.copy(session = state.session.copy(callType = callType))
            else -> return
        }
        callSessionStore.setState(updated)
    }

    fun matchesSession(callSessionId: String): Boolean {
        val session = callSessionStore.currentSessionOrNull()
        return session != null && session.id == callSessionId
    }

    fun canAcceptSignalingAnswer(): Boolean {
        return when (callSessionStore.state.value) {
            is CallState.Outgoing,
            is CallState.Connecting,
            -> true
            else -> false
        }
    }

    fun endReasonForReject(): CallEndReason {
        return when (callSessionStore.state.value) {
            is CallState.Outgoing -> CallEndReason.Rejected
            is CallState.Incoming -> CallEndReason.Cancelled
            else -> CallEndReason.RemoteHangup
        }
    }
}
