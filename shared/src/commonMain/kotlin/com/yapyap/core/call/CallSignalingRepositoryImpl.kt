package com.yapyap.core.call

import kotlinx.coroutines.CoroutineScope

/**
 * Receives call signaling events forwarded from [com.yapyap.core.realtime.ChatRealtimeRepository].
 * Does not subscribe to the socket directly — a single collector forwards call events to avoid
 * losing [call:incoming] when this repository starts after the chat collector.
 */
class CallSignalingRepositoryImpl(
    private val callCoordinator: Lazy<CallCoordinator>,
) : CallSignalingRepository {
    override fun start(parentScope: CoroutineScope) {
        CallDiagnostics.stage(stage = "CALL_SIGNALING_REPO_STARTED")
    }

    override fun handleEvent(event: CallSignalingEvent) {
        CallDiagnostics.stage(
            stage = "CALL_SIGNALING_REPO_RECEIVED",
            callSessionId = event.callSessionIdOrNull(),
            extra = mapOf("type" to event::class.simpleName.orEmpty()),
        )
        callCoordinator.value.onSignalingEvent(event)
    }

    private fun CallSignalingEvent.callSessionIdOrNull(): String? = when (this) {
        is CallSignalingEvent.Answer -> callSessionId
        is CallSignalingEvent.End -> callSessionId
        is CallSignalingEvent.Sync -> callSessionId
        is CallSignalingEvent.IceCandidate -> callSessionId
        is CallSignalingEvent.Invite -> callSessionId
        is CallSignalingEvent.Reject -> callSessionId
    }
}
