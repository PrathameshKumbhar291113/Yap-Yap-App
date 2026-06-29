package com.yapyap.feature.chat.data.outbox

import com.yapyap.core.database.Outgoing_message_outbox
import com.yapyap.core.database.YapYapDatabase
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.chat.domain.entity.OutboxMessage
import com.yapyap.feature.chat.domain.entity.OutboxStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OutgoingMessageOutboxStore(
    database: YapYapDatabase,
) : OutgoingMessageOutboxPersistence {
    private val queries = database.outgoingMessageOutboxQueries
    private val mutex = Mutex()

    override suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    override fun selectAllOrdered(): List<OutboxMessage> =
        queries.selectAllOrdered().executeAsList().map { it.toDomain() }

    override fun selectPendingOrdered(): List<OutboxMessage> =
        queries.selectPendingOrdered().executeAsList().map { it.toDomain() }

    override fun selectForConversation(conversationId: Int): List<OutboxMessage> =
        queries.selectForConversation(conversationId.toLong()).executeAsList().map { it.toDomain() }

    override fun selectByClientMessageId(clientMessageId: String): OutboxMessage? =
        queries.selectByClientMessageId(clientMessageId).executeAsOneOrNull()?.toDomain()

    override fun nextEnqueueOrder(): Long {
        val current = queries.maxEnqueueOrder().executeAsOne()
        return (current.MAX ?: 0L) + 1L
    }

    override fun insert(message: OutboxMessage) {
        queries.insertOutboxMessage(
            client_message_id = message.clientMessageId,
            conversation_id = message.conversationId.toLong(),
            sender_id = message.senderId.toLong(),
            content = message.content,
            message_type = MessageType.toApi(message.messageType),
            status = message.status.toStorageValue(),
            created_at = message.createdAt,
            retry_count = message.retryCount.toLong(),
            next_retry_at_ms = message.nextRetryAtMs,
            enqueue_order = message.enqueueOrder,
        )
    }

    override fun updateStatus(
        clientMessageId: String,
        status: OutboxStatus,
        retryCount: Int,
        nextRetryAtMs: Long,
    ) {
        queries.updateOutboxStatus(
            status = status.toStorageValue(),
            retry_count = retryCount.toLong(),
            next_retry_at_ms = nextRetryAtMs,
            client_message_id = clientMessageId,
        )
    }

    override fun resetSendingToQueued() {
        queries.resetSendingToQueued()
    }

    override fun delete(clientMessageId: String) {
        queries.deleteOutboxMessage(clientMessageId)
    }

    override fun pendingCount(): Long = queries.countPending().executeAsOne()

    private fun Outgoing_message_outbox.toDomain(): OutboxMessage = OutboxMessage(
        clientMessageId = client_message_id,
        conversationId = conversation_id.toInt(),
        senderId = sender_id.toInt(),
        content = content,
        messageType = MessageType.fromApi(message_type),
        status = OutboxStatus.fromStorage(status),
        createdAt = created_at,
        retryCount = retry_count.toInt(),
        nextRetryAtMs = next_retry_at_ms,
        enqueueOrder = enqueue_order,
    )
}
