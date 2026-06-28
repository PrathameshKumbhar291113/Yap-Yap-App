package com.yapyap.core.realtime

import com.yapyap.feature.chat.data.dto.MessageDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SocketReadyPayload(
    val userId: Int,
)

@Serializable
data class MessageNewPayload(
    val conversationId: Int,
    val message: MessageDto,
)

@Serializable
data class MessageStatusPayload(
    val conversationId: Int,
    val messageId: Int,
    val status: String,
    val userId: Int,
)

@Serializable
data class TypingPayload(
    val conversationId: Int,
    val userId: Int,
    val isTyping: Boolean,
)

@Serializable
data class PresenceUpdatePayload(
    val userId: Int,
    val online: Boolean,
)

@Serializable
data class SocketSendMessagePayload(
    val conversationId: Int,
    val content: String? = null,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("client_message_id") val clientMessageId: String? = null,
)

@Serializable
data class SocketConversationPayload(
    val conversationId: Int,
)

@Serializable
data class SocketMessageReadPayload(
    val conversationId: Int,
    val messageId: Int? = null,
)

@Serializable
data class SocketMessageDeliveredPayload(
    val conversationId: Int,
    val messageId: Int,
)
