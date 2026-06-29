package com.yapyap.feature.chat.data.repository

import com.yapyap.core.realtime.NoopMessageDeliveryAcknowledgementGateway
import com.yapyap.feature.chat.data.outbox.FakeOutgoingMessageOutboxGateway
import com.yapyap.feature.chat.data.outbox.OutgoingMessageOutboxGateway
import com.yapyap.feature.chat.data.remote.ChatApi
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.repository.OutgoingMessageCacheWriter
import com.yapyap.core.realtime.ChatRealtimeClient

fun createTestChatRepository(
    chatApi: ChatApi,
    chatRealtimeClient: ChatRealtimeClient,
    sendHandler: suspend (
        conversationId: Int,
        senderId: Int,
        content: String,
        clientMessageId: String,
    ) -> Result<Message> = { _, _, _, _ ->
        Result.failure(IllegalStateException("send disabled in test"))
    },
): ChatRepositoryImpl {
    lateinit var repository: ChatRepositoryImpl
    val outbox: OutgoingMessageOutboxGateway = FakeOutgoingMessageOutboxGateway(
        cacheWriter = object : OutgoingMessageCacheWriter {
            override suspend fun upsertOutboxMessage(message: Message) =
                repository.upsertOutboxMessage(message)

            override suspend fun markOutboxSending(clientMessageId: String) =
                repository.markOutboxSending(clientMessageId)

            override suspend fun markOutboxSent(clientMessageId: String, serverMessage: Message) =
                repository.markOutboxSent(clientMessageId, serverMessage)

            override suspend fun markOutboxFailed(clientMessageId: String) =
                repository.markOutboxFailed(clientMessageId)

            override suspend fun removeOutboxMessage(clientMessageId: String) =
                repository.removeOutboxMessage(clientMessageId)

            override suspend fun pendingOutboxMessages(conversationId: Int): List<Message> =
                repository.pendingOutboxMessages(conversationId)
        },
        sendHandler = sendHandler,
    )
    repository = ChatRepositoryImpl(
        chatApi = chatApi,
        chatRealtimeClient = chatRealtimeClient,
        deliveryAckService = NoopMessageDeliveryAcknowledgementGateway,
        outboxGateway = lazy { outbox },
    )
    return repository
}
