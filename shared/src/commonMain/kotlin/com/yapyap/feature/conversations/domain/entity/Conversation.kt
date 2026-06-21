package com.yapyap.feature.conversations.domain.entity

data class Conversation(
    val id: Int,
    val type: String,
    val createdAt: String,
    val updatedAt: String,
    val unreadCount: Int,
    val lastMessage: LastMessage?,
    val partner: ConversationPartner? = null,
)

val Conversation.displayTitle: String
    get() = partner?.displayName() ?: "Chat #$id"
