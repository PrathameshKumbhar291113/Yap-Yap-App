package com.yapyap.feature.chat.presentation

internal fun needsOlderMessagesForUnread(
    messages: List<MessageUiModel>,
    unreadCount: Int,
    hasMore: Boolean,
): Boolean {
    if (unreadCount <= 0 || !hasMore) return false
    val incomingCount = messages.count { !it.isOutgoing }
    return incomingCount < unreadCount
}

internal fun findFirstUnreadListIndex(
    listItems: List<ChatListItem>,
    messages: List<MessageUiModel>,
    unreadCount: Int,
): Int {
    if (listItems.isEmpty()) return 0
    if (unreadCount <= 0) return listItems.lastIndex

    val incoming = messages.filter { !it.isOutgoing }
    if (incoming.isEmpty()) return listItems.lastIndex

    val effectiveUnreadCount = unreadCount.coerceAtMost(incoming.size)
    val firstUnreadMessage = incoming[incoming.size - effectiveUnreadCount]

    val listIndex = listItems.indexOfFirst { item ->
        item is ChatListItem.MessageItem && item.message.stableKey == firstUnreadMessage.stableKey
    }
    return if (listIndex >= 0) listIndex else listItems.lastIndex
}

internal fun findLatestMessageListIndex(
    listItems: List<ChatListItem>,
    latestMessageKey: String?,
): Int {
    if (latestMessageKey == null || listItems.isEmpty()) return listItems.lastIndex.coerceAtLeast(0)
    val index = listItems.indexOfLast { item ->
        item is ChatListItem.MessageItem && item.message.stableKey == latestMessageKey
    }
    return if (index >= 0) index else listItems.lastIndex
}
