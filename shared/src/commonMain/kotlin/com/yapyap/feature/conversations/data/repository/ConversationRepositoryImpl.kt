package com.yapyap.feature.conversations.data.repository

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

class ConversationRepositoryImpl(
    private val conversationApi: ConversationApi,
) : ConversationRepository {
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())

    override fun observeConversations(): Flow<List<Conversation>> = _conversations.asStateFlow()

    override suspend fun refreshConversations(): Result<Unit> {
        return runCatching {
            val items = conversationApi.getConversations().getOrThrow()
            _conversations.value = sortByLatest(items.map { it.toDomain() })
        }
    }

    override suspend fun createDirectConversation(contact: String): Result<Conversation> {
        return runCatching {
            val summary = conversationApi.createDirectConversation(contact).getOrThrow()
            val conversation = summary.toDomain()
            val current = _conversations.value
            if (current.none { it.id == conversation.id }) {
                _conversations.value = sortByLatest(listOf(conversation) + current)
            }
            conversation
        }
    }

    override fun applyConversationRead(conversationId: Int) {
        val current = _conversations.value
        val index = current.indexOfFirst { it.id == conversationId }
        if (index < 0) return

        val existing = current[index]
        if (existing.unreadCount == 0) return

        val updated = existing.copy(unreadCount = 0)
        _conversations.value = sortByLatest(listOf(updated) + current.filterNot { it.id == conversationId })
    }

    override fun applyIncomingMessage(
        conversationId: Int,
        message: Message,
        currentUserId: Int?,
        isViewingConversation: Boolean,
    ): Boolean {
        val messageId = message.id ?: return false

        var current = _conversations.value
        val wasUnknown = current.none { it.id == conversationId }
        if (wasUnknown) {
            current = sortByLatest(listOf(stubConversation(conversationId, message)) + current)
            _conversations.value = current
        }

        val index = current.indexOfFirst { it.id == conversationId }
        if (index < 0) return wasUnknown

        val lastMessage = LastMessage(
            id = messageId,
            content = message.content,
            messageType = MessageType.toApi(message.messageType),
            senderId = message.senderId,
            createdAt = message.createdAt,
        )

        val existing = current[index]
        val isFromSelf = currentUserId != null && message.senderId == currentUserId
        val unreadCount = when {
            isFromSelf -> existing.unreadCount
            isViewingConversation -> 0
            else -> existing.unreadCount + 1
        }
        val updated = existing.copy(
            updatedAt = message.createdAt,
            lastMessage = lastMessage,
            unreadCount = unreadCount,
        )

        _conversations.value = sortByLatest(listOf(updated) + current.filterNot { it.id == conversationId })
        return wasUnknown
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
