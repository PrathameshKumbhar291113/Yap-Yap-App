package com.yapyap.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.core.realtime.ActiveConversationTracker
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.conversations.domain.entity.displayTitle
import com.yapyap.feature.conversations.domain.usecase.GetConversationsUseCase
import com.yapyap.feature.chat.domain.usecase.DeleteOutgoingMessageUseCase
import com.yapyap.feature.chat.domain.usecase.LoadMoreMessagesUseCase
import com.yapyap.feature.chat.domain.usecase.MarkConversationReadUseCase
import com.yapyap.feature.chat.domain.usecase.ObserveChatUseCase
import com.yapyap.feature.chat.domain.usecase.ObserveTypingStateUseCase
import com.yapyap.feature.chat.domain.usecase.RetryOutgoingMessageUseCase
import com.yapyap.feature.chat.domain.usecase.SendMessageUseCase
import com.yapyap.core.diagnostics.OutboxPipelineTrace
import com.yapyap.core.diagnostics.ReceiverSyncTrace
import com.yapyap.core.realtime.reconcile.ReceiverRepairGateway
import com.yapyap.core.realtime.reconcile.ReceiverRepairService
import com.yapyap.core.util.UserMessages
import com.yapyap.core.util.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationId: Int,
    private val getConversationsUseCase: GetConversationsUseCase,
    private val observeChatUseCase: ObserveChatUseCase,
    private val loadMoreMessagesUseCase: LoadMoreMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val retryOutgoingMessageUseCase: RetryOutgoingMessageUseCase,
    private val deleteOutgoingMessageUseCase: DeleteOutgoingMessageUseCase,
    private val markConversationReadUseCase: MarkConversationReadUseCase,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val observeTypingStateUseCase: ObserveTypingStateUseCase,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val activeConversationTracker: ActiveConversationTracker,
    private val pushNotificationCoordinator: PushNotificationCoordinator,
    private val receiverRepairGateway: ReceiverRepairGateway,
) : ViewModel() {
    private val isInitialLoading = MutableStateFlow(true)
    private val initialError = MutableStateFlow<String?>(null)
    private val sendError = MutableStateFlow<String?>(null)
    private val inputText = MutableStateFlow("")
    private val isSending = MutableStateFlow(false)
    private var typingStopJob: Job? = null
    private var lastMarkedMessageId: Int? = null

    val messageInput: StateFlow<String> = inputText

    val conversationTitle: StateFlow<String> = getConversationsUseCase()
        .map { conversations ->
            conversations.find { it.id == conversationId }?.displayTitle.orEmpty()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = "",
        )

    val uiState: StateFlow<ChatUiState> = combine(
        observeChatUseCase(conversationId),
        observeAuthStateUseCase(),
        observeTypingStateUseCase(conversationId),
        getConversationsUseCase(),
        isInitialLoading,
        initialError,
        sendError,
        isSending,
    ) { values ->
        val messages = values[0] as List<com.yapyap.feature.chat.domain.entity.Message>
        val authState = values[1] as AuthState
        val typingState = values[2] as com.yapyap.core.realtime.TypingState
        val conversations = values[3] as List<com.yapyap.feature.conversations.domain.entity.Conversation>
        val loading = values[4] as Boolean
        val error = values[5] as String?
        val sendErr = values[6] as String?
        val sending = values[7] as Boolean
        val currentUserId = (authState as? AuthState.Authenticated)?.user?.id
        val unreadCount = conversations.find { it.id == conversationId }?.unreadCount ?: 0
        when {
            currentUserId == null -> ChatUiState.Loading
            error != null && messages.isEmpty() -> ChatUiState.Error(error)
            loading && messages.isEmpty() -> ChatUiState.Loading
            else -> ChatUiState.Content(
                messages = messages.map { it.toUiModel(currentUserId) },
                isLoadingOlder = loadMoreMessagesUseCase.isLoadingOlder(conversationId),
                hasMore = loadMoreMessagesUseCase.hasMore(conversationId),
                unreadCount = unreadCount,
                sendError = sendErr,
                typingUserCount = typingState.typingUserIds.count { it != currentUserId },
                isSending = sending,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ChatUiState.Loading,
    )

    init {
        viewModelScope.launch {
            observeChatUseCase(conversationId).collect { messages ->
                OutboxPipelineTrace.stage(
                    stage = "UI_MESSAGES_RECOMPOSED",
                    conversationId = conversationId,
                    extra = mapOf(
                        "messageCount" to messages.size.toString(),
                        "pendingCount" to messages.count { it.isPending }.toString(),
                        "serverIds" to messages.mapNotNull { it.id }.joinToString(","),
                        "clientIds" to messages.mapNotNull { it.clientMessageId }.joinToString(","),
                    ),
                )
                ReceiverSyncTrace.stage(
                    stage = "UI_MESSAGES_RECOMPOSED",
                    conversationId = conversationId,
                    latestServerMessageId = messages.mapNotNull { it.id }.maxOrNull(),
                    trigger = "ChatViewModel.observeChatUseCase",
                    extra = mapOf(
                        "messageCount" to messages.size.toString(),
                        "uiRecomposed" to "true",
                    ),
                )
            }
        }
        activeConversationTracker.setActiveConversationId(conversationId)
        pushNotificationCoordinator.onConversationOpened(conversationId)
        repairOnOpen()
    }

    private fun repairOnOpen() {
        viewModelScope.launch {
            val lastKnownMessageId = observeChatUseCase(conversationId).first()
                .mapNotNull { it.id }
                .maxOrNull()
            ReceiverSyncTrace.stage(
                stage = "CHAT_REPAIR_ON_OPEN_BEGIN",
                conversationId = conversationId,
                lastKnownMessageId = lastKnownMessageId,
                trigger = ReceiverRepairService.TRIGGER_CHAT_OPEN,
            )
            isInitialLoading.value = true
            initialError.value = null
            receiverRepairGateway.repairConversation(
                conversationId = conversationId,
                trigger = ReceiverRepairService.TRIGGER_CHAT_OPEN,
            ).onSuccess { result ->
                isInitialLoading.value = false
                ReceiverSyncTrace.stage(
                    stage = "CHAT_REPAIR_ON_OPEN_DONE",
                    conversationId = conversationId,
                    latestServerMessageId = result.serverLatestMessageId,
                    trigger = ReceiverRepairService.TRIGGER_CHAT_OPEN,
                    extra = mapOf(
                        "strategy" to result.strategy.name,
                        "messagesMerged" to result.messagesMerged.toString(),
                    ),
                )
            }.onFailure { error ->
                isInitialLoading.value = false
                initialError.value = error.toUserMessage(UserMessages.UNKNOWN)
                ReceiverSyncTrace.stage(
                    stage = "CHAT_REPAIR_ON_OPEN_FAILED",
                    conversationId = conversationId,
                    trigger = ReceiverRepairService.TRIGGER_CHAT_OPEN,
                    extra = mapOf("error" to error.message),
                )
            }
        }
    }

    fun onReachedBottom() {
        viewModelScope.launch {
            val authState = observeAuthStateUseCase().value
            val currentUserId = (authState as? AuthState.Authenticated)?.user?.id ?: return@launch
            val messages = observeChatUseCase(conversationId).first()
            val latestIncomingId = messages
                .asSequence()
                .filter { it.senderId != currentUserId }
                .mapNotNull { it.id }
                .maxOrNull() ?: return@launch
            if (latestIncomingId != lastMarkedMessageId) {
                markConversationReadUseCase(conversationId, latestIncomingId)
                    .onSuccess { lastMarkedMessageId = latestIncomingId }
            }
        }
    }

    fun onInputChange(value: String) {
        inputText.value = value
        sendError.value = null
        handleTyping(value.isNotBlank())
    }

    fun refreshLatest() {
        viewModelScope.launch {
            val lastKnownMessageId = observeChatUseCase(conversationId).first()
                .mapNotNull { it.id }
                .maxOrNull()
            ReceiverSyncTrace.stage(
                stage = "CHAT_REFRESH_LATEST_BEGIN",
                conversationId = conversationId,
                lastKnownMessageId = lastKnownMessageId,
                trigger = "ChatViewModel.refreshLatest",
            )
            isInitialLoading.value = true
            initialError.value = null
            receiverRepairGateway.repairConversation(
                conversationId = conversationId,
                trigger = "ChatViewModel.refreshLatest",
            ).onSuccess {
                isInitialLoading.value = false
                ReceiverSyncTrace.stage(
                    stage = "CHAT_REFRESH_LATEST_DONE",
                    conversationId = conversationId,
                    trigger = "ChatViewModel.refreshLatest",
                    extra = mapOf("uiRecomposed" to "pending_flow_collect"),
                )
            }.onFailure { error ->
                isInitialLoading.value = false
                initialError.value = error.toUserMessage(UserMessages.UNKNOWN)
                ReceiverSyncTrace.stage(
                    stage = "CHAT_REFRESH_LATEST_FAILED",
                    conversationId = conversationId,
                    trigger = "ChatViewModel.refreshLatest",
                    extra = mapOf("error" to error.message),
                )
            }
        }
    }

    fun loadOlderMessages() {
        if (!loadMoreMessagesUseCase.hasMore(conversationId)) return
        if (loadMoreMessagesUseCase.isLoadingOlder(conversationId)) return
        viewModelScope.launch {
            loadMoreMessagesUseCase(conversationId)
        }
    }

    fun sendMessage() {
        if (isSending.value) return
        val content = inputText.value.trim()
        val authState = observeAuthStateUseCase().value
        val senderId = (authState as? AuthState.Authenticated)?.user?.id ?: return
        if (content.isEmpty()) return

        viewModelScope.launch {
            isSending.value = true
            sendError.value = null
            stopTyping()
            sendMessageUseCase(conversationId, senderId, content)
                .onSuccess {
                    inputText.value = ""
                }
            isSending.value = false
        }
    }

    fun retryMessage(clientMessageId: String) {
        viewModelScope.launch {
            retryOutgoingMessageUseCase(clientMessageId)
        }
    }

    fun deleteMessage(clientMessageId: String) {
        viewModelScope.launch {
            deleteOutgoingMessageUseCase(clientMessageId)
        }
    }

    fun markVisibleAsRead() {
        viewModelScope.launch {
            val messages = observeChatUseCase(conversationId).first()
            val latestId = messages.mapNotNull { message -> message.id }.maxOrNull()
            markConversationReadUseCase(conversationId, latestId)
        }
    }

    override fun onCleared() {
        if (activeConversationTracker.isViewing(conversationId)) {
            activeConversationTracker.setActiveConversationId(null)
        }
        stopTyping()
        viewModelScope.launch {
            getConversationsUseCase.refresh()
        }
        super.onCleared()
    }

    private fun handleTyping(isTyping: Boolean) {
        if (!chatRealtimeClient.isReady) return
        if (isTyping) {
            chatRealtimeClient.emitTypingStart(conversationId)
            typingStopJob?.cancel()
            typingStopJob = viewModelScope.launch {
                delay(TYPING_IDLE_MS)
                stopTyping()
            }
        } else {
            stopTyping()
        }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        typingStopJob = null
        if (chatRealtimeClient.isReady) {
            chatRealtimeClient.emitTypingStop(conversationId)
        }
    }

    private companion object {
        const val TYPING_IDLE_MS = 2_000L
    }
}
