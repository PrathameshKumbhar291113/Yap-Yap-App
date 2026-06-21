package com.yapyap.core.realtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActiveConversationTracker {
    private val _activeConversationId = MutableStateFlow<Int?>(null)
    val activeConversationId: StateFlow<Int?> = _activeConversationId.asStateFlow()

    fun setActiveConversationId(conversationId: Int?) {
        _activeConversationId.value = conversationId
    }

    fun isViewing(conversationId: Int): Boolean = _activeConversationId.value == conversationId
}
