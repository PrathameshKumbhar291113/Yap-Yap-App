package com.yapyap.feature.conversations.domain.usecase

import com.yapyap.feature.conversations.domain.entity.Conversation
import com.yapyap.feature.conversations.domain.repository.ConversationRepository

class StartDirectChatUseCase(
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(contact: String): Result<Conversation> {
        return conversationRepository.createDirectConversation(contact)
    }
}
