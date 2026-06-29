package com.yapyap.feature.chat.data.outbox

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.chat.util.currentTimestampIso
import com.yapyap.feature.chat.util.newClientMessageId
import kotlinx.coroutines.CoroutineScope

class FakeOutgoingMessageOutboxGateway(
    private val cacheWriter: com.yapyap.feature.chat.domain.repository.OutgoingMessageCacheWriter,
    private val sendHandler: suspend (
        conversationId: Int,
        senderId: Int,
        content: String,
        clientMessageId: String,
    ) -> Result<Message>,
) : OutgoingMessageOutboxGateway {
    private val pending = linkedMapOf<String, Message>()

    override fun start(parentScope: CoroutineScope) = Unit

    override suspend fun enqueue(
        conversationId: Int,
        senderId: Int,
        content: String,
    ): Message {
        val clientMessageId = newClientMessageId()
        val message = Message(
            id = null,
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            messageType = MessageType.TEXT,
            mediaUrl = null,
            status = MessageStatus.QUEUED,
            clientMessageId = clientMessageId,
            createdAt = currentTimestampIso(),
            isPending = true,
        )
        pending[clientMessageId] = message
        cacheWriter.upsertOutboxMessage(message)
        flushPendingOutgoingMessages("enqueue")
        return message
    }

    override suspend fun retry(clientMessageId: String, trigger: String) {
        flushPendingOutgoingMessages(trigger)
    }

    override suspend fun delete(clientMessageId: String, trigger: String) {
        pending.remove(clientMessageId)
        cacheWriter.removeOutboxMessage(clientMessageId)
    }

    override suspend fun flushPendingOutgoingMessages(trigger: String) {
        val entries = pending.values.toList()
        for (message in entries) {
            val clientMessageId = message.clientMessageId ?: continue
            cacheWriter.markOutboxSending(clientMessageId)
            val result = sendHandler(
                message.conversationId,
                message.senderId,
                message.content.orEmpty(),
                clientMessageId,
            )
            if (result.isSuccess) {
                pending.remove(clientMessageId)
                cacheWriter.markOutboxSent(clientMessageId, result.getOrThrow())
            } else {
                cacheWriter.markOutboxFailed(clientMessageId)
            }
        }
    }

    override fun pendingMessagesFor(conversationId: Int): List<Message> {
        return pending.values.filter { it.conversationId == conversationId }
    }
}
