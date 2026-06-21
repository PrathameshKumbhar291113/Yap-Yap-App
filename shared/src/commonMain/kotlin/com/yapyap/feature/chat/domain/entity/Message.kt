package com.yapyap.feature.chat.domain.entity

data class Message(
    val id: Int?,
    val conversationId: Int,
    val senderId: Int,
    val content: String?,
    val messageType: MessageType,
    val mediaUrl: String?,
    val status: MessageStatus,
    val clientMessageId: String?,
    val createdAt: String,
    val updatedAt: String? = null,
    val deletedAt: String? = null,
    val isPending: Boolean = false,
) {
    val stableKey: String
        get() = clientMessageId ?: id?.toString() ?: createdAt
}
