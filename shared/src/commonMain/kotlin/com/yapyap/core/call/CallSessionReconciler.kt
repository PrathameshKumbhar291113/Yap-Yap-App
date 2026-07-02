package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.ActiveCallSnapshot
import com.yapyap.feature.call.domain.entity.CallDirection
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallReconcileAction
import com.yapyap.feature.call.domain.entity.CallServerStatus
import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.remoteParticipant
import com.yapyap.feature.call.domain.repository.CallRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallSessionReconciler(
    private val callRepository: CallRepository,
    private val stateMachine: CallStateMachine,
    private val mediaLifecycle: CallMediaLifecycle,
    private val entryHandler: CallEntryHandler,
    private val activeCallTracker: ActiveCallSessionTracker,
) {
    private val activeIncomingMutex = Mutex()

    suspend fun restorePersistedSession(sessionId: String) {
        if (stateMachine.hasActiveCall()) return

        CallDiagnostics.stage(
            stage = "CALL_RESTORE_STARTED",
            callSessionId = sessionId,
        )

        callRepository.reconcileSession(sessionId)
            .fold(
                onSuccess = { snapshot ->
                    if (snapshot == null || snapshot.status.isTerminal) {
                        activeCallTracker.clear()
                        CallDiagnostics.stage(
                            stage = "CALL_RESTORE_STALE",
                            callSessionId = sessionId,
                        )
                        return
                    }
                    applyColdStartSnapshot(snapshot)
                },
                onFailure = { error ->
                    CallDiagnostics.stage(
                        stage = "CALL_RESTORE_FAILED",
                        callSessionId = sessionId,
                        extra = mapOf("error" to (error.message ?: "unknown")),
                    )
                },
            )
    }

    /**
     * Polls GET /api/calls/active to recover a missed [call:incoming] while the app was
     * starting, reconnecting, or before the call signaling collector was attached.
     */
    suspend fun reconcileActiveIncomingCall() = activeIncomingMutex.withLock {
        CallDiagnostics.stage(stage = "CALL_INCOMING_RECONCILE_STARTED")
        if (stateMachine.hasActiveCall()) {
            CallDiagnostics.stage(
                stage = "CALL_INCOMING_RECONCILE_SKIP",
                callSessionId = stateMachine.currentSessionOrNull()?.id,
                extra = mapOf(
                    "reason" to "local_active",
                    "localState" to stateMachine.currentState()::class.simpleName.orEmpty(),
                ),
            )
            return@withLock
        }
        callRepository.fetchActiveCall().fold(
            onSuccess = { snapshot ->
                if (snapshot == null || snapshot.status.isTerminal) {
                    CallDiagnostics.stage(stage = "CALL_INCOMING_RECONCILE_NONE")
                    return@withLock
                }
                applyActiveCallSnapshot(snapshot)
            },
            onFailure = { error ->
                CallDiagnostics.stage(
                    stage = "CALL_INCOMING_RECONCILE_FAILED",
                    extra = mapOf("error" to (error.message ?: "unknown")),
                )
            },
        )
    }

    suspend fun reconcileAfterSocketReady() {
        if (!stateMachine.shouldReconcileOnSocketReady()) {
            return
        }
        val localState = stateMachine.currentState()
        val localSession = stateMachine.currentSessionOrNull() ?: return

        CallDiagnostics.stage(
            stage = "CALL_RECONCILE_STARTED",
            callSessionId = localSession.id,
            extra = mapOf("localState" to localState::class.simpleName.orEmpty()),
        )

        callRepository.reconcileSession(localSession.id)
            .fold(
                onSuccess = { snapshot ->
                    applySnapshot(
                        localSession = localSession,
                        localState = localState,
                        snapshot = snapshot,
                    )
                },
                onFailure = { error ->
                    CallDiagnostics.stage(
                        stage = "CALL_RECONCILE_FAILED",
                        callSessionId = localSession.id,
                        extra = mapOf("error" to (error.message ?: "unknown")),
                    )
                },
            )
    }

    fun enterReconnectingFromSocketLoss() {
        val session = stateMachine.currentSessionOrNull() ?: return
        when (val state = stateMachine.currentState()) {
            is CallState.Connected -> {
                stateMachine.setReconnecting(
                    session = session,
                    attempt = 1,
                    reason = com.yapyap.feature.call.domain.entity.ReconnectReason.SocketDisconnect,
                )
                CallDiagnostics.stage(
                    stage = "CALL_RECONNECTING",
                    callSessionId = session.id,
                    extra = mapOf(
                        "from" to "Connected",
                        "attempt" to "1",
                    ),
                )
            }
            is CallState.Connecting -> {
                stateMachine.setReconnecting(
                    session = session,
                    attempt = 1,
                    reason = com.yapyap.feature.call.domain.entity.ReconnectReason.SocketDisconnect,
                )
                CallDiagnostics.stage(
                    stage = "CALL_RECONNECTING",
                    callSessionId = session.id,
                    extra = mapOf(
                        "from" to "Connecting",
                        "attempt" to "1",
                    ),
                )
            }
            is CallState.Reconnecting -> {
                val attempt = stateMachine.incrementReconnectAttempt()
                CallDiagnostics.stage(
                    stage = "CALL_RECONNECTING",
                    callSessionId = session.id,
                    extra = mapOf(
                        "from" to "Reconnecting",
                        "attempt" to attempt.toString(),
                    ),
                )
            }
            else -> Unit
        }
    }

    fun failRecovery(reason: CallEndReason = CallEndReason.Failed) {
        val session = stateMachine.currentSessionOrNull() ?: return
        if (stateMachine.currentState() !is CallState.Reconnecting &&
            stateMachine.currentState() !is CallState.Connecting
        ) {
            return
        }
        CallDiagnostics.stage(
            stage = "CALL_RECOVERY_FAILED",
            callSessionId = session.id,
            extra = mapOf("reason" to reason.name),
        )
        stateMachine.setEnded(session = session, reason = reason)
        mediaLifecycle.scheduleReleaseAndDismiss(session)
    }

    private fun applyActiveCallSnapshot(snapshot: ActiveCallSnapshot) {
        snapshot.reconciliation?.let { reconciliation ->
            if (reconciliation.forceEndLocal || reconciliation.action == CallReconcileAction.EndLocal) {
                endFromServer(
                    snapshot = snapshot,
                    reason = mapEndReason(reconciliation.reason, snapshot.status),
                )
                return
            }
        }
        when (snapshot.status) {
            CallServerStatus.Ringing,
            CallServerStatus.Connecting,
            -> {
                restoreRingingOrConnecting(snapshot)
                CallDiagnostics.stage(
                    stage = "CALL_INCOMING_RECONCILE_APPLIED",
                    callSessionId = snapshot.session.id,
                    extra = mapOf(
                        "status" to snapshot.status.name,
                        "direction" to stateMachine.callDirectionOrDefault(
                            snapshot.session,
                            entryHandler.localParticipantOrNull()?.userId,
                        ).name,
                    ),
                )
            }
            CallServerStatus.Connected -> {
                restoreConnected(snapshot)
                CallDiagnostics.stage(
                    stage = "CALL_INCOMING_RECONCILE_APPLIED",
                    callSessionId = snapshot.session.id,
                    extra = mapOf("status" to "Connected"),
                )
            }
            else -> {
                CallDiagnostics.stage(
                    stage = "CALL_INCOMING_RECONCILE_IGNORED",
                    callSessionId = snapshot.session.id,
                    extra = mapOf("status" to snapshot.status.name),
                )
            }
        }
    }

    private fun applySnapshot(
        localSession: CallSession,
        localState: CallState,
        snapshot: ActiveCallSnapshot?,
    ) {
        if (snapshot == null) {
            CallDiagnostics.stage(
                stage = "CALL_RECONCILE_STALE",
                callSessionId = localSession.id,
                extra = mapOf("reason" to "no_active_session"),
            )
            stateMachine.setEnded(
                session = localSession,
                reason = CallEndReason.RemoteHangup,
            )
            mediaLifecycle.scheduleReleaseAndDismiss(localSession)
            return
        }

        if (snapshot.session.id != localSession.id) {
            CallDiagnostics.stage(
                stage = "CALL_RECONCILE_STALE",
                callSessionId = localSession.id,
                extra = mapOf(
                    "reason" to "session_mismatch",
                    "serverSessionId" to snapshot.session.id,
                ),
            )
            stateMachine.setEnded(
                session = localSession,
                reason = CallEndReason.RemoteHangup,
            )
            mediaLifecycle.scheduleReleaseAndDismiss(localSession)
            return
        }

        snapshot.reconciliation?.let { reconciliation ->
            if (reconciliation.forceEndLocal || reconciliation.action == CallReconcileAction.EndLocal) {
                endFromServer(
                    snapshot = snapshot,
                    reason = mapEndReason(reconciliation.reason, snapshot.status),
                )
                return
            }
            when (reconciliation.action) {
                CallReconcileAction.Resume -> {
                    restoreConnected(snapshot)
                    return
                }
                CallReconcileAction.Wait -> {
                    restoreInProgress(snapshot, localState)
                    return
                }
                CallReconcileAction.EndLocal -> {
                    endFromServer(
                        snapshot = snapshot,
                        reason = mapEndReason(reconciliation.reason, snapshot.status),
                    )
                    return
                }
            }
        }

        when (snapshot.status) {
            CallServerStatus.Connected -> restoreConnected(snapshot)
            CallServerStatus.Connecting,
            CallServerStatus.Ringing,
            -> restoreInProgress(snapshot, localState)
            CallServerStatus.Ended -> endFromServer(snapshot, CallEndReason.RemoteHangup)
            CallServerStatus.Rejected -> endFromServer(snapshot, CallEndReason.Rejected)
            CallServerStatus.Cancelled -> endFromServer(snapshot, CallEndReason.Cancelled)
        }
    }

    private fun restoreConnected(snapshot: ActiveCallSnapshot) {
        val media = stateMachine.connectedMediaFlagsOrDefaults(snapshot.session)
        stateMachine.setConnected(
            session = snapshot.session,
            connectedAt = media.connectedAt,
            isMuted = media.isMuted,
            isCameraEnabled = media.isCameraEnabled,
            audioRoute = media.audioRoute,
        )
        CallDiagnostics.stage(
            stage = "CALL_RECONCILE_CONNECTED",
            callSessionId = snapshot.session.id,
            extra = mapOf("via" to "active_session"),
        )
    }

    private fun restoreInProgress(snapshot: ActiveCallSnapshot, localState: CallState) {
        val localUserId = entryHandler.localParticipantOrNull()?.userId
        val direction = stateMachine.callDirectionOrDefault(snapshot.session, localUserId)
        when (localState) {
            is CallState.Reconnecting,
            is CallState.Connecting,
            -> {
                stateMachine.setConnecting(
                    session = snapshot.session,
                    direction = direction,
                )
                CallDiagnostics.stage(
                    stage = "CALL_RECONCILE_CONNECTING",
                    callSessionId = snapshot.session.id,
                    extra = mapOf("serverStatus" to snapshot.status.name),
                )
            }
            else -> restoreConnected(snapshot)
        }
    }

    private fun applyColdStartSnapshot(snapshot: ActiveCallSnapshot) {
        snapshot.reconciliation?.let { reconciliation ->
            if (reconciliation.forceEndLocal || reconciliation.action == CallReconcileAction.EndLocal) {
                activeCallTracker.clear()
                endFromServer(
                    snapshot = snapshot,
                    reason = mapEndReason(reconciliation.reason, snapshot.status),
                )
                return
            }
            when (reconciliation.action) {
                CallReconcileAction.Resume -> {
                    restoreConnected(snapshot)
                    CallDiagnostics.stage(
                        stage = "CALL_RESTORE_CONNECTED",
                        callSessionId = snapshot.session.id,
                    )
                    return
                }
                CallReconcileAction.Wait -> {
                    restoreRingingOrConnecting(snapshot)
                    CallDiagnostics.stage(
                        stage = "CALL_RESTORE_WAIT",
                        callSessionId = snapshot.session.id,
                    )
                    return
                }
                CallReconcileAction.EndLocal -> {
                    activeCallTracker.clear()
                    endFromServer(
                        snapshot = snapshot,
                        reason = mapEndReason(reconciliation.reason, snapshot.status),
                    )
                    return
                }
            }
        }

        when (snapshot.status) {
            CallServerStatus.Connected -> {
                restoreConnected(snapshot)
                CallDiagnostics.stage(
                    stage = "CALL_RESTORE_CONNECTED",
                    callSessionId = snapshot.session.id,
                )
            }
            CallServerStatus.Connecting -> {
                restoreRingingOrConnecting(snapshot)
                CallDiagnostics.stage(
                    stage = "CALL_RESTORE_CONNECTING",
                    callSessionId = snapshot.session.id,
                )
            }
            CallServerStatus.Ringing -> {
                restoreRingingOrConnecting(snapshot)
                CallDiagnostics.stage(
                    stage = "CALL_RESTORE_RINGING",
                    callSessionId = snapshot.session.id,
                )
            }
            CallServerStatus.Ended,
            CallServerStatus.Rejected,
            CallServerStatus.Cancelled,
            -> {
                activeCallTracker.clear()
            }
        }
    }

    private fun restoreRingingOrConnecting(snapshot: ActiveCallSnapshot) {
        val localUserId = entryHandler.localParticipantOrNull()?.userId
        val direction = stateMachine.callDirectionOrDefault(snapshot.session, localUserId)
        val remoteUser = localUserId?.let { snapshot.session.remoteParticipant(it) }
            ?: snapshot.session.caller
        when (snapshot.status) {
            CallServerStatus.Connecting -> {
                stateMachine.setConnecting(
                    session = snapshot.session,
                    direction = direction,
                )
            }
            else -> {
                if (direction == CallDirection.Outgoing) {
                    stateMachine.setOutgoing(
                        session = snapshot.session,
                        remoteUser = remoteUser,
                    )
                } else {
                    stateMachine.setIncoming(
                        session = snapshot.session,
                        remoteUser = remoteUser,
                    )
                    CallDiagnostics.stage(
                        stage = "CALL_STATE_MACHINE_SET_INCOMING",
                        callSessionId = snapshot.session.id,
                        extra = mapOf("via" to "reconcile"),
                    )
                }
            }
        }
    }

    private fun endFromServer(
        snapshot: ActiveCallSnapshot,
        reason: CallEndReason,
    ) {
        stateMachine.setEnded(
            session = snapshot.session,
            reason = reason,
            durationMs = snapshot.durationMs,
        )
        mediaLifecycle.scheduleReleaseAndDismiss(snapshot.session)
        CallDiagnostics.stage(
            stage = "CALL_RECONCILE_ENDED",
            callSessionId = snapshot.session.id,
            extra = mapOf("reason" to reason.name),
        )
    }

    private fun mapEndReason(
        reconciliationReason: String?,
        status: CallServerStatus,
    ): CallEndReason = when (reconciliationReason?.lowercase()) {
        "rejected" -> CallEndReason.Rejected
        "missed" -> CallEndReason.Missed
        "failed" -> CallEndReason.Failed
        "cancelled", "canceled" -> CallEndReason.Cancelled
        "room_deleted" -> CallEndReason.RemoteHangup
        else -> when (status) {
            CallServerStatus.Rejected -> CallEndReason.Rejected
            CallServerStatus.Cancelled -> CallEndReason.Cancelled
            else -> CallEndReason.RemoteHangup
        }
    }
}
