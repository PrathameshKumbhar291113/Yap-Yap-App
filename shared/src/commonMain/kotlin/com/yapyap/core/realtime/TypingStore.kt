package com.yapyap.core.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class TypingStore {
    private val typingByConversation = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())

    fun observe(conversationId: Int): Flow<TypingState> {
        return typingByConversation.map { map ->
            TypingState(typingUserIds = map[conversationId].orEmpty())
        }
    }

    fun applyTyping(conversationId: Int, userId: Int, isTyping: Boolean) {
        typingByConversation.update { current ->
            val existing = current[conversationId].orEmpty()
            val updatedUsers = if (isTyping) {
                existing + userId
            } else {
                existing - userId
            }
            if (updatedUsers.isEmpty()) {
                current - conversationId
            } else {
                current + (conversationId to updatedUsers)
            }
        }
    }
}
