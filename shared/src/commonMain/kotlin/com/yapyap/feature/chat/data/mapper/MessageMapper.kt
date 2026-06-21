package com.yapyap.feature.chat.data.mapper

import com.yapyap.feature.chat.data.dto.MessageDto
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType

fun MessageDto.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    content = content,
    messageType = MessageType.fromApi(messageType),
    mediaUrl = mediaUrl,
    status = MessageStatus.fromApi(status),
    clientMessageId = clientMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    isPending = false,
)
