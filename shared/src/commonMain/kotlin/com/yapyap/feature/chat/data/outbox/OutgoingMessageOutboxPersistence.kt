package com.yapyap.feature.chat.data.outbox

import com.yapyap.feature.chat.domain.entity.OutboxMessage
import com.yapyap.feature.chat.domain.entity.OutboxStatus

interface OutgoingMessageOutboxPersistence {
    suspend fun <T> withLock(block: suspend () -> T): T

    fun selectAllOrdered(): List<OutboxMessage>

    fun selectPendingOrdered(): List<OutboxMessage>

    fun selectForConversation(conversationId: Int): List<OutboxMessage>

    fun selectByClientMessageId(clientMessageId: String): OutboxMessage?

    fun nextEnqueueOrder(): Long

    fun insert(message: OutboxMessage)

    fun updateStatus(
        clientMessageId: String,
        status: OutboxStatus,
        retryCount: Int,
        nextRetryAtMs: Long,
    )

    fun resetSendingToQueued()

    fun delete(clientMessageId: String)

    fun pendingCount(): Long
}
