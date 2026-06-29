package com.yapyap.feature.conversations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yapyap.core.diagnostics.ReceiverSyncTrace
import com.yapyap.feature.conversations.domain.usecase.GetConversationsUseCase
import com.yapyap.feature.conversations.domain.usecase.StartDirectChatUseCase
import com.yapyap.core.util.UserMessages
import com.yapyap.core.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConversationsViewModel(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val startDirectChatUseCase: StartDirectChatUseCase,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(true)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isCreatingChat = MutableStateFlow(false)
    private val createChatError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState<List<ConversationUiModel>>> = combine(
        getConversationsUseCase(),
        isRefreshing,
        errorMessage,
    ) { conversations, refreshing, error ->
        when {
            error != null -> UiState.Error(error)
            refreshing && conversations.isEmpty() -> UiState.Loading
            else -> UiState.Success(conversations.map { it.toUiModel() })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = UiState.Loading,
    )

    val isPullRefreshing: StateFlow<Boolean> = isRefreshing
    val isCreatingDirectChat: StateFlow<Boolean> = isCreatingChat
    val newChatError: StateFlow<String?> = createChatError

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            ReceiverSyncTrace.stage(
                stage = "CONVERSATIONS_UI_REFRESH_BEGIN",
                trigger = "ConversationsViewModel.refresh",
            )
            isRefreshing.value = true
            errorMessage.value = null
            getConversationsUseCase.refresh()
                .onSuccess {
                    isRefreshing.value = false
                    ReceiverSyncTrace.stage(
                        stage = "CONVERSATIONS_UI_REFRESH_DONE",
                        trigger = "ConversationsViewModel.refresh",
                        extra = mapOf("uiRecomposed" to "pending_flow_collect"),
                    )
                }
                .onFailure { error ->
                    isRefreshing.value = false
                    errorMessage.value = error.toUserMessage(UserMessages.LOAD_CONVERSATIONS_FAILED)
                    ReceiverSyncTrace.stage(
                        stage = "CONVERSATIONS_UI_REFRESH_FAILED",
                        trigger = "ConversationsViewModel.refresh",
                        extra = mapOf("error" to error.message),
                    )
                }
        }
    }

    fun startDirectChat(contact: String, onSuccess: (conversationId: String) -> Unit) {
        viewModelScope.launch {
            isCreatingChat.value = true
            createChatError.value = null
            startDirectChatUseCase(contact)
                .onSuccess { conversation ->
                    isCreatingChat.value = false
                    onSuccess(conversation.id.toString())
                }
                .onFailure { error ->
                    isCreatingChat.value = false
                    createChatError.value = error.toUserMessage(UserMessages.START_CHAT_FAILED)
                }
        }
    }

    fun clearCreateChatError() {
        createChatError.value = null
    }
}
