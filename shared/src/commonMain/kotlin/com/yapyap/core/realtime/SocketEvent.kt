package com.yapyap.core.realtime

import com.yapyap.feature.chat.data.dto.MessageDto

sealed interface SocketEvent {
    data class Ready(val userId: Int) : SocketEvent

    data class MessageNew(
        val conversationId: Int,
        val message: MessageDto,
    ) : SocketEvent

    data class MessageStatus(
        val conversationId: Int,
        val messageId: Int,
        val status: String,
        val userId: Int,
    ) : SocketEvent

    data class Typing(
        val conversationId: Int,
        val userId: Int,
        val isTyping: Boolean,
    ) : SocketEvent

    data class PresenceUpdate(
        val userId: Int,
        val online: Boolean,
    ) : SocketEvent

    data class CallIncoming(
        val callSessionId: String,
        val callerId: Int,
        val callerName: String?,
        val callType: String?,
        val conversationId: Int?,
        val roomName: String?,
    ) : SocketEvent

    data class CallAccepted(
        val callSessionId: String,
        val providerToken: String?,
        val livekitUrl: String?,
        val roomName: String?,
    ) : SocketEvent

    data class CallRejected(
        val callSessionId: String,
        val reason: String?,
    ) : SocketEvent

    data class CallEnded(
        val callSessionId: String,
        val durationSec: Int?,
    ) : SocketEvent

    data class CallSync(
        val callSessionId: String,
        val status: String,
        val endReason: String? = null,
        val durationSec: Int? = null,
        val event: String? = null,
        val conversationId: Int? = null,
    ) : SocketEvent
}
