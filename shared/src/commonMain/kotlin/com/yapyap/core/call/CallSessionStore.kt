package com.yapyap.core.call

import com.yapyap.feature.call.domain.entity.CallSession
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.reader.CallSessionReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallSessionStore(
    private val activeCallTracker: ActiveCallSessionTracker? = null,
) : CallSessionReader {
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()

    internal fun setState(newState: CallState) {
        val previous = _state.value
        if (previous != newState) {
            CallDiagnostics.stateChanged(
                from = previous,
                to = newState,
                callSessionId = newState.sessionOrNull()?.id ?: previous.sessionOrNull()?.id,
            )
        }
        _state.value = newState
        activeCallTracker?.onStateChanged(newState)
    }

    private fun CallState.sessionOrNull(): CallSession? = when (this) {
        is CallState.Outgoing -> session
        is CallState.Incoming -> session
        is CallState.Connecting -> session
        is CallState.Connected -> session
        is CallState.Reconnecting -> session
        is CallState.Ended -> session
        is CallState.Failed -> session
        CallState.Idle -> null
    }

    internal fun currentSessionOrNull(): CallSession? = when (val current = _state.value) {
        is CallState.Outgoing -> current.session
        is CallState.Incoming -> current.session
        is CallState.Connecting -> current.session
        is CallState.Connected -> current.session
        is CallState.Reconnecting -> current.session
        is CallState.Ended -> current.session
        is CallState.Failed -> current.session
        CallState.Idle -> null
    }
}
