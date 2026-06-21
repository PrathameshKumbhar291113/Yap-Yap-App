package com.yapyap.feature.conversations.presentation

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ConversationUiModel(
    val id: String,
    val title: String,
    val lastMessagePreview: String?,
    val timestamp: String?,
    val unreadCount: Int,
)
