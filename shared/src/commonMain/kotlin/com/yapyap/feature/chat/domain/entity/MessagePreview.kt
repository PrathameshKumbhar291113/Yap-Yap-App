package com.yapyap.feature.chat.domain.entity

import com.yapyap.feature.conversations.domain.entity.LastMessage

fun Message.previewText(): String = when {
    !content.isNullOrBlank() -> content
    messageType != MessageType.TEXT -> MessageType.toApi(messageType).replaceFirstChar { it.uppercase() }
    else -> "Message"
}

fun LastMessage.previewText(): String = when {
    !content.isNullOrBlank() -> content
    messageType != "text" -> messageType.replaceFirstChar { it.uppercase() }
    else -> "Message"
}
