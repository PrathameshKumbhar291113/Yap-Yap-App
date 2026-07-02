package com.yapyap.feature.chat.domain.usecase

import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.conversations.domain.repository.ConversationRepository

class MarkConversationReadUseCase(
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(conversationId: Int, messageId: Int? = null): Result<Unit> {
        val result = chatRepository.markRead(conversationId, messageId)
        if (result.isSuccess) {
            conversationRepository.applyConversationRead(conversationId)
        }
        return result
    }
}
