package com.yapyap.core.call

import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.SocketConstants

class CallSignalingClientImpl(
    private val chatRealtimeClient: ChatRealtimeClient,
) : CallSignalingClient {

    override suspend fun emitAnswer(callSessionId: String): Result<Unit> {
        return emitCallAction(
            event = SocketConstants.CALL_ACCEPT,
            callSessionId = callSessionId,
        )
    }

    override suspend fun emitReject(callSessionId: String): Result<Unit> {
        return emitCallAction(
            event = SocketConstants.CALL_REJECT,
            callSessionId = callSessionId,
        )
    }

    override suspend fun emitEnd(callSessionId: String): Result<Unit> {
        return emitCallAction(
            event = SocketConstants.CALL_END,
            callSessionId = callSessionId,
        )
    }

    override suspend fun emitIceCandidate(callSessionId: String, candidate: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("WebRTC signaling is not enabled in Phase 2"))
    }

    private suspend fun emitCallAction(event: String, callSessionId: String): Result<Unit> {
        val result = when (event) {
            SocketConstants.CALL_ACCEPT -> chatRealtimeClient.acceptCall(callSessionId)
            SocketConstants.CALL_REJECT -> chatRealtimeClient.rejectCall(callSessionId)
            SocketConstants.CALL_END -> chatRealtimeClient.endCall(callSessionId)
            else -> Result.failure(IllegalArgumentException("Unsupported call event: $event"))
        }
        CallDiagnostics.socketEmit(
            event = event,
            callSessionId = callSessionId,
            success = result.isSuccess,
            error = result.exceptionOrNull()?.message,
        )
        return result
    }
}
