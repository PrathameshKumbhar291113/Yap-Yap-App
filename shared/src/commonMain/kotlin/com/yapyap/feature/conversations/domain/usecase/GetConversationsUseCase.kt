package com.yapyap.feature.conversations.domain.usecase

import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.Flow

class GetConversationsUseCase(
    private val conversationRepository: ConversationRepository,
) {
    operator fun invoke(): Flow<List<Conversation>> = conversationRepository.observeConversations()

    suspend fun refresh(): Result<Unit> = conversationRepository.refreshConversations()
}
