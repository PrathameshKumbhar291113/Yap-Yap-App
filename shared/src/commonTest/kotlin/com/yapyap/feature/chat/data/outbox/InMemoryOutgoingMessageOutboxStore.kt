package com.yapyap.feature.chat.data.outbox

import com.yapyap.feature.chat.domain.entity.OutboxMessage
import com.yapyap.feature.chat.domain.entity.OutboxStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryOutgoingMessageOutboxStore : OutgoingMessageOutboxPersistence {
    private val mutex = Mutex()
    private val messages = linkedMapOf<String, OutboxMessage>()
    private var enqueueSequence = 0L

    override suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    override fun selectAllOrdered(): List<OutboxMessage> = messages.values.sortedBy { it.enqueueOrder }

    override fun selectPendingOrdered(): List<OutboxMessage> =
        messages.values
            .filter { it.status == OutboxStatus.QUEUED || it.status == OutboxStatus.SENDING }
            .sortedBy { it.enqueueOrder }

    override fun selectForConversation(conversationId: Int): List<OutboxMessage> =
        messages.values.filter { it.conversationId == conversationId }.sortedBy { it.enqueueOrder }

    override fun selectByClientMessageId(clientMessageId: String): OutboxMessage? = messages[clientMessageId]

    override fun nextEnqueueOrder(): Long {
        enqueueSequence += 1
        return enqueueSequence
    }

    override fun insert(message: OutboxMessage) {
        messages[message.clientMessageId] = message
    }

    override fun updateStatus(
        clientMessageId: String,
        status: OutboxStatus,
        retryCount: Int,
        nextRetryAtMs: Long,
    ) {
        val existing = messages[clientMessageId] ?: return
        messages[clientMessageId] = existing.copy(
            status = status,
            retryCount = retryCount,
            nextRetryAtMs = nextRetryAtMs,
        )
    }

    override fun resetSendingToQueued() {
        messages.replaceAll { _, message ->
            if (message.status == OutboxStatus.SENDING) {
                message.copy(status = OutboxStatus.QUEUED)
            } else {
                message
            }
        }
    }

    override fun delete(clientMessageId: String) {
        messages.remove(clientMessageId)
    }

    override fun pendingCount(): Long =
        messages.values.count { it.status == OutboxStatus.QUEUED || it.status == OutboxStatus.SENDING }.toLong()
}
