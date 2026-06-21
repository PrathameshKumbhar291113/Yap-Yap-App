package com.yapyap.feature.conversations.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationsListDataDto(
    val conversations: List<ConversationListItemDto>,
)

@Serializable
data class ConversationListItemDto(
    val id: Int,
    val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("unread_count") val unreadCount: Int,
    @SerialName("last_message") val lastMessage: LastMessageDto? = null,
    val partner: ConversationPartnerDto? = null,
)

@Serializable
data class ConversationPartnerDto(
    val id: Int,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
data class LastMessageDto(
    val id: Int,
    val content: String? = null,
    @SerialName("message_type") val messageType: String,
    @SerialName("sender_id") val senderId: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DirectConversationRequestDto(
    val otherUserId: Int? = null,
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
data class DirectConversationDataDto(
    val conversation: ConversationSummaryDto,
)

@Serializable
data class ConversationSummaryDto(
    val id: Int,
    val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val partner: ConversationPartnerDto? = null,
)
