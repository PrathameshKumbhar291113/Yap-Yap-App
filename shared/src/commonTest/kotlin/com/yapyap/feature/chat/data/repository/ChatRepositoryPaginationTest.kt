package com.yapyap.feature.chat.data.repository

import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.appJson
import com.yapyap.core.realtime.NoopMessageDeliveryAcknowledgementGateway
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.realtime.SocketConnectionDiagnostics
import com.yapyap.core.realtime.SocketEvent
import com.yapyap.core.realtime.SocketSendMessagePayload
import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.data.dto.MessagesPageDto
import com.yapyap.feature.chat.data.remote.ChatApi
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatRepositoryPaginationTest {

    @Test
    fun fullPageSetsNextCursorAndHasMore() = runTest {
        val fullPage = messagesPage(
            messageCount = ChatApi.DEFAULT_LIMIT,
            startId = 200,
            includeNextCursor = true,
        )
        val repository = createRepository(
            pages = mapOf(null to fullPage),
        )

        val loadResult = repository.loadLatestMessages(CONVERSATION_ID)

        assertTrue(loadResult.isSuccess)
        assertTrue(repository.hasMoreMessages(CONVERSATION_ID))
        assertEquals(ChatApi.DEFAULT_LIMIT, repository.observeMessages(CONVERSATION_ID).first().size)
    }

    @Test
    fun partialPageClearsNextCursorAndHasMore() = runTest {
        val partialPage = messagesPage(
            messageCount = 12,
            startId = 100,
            includeNextCursor = false,
        )
        val repository = createRepository(
            pages = mapOf(null to partialPage),
        )

        val loadResult = repository.loadLatestMessages(CONVERSATION_ID)

        assertTrue(loadResult.isSuccess)
        assertFalse(repository.hasMoreMessages(CONVERSATION_ID))
        assertEquals(12, repository.observeMessages(CONVERSATION_ID).first().size)
    }

    @Test
    fun loadOlderUsesStoredCursorAndStopsWhenPageNotFull() = runTest {
        val latestPage = messagesPage(
            messageCount = ChatApi.DEFAULT_LIMIT,
            startId = 300,
            includeNextCursor = true,
        )
        val olderPartialPage = messagesPage(
            messageCount = 5,
            startId = 250,
            includeNextCursor = false,
        )
        val requestedCursors = mutableListOf<Int?>()
        val repository = createRepository(
            pages = mapOf(
                null to latestPage,
                300 to olderPartialPage,
            ),
            onGetMessages = { cursor -> requestedCursors += cursor },
        )

        repository.loadLatestMessages(CONVERSATION_ID)
        assertTrue(repository.hasMoreMessages(CONVERSATION_ID))

        val olderResult = repository.loadOlderMessages(CONVERSATION_ID)

        assertTrue(olderResult.isSuccess)
        assertEquals(listOf(null, 300), requestedCursors)
        assertFalse(repository.hasMoreMessages(CONVERSATION_ID))
        assertEquals(
            ChatApi.DEFAULT_LIMIT + 5,
            repository.observeMessages(CONVERSATION_ID).first().size,
        )
    }

    private fun createRepository(
        pages: Map<Int?, MessagesPageDto>,
        onGetMessages: (cursor: Int?) -> Unit = {},
    ): ChatRepositoryImpl {
        val engine = MockEngine { request ->
            val cursor = request.url.parameters["cursor"]?.toIntOrNull()
            onGetMessages(cursor)
            val page = pages[cursor]
                ?: error("Unexpected cursor request: $cursor")
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
        return ChatRepositoryImpl(
            chatApi = ChatApi(httpClient),
            chatRealtimeClient = FakeChatRealtimeClient(),
            deliveryAckService = NoopMessageDeliveryAcknowledgementGateway,
        )
    }

    private fun messagesPage(
        messageCount: Int,
        startId: Int,
        includeNextCursor: Boolean,
    ): MessagesPageDto {
        val messages = (0 until messageCount).map { index ->
            messageDto(
                id = startId + index,
                createdAt = "2026-01-01T10:${index.toString().padStart(2, '0')}:00Z",
            )
        }
        val nextCursor = if (includeNextCursor && messages.isNotEmpty()) messages.first().id else null
        return MessagesPageDto(
            messages = messages,
            nextCursor = nextCursor,
        )
    }

    private fun messageDto(id: Int, createdAt: String): MessageDto = MessageDto(
        id = id,
        conversationId = CONVERSATION_ID,
        senderId = 1,
        content = "Message $id",
        messageType = "text",
        createdAt = createdAt,
    )

    private class FakeChatRealtimeClient : ChatRealtimeClient {
        override val connectionState = MutableStateFlow(RealtimeConnectionState.Disconnected)
        override val events: SharedFlow<SocketEvent> = MutableSharedFlow(replay = 0, extraBufferCapacity = 1)
        override val isReady: Boolean = false

        override fun connectionDiagnostics() = SocketConnectionDiagnostics(
            connected = false,
            authenticated = false,
            socketId = null,
            userId = null,
        )

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
