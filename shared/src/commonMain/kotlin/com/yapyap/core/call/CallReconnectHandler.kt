package com.yapyap.core.call

import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class CallReconnectHandler(
    private val chatRealtimeClient: ChatRealtimeClient,
    private val stateMachine: CallStateMachine,
    private val reconciler: CallSessionReconciler,
    private val activeCallTracker: ActiveCallSessionTracker,
    private val dispatcherProvider: DispatcherProvider,
) {
    private var scope: CoroutineScope? = null
    private var socketWasReady: Boolean = false

    fun bindScope(parentScope: CoroutineScope) {
        scope = parentScope
    }

    fun startObserving(parentScope: CoroutineScope) {
        bindScope(parentScope)
        parentScope.launch(dispatcherProvider.default) {
            chatRealtimeClient.connectionState.collect { state ->
                onConnectionStateChanged(state)
            }
        }
        if (chatRealtimeClient.connectionState.value is RealtimeConnectionState.Ready) {
            socketWasReady = true
            restorePersistedSessionIfNeeded()
        }
    }

    fun reconcileIfSocketReady() {
        if (chatRealtimeClient.connectionState.value !is RealtimeConnectionState.Ready) {
            return
        }
        scope?.launch(dispatcherProvider.default) {
            reconciler.reconcileAfterSocketReady()
        }
    }

    suspend fun recoverSession(sessionId: String) {
        reconciler.restorePersistedSession(sessionId)
    }

    fun restorePersistedSessionIfNeeded() {
        if (stateMachine.hasActiveCall()) return
        val sessionId = activeCallTracker.read() ?: return
        if (chatRealtimeClient.connectionState.value !is RealtimeConnectionState.Ready) {
            return
        }
        scope?.launch(dispatcherProvider.default) {
            reconciler.restorePersistedSession(sessionId)
        }
    }

    fun reconcileActiveIncomingIfNeeded() {
        if (chatRealtimeClient.connectionState.value !is RealtimeConnectionState.Ready) {
            return
        }
        scope?.launch(dispatcherProvider.default) {
            reconciler.reconcileActiveIncomingCall()
        }
    }

    internal fun onConnectionStateChanged(state: RealtimeConnectionState) {
        val isReady = state is RealtimeConnectionState.Ready

        if (socketWasReady && !isReady) {
            onSocketLost(state)
        }

        if (!socketWasReady && isReady) {
            onSocketReady()
        }

        if (state is RealtimeConnectionState.Disconnected &&
            stateMachine.currentState() is CallState.Reconnecting
        ) {
            reconciler.failRecovery()
        }

        socketWasReady = isReady
    }

    private fun onSocketLost(state: RealtimeConnectionState) {
        if (!stateMachine.isRecoverableCallState()) {
            return
        }
        reconciler.enterReconnectingFromSocketLoss()
        CallDiagnostics.stage(
            stage = "CALL_SOCKET_LOST",
            callSessionId = stateMachine.currentSessionOrNull()?.id,
            extra = mapOf("socketState" to state::class.simpleName.orEmpty()),
        )
    }

    private fun onSocketReady() {
        CallDiagnostics.stage(stage = "CALL_SOCKET_READY")
        restorePersistedSessionIfNeeded()
        reconcileActiveIncomingIfNeeded()
        if (!stateMachine.shouldReconcileOnSocketReady()) {
            return
        }
        scope?.launch(dispatcherProvider.default) {
            reconciler.reconcileAfterSocketReady()
        }
    }
}
