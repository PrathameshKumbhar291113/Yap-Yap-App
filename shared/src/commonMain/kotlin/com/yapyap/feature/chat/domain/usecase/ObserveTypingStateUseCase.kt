package com.yapyap.feature.chat.domain.usecase

import com.yapyap.core.realtime.TypingState
import com.yapyap.core.realtime.TypingStore
import kotlinx.coroutines.flow.Flow

class ObserveTypingStateUseCase(
    private val typingStore: TypingStore,
) {
    operator fun invoke(conversationId: Int): Flow<TypingState> {
        return typingStore.observe(conversationId)
    }
}
