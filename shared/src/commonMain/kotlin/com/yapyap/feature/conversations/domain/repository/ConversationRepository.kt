package com.yapyap.feature.conversations.domain.repository

import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.chat.domain.entity.Message
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>

    suspend fun refreshConversations(): Result<Unit>

    suspend fun createDirectConversation(contact: String): Result<Conversation>

    /**
     * Updates list ordering, last message, and unread count.
     * When [conversationId] is not in local state, inserts a minimal stub entry first.
     *
     * @return `true` if the conversation was previously unknown (caller may refresh to enrich).
     */
    fun applyIncomingMessage(
        conversationId: Int,
        message: Message,
        currentUserId: Int?,
        isViewingConversation: Boolean = false,
    ): Boolean

    /** Clears local unread badge after the server read cursor is updated. */
    fun applyConversationRead(conversationId: Int)

    /** Current in-memory conversation list (for pre-refresh snapshot). */
    fun snapshotConversations(): List<Conversation>
}
