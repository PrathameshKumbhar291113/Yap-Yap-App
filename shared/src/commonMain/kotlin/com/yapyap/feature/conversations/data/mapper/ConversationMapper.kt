package com.yapyap.feature.conversations.data.mapper

import com.yapyap.feature.conversations.data.dto.ConversationListItemDto
import com.yapyap.feature.conversations.data.dto.ConversationPartnerDto
import com.yapyap.feature.conversations.data.dto.ConversationSummaryDto
import com.yapyap.feature.conversations.data.dto.LastMessageDto
import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.entity.ConversationPartner
import com.yapyap.feature.conversations.domain.entity.LastMessage

fun ConversationListItemDto.toDomain(): Conversation = Conversation(
    id = id,
    type = type,
    createdAt = createdAt,
    updatedAt = updatedAt,
    unreadCount = unreadCount,
    lastMessage = lastMessage?.toDomain(),
    partner = partner?.toDomain(),
)

fun ConversationSummaryDto.toDomain(): Conversation = Conversation(
    id = id,
    type = type,
    createdAt = createdAt,
    updatedAt = updatedAt,
    unreadCount = 0,
    lastMessage = null,
    partner = partner?.toDomain(),
)

fun ConversationPartnerDto.toDomain(): ConversationPartner = ConversationPartner(
    id = id,
    name = name,
    email = email,
    phone = phone,
)

fun LastMessageDto.toDomain(): LastMessage = LastMessage(
    id = id,
    content = content,
    messageType = messageType,
    senderId = senderId,
    createdAt = createdAt,
)
