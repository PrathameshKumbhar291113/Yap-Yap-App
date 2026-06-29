package com.yapyap.feature.chat.data.outbox

import com.yapyap.feature.chat.domain.entity.Message
import kotlinx.coroutines.CoroutineScope

interface OutgoingMessageOutboxGateway {
    fun start(parentScope: CoroutineScope)
    suspend fun enqueue(
        conversationId: Int,
        senderId: Int,
        content: String,
    ): Message
    suspend fun retry(clientMessageId: String, trigger: String = "manual_retry")
    suspend fun delete(clientMessageId: String, trigger: String = "manual_delete")
    suspend fun flushPendingOutgoingMessages(trigger: String)
    fun pendingMessagesFor(conversationId: Int): List<Message>
}
