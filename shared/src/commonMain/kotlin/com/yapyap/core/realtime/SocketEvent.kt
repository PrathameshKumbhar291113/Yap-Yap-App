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
}
