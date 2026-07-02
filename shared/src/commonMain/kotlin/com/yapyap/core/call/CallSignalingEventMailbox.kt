package com.yapyap.core.call

import com.yapyap.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serializes call signaling events through a single consumer coroutine.
 * Producers enqueue via [enqueue]; [start] launches the mailbox actor once per scope.
 */
internal class CallSignalingEventMailbox(
    private val dispatcherProvider: DispatcherProvider,
    private val processor: suspend (CallSignalingEvent) -> Unit,
) {
    private val channel = Channel<CallSignalingEvent>(capacity = Channel.UNLIMITED)
    private var consumerJob: Job? = null

    fun start(parentScope: CoroutineScope) {
        if (consumerJob?.isActive == true) return
        consumerJob = parentScope.launch(dispatcherProvider.default) {
            consumeEvents()
        }
        consumerJob?.invokeOnCompletion {
            channel.close()
        }
    }

    fun enqueue(event: CallSignalingEvent): Boolean {
        val success = channel.trySend(event).isSuccess
        if (success) {
            CallDiagnostics.stage(
                stage = "CALL_SIGNALING_MAILBOX_ENQUEUED",
                callSessionId = event.callSessionIdOrNull(),
                extra = mapOf("type" to event::class.simpleName.orEmpty()),
            )
        }
        return success
    }

    private fun CallSignalingEvent.callSessionIdOrNull(): String? = when (this) {
        is CallSignalingEvent.Answer -> callSessionId
        is CallSignalingEvent.End -> callSessionId
        is CallSignalingEvent.Sync -> callSessionId
        is CallSignalingEvent.IceCandidate -> callSessionId
        is CallSignalingEvent.Invite -> callSessionId
        is CallSignalingEvent.Reject -> callSessionId
    }

    fun close() {
        channel.close()
    }

    private suspend fun consumeEvents() {
        try {
            for (event in channel) {
                processor(event)
            }
        } finally {
            channel.close()
        }
    }
}
