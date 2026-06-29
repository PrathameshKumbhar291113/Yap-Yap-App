package com.yapyap.core.realtime

import com.yapyap.core.diagnostics.SocketRoomTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActiveConversationTracker {
    private val _activeConversationId = MutableStateFlow<Int?>(null)
    val activeConversationId: StateFlow<Int?> = _activeConversationId.asStateFlow()

    fun setActiveConversationId(conversationId: Int?) {
        val previous = _activeConversationId.value
        _activeConversationId.value = conversationId
        when {
            conversationId != null && previous != conversationId -> {
                SocketRoomTrace.stage(
                    stage = "ACTIVE_CONVERSATION_SET",
                    extra = mapOf(
                        "conversationId" to conversationId.toString(),
                        "previousConversationId" to (previous?.toString() ?: "null"),
                        "note" to "local_only_not_server_conversation_view",
                    ),
                )
            }
            conversationId == null && previous != null -> {
                SocketRoomTrace.stage(
                    stage = "ACTIVE_CONVERSATION_CLEARED",
                    extra = mapOf(
                        "previousConversationId" to previous.toString(),
                        "note" to "local_only_not_server_conversation_leave",
                    ),
                )
            }
        }
    }

    fun isViewing(conversationId: Int): Boolean = _activeConversationId.value == conversationId
}
