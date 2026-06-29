package com.yapyap.core.realtime.reconcile

import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.entity.ConversationRepairTarget

/**
 * Compares conversation list snapshots and selects conversations needing chat cache repair.
 */
object ConversationChangeDetector {

    fun detectChangedConversations(
        local: List<Conversation>,
        server: List<Conversation>,
    ): List<ConversationRepairTarget> {
        val localById = local.associateBy { it.id }
        return server.mapNotNull { serverConversation ->
            val localConversation = localById[serverConversation.id]
            buildRepairTarget(localConversation, serverConversation)
        }
    }

    private fun buildRepairTarget(
        local: Conversation?,
        server: Conversation,
    ): ConversationRepairTarget? {
        val serverLast = server.lastMessage ?: return null
        val localLast = local?.lastMessage
        val reasons = mutableListOf<String>()

        if (local == null) {
            reasons += "conversation_new_to_client"
        }
        if (localLast == null && serverLast.id > 0) {
            reasons += "local_last_message_missing"
        }
        if (localLast != null && serverLast.id > localLast.id) {
            reasons += "last_message_id_advanced"
        }
        if (localLast != null && serverLast.createdAt > localLast.createdAt) {
            reasons += "last_message_timestamp_advanced"
        }
        if (local != null && server.updatedAt > local.updatedAt) {
            reasons += "conversation_updated_at_advanced"
        }
        if (local != null && server.unreadCount > local.unreadCount) {
            reasons += "unread_count_advanced"
        }

        if (reasons.isEmpty()) {
            return null
        }

        return ConversationRepairTarget(
            conversationId = server.id,
            localLatestMessageId = localLast?.id,
            localLatestTimestamp = localLast?.createdAt,
            localUnreadCount = local?.unreadCount ?: 0,
            serverLatestMessageId = serverLast.id,
            serverLatestTimestamp = serverLast.createdAt,
            serverUnreadCount = server.unreadCount,
            changeReason = reasons.joinToString(","),
        )
    }

    /**
     * Conversations whose list metadata matches the server but whose chat cache may contain
     * interior sequence gaps (e.g. socket delivered 100 and 102 but missed 101).
     */
    fun detectCacheGapCheckTargets(
        server: List<Conversation>,
        excludedConversationIds: Set<Int>,
        needsGapRepair: (conversationId: Int, serverLatestMessageId: Int) -> Boolean,
    ): List<ConversationRepairTarget> {
        return server.mapNotNull { serverConversation ->
            if (serverConversation.id in excludedConversationIds) return@mapNotNull null
            val serverLast = serverConversation.lastMessage ?: return@mapNotNull null
            if (!needsGapRepair(serverConversation.id, serverLast.id)) return@mapNotNull null
            ConversationRepairTarget(
                conversationId = serverConversation.id,
                localLatestMessageId = serverLast.id,
                localLatestTimestamp = serverLast.createdAt,
                localUnreadCount = serverConversation.unreadCount,
                serverLatestMessageId = serverLast.id,
                serverLatestTimestamp = serverLast.createdAt,
                serverUnreadCount = serverConversation.unreadCount,
                changeReason = "cache_interior_gap_check",
            )
        }
    }
}
