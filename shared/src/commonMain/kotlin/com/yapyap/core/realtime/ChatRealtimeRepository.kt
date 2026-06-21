package com.yapyap.core.realtime

import com.yapyap.core.push.PushMessageDeduplicator
import com.yapyap.core.push.PushPlatform
import com.yapyap.core.push.model.LocalPushNotification
import com.yapyap.core.push.model.PushNotificationChannel
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
    private val incomingMessageNotifier: IncomingMessageNotifier,
    private val deduplicator: PushMessageDeduplicator,
    private val pushPlatform: PushPlatform,
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
        message.id?.let { deduplicator.markSeen(it.toString()) }
        chatRepository.upsertIncomingMessage(message)

        val isViewing = activeConversationTracker.isViewing(event.conversationId)
        val wasUnknown = conversationRepository.applyIncomingMessage(
            conversationId = event.conversationId,
            message = message,
            currentUserId = currentUserId,
            isViewingConversation = isViewing,
        )

        if (wasUnknown) {
            appScope?.launch {
                conversationRepository.refreshConversations()
            }
        }

        val isFromOther = currentUserId != null && message.senderId != currentUserId
        if (isFromOther && isViewing) {
            message.id?.let { messageId ->
                appScope?.launch {
                    chatRepository.markRead(event.conversationId, messageId)
                        .onSuccess { conversationRepository.applyConversationRead(event.conversationId) }
                }
            }
        }
        if (isFromOther && !activeConversationTracker.isViewing(event.conversationId)) {
            val preview = message.previewText()
            incomingMessageNotifier.notify(
                IncomingMessageNotification(
                    conversationId = event.conversationId,
                    preview = preview,
                ),
            )
            val notificationData = buildMap {
                put("type", "new_message")
                put("conversationId", event.conversationId.toString())
                put("senderId", message.senderId.toString())
                message.id?.let { put("messageId", it.toString()) }
                put("preview", preview)
            }
            pushPlatform.showLocalNotification(
                LocalPushNotification(
                    title = "New message",
                    body = preview,
                    channel = PushNotificationChannel.Messages,
                    data = notificationData,
                ),
            )
        }
    }

    private suspend fun handleMessageStatus(event: SocketEvent.MessageStatus) {
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
