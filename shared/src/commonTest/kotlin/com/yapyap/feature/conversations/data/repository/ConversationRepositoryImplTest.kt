package com.yapyap.feature.conversations.data.repository

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.conversations.data.remote.ConversationApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationRepositoryImplTest {
    private val repository = ConversationRepositoryImpl(
        ConversationApi(
            HttpClient(
                MockEngine {
                    respond("", HttpStatusCode.NotFound)
                },
            ) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            },
        ),
    )

    @Test
    fun applyIncomingMessage_addsStubWhenConversationUnknown() = runTest {
        val message = sampleMessage(conversationId = 99, id = 1, senderId = 2)

        val wasUnknown = repository.applyIncomingMessage(
            conversationId = 99,
            message = message,
            currentUserId = 1,
        )

        assertTrue(wasUnknown)
        val conversations = repository.observeConversations().first()
        assertEquals(1, conversations.size)
        assertEquals(99, conversations.first().id)
        assertEquals(1, conversations.first().unreadCount)
        assertEquals("Hello", conversations.first().lastMessage?.content)
    }

    @Test
    fun applyIncomingMessage_doesNotRecreateKnownConversation() = runTest {
        repository.applyIncomingMessage(
            conversationId = 99,
            message = sampleMessage(conversationId = 99, id = 1, senderId = 2),
            currentUserId = 1,
        )

        val wasUnknown = repository.applyIncomingMessage(
            conversationId = 99,
            message = sampleMessage(conversationId = 99, id = 2, senderId = 2, content = "Again"),
            currentUserId = 1,
        )

        assertFalse(wasUnknown)
        val conversations = repository.observeConversations().first()
        assertEquals(1, conversations.size)
        assertEquals("Again", conversations.first().lastMessage?.content)
        assertEquals(2, conversations.first().unreadCount)
    }

    @Test
    fun applyIncomingMessage_doesNotIncrementUnreadWhileViewing() = runTest {
        repository.applyIncomingMessage(
            conversationId = 99,
            message = sampleMessage(conversationId = 99, id = 1, senderId = 2),
            currentUserId = 1,
            isViewingConversation = true,
        )

        val conversations = repository.observeConversations().first()
        assertEquals(0, conversations.first().unreadCount)
    }

    @Test
    fun applyIncomingMessage_movesUpdatedConversationToTop() = runTest {
        repository.applyIncomingMessage(
            conversationId = 1,
            message = sampleMessage(
                conversationId = 1,
                id = 1,
                senderId = 2,
                createdAt = "2026-06-21T10:00:00Z",
            ),
            currentUserId = 1,
        )
        repository.applyIncomingMessage(
            conversationId = 2,
            message = sampleMessage(
                conversationId = 2,
                id = 2,
                senderId = 2,
                createdAt = "2026-06-21T11:00:00Z",
            ),
            currentUserId = 1,
        )
        repository.applyIncomingMessage(
            conversationId = 1,
            message = sampleMessage(
                conversationId = 1,
                id = 3,
                senderId = 2,
                content = "Newest",
                createdAt = "2026-06-21T12:00:00Z",
            ),
            currentUserId = 1,
        )

        val conversations = repository.observeConversations().first()
        assertEquals(2, conversations.size)
        assertEquals(1, conversations.first().id)
        assertEquals("Newest", conversations.first().lastMessage?.content)
    }

    @Test
    fun applyConversationRead_clearsUnreadBadge() = runTest {
        repository.applyIncomingMessage(
            conversationId = 99,
            message = sampleMessage(conversationId = 99, id = 1, senderId = 2),
            currentUserId = 1,
        )
        repository.applyIncomingMessage(
            conversationId = 99,
            message = sampleMessage(conversationId = 99, id = 2, senderId = 2, content = "Again"),
            currentUserId = 1,
        )

        repository.applyConversationRead(99)

        val conversations = repository.observeConversations().first()
        assertEquals(0, conversations.first().unreadCount)
    }

    private fun sampleMessage(
        conversationId: Int,
        id: Int,
        senderId: Int,
        content: String = "Hello",
        createdAt: String = "2026-06-21T12:00:00Z",
    ): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        content = content,
        messageType = MessageType.TEXT,
        mediaUrl = null,
        status = MessageStatus.SENT,
        clientMessageId = null,
        createdAt = createdAt,
    )
}
