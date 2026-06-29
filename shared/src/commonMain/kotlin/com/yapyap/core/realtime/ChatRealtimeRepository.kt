package com.yapyap.core.realtime

import com.yapyap.core.diagnostics.OutboxPipelineTrace
import com.yapyap.core.diagnostics.ReceiverSyncTrace
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.diagnostics.SocketRoomTrace
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
            is SocketEvent.Ready -> handleSocketReady(event)
            is SocketEvent.MessageNew -> handleMessageNew(event)
            is SocketEvent.MessageStatus -> handleMessageStatus(event)
            is SocketEvent.Typing -> handleTyping(event)
            is SocketEvent.PresenceUpdate -> handlePresence(event)
        }
    }

    private fun handleSocketReady(event: SocketEvent.Ready) {
        val diagnostics = chatRealtimeClient.connectionDiagnostics()
        val activeConversationId = activeConversationTracker.activeConversationId.value
        ReceiverSyncTrace.stage(
            stage = "SOCKET_READY_EVENT",
            conversationId = activeConversationId,
            trigger = "ChatRealtimeRepository.handleSocketReady",
            extra = mapOf(
                "userId" to event.userId.toString(),
                "socketId" to (diagnostics.socketId ?: "null"),
                "socketAuthenticated" to diagnostics.authenticated.toString(),
                "activeConversationId" to (activeConversationId?.toString() ?: "null"),
                "repairAction" to "none",
            ),
        )
    }

    private suspend fun handleMessageNew(event: SocketEvent.MessageNew) {
        val message = event.message.toDomain()
        val messageId = message.id
        val diagnostics = chatRealtimeClient.connectionDiagnostics()
        SocketRoomTrace.stage(
            stage = "SOCKET_MESSAGE_NEW_HANDLED",
            extra = mapOf(
                "conversationId" to event.conversationId.toString(),
                "messageId" to (messageId?.toString() ?: "null"),
                "clientMessageId" to (message.clientMessageId ?: "null"),
                "socketId" to (diagnostics.socketId ?: "null"),
                "socketConnected" to diagnostics.connected.toString(),
                "socketAuthenticated" to diagnostics.authenticated.toString(),
                "activeConversationId" to (activeConversationTracker.activeConversationId.value?.toString() ?: "null"),
            ),
        )
        OutboxPipelineTrace.stage(
            stage = "MESSAGE_NEW_RECEIVED",
            clientMessageId = message.clientMessageId,
            serverMessageId = messageId,
            conversationId = event.conversationId,
            extra = mapOf(
                "senderId" to message.senderId.toString(),
                "currentUserId" to currentUserId?.toString(),
                "stableKey" to message.stableKey,
            ),
        )
        val dedupKey = messageId?.toString()
        val alreadyHandled = dedupKey != null && deduplicator.hasSeen(dedupKey)
        val dedupReason = when {
            dedupKey == null -> "no_server_message_id"
            alreadyHandled -> "message_id_already_seen"
            else -> "not_seen"
        }
        OutboxPipelineTrace.stage(
            stage = "DEDUP_CHECK",
            clientMessageId = message.clientMessageId,
            serverMessageId = messageId,
            conversationId = event.conversationId,
            extra = mapOf(
                "stableKey" to message.stableKey,
                "dedupKey" to dedupKey,
                "alreadyHandled" to alreadyHandled.toString(),
                "reason" to dedupReason,
                "note" to "dedup_only_affects_push_notification_not_cache_upsert",
            ),
        )
        if (dedupKey != null) {
            deduplicator.markSeen(dedupKey)
        }

        val resolvedUserId = (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user?.id
        val shouldAck = messageId != null && shouldAckInboundMessage(message.senderId, resolvedUserId)
        if (shouldAck) {
            deliveryAckService.onMessageReceived(
                source = "socket",
                conversationId = event.conversationId,
                messageId = messageId!!,
                senderId = message.senderId,
                trigger = "ChatRealtimeRepository.handleMessageNew",
            )
        }

        chatRepository.upsertIncomingMessage(message)
        OutboxPipelineTrace.stage(
            stage = "UPSERT_MESSAGE",
            clientMessageId = message.clientMessageId,
            serverMessageId = messageId,
            conversationId = event.conversationId,
            extra = mapOf(
                "stableKey" to message.stableKey,
                "alreadyHandled" to alreadyHandled.toString(),
                "upsertExecuted" to "true",
            ),
        )

        if (shouldAck) {
            deliveryAckService.onMessagePersisted(
                source = "socket",
                conversationId = event.conversationId,
                messageId = messageId!!,
                senderId = message.senderId,
                trigger = "ChatRealtimeRepository.handleMessageNew",
            )
        }

        val isFromOther = resolvedUserId != null && message.senderId != resolvedUserId

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
        OutboxPipelineTrace.stage(
            stage = "CHAT_REALTIME_REPOSITORY_PROCESSED",
            clientMessageId = message.clientMessageId,
            serverMessageId = messageId,
            conversationId = event.conversationId,
            extra = mapOf(
                "alreadyHandled" to alreadyHandled.toString(),
                "wasUnknownConversation" to wasUnknown.toString(),
                "isViewing" to isViewing.toString(),
            ),
        )
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
