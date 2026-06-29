package com.yapyap.feature.chat.domain.repository

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus

interface OutgoingMessageCacheWriter {
    suspend fun upsertOutboxMessage(message: Message)
    suspend fun markOutboxSending(clientMessageId: String)
    suspend fun markOutboxSent(clientMessageId: String, serverMessage: Message)
    suspend fun markOutboxFailed(clientMessageId: String)
    suspend fun removeOutboxMessage(clientMessageId: String)
    suspend fun pendingOutboxMessages(conversationId: Int): List<Message>
}
