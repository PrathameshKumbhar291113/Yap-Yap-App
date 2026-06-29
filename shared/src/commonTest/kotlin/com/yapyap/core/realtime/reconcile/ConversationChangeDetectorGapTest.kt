package com.yapyap.core.realtime.reconcile

import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.entity.LastMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationChangeDetectorGapTest {

    @Test
    fun detectCacheGapCheckTargets_includesGapCandidatesNotAlreadyTargeted() {
        val server = listOf(conversation(id = 1, lastId = 102))
        val metadataTargets = ConversationChangeDetector.detectChangedConversations(
            local = listOf(conversation(id = 1, lastId = 102)),
            server = server,
        )
        assertEquals(0, metadataTargets.size)

        val gapTargets = ConversationChangeDetector.detectCacheGapCheckTargets(
            server = server,
            excludedConversationIds = metadataTargets.map { it.conversationId }.toSet(),
            needsGapRepair = { conversationId, _ -> conversationId == 1 },
        )

        assertEquals(1, gapTargets.size)
        assertEquals("cache_interior_gap_check", gapTargets.first().changeReason)
        assertEquals(102, gapTargets.first().serverLatestMessageId)
    }

    @Test
    fun detectCacheGapCheckTargets_skipsAlreadyTargetedConversations() {
        val server = listOf(conversation(id = 1, lastId = 105))
        val metadataTargets = ConversationChangeDetector.detectChangedConversations(
            local = listOf(conversation(id = 1, lastId = 100)),
            server = server,
        )
        assertEquals(1, metadataTargets.size)

        val gapTargets = ConversationChangeDetector.detectCacheGapCheckTargets(
            server = server,
            excludedConversationIds = metadataTargets.map { it.conversationId }.toSet(),
            needsGapRepair = { _, _ -> true },
        )

        assertEquals(0, gapTargets.size)
    }

    private fun conversation(id: Int, lastId: Int): Conversation {
        return Conversation(
            id = id,
            type = "direct",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            unreadCount = 0,
            lastMessage = LastMessage(
                id = lastId,
                content = "msg",
                messageType = "text",
                senderId = 2,
                createdAt = "2026-01-01T00:00:0$lastId",
            ),
        )
    }
}
