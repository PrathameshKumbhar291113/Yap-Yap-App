package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.core.network.ApiException
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.call.domain.entity.CallDirection
import com.yapyap.feature.call.domain.entity.CallEndReason
import com.yapyap.feature.call.domain.entity.CallError
import com.yapyap.feature.call.domain.entity.CallParticipant
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.entity.CallType
import com.yapyap.feature.call.domain.orchestrator.CallOrchestrator
import com.yapyap.feature.call.domain.repository.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CallCoordinator(
    private val callRepository: CallRepository,
    private val callSignalingRepository: Lazy<CallSignalingRepository>,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val stateMachine: CallStateMachine,
    private val entryHandler: CallEntryHandler,
    private val signalingHandler: CallSignalingHandler,
    private val mediaLifecycle: CallMediaLifecycle,
    private val reconnectHandler: CallReconnectHandler,
    private val activeCallTracker: ActiveCallSessionTracker,
    private val ringtoneCoordinator: CallRingtoneCoordinator,
    private val callNativeCoordinator: CallNativeCoordinator,
) : CallOrchestrator {
    private var scope: CoroutineScope? = null
    private val answerMutex = Mutex()
    private val endMutex = Mutex()

    fun start(parentScope: CoroutineScope) {
        if (scope != null) return
        scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
        val coordinatorScope = scope ?: return

        signalingHandler.bindScope(coordinatorScope)
        mediaLifecycle.bindScope(coordinatorScope)
        ringtoneCoordinator.start(coordinatorScope)
        callNativeCoordinator.start(coordinatorScope)
        reconnectHandler.startObserving(coordinatorScope)
        callSignalingRepository.value.start(coordinatorScope)

        coordinatorScope.launch(dispatcherProvider.default) {
            observeAuthStateUseCase()
                .map { state -> state is AuthState.Authenticated }
                .distinctUntilChanged()
                .collect { authenticated ->
                    if (authenticated) {
                        reconnectHandler.restorePersistedSessionIfNeeded()
                        reconnectHandler.reconcileActiveIncomingIfNeeded()
                    } else {
                        mediaLifecycle.resetToIdle(reason = CallEndReason.Cancelled)
                    }
                }
        }
    }

    override suspend fun startOutgoing(
        conversationId: Int,
        callType: CallType,
        remoteUser: CallParticipant,
    ): Result<Unit> {
        ensureNotBusy()?.let { return it }
        CallDiagnostics.stage(
            stage = "CALL_OUTGOING_REQUESTED",
            extra = mapOf(
                "conversationId" to conversationId.toString(),
                "callType" to callType.name,
                "remoteUserId" to remoteUser.userId.toString(),
            ),
        )
        if (entryHandler.localParticipantOrNull() == null) {
            return Result.failure(IllegalStateException("Cannot start call while unauthenticated"))
        }
        return callRepository.createOutgoingSession(
            conversationId = conversationId,
            callType = callType,
            calleeUserId = remoteUser.userId,
        ).fold(
            onSuccess = { session ->
                stateMachine.setOutgoing(session = session, remoteUser = remoteUser)
                CallDiagnostics.stage(
                    stage = "CALL_OUTGOING_RINGING",
                    callSessionId = session.id,
                )
                Result.success(Unit)
            },
            onFailure = { error -> handleOutgoingFailure(error) },
        )
    }

    override suspend fun redialFromHistory(
        callHistoryId: Int,
        remoteUser: CallParticipant,
    ): Result<Unit> {
        ensureNotBusy()?.let { return it }
        CallDiagnostics.stage(
            stage = "CALL_REDIAL_REQUESTED",
            extra = mapOf(
                "callHistoryId" to callHistoryId.toString(),
                "remoteUserId" to remoteUser.userId.toString(),
            ),
        )
        if (entryHandler.localParticipantOrNull() == null) {
            return Result.failure(IllegalStateException("Cannot redial while unauthenticated"))
        }
        return callRepository.redialFromHistory(callHistoryId).fold(
            onSuccess = { session ->
                stateMachine.setOutgoing(session = session, remoteUser = remoteUser)
                CallDiagnostics.stage(
                    stage = "CALL_OUTGOING_RINGING",
                    callSessionId = session.id,
                    extra = mapOf("via" to "redial"),
                )
                Result.success(Unit)
            },
            onFailure = { error -> handleOutgoingFailure(error) },
        )
    }

    suspend fun onIncomingPush(
        callSessionId: String,
        callerId: Int?,
        callerName: String?,
        callType: CallType,
        conversationId: Int?,
    ) {
        entryHandler.onIncomingPush(
            callSessionId = callSessionId,
            callerId = callerId,
            callerName = callerName,
            callType = callType,
            conversationId = conversationId,
        )
    }

    suspend fun onIncomingFromNavigation(callSessionId: String) {
        entryHandler.onIncomingFromNavigation(callSessionId)
    }

    fun onSignalingEvent(event: CallSignalingEvent) {
        signalingHandler.onSignalingEvent(event)
    }

    override suspend fun answerIncoming(): Result<Unit> = answerMutex.withLock {
        val incoming = stateMachine.currentState() as? CallState.Incoming
            ?: return@withLock Result.failure(IllegalStateException("No incoming call to answer"))
        CallDiagnostics.stage(stage = "CALL_ACCEPT_REQUESTED", callSessionId = incoming.session.id)
        stateMachine.setConnecting(
            session = incoming.session,
            direction = CallDirection.Incoming,
        )
        return@withLock callRepository.answerSession(incoming.session.id).fold(
            onSuccess = {
                CallDiagnostics.stage(
                    stage = "CALL_CONNECTING",
                    callSessionId = incoming.session.id,
                    extra = mapOf("direction" to CallDirection.Incoming.name),
                )
                reconnectHandler.reconcileIfSocketReady()
                Result.success(Unit)
            },
            onFailure = { error ->
                if (stateMachine.currentState() is CallState.Connecting) {
                    stateMachine.setIncoming(
                        session = incoming.session,
                        remoteUser = incoming.remoteUser,
                    )
                }
                CallDiagnostics.stage(
                    stage = "CALL_ACCEPT_FAILED",
                    callSessionId = incoming.session.id,
                    extra = mapOf("error" to (error.message ?: "unknown")),
                )
                Result.failure(error)
            },
        )
    }

    override suspend fun rejectIncoming(): Result<Unit> {
        val incoming = stateMachine.currentState() as? CallState.Incoming
            ?: return Result.failure(IllegalStateException("No incoming call to reject"))
        CallDiagnostics.stage(stage = "CALL_REJECT_REQUESTED", callSessionId = incoming.session.id)
        return callRepository.rejectSession(incoming.session.id).mapCatching {
            stateMachine.setEnded(
                session = incoming.session,
                reason = CallEndReason.Rejected,
            )
            mediaLifecycle.dismissEndedSoon(incoming.session.id)
            Unit
        }
    }

    override suspend fun endActiveCall(): Result<Unit> = endMutex.withLock {
        val session = stateMachine.currentSessionOrNull()
            ?: resolvePersistedSession()
            ?: return@withLock Result.failure(IllegalStateException("No active call to end"))
        CallDiagnostics.stage(stage = "CALL_END_REQUESTED", callSessionId = session.id)
        val remoteResult = callRepository.endSession(session.id)
        stateMachine.setEnded(
            session = session,
            reason = CallEndReason.LocalHangup,
        )
        mediaLifecycle.dismissEndedSoon(session.id)
        return@withLock remoteResult
    }

    override fun dismissOverlay() {
        stateMachine.dismissOverlayIfTerminal()
    }

    private fun ensureNotBusy(): Result<Unit>? {
        if (!stateMachine.hasActiveCall()) return null
        CallDiagnostics.stage(
            stage = "CALL_BUSY_BLOCKED",
            callSessionId = stateMachine.currentSessionOrNull()?.id,
        )
        return Result.failure(IllegalStateException("A call is already in progress"))
    }

    private suspend fun handleOutgoingFailure(
        error: Throwable,
    ): Result<Unit> {
        if (error is ApiException.CallAlreadyActive) {
            return recoverExistingCall(error.callSessionId)
        }
        stateMachine.setFailed(
            session = null,
            error = CallError(
                code = "outgoing_start_failed",
                message = error.message ?: "Failed to start call",
            ),
        )
        return Result.failure(error)
    }

    private suspend fun recoverExistingCall(callSessionId: String): Result<Unit> {
        CallDiagnostics.stage(
            stage = "CALL_RECOVER_EXISTING",
            callSessionId = callSessionId,
        )
        return runCatching {
            reconnectHandler.recoverSession(callSessionId)
            check(stateMachine.hasActiveCall()) { "Failed to restore active call" }
        }
    }

    private suspend fun resolvePersistedSession(): com.yapyap.feature.call.domain.entity.CallSession? {
        val sessionId = activeCallTracker.read() ?: return null
        return callRepository.fetchSession(sessionId).getOrNull()
    }
}
