package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import kotlinx.coroutines.CoroutineScope

class CallSignalingHandler(
    private val inviteDeduplicator: CallInviteDeduplicator,
    private val stateMachine: CallStateMachine,
    private val entryHandler: CallEntryHandler,
    private val mediaLifecycle: CallMediaLifecycle,
    private val credentialsStore: CallMediaCredentialsStore,
    dispatcherProvider: DispatcherProvider,
) {
    private val mailbox = CallSignalingEventMailbox(
        dispatcherProvider = dispatcherProvider,
        processor = ::processEvent,
    )

    fun bindScope(parentScope: CoroutineScope) {
        mailbox.start(parentScope)
    }

    fun onSignalingEvent(event: CallSignalingEvent) {
        if (!mailbox.enqueue(event)) {
            CallDiagnostics.stage(
                stage = "CALL_SIGNALING_EVENT_DROPPED",
                callSessionId = event.callSessionIdOrNull(),
                extra = mapOf("type" to event::class.simpleName.orEmpty()),
            )
        }
    }

    private suspend fun processEvent(event: CallSignalingEvent) {
        CallDiagnostics.stage(
            stage = "CALL_SIGNALING_MAILBOX_PROCESSING",
            callSessionId = event.callSessionIdOrNull(),
            extra = mapOf("type" to event::class.simpleName.orEmpty()),
        )
        when (event) {
            is CallSignalingEvent.Invite -> handleIncomingInvite(event)
            is CallSignalingEvent.Answer -> handleCallAccepted(event)
            is CallSignalingEvent.Reject -> handleCallRejected(event)
            is CallSignalingEvent.End -> handleCallEnded(event)
            is CallSignalingEvent.Sync -> handleCallSync(event)
            is CallSignalingEvent.IceCandidate -> Unit
        }
    }

    private suspend fun handleIncomingInvite(event: CallSignalingEvent.Invite) {
        val localUserId = entryHandler.localParticipantOrNull()?.userId
        if (localUserId != null && event.callerId == localUserId) {
            CallDiagnostics.stage(
                stage = "CALL_INVITE_IGNORED",
                callSessionId = event.callSessionId,
                extra = mapOf("reason" to "self_caller_signaling"),
            )
            return
        }
        CallDiagnostics.socketReceived(
            event = "call:incoming",
            callSessionId = event.callSessionId,
            extra = mapOf(
                "callerId" to event.callerId.toString(),
                "callTypeRaw" to (event.callTypeRaw ?: "null"),
            ),
        )
        if (!inviteDeduplicator.tryClaim(event.callSessionId)) {
            CallDiagnostics.stage(
                stage = "CALL_INVITE_DEDUPED",
                callSessionId = event.callSessionId,
                extra = mapOf("source" to "socket"),
            )
            return
        }
        CallDiagnostics.stage(
            stage = "CALL_DEDUP_CLAIMED",
            callSessionId = event.callSessionId,
            extra = mapOf("source" to "socket"),
        )
        entryHandler.applyIncomingInvite(
            callSessionId = event.callSessionId,
            conversationId = event.conversationId,
            callerId = event.callerId,
            callerName = event.callerName,
            callType = CallType.fromRaw(event.callTypeRaw),
        )
    }

    private suspend fun handleCallAccepted(event: CallSignalingEvent.Answer) {
        CallDiagnostics.socketReceived(
            event = "call:accepted",
            callSessionId = event.callSessionId,
            extra = buildMap {
                event.roomName?.let { put("roomName", it) }
                event.livekitUrl?.let { put("livekitUrl", it) }
                put("hasProviderToken", (!event.providerToken.isNullOrBlank()).toString())
            },
        )
        if (!stateMachine.matchesSession(event.callSessionId)) {
            CallDiagnostics.stage(
                stage = "CALL_ACCEPTED_IGNORED",
                callSessionId = event.callSessionId,
                extra = mapOf("reason" to "no_matching_session"),
            )
            return
        }
        if (!stateMachine.canAcceptSignalingAnswer()) {
            val session = stateMachine.currentSessionOrNull() ?: return
            if (stateMachine.currentState() is CallState.Incoming) {
                storeCredentialsFromAnswer(event)
                stateMachine.setConnecting(
                    session = session,
                    direction = com.yapyap.feature.call.domain.entity.CallDirection.Incoming,
                )
                CallDiagnostics.stage(
                    stage = "CALL_CONNECTING",
                    callSessionId = session.id,
                    extra = mapOf("via" to "call:accepted"),
                )
            }
            return
        }
        val session = stateMachine.currentSessionOrNull() ?: return
        storeCredentialsFromAnswer(event)
        stateMachine.setConnected(session = session)
        CallDiagnostics.stage(
            stage = "CALL_CONNECTED",
            callSessionId = session.id,
            extra = mapOf("via" to "signaling"),
        )
    }

    private suspend fun storeCredentialsFromAnswer(event: CallSignalingEvent.Answer) {
        val token = event.providerToken?.trim().orEmpty()
        val url = event.livekitUrl?.trim().orEmpty()
        val room = event.roomName?.trim().orEmpty().ifBlank { event.callSessionId }
        val credentials = com.yapyap.feature.call.domain.entity.CallRoomCredentials(
            livekitUrl = url,
            providerToken = token,
            roomName = room,
        )
        if (credentials.isValid()) {
            credentialsStore.store(event.callSessionId, credentials, canJoinMedia = true)
        } else {
            CallDiagnostics.stage(
                stage = "CALL_TOKEN_INVALID",
                callSessionId = event.callSessionId,
                extra = mapOf(
                    "hasToken" to token.isNotEmpty().toString(),
                    "hasUrl" to url.isNotEmpty().toString(),
                    "roomName" to room,
                ),
            )
        }
    }

    private fun handleCallRejected(event: CallSignalingEvent.Reject) {
        CallDiagnostics.socketReceived(
            event = "call:rejected",
            callSessionId = event.callSessionId,
            extra = event.reason?.let { mapOf("reason" to it) }.orEmpty(),
        )
        if (!stateMachine.matchesSession(event.callSessionId)) {
            return
        }
        val session = stateMachine.currentSessionOrNull() ?: return
        transitionToEnded(
            session = session,
            reason = stateMachine.endReasonForReject(),
        )
    }

    private fun handleCallEnded(event: CallSignalingEvent.End) {
        CallDiagnostics.socketReceived(
            event = "call:ended",
            callSessionId = event.callSessionId,
            extra = event.durationSec?.let { mapOf("durationSec" to it.toString()) }.orEmpty(),
        )
        if (!stateMachine.matchesSession(event.callSessionId)) {
            return
        }
        val session = stateMachine.currentSessionOrNull() ?: return
        transitionToEnded(
            session = session,
            reason = CallEndReason.RemoteHangup,
            durationMs = event.durationSec?.times(1_000L),
        )
    }

    private suspend fun handleCallSync(event: CallSignalingEvent.Sync) {
        CallDiagnostics.socketReceived(
            event = "call:sync",
            callSessionId = event.callSessionId,
            extra = mapOf(
                "status" to event.status,
                "syncEvent" to event.event.orEmpty(),
            ),
        )
        if (!stateMachine.matchesSession(event.callSessionId)) {
            val status = event.status.lowercase()
            if (stateMachine.currentState() is CallState.Idle &&
                status in RINGING_SYNC_STATUSES
            ) {
                CallDiagnostics.stage(
                    stage = "CALL_SYNC_RECOVER_INCOMING",
                    callSessionId = event.callSessionId,
                    extra = mapOf("status" to status),
                )
                entryHandler.onIncomingFromNavigation(event.callSessionId)
            }
            return
        }
        val session = stateMachine.currentSessionOrNull() ?: return
        when (event.status.lowercase()) {
            "accepted" -> {
                when (stateMachine.currentState()) {
                    is CallState.Incoming -> {
                        stateMachine.setConnecting(
                            session = session,
                            direction = com.yapyap.feature.call.domain.entity.CallDirection.Incoming,
                        )
                        CallDiagnostics.stage(
                            stage = "CALL_CONNECTING",
                            callSessionId = session.id,
                            extra = mapOf("via" to "call:sync"),
                        )
                    }
                    is CallState.Outgoing,
                    is CallState.Connecting,
                    -> {
                        if (stateMachine.canAcceptSignalingAnswer()) {
                            stateMachine.setConnected(session = session)
                            CallDiagnostics.stage(
                                stage = "CALL_CONNECTED",
                                callSessionId = session.id,
                                extra = mapOf("via" to "call:sync"),
                            )
                        }
                    }
                    else -> Unit
                }
            }
            "rejected" -> transitionToEnded(session, CallEndReason.Rejected, event.durationSec?.times(1_000L))
            "missed" -> transitionToEnded(session, CallEndReason.Missed, event.durationSec?.times(1_000L))
            "failed" -> transitionToEnded(session, CallEndReason.Failed, event.durationSec?.times(1_000L))
            "ended" -> transitionToEnded(
                session,
                CallEndReason.RemoteHangup,
                event.durationSec?.times(1_000L),
            )
        }
    }

    private fun transitionToEnded(
        session: CallSession,
        reason: CallEndReason,
        durationMs: Long? = null,
    ) {
        stateMachine.setEnded(
            session = session,
            reason = reason,
            durationMs = durationMs,
        )
        mediaLifecycle.scheduleReleaseAndDismiss(session)
    }

    private fun CallSignalingEvent.callSessionIdOrNull(): String? = when (this) {
        is CallSignalingEvent.Answer -> callSessionId
        is CallSignalingEvent.End -> callSessionId
        is CallSignalingEvent.Sync -> callSessionId
        is CallSignalingEvent.IceCandidate -> callSessionId
        is CallSignalingEvent.Invite -> callSessionId
        is CallSignalingEvent.Reject -> callSessionId
    }

    private companion object {
        val RINGING_SYNC_STATUSES = setOf("ringing", "initiated", "incoming", "outgoing")
    }
}
