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

class ChatRepositoryOutboxDuplicateTest {

    @Test
    fun markOutboxSentThenSocketEcho_doesNotDuplicate() = runTest {
        val repository = createRepository()
        val clientMessageId = "offline-abc-123"

        repository.upsertOutboxMessage(
            queuedMessage(clientMessageId = clientMessageId, id = null),
        )
        repository.markOutboxSent(
            clientMessageId = clientMessageId,
            serverMessage = serverMessage(
                id = 42,
                clientMessageId = clientMessageId,
                status = MessageStatus.SENT,
            ),
        )
        repository.upsertIncomingMessage(
            serverMessage(
                id = 42,
                clientMessageId = clientMessageId,
                status = MessageStatus.SENT,
            ),
        )

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(1, messages.size)
        assertEquals(42, messages.single().id)
        assertEquals(clientMessageId, messages.single().clientMessageId)
    }

    @Test
    fun socketEchoBeforeMarkOutboxSent_doesNotDuplicate() = runTest {
        val repository = createRepository()
        val clientMessageId = "offline-def-456"

        repository.upsertOutboxMessage(
            queuedMessage(clientMessageId = clientMessageId, id = null),
        )
        repository.upsertIncomingMessage(
            serverMessage(
                id = 99,
                clientMessageId = clientMessageId,
                status = MessageStatus.SENT,
            ),
        )
        repository.markOutboxSent(
            clientMessageId = clientMessageId,
            serverMessage = serverMessage(
                id = 99,
                clientMessageId = clientMessageId,
                status = MessageStatus.SENT,
            ),
        )

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(1, messages.size)
        assertEquals(99, messages.single().id)
        assertEquals(clientMessageId, messages.single().clientMessageId)
    }

    @Test
    fun markOutboxSentWithSplitStableKeysThenSocketEcho_doesNotDuplicate() = runTest {
        val repository = createRepository()
        val clientMessageId = "offline-split-789"

        repository.upsertOutboxMessage(
            queuedMessage(clientMessageId = clientMessageId, id = null),
        )
        repository.markOutboxSent(
            clientMessageId = clientMessageId,
            serverMessage = serverMessage(
                id = 55,
                clientMessageId = null,
                status = MessageStatus.SENT,
            ),
        )
        repository.upsertIncomingMessage(
            serverMessage(
                id = 55,
                clientMessageId = clientMessageId,
                status = MessageStatus.SENT,
            ),
        )

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(1, messages.size)
        assertEquals(55, messages.single().id)
        assertEquals(clientMessageId, messages.single().clientMessageId)
    }

    @Test
    fun reconcileAfterMarkOutboxSent_doesNotDuplicate() = runTest {
        val repository = createRepository()
        val clientMessageId = "offline-reconcile-101"

        repository.upsertOutboxMessage(
            queuedMessage(clientMessageId = clientMessageId, id = null),
        )
        repository.markOutboxSent(
            clientMessageId = clientMessageId,
            serverMessage = serverMessage(
                id = 77,
                clientMessageId = null,
                status = MessageStatus.SENT,
            ),
        )

        repository.reconcileConversation(
            conversationId = CONVERSATION_ID,
            serverLatestMessageId = 77,
            trigger = "test",
        )

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(1, messages.size)
        assertEquals(77, messages.single().id)
        assertEquals(clientMessageId, messages.single().clientMessageId)
    }

    private fun createRepository(): ChatRepositoryImpl {
        val httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }) {
            install(ContentNegotiation) {
                json()
            }
        }
        return createTestChatRepository(
            chatApi = ChatApi(httpClient),
            chatRealtimeClient = NoopChatRealtimeClient(),
        )
    }

    private fun queuedMessage(clientMessageId: String, id: Int?): Message = Message(
        id = id,
        conversationId = CONVERSATION_ID,
        senderId = SENDER_ID,
        content = "queued offline",
        messageType = MessageType.TEXT,
        mediaUrl = null,
        status = MessageStatus.QUEUED,
        clientMessageId = clientMessageId,
        createdAt = "2026-01-01T00:00:00Z",
        isPending = true,
    )

    private fun serverMessage(
        id: Int,
        clientMessageId: String?,
        status: MessageStatus,
    ): Message = Message(
        id = id,
        conversationId = CONVERSATION_ID,
        senderId = SENDER_ID,
        content = "queued offline",
        messageType = MessageType.TEXT,
        mediaUrl = null,
        status = status,
        clientMessageId = clientMessageId,
        createdAt = "2026-01-01T00:00:01Z",
        isPending = false,
    )

    private class NoopChatRealtimeClient : ChatRealtimeClient {
        override val connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Disconnected)
        override val events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 1)
        override val isReady = false
        override fun connectionDiagnostics() = SocketConnectionDiagnostics(false, false, null, null)
        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit
        override suspend fun sendMessage(payload: com.yapyap.core.realtime.SocketSendMessagePayload) =
            Result.failure<MessageDto>(UnsupportedOperationException())
        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?) = Result.success(Unit)
        override suspend fun markDelivered(conversationId: Int, messageId: Int) = Result.success(Unit)
    }

    private companion object {
        const val CONVERSATION_ID = 1
        const val SENDER_ID = 2
    }
}
