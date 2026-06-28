package com.yapyap.core.realtime

import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.push.PushMessageDeduplicator
import com.yapyap.core.push.PushNotificationCoordinator
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.data.mapper.toDomain
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.previewText
import com.yapyap.feature.chat.domain.repository.ChatRepository
import com.yapyap.feature.conversations.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ChatRealtimeRepository(
    private val chatRealtimeClient: ChatRealtimeClient,
    private val chatRepository: ChatRepository,
    private val conversationRepository: ConversationRepository,
    private val typingStore: TypingStore,
    private val presenceStore: PresenceStore,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val activeConversationTracker: ActiveConversationTracker,
    private val deduplicator: PushMessageDeduplicator,
    private val pushNotificationCoordinator: Lazy<PushNotificationCoordinator>,
    private val deliveryAckService: MessageDeliveryAcknowledgementGateway,
) {
    private var currentUserId: Int? = null
    private var appScope: CoroutineScope? = null

    fun start(scope: CoroutineScope) {
        appScope = scope

        scope.launch {
            observeAuthStateUseCase()
                .map { state -> (state as? AuthState.Authenticated)?.user?.id }
                .distinctUntilChanged()
                .collect { userId -> currentUserId = userId }
        }

        scope.launch {
            chatRealtimeClient.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    private suspend fun handleEvent(event: SocketEvent) {
        when (event) {
            is SocketEvent.Ready -> Unit
            is SocketEvent.MessageNew -> handleMessageNew(event)
            is SocketEvent.MessageStatus -> handleMessageStatus(event)
            is SocketEvent.Typing -> handleTyping(event)
            is SocketEvent.PresenceUpdate -> handlePresence(event)
        }
    }

    private suspend fun handleMessageNew(event: SocketEvent.MessageNew) {
        val message = event.message.toDomain()
        val messageId = message.id
        val alreadyHandled = messageId != null && deduplicator.hasSeen(messageId.toString())
        if (messageId != null) {
            deduplicator.markSeen(messageId.toString())
        }

        val resolvedUserId = (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user?.id
        val isFromOther = resolvedUserId != null && message.senderId != resolvedUserId
        if (isFromOther && messageId != null) {
            deliveryAckService.onMessageReceived(
                source = "socket",
                conversationId = event.conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = "ChatRealtimeRepository.handleMessageNew",
            )
        }

        chatRepository.upsertIncomingMessage(message)

        if (isFromOther && messageId != null) {
            deliveryAckService.onMessagePersisted(
                source = "socket",
                conversationId = event.conversationId,
                messageId = messageId,
                senderId = message.senderId,
                trigger = "ChatRealtimeRepository.handleMessageNew",
            )
        }

        val isViewing = activeConversationTracker.isViewing(event.conversationId)
        val wasUnknown = conversationRepository.applyIncomingMessage(
            conversationId = event.conversationId,
            message = message,
            currentUserId = resolvedUserId,
            isViewingConversation = isViewing,
        )

        if (wasUnknown) {
            appScope?.launch {
                conversationRepository.refreshConversations()
            }
        }

        if (isFromOther && isViewing) {
            message.id?.let { messageId ->
                appScope?.launch {
                    chatRepository.markRead(event.conversationId, messageId)
                        .onSuccess { conversationRepository.applyConversationRead(event.conversationId) }
                }
            }
        }
        if (isFromOther && !alreadyHandled) {
            RuntimeDiagnostics.event(
                "Realtime",
                "socket message:new conversationId=${event.conversationId} messageId=$messageId " +
                    "senderId=${message.senderId} isViewing=$isViewing",
            )
            pushNotificationCoordinator.value.onRealtimeNewMessage(
                conversationId = event.conversationId,
                messageId = messageId?.toString(),
                senderId = message.senderId,
                preview = message.previewText(),
            )
        }
    }

    private suspend fun handleMessageStatus(event: SocketEvent.MessageStatus) {
        RuntimeDiagnostics.event(
            "Delivery",
            "message:status conversationId=${event.conversationId} messageId=${event.messageId} " +
                "status=${event.status} userId=${event.userId}",
        )
        chatRepository.applyMessageStatus(
            conversationId = event.conversationId,
            messageId = event.messageId,
            status = MessageStatus.fromApi(event.status),
            readerUserId = event.userId,
        )
    }

    private fun handleTyping(event: SocketEvent.Typing) {
        if (event.userId == currentUserId) return
        typingStore.applyTyping(
            conversationId = event.conversationId,
            userId = event.userId,
            isTyping = event.isTyping,
        )
    }

    private fun handlePresence(event: SocketEvent.PresenceUpdate) {
        presenceStore.applyPresenceUpdate(event.userId, event.online)
    }
}
