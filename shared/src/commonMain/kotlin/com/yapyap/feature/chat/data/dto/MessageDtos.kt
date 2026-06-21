package com.yapyap.feature.chat.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessagesPageDto(
    val messages: List<MessageDto>,
    val nextCursor: Int? = null,
)

@Serializable
data class MessageDto(
    val id: Int,
    @SerialName("conversation_id") val conversationId: Int,
    @SerialName("sender_id") val senderId: Int,
    val content: String? = null,
    @SerialName("message_type") val messageType: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    val status: String? = null,
    @SerialName("client_message_id") val clientMessageId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class SendMessageRequestDto(
    val content: String? = null,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("client_message_id") val clientMessageId: String? = null,
)

@Serializable
data class SendMessageDataDto(
    val message: MessageDto,
)

@Serializable
data class MarkReadRequestDto(
    val messageId: Int? = null,
)
