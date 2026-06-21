package com.yapyap.feature.chat.domain.usecase

import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow

class ObserveChatUseCase(
    private val chatRepository: ChatRepository,
) {
    operator fun invoke(conversationId: Int): Flow<List<Message>> {
        return chatRepository.observeMessages(conversationId)
    }
}
