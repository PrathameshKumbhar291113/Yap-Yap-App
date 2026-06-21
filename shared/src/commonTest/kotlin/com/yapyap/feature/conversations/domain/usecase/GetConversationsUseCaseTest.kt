package com.yapyap.feature.conversations.domain.usecase

import com.yapyap.core.network.ApiResponse
import com.yapyap.core.network.appJson
import com.yapyap.feature.conversations.data.dto.ConversationListItemDto
import com.yapyap.feature.conversations.data.dto.ConversationsListDataDto
import com.yapyap.feature.conversations.data.dto.LastMessageDto
import com.yapyap.feature.conversations.data.remote.ConversationApi
import com.yapyap.feature.conversations.data.repository.ConversationRepositoryImpl
import com.yapyap.feature.conversations.domain.entity.LastMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetConversationsUseCaseTest {

    @Test
    fun refreshMapsConversationDtosToDomainEntities() = runTest {
        val directChatDto = ConversationListItemDto(
            id = 42,
            type = "direct",
            createdAt = "2026-01-01T10:00:00Z",
            updatedAt = "2026-01-02T12:30:00Z",
            unreadCount = 3,
            lastMessage = LastMessageDto(
                id = 900,
                content = "See you soon",
                messageType = "text",
                senderId = 7,
                createdAt = "2026-01-02T12:30:00Z",
            ),
        )
        val groupChatDto = ConversationListItemDto(
            id = 99,
            type = "group",
            createdAt = "2026-01-03T08:00:00Z",
            updatedAt = "2026-01-03T08:15:00Z",
            unreadCount = 0,
            lastMessage = null,
        )

        val api = ConversationApi(createMockClient(listOf(directChatDto, groupChatDto)))
        val repository = ConversationRepositoryImpl(api)
        val useCase = GetConversationsUseCase(repository)

        val refreshResult = useCase.refresh()
        val conversations = useCase().first()

        assertTrue(refreshResult.isSuccess)
        assertEquals(2, conversations.size)

        val direct = conversations.first { it.id == 42 }
        assertEquals("direct", direct.type)
        assertEquals("2026-01-01T10:00:00Z", direct.createdAt)
        assertEquals("2026-01-02T12:30:00Z", direct.updatedAt)
        assertEquals(3, direct.unreadCount)
        assertEquals(
            LastMessage(
                id = 900,
                content = "See you soon",
                messageType = "text",
                senderId = 7,
                createdAt = "2026-01-02T12:30:00Z",
            ),
            direct.lastMessage,
        )

        val group = conversations.first { it.id == 99 }
        assertEquals("group", group.type)
        assertEquals(0, group.unreadCount)
        assertEquals(null, group.lastMessage)
    }

    private fun createMockClient(conversations: List<ConversationListItemDto>): HttpClient {
        val body = appJson.encodeToString(
            ApiResponse.serializer(ConversationsListDataDto.serializer()),
            ApiResponse(
                success = true,
                data = ConversationsListDataDto(conversations = conversations),
            ),
        )
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(appJson)
            }
        }
    }
}
