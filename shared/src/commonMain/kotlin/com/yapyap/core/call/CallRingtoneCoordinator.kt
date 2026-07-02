package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.call.domain.entity.CallState
import com.yapyap.feature.call.domain.reader.CallSessionReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Plays incoming ringtone / outgoing ringback while the call is ringing.
 * Stops automatically on accept, reject, end, or timeout transitions.
 */
class CallRingtoneCoordinator(
    private val callSessionReader: CallSessionReader,
    private val ringtoneController: CallRingtoneController,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun start(parentScope: CoroutineScope) {
        parentScope.launch(dispatcherProvider.default) {
            callSessionReader.state
                .distinctUntilChangedBy { ringingKey(it) }
                .collect { state -> syncRingtone(state) }
        }
    }

    private fun syncRingtone(state: CallState) {
        when (state) {
            is CallState.Incoming -> {
                CallDiagnostics.stage(stage = "CALL_RINGTONE_INCOMING")
                ringtoneController.startIncomingRingtone()
            }
            is CallState.Outgoing -> {
                CallDiagnostics.stage(stage = "CALL_RINGBACK_OUTGOING")
                ringtoneController.startOutgoingRingback()
            }
            else -> ringtoneController.stopAll()
        }
    }

    private fun ringingKey(state: CallState): String = when (state) {
        is CallState.Incoming -> "incoming"
        is CallState.Outgoing -> "outgoing"
        else -> "idle"
    }
}
