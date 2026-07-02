package com.yapyap.core.realtime

import com.yapyap.feature.chat.data.dto.MessageDto
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatRealtimeClient {
    val connectionState: StateFlow<RealtimeConnectionState>
    val events: SharedFlow<SocketEvent>

    val isReady: Boolean

    fun connectionDiagnostics(): SocketConnectionDiagnostics

    fun connect(accessToken: String)

    fun disconnect()

    fun refreshAccessToken(accessToken: String)

    suspend fun sendMessage(payload: SocketSendMessagePayload): Result<MessageDto>

    fun emitTypingStart(conversationId: Int)

    fun emitTypingStop(conversationId: Int)

    suspend fun markRead(conversationId: Int, messageId: Int?): Result<Unit>

    suspend fun markDelivered(conversationId: Int, messageId: Int): Result<Unit>

    suspend fun acceptCall(callSessionId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Call signaling not supported"))

    suspend fun rejectCall(callSessionId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Call signaling not supported"))

    suspend fun endCall(callSessionId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Call signaling not supported"))
}
