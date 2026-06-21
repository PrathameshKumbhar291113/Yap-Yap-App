package com.yapyap.feature.chat.domain.repository

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(conversationId: Int): Flow<List<Message>>

    suspend fun loadLatestMessages(conversationId: Int): Result<Unit>

    suspend fun loadOlderMessages(conversationId: Int): Result<Unit>

    suspend fun sendMessage(
        conversationId: Int,
        senderId: Int,
        content: String,
    ): Result<Message>

    suspend fun markRead(conversationId: Int, messageId: Int? = null): Result<Unit>

    fun hasMoreMessages(conversationId: Int): Boolean

    fun isLoadingOlder(conversationId: Int): Boolean

    suspend fun upsertIncomingMessage(message: Message)

    suspend fun applyMessageStatus(
        conversationId: Int,
        messageId: Int,
        status: MessageStatus,
        readerUserId: Int? = null,
    )
}
