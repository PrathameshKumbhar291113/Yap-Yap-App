package com.yapyap.core.realtime.reconcile

import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.entity.LastMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationChangeDetectorTest {

    @Test
    fun detectsLastMessageIdAdvance() {
        val local = listOf(conversation(id = 1, lastId = 10, unread = 0))
        val server = listOf(conversation(id = 1, lastId = 13, unread = 1))

        val changed = ConversationChangeDetector.detectChangedConversations(local, server)

        assertEquals(1, changed.size)
        assertEquals(1, changed.first().conversationId)
        assertEquals(10, changed.first().localLatestMessageId)
        assertEquals(13, changed.first().serverLatestMessageId)
        assertTrue(changed.first().changeReason.contains("last_message_id_advanced"))
    }

    @Test
    fun ignoresUnchangedConversation() {
        val local = listOf(conversation(id = 2, lastId = 5, unread = 0))
        val server = listOf(conversation(id = 2, lastId = 5, unread = 0))

        val changed = ConversationChangeDetector.detectChangedConversations(local, server)

        assertEquals(0, changed.size)
    }

    @Test
    fun detectsMultipleChangedConversations() {
        val local = listOf(
            conversation(id = 1, lastId = 1, unread = 0),
            conversation(id = 2, lastId = 2, unread = 0),
            conversation(id = 3, lastId = 3, unread = 0),
        )
        val server = listOf(
            conversation(id = 1, lastId = 4, unread = 1),
            conversation(id = 2, lastId = 5, unread = 1),
            conversation(id = 3, lastId = 3, unread = 0),
        )

        val changed = ConversationChangeDetector.detectChangedConversations(local, server)

        assertEquals(2, changed.size)
        assertEquals(setOf(1, 2), changed.map { it.conversationId }.toSet())
    }

    private fun conversation(id: Int, lastId: Int, unread: Int): Conversation {
        return Conversation(
            id = id,
            type = "direct",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            unreadCount = unread,
            lastMessage = LastMessage(
                id = lastId,
                content = "msg-$lastId",
                messageType = "text",
                senderId = 2,
                createdAt = "2026-01-01T00:00:0${lastId}Z",
            ),
        )
    }
}
