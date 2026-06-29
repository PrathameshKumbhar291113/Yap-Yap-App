package com.yapyap.feature.chat.domain.usecase

import com.yapyap.feature.chat.domain.repository.ChatRepository

class DeleteOutgoingMessageUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(clientMessageId: String): Result<Unit> {
        return chatRepository.deleteOutgoingMessage(clientMessageId)
    }
}
