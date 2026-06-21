package com.yapyap.feature.conversations.presentation

import com.yapyap.core.util.formatConversationTimestamp
import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.entity.LastMessage
import com.yapyap.feature.conversations.domain.entity.displayTitle

fun Conversation.toUiModel(): ConversationUiModel = ConversationUiModel(
    id = id.toString(),
    title = displayTitle,
    lastMessagePreview = lastMessage?.previewText(),
    timestamp = formatConversationTimestamp(lastMessage?.createdAt ?: updatedAt),
    unreadCount = unreadCount,
)

private fun LastMessage.previewText(): String = when {
    !content.isNullOrBlank() -> content
    messageType != "text" -> messageType.replaceFirstChar { it.uppercase() }
    else -> "Message"
}
