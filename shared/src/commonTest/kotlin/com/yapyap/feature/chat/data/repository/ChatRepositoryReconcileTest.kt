package com.yapyap.feature.chat.data.repository

import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.appJson
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.realtime.SocketConnectionDiagnostics
import com.yapyap.core.realtime.SocketEvent
import com.yapyap.core.realtime.SocketSendMessagePayload
import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.data.dto.MessagesPageDto
import com.yapyap.feature.chat.data.remote.ChatApi
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.chat.domain.entity.ReconcileStrategy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRepositoryReconcileTest {

    @Test
    fun reconcile_preservesPendingOutboxAndMergesIncoming() = runTest {
        val requestedAfter = mutableListOf<Int?>()
        val repository = createRepository(
            afterPages = mapOf(
                5 to messagesPage(startId = 6, count = 3),
            ),
            onGetMessages = { cursor, after ->
                requestedAfter += after
            },
        )

        repository.upsertIncomingMessage(message(id = 5, senderId = 2))
        repository.upsertOutboxMessage(
            Message(
                id = null,
                conversationId = CONVERSATION_ID,
                senderId = 1,
                content = "queued",
                messageType = MessageType.TEXT,
                mediaUrl = null,
                status = MessageStatus.QUEUED,
                clientMessageId = "client-queued-1",
                createdAt = "2026-01-01T10:05:00Z",
                isPending = true,
            ),
        )

        val result = repository.reconcileConversation(
            conversationId = CONVERSATION_ID,
            serverLatestMessageId = 8,
            trigger = "test",
        ).getOrThrow()

        assertEquals(ReconcileStrategy.INCREMENTAL_AFTER, result.strategy)
        assertEquals(3, result.messagesMerged)
        assertEquals(1, result.pendingMessagesPreserved)
        assertEquals(listOf<Int?>(5), requestedAfter)

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(5, messages.size)
        assertTrue(messages.any { it.clientMessageId == "client-queued-1" && it.status == MessageStatus.QUEUED })
        assertTrue(messages.mapNotNull { it.id }.containsAll(listOf(5, 6, 7, 8)))
        assertEquals(5, messages.map { it.stableKey }.toSet().size)
    }

    @Test
    fun reconcile_gapRepair_fillsSingleInteriorGap() = runTest {
        val repository = createRepository(
            latestPage = messagesPage(startId = 100, count = 3),
            afterPages = mapOf(
                100 to messagesPage(startId = 101, count = 2),
            ),
        )

        repository.upsertIncomingMessage(message(id = 100, senderId = 2))
        repository.upsertIncomingMessage(message(id = 102, senderId = 2))

        val result = repository.reconcileConversation(
            conversationId = CONVERSATION_ID,
            serverLatestMessageId = 102,
            trigger = "gap_test_a",
        ).getOrThrow()

        assertEquals(ReconcileStrategy.GAP_REPAIR, result.strategy)
        assertEquals(1, result.messagesMerged)

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(listOf(100, 101, 102), messages.mapNotNull { it.id })
    }

    @Test
    fun reconcile_gapRepair_fillsMultipleInteriorGaps() = runTest {
        val repository = createRepository(
            latestPage = messagesPage(startId = 100, count = 5),
            afterPages = mapOf(
                100 to messagesPage(startId = 101, count = 4),
                102 to messagesPage(startId = 103, count = 2),
            ),
        )

        repository.upsertIncomingMessage(message(id = 100, senderId = 2))
        repository.upsertIncomingMessage(message(id = 102, senderId = 2))
        repository.upsertIncomingMessage(message(id = 104, senderId = 2))

        val result = repository.reconcileConversation(
            conversationId = CONVERSATION_ID,
            serverLatestMessageId = 104,
            trigger = "gap_test_b",
        ).getOrThrow()

        assertEquals(ReconcileStrategy.GAP_REPAIR, result.strategy)
        assertEquals(2, result.messagesMerged)

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(listOf(100, 101, 102, 103, 104), messages.mapNotNull { it.id })
    }

    @Test
    fun reconcile_gapRepair_skipsWhenFullyLoadedContiguousCache() = runTest {
        val requestedLatest = mutableListOf<Boolean>()
        val repository = createRepository(
            latestPage = messagesPage(startId = 100, count = 3),
            onLatestPageRequested = { requestedLatest += true },
        )

        repository.loadLatestMessages(CONVERSATION_ID).getOrThrow()
        requestedLatest.clear()

        val result = repository.reconcileConversation(
            conversationId = CONVERSATION_ID,
            serverLatestMessageId = 102,
            trigger = "gap_test_skip",
        ).getOrThrow()

        assertEquals(ReconcileStrategy.SKIP_ALREADY_CURRENT, result.strategy)
        assertEquals(0, result.messagesMerged)
        assertEquals(0, requestedLatest.size)

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertEquals(listOf(100, 101, 102), messages.mapNotNull { it.id })
    }

    @Test
    fun reconcile_gapRepair_preservesPendingOutbox() = runTest {
        val repository = createRepository(
            latestPage = messagesPage(startId = 100, count = 3),
            afterPages = mapOf(
                100 to messagesPage(startId = 101, count = 2),
            ),
        )

        repository.upsertIncomingMessage(message(id = 100, senderId = 2))
        repository.upsertIncomingMessage(message(id = 102, senderId = 2))
        repository.upsertOutboxMessage(
            Message(
                id = null,
                conversationId = CONVERSATION_ID,
                senderId = 1,
                content = "queued",
                messageType = MessageType.TEXT,
                mediaUrl = null,
                status = MessageStatus.QUEUED,
                clientMessageId = "client-queued-gap",
                createdAt = "2026-01-01T10:05:00Z",
                isPending = true,
            ),
        )

        repository.reconcileConversation(
            conversationId = CONVERSATION_ID,
            serverLatestMessageId = 102,
            trigger = "gap_test_outbox",
        ).getOrThrow()

        val messages = repository.observeMessages(CONVERSATION_ID).first()
        assertTrue(messages.any { it.clientMessageId == "client-queued-gap" && it.status == MessageStatus.QUEUED })
        assertEquals(listOf(100, 101, 102), messages.mapNotNull { it.id })
    }

    private fun createRepository(
        afterPages: Map<Int, MessagesPageDto> = emptyMap(),
        latestPage: MessagesPageDto = messagesPage(startId = 1, count = 0),
        onGetMessages: (cursor: Int?, after: Int?) -> Unit = { _, _ -> },
        onLatestPageRequested: () -> Unit = {},
    ): ChatRepositoryImpl {
        val engine = MockEngine { request ->
            val cursor = request.url.parameters["cursor"]?.toIntOrNull()
            val after = request.url.parameters["after"]?.toIntOrNull()
            onGetMessages(cursor, after)
            val page = when {
                after != null -> afterPages[after] ?: error("Unexpected after=$after")
                cursor == null -> {
                    onLatestPageRequested()
                    latestPage
                }
                else -> error("Unexpected cursor=$cursor")
            }
            val body = appJson.encodeToString(
                ApiResponse.serializer(MessagesPageDto.serializer()),
                ApiResponse(success = true, data = page),
            )
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(appJson)
            }
        }
        return createTestChatRepository(
            chatApi = ChatApi(httpClient),
            chatRealtimeClient = FakeChatRealtimeClient(),
        )
    }

    private fun messagesPage(startId: Int, count: Int): MessagesPageDto {
        val messages = (0 until count).map { index ->
            messageDto(id = startId + index)
        }
        return MessagesPageDto(messages = messages, nextCursor = null)
    }

    private fun message(id: Int, senderId: Int): Message = Message(
        id = id,
        conversationId = CONVERSATION_ID,
        senderId = senderId,
        content = "Message $id",
        messageType = MessageType.TEXT,
        mediaUrl = null,
        status = MessageStatus.SENT,
        clientMessageId = null,
        createdAt = "2026-01-01T10:${id.toString().padStart(2, '0')}:00Z",
        isPending = false,
    )

    private fun messageDto(id: Int): MessageDto = MessageDto(
        id = id,
        conversationId = CONVERSATION_ID,
        senderId = 2,
        content = "Message $id",
        messageType = "text",
        createdAt = "2026-01-01T10:${id.toString().padStart(2, '0')}:00Z",
    )

    private class FakeChatRealtimeClient : ChatRealtimeClient {
        override val connectionState = MutableStateFlow(RealtimeConnectionState.Disconnected)
        override val events: SharedFlow<SocketEvent> = MutableSharedFlow(replay = 0, extraBufferCapacity = 1)
        override val isReady: Boolean = false
        override fun connectionDiagnostics() = SocketConnectionDiagnostics(false, false, null, null)
        override fun connect(accessToken: String) = Unit
        override fun disconnect() = Unit
        override fun refreshAccessToken(accessToken: String) = Unit
        override suspend fun sendMessage(payload: SocketSendMessagePayload): Result<MessageDto> =
            Result.failure(IllegalStateException("Socket unavailable in test"))
        override fun emitTypingStart(conversationId: Int) = Unit
        override fun emitTypingStop(conversationId: Int) = Unit
        override suspend fun markRead(conversationId: Int, messageId: Int?) = Result.success(Unit)
        override suspend fun markDelivered(conversationId: Int, messageId: Int) = Result.success(Unit)
    }

    private companion object {
        const val CONVERSATION_ID = 7
    }
}
