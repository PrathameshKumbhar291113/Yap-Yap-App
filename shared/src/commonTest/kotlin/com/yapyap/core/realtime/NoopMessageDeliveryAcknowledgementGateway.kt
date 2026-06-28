package com.yapyap.core.realtime

import kotlinx.coroutines.CoroutineScope

object NoopMessageDeliveryAcknowledgementGateway : MessageDeliveryAcknowledgementGateway {
    override fun start(parentScope: CoroutineScope) = Unit

    override suspend fun onMessageReceived(
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
    ) = Unit

    override suspend fun onMessagePersisted(
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
    ) = Unit

    override suspend fun ackConversationSummaries(
        conversations: List<com.yapyap.feature.conversations.domain.entity.Conversation>,
        trigger: String,
    ) = Unit
}
