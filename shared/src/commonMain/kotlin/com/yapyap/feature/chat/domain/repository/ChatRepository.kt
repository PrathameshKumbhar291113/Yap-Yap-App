package com.yapyap.feature.chat.domain.repository

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.ChatSyncSnapshot
import com.yapyap.feature.chat.domain.entity.ReconcileResult
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

    suspend fun retryOutgoingMessage(clientMessageId: String): Result<Unit>

    suspend fun deleteOutgoingMessage(clientMessageId: String): Result<Unit>

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

    /** Local cache snapshot for reconnect reconciliation. */
    fun getLocalSyncSnapshot(conversationId: Int): ChatSyncSnapshot

    /**
     * True when local tail matches server tail but cached server IDs may contain interior holes
     * or an incomplete prefix (socket-only cache).
     */
    fun needsInteriorGapRepair(conversationId: Int, serverLatestMessageId: Int?): Boolean

    /**
     * Downloads and merges missed messages for [conversationId] based on [serverLatestMessageId].
     * Preserves pending outbox messages and ignores duplicates.
     */
    suspend fun reconcileConversation(
        conversationId: Int,
        serverLatestMessageId: Int?,
        trigger: String,
    ): Result<ReconcileResult>
}
