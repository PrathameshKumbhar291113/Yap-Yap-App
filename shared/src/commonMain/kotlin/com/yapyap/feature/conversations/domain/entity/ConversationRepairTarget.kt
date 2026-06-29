package com.yapyap.feature.conversations.domain.entity

/**
 * A conversation whose server metadata advanced beyond local state and needs chat repair.
 */
data class ConversationRepairTarget(
    val conversationId: Int,
    val localLatestMessageId: Int?,
    val localLatestTimestamp: String?,
    val localUnreadCount: Int,
    val serverLatestMessageId: Int?,
    val serverLatestTimestamp: String,
    val serverUnreadCount: Int,
    val changeReason: String,
)
