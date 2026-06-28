package com.yapyap.core.push

import com.yapyap.core.push.model.PushPayload
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.chat.util.currentTimestampIso

fun PushPayload.toIncomingMessage(): Message? {
    val parsedConversationId = conversationId?.toIntOrNull() ?: return null
    val parsedMessageId = messageId?.toIntOrNull() ?: return null
    val parsedSenderId = senderId?.toIntOrNull() ?: return null
    val type = MessageType.fromApi(messageType ?: "text")
    val resolvedMediaUrl = mediaUrl ?: raw["mediaUrl"] ?: raw["media_url"]
    return Message(
        id = parsedMessageId,
        conversationId = parsedConversationId,
        senderId = parsedSenderId,
        content = preview,
        messageType = type,
        mediaUrl = resolvedMediaUrl,
        status = MessageStatus.SENT,
        clientMessageId = null,
        createdAt = createdAt?.takeIf { it.isNotBlank() } ?: currentTimestampIso(),
        isPending = false,
    )
}
