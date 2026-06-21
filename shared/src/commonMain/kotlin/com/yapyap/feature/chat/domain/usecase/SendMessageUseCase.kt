package com.yapyap.feature.chat.domain.usecase

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.repository.ChatRepository

class SendMessageUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        conversationId: Int,
        senderId: Int,
        content: String,
    ): Result<Message> {
        return chatRepository.sendMessage(conversationId, senderId, content)
    }
}
