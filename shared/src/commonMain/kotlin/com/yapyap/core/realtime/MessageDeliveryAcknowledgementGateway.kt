package com.yapyap.core.realtime

import kotlinx.coroutines.CoroutineScope

interface MessageDeliveryAcknowledgementGateway {
    fun start(parentScope: CoroutineScope)

    suspend fun onMessageReceived(
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
    )

    suspend fun onMessagePersisted(
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
    )

    suspend fun ackConversationSummaries(
        conversations: List<com.yapyap.feature.conversations.domain.entity.Conversation>,
        trigger: String,
    )
}
