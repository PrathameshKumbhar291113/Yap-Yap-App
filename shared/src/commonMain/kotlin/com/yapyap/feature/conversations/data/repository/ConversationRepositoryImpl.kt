package com.yapyap.feature.conversations.data.repository

import com.yapyap.core.realtime.MessageDeliveryAcknowledgementGateway
import com.yapyap.core.diagnostics.OutboxPipelineTrace
import com.yapyap.core.diagnostics.ReceiverSyncTrace
import com.yapyap.core.network.getOrThrow
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.conversations.data.mapper.toDomain
import com.yapyap.feature.conversations.data.remote.ConversationApi
import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.entity.LastMessage
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConversationRepositoryImpl(
    private val conversationApi: ConversationApi,
    private val deliveryAckService: MessageDeliveryAcknowledgementGateway,
) : ConversationRepository {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val mutex = Mutex()

    override fun observeConversations(): Flow<List<Conversation>> = _conversations.asStateFlow()

    override suspend fun refreshConversations(): Result<Unit> {
        ReceiverSyncTrace.stage(
            stage = "REFRESH_CONVERSATIONS_BEGIN",
            trigger = "ConversationRepositoryImpl.refreshConversations",
            extra = mapOf("endpoint" to "GET /api/conversations"),
        )
        return mutex.withLock {
            runCatching {
                val items = conversationApi.getConversations().getOrThrow()
                val conversations = dedupeConversations(sortByLatest(items.map { it.toDomain() }))
                val previousCount = _conversations.value.size
                _conversations.value = conversations
                logStoreEvent(
                    event = "CONVERSATION_SYNC_RECEIVED",
                    source = "REST",
                    conversationId = null,
                    participantIds = conversations.mapNotNull { it.partner?.id }.joinToString(","),
                    storeSize = conversations.size,
                    extra = mapOf(
                        "previousCount" to previousCount.toString(),
                        "rawCount" to items.size.toString(),
                    ),
                )
                val latestServerMessageIds = conversations
                    .mapNotNull { conversation ->
                        conversation.lastMessage?.id?.let { conversation.id to it }
                    }
                    .joinToString(";") { (conversationId, messageId) -> "$conversationId:$messageId" }
                ReceiverSyncTrace.stage(
                    stage = "REFRESH_CONVERSATIONS_DONE",
                    trigger = "ConversationRepositoryImpl.refreshConversations",
                    extra = mapOf(
                        "conversationCount" to conversations.size.toString(),
                        "previousCount" to previousCount.toString(),
                        "conversationListUpdated" to "true",
                        "latestServerMessageIds" to latestServerMessageIds,
                        "repositoryUpdated" to "true",
                    ),
                )
                deliveryAckService.ackConversationSummaries(
                    conversations = conversations,
                    trigger = "ConversationRepositoryImpl.refreshConversations",
                )
            }.onFailure { error ->
                ReceiverSyncTrace.stage(
                    stage = "REFRESH_CONVERSATIONS_FAILED",
                    trigger = "ConversationRepositoryImpl.refreshConversations",
                    extra = mapOf("error" to error.message),
                )
            }
        }
    }

    override suspend fun createDirectConversation(contact: String): Result<Conversation> {
        return mutex.withLock {
            runCatching {
                val summary = conversationApi.createDirectConversation(contact).getOrThrow()
                val conversation = summary.toDomain()
                upsertConversation(
                    conversation = conversation,
                    source = "REST",
                    event = "CONVERSATION_UPSERT",
                )
                conversation
            }
        }
    }

    override fun snapshotConversations(): List<Conversation> = _conversations.value

    override suspend fun applyConversationRead(conversationId: Int) {
        mutex.withLock {
            val current = _conversations.value
            val index = current.indexOfFirst { it.id == conversationId }
            if (index < 0) return@withLock

            val existing = current[index]
            if (existing.unreadCount == 0) return@withLock

            upsertConversation(
                conversation = existing.copy(unreadCount = 0),
                source = "LOCAL",
                event = "CONVERSATION_UPDATE",
            )
        }
    }

    override suspend fun applyIncomingMessage(
        conversationId: Int,
        message: Message,
        currentUserId: Int?,
        isViewingConversation: Boolean,
    ): Boolean {
        return mutex.withLock {
            val messageId = message.id ?: return@withLock false

            val current = _conversations.value
            val wasUnknown = current.none { it.id == conversationId }
            val index = current.indexOfFirst { it.id == conversationId }

            val lastMessage = LastMessage(
                id = messageId,
                content = message.content,
                messageType = MessageType.toApi(message.messageType),
                senderId = message.senderId,
                createdAt = message.createdAt,
            )

            val existing = if (index >= 0) {
                current[index]
            } else {
                stubConversation(conversationId, message)
            }

            val isFromSelf = currentUserId != null && message.senderId == currentUserId
            val unreadCount = when {
                isFromSelf -> existing.unreadCount
                isViewingConversation -> 0
                else -> existing.unreadCount + 1
            }

            upsertConversation(
                conversation = existing.copy(
                    updatedAt = message.createdAt,
                    lastMessage = lastMessage,
                    unreadCount = unreadCount,
                ),
                source = "Socket",
                event = if (wasUnknown) "CONVERSATION_UPSERT" else "CONVERSATION_UPDATE",
            )

            OutboxPipelineTrace.stage(
                stage = "CONVERSATION_REPOSITORY_UPDATED",
                serverMessageId = messageId,
                conversationId = conversationId,
                extra = mapOf(
                    "wasUnknown" to wasUnknown.toString(),
                    "isFromSelf" to isFromSelf.toString(),
                    "unreadCount" to unreadCount.toString(),
                ),
            )
            wasUnknown
        }
    }

    private fun upsertConversation(
        conversation: Conversation,
        source: String,
        event: String,
    ) {
        val current = _conversations.value
        val duplicatePartner = conversation.partner?.let { partner ->
            current.firstOrNull {
                it.id != conversation.id &&
                    it.type == "direct" &&
                    it.partner?.id == partner.id
            }
        }

        if (duplicatePartner != null) {
            logStoreEvent(
                event = "CONVERSATION_DUPLICATE_DETECTED",
                source = source,
                conversationId = conversation.id,
                participantIds = conversation.partner?.id?.toString(),
                storeSize = current.size,
                extra = mapOf(
                    "duplicateConversationId" to duplicatePartner.id.toString(),
                    "keptConversationId" to conversation.id.toString(),
                ),
            )
        }

        val merged = current
            .filterNot { it.id == conversation.id }
            .filterNot {
                conversation.type == "direct" &&
                    conversation.partner != null &&
                    it.type == "direct" &&
                    it.partner?.id == conversation.partner?.id
            }

        val next = dedupeConversations(sortByLatest(listOf(conversation) + merged))
        _conversations.value = next

        logStoreEvent(
            event = event,
            source = source,
            conversationId = conversation.id,
            participantIds = conversation.partner?.id?.toString(),
            storeSize = next.size,
        )
    }

    private fun dedupeConversations(conversations: List<Conversation>): List<Conversation> {
        val byId = linkedMapOf<Int, Conversation>()
        for (conversation in conversations) {
            byId[conversation.id] = conversation
        }

        val deduped = mutableListOf<Conversation>()
        val seenPartners = mutableSetOf<Int>()
        for (conversation in sortByLatest(byId.values.toList())) {
            val partnerId = conversation.partner?.id
            if (conversation.type == "direct" && partnerId != null) {
                if (!seenPartners.add(partnerId)) {
                    continue
                }
            }
            deduped.add(conversation)
        }
        return deduped
    }

    private fun logStoreEvent(
        event: String,
        source: String,
        conversationId: Int?,
        participantIds: String?,
        storeSize: Int,
        extra: Map<String, String> = emptyMap(),
    ) {
        ReceiverSyncTrace.stage(
            stage = event,
            trigger = "ConversationRepositoryImpl",
            extra = buildMap {
                put("source", source)
                conversationId?.let { put("conversationId", it.toString()) }
                participantIds?.let { put("participantIds", it) }
                put("storeSize", storeSize.toString())
                putAll(extra)
            },
        )
    }

    private fun sortByLatest(conversations: List<Conversation>): List<Conversation> =
        conversations.sortedWith(
            compareByDescending<Conversation> { it.updatedAt }
                .thenByDescending { it.lastMessage?.createdAt ?: it.createdAt }
                .thenByDescending { it.id },
        )

    private fun stubConversation(conversationId: Int, message: Message): Conversation {
        return Conversation(
            id = conversationId,
            type = "direct",
            createdAt = message.createdAt,
            updatedAt = message.createdAt,
            unreadCount = 0,
            lastMessage = null,
        )
    }
}
