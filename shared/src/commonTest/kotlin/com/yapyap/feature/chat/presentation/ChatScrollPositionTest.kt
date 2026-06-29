package com.yapyap.feature.chat.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatScrollPositionTest {
    @Test
    fun needsOlderMessagesForUnread_whenUnreadExceedsLoadedIncoming() {
        val messages = List(5) { index ->
            message(index.toString(), isOutgoing = index % 2 == 0)
        }

        assertTrue(needsOlderMessagesForUnread(messages, unreadCount = 4, hasMore = true))
        assertFalse(needsOlderMessagesForUnread(messages, unreadCount = 4, hasMore = false))
    }

    @Test
    fun findFirstUnreadListIndex_scrollsToFirstUnreadIncomingMessage() {
        val messages = listOf(
            message("1", isOutgoing = true),
            message("2", isOutgoing = false),
            message("3", isOutgoing = false),
            message("4", isOutgoing = false),
        )
        val listItems = messages.toChatListItems(
            todayKey = "today",
            yesterdayKey = "yesterday",
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
        )

        val index = findFirstUnreadListIndex(listItems, messages, unreadCount = 2)

        assertEquals(3, index)
    }

    @Test
    fun findFirstUnreadListIndex_withNoUnread_scrollsToBottom() {
        val messages = listOf(
            message("1", isOutgoing = false),
            message("2", isOutgoing = true),
        )
        val listItems = messages.toChatListItems(
            todayKey = "today",
            yesterdayKey = "yesterday",
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
        )

        val index = findFirstUnreadListIndex(listItems, messages, unreadCount = 0)

        assertEquals(listItems.lastIndex, index)
    }

    @Test
    fun findLatestMessageListIndex_returnsLastMatchingMessage() {
        val messages = listOf(
            message("1", isOutgoing = false),
            message("2", isOutgoing = true),
        )
        val listItems = messages.toChatListItems(
            todayKey = "today",
            yesterdayKey = "yesterday",
            todayLabel = "Today",
            yesterdayLabel = "Yesterday",
        )

        val index = findLatestMessageListIndex(listItems, latestMessageKey = "2")

        assertEquals(listItems.lastIndex, index)
    }

    private fun message(
        stableKey: String,
        isOutgoing: Boolean,
    ): MessageUiModel = MessageUiModel(
        stableKey = stableKey,
        clientMessageId = stableKey,
        content = stableKey,
        isOutgoing = isOutgoing,
        timestamp = "12:00",
        dateKey = "today",
        readReceipt = null,
        isPending = false,
        isFailed = false,
    )
}
