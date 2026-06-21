package com.yapyap.feature.chat.domain.usecase

import com.yapyap.feature.chat.domain.repository.ChatRepository

class LoadMoreMessagesUseCase(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(conversationId: Int): Result<Unit> {
        return chatRepository.loadOlderMessages(conversationId)
    }

    fun hasMore(conversationId: Int): Boolean = chatRepository.hasMoreMessages(conversationId)

    fun isLoadingOlder(conversationId: Int): Boolean = chatRepository.isLoadingOlder(conversationId)

    suspend fun loadLatest(conversationId: Int): Result<Unit> {
        return chatRepository.loadLatestMessages(conversationId)
    }
}
