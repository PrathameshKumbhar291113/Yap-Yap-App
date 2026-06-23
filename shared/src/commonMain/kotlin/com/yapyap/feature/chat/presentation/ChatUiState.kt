package com.yapyap.feature.chat.presentation

sealed interface ChatUiState {
    data object Loading : ChatUiState

    data class Content(
        val messages: List<MessageUiModel>,
        val isLoadingOlder: Boolean,
        val hasMore: Boolean,
        val unreadCount: Int = 0,
        val sendError: String? = null,
        val typingUserCount: Int = 0,
        val isSending: Boolean = false,
    ) : ChatUiState

    data class Error(val message: String) : ChatUiState
}
