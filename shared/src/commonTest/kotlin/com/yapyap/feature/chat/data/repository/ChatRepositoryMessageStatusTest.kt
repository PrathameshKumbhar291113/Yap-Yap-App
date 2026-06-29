package com.yapyap.feature.chat.data.repository

import com.yapyap.core.realtime.NoopMessageDeliveryAcknowledgementGateway
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.realtime.SocketConnectionDiagnostics
import com.yapyap.core.realtime.SocketEvent
import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.data.remote.ChatApi
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatRepositoryMessageStatusTest {

    @Test
    fun ignoresSelfReadOnOutgoingMessage() = runTest {
        val repository = createRepository()
        seedMessage(
            repository = repository,
            id = 42,
            senderId = SENDER_ID,
            status = MessageStatus.SENT,
        )

        repository.applyMessageStatus(
            conversationId = CONVERSATION_ID,
            messageId = 42,
            status = MessageStatus.READ,
            readerUserId = SENDER_ID,
        )

        assertEquals(
            MessageStatus.SENT,
            repository.observeMessages(CONVERSATION_ID).first().single().status,
        )
    }

    @Test
    fun appliesRecipientReadOnOutgoingMessage() = runTest {
        val repository = createRepository()
        seedMessage(
            repository = repository,
            id = 42,
            senderId = SENDER_ID,
            status = MessageStatus.DELIVERED,
        )

        repository.applyMessageStatus(
            conversationId = CONVERSATION_ID,
            messageId = 42,
            status = MessageStatus.READ,
            readerUserId = RECIPIENT_ID,
        )

        assertEquals(
            MessageStatus.READ,
            repository.observeMessages(CONVERSATION_ID).first().single().status,
        )
    }

    @Test
    fun appliesRecipientDeliveredOnOutgoingMessage() = runTest {
        val repository = createRepository()
        seedMessage(
            repository = repository,
            id = 7,
            senderId = SENDER_ID,
            status = MessageStatus.SENT,
        )

        repository.applyMessageStatus(
            conversationId = CONVERSATION_ID,
            messageId = 7,
            status = MessageStatus.DELIVERED,
            readerUserId = RECIPIENT_ID,
        )

        assertEquals(
            MessageStatus.DELIVERED,
            repository.observeMessages(CONVERSATION_ID).first().single().status,
        )
    }

    private suspend fun seedMessage(
        repository: ChatRepositoryImpl,
        id: Int,
        senderId: Int,
        status: MessageStatus,
    ) {
        repository.upsertIncomingMessage(
            Message(
                id = id,
                conversationId = CONVERSATION_ID,
                senderId = senderId,
                content = "hello",
                messageType = MessageType.TEXT,
                mediaUrl = null,
                status = status,
                clientMessageId = "client-$id",
                createdAt = "2026-01-01T00:00:00Z",
                isPending = false,
            ),
        )
    }

    private fun createRepository(): ChatRepositoryImpl {
        val engine = MockEngine {
            respond("", status = HttpStatusCode.NotFound)
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(com.yapyap.core.network.appJson)
            }
        }
        return createTestChatRepository(
            chatApi = ChatApi(httpClient),
            chatRealtimeClient = NoopChatRealtimeClient,
        )
    }

    private object NoopChatRealtimeClient : ChatRealtimeClient {
        override val connectionState = MutableStateFlow<RealtimeConnectionState>(
            RealtimeConnectionState.Disconnected,
        )
        override val events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 1)
        override val isReady = false

        override fun connectionDiagnostics() = SocketConnectionDiagnostics(
            connected = false,
            authenticated = false,
            socketId = null,
            userId = null,
        )

        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit
        override suspend fun sendMessage(payload: com.yapyap.core.realtime.SocketSendMessagePayload): Result<MessageDto> =
            Result.failure(IllegalStateException("not connected"))
        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?): Result<Unit> =
            Result.failure(IllegalStateException("not connected"))

        override suspend fun markDelivered(conversationId: Int, messageId: Int): Result<Unit> =
            Result.failure(IllegalStateException("not connected"))
    }

    private companion object {
        const val CONVERSATION_ID = 1
        const val SENDER_ID = 10
        const val RECIPIENT_ID = 20
    }
}
