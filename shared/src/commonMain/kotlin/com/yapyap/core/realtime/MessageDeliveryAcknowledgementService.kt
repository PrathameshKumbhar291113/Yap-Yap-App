package com.yapyap.core.realtime

import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.conversations.domain.entity.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Emits `message:delivered` to the backend after an inbound message is persisted locally.
 * Queues acks when the socket is offline or auth is not ready; retries on reconnect/auth.
 */
class MessageDeliveryAcknowledgementService(
    private val chatRealtimeClient: ChatRealtimeClient,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : MessageDeliveryAcknowledgementGateway {
    private val mutex = Mutex()
    private val acknowledgedMessageIds = mutableSetOf<Int>()
    private val pendingAcks = LinkedHashMap<Int, PendingDeliveryAck>()

    override fun start(parentScope: CoroutineScope) {
        parentScope.launch(dispatcherProvider.default) {
            observeAuthStateUseCase()
                .map { state -> (state as? AuthState.Authenticated)?.user?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) {
                        flushPendingAcks(trigger = "user_authenticated")
                    }
                }
        }
        parentScope.launch(dispatcherProvider.default) {
            chatRealtimeClient.connectionState.collect { state ->
                if (state is RealtimeConnectionState.Ready) {
                    flushPendingAcks(trigger = "socket_ready")
                }
            }
        }
    }

    override suspend fun onMessageReceived(
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
    ) {
        log(
            event = "MESSAGE_RECEIVED",
            source = source,
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            trigger = trigger,
        )
    }

    override suspend fun onMessagePersisted(
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
    ) {
        log(
            event = "MESSAGE_PERSISTED",
            source = source,
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            trigger = trigger,
        )

        val recipientId = resolvedUserId()
        if (recipientId == null) {
            queuePending(PendingDeliveryAck(conversationId, messageId, senderId, source, trigger))
            logSkip(
                reason = "not_authenticated",
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                queued = true,
            )
            return
        }
        if (senderId == recipientId) {
            logSkip(
                reason = "own_message",
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
            )
            return
        }
        if (acknowledgedMessageIds.contains(messageId)) {
            logSkip(
                reason = "already_acknowledged",
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
            )
            return
        }

        emitDeliveredAck(
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            recipientId = recipientId,
            source = source,
            trigger = trigger,
            retryCount = 0,
        )
    }

    override suspend fun ackConversationSummaries(
        conversations: List<Conversation>,
        trigger: String,
    ) {
        val recipientId = resolvedUserId()
        log(
            event = "REST_CONVERSATION_SYNC",
            source = "rest",
            conversationId = -1,
            messageId = -1,
            senderId = -1,
            trigger = "$trigger count=${conversations.size} recipientId=$recipientId",
        )
        conversations.forEach { conversation ->
            val last = conversation.lastMessage ?: return@forEach
            val messageId = last.id
            onMessageReceived(
                source = "rest",
                conversationId = conversation.id,
                messageId = messageId,
                senderId = last.senderId,
                trigger = trigger,
            )
            onMessagePersisted(
                source = "rest",
                conversationId = conversation.id,
                messageId = messageId,
                senderId = last.senderId,
                trigger = trigger,
            )
        }
    }

    private suspend fun emitDeliveredAck(
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        recipientId: Int,
        source: String,
        trigger: String,
        retryCount: Int,
    ) {
        val diagnostics = chatRealtimeClient.connectionDiagnostics()
        log(
            event = "MESSAGE_DELIVERY_ACK_PREPARE",
            source = source,
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            trigger = trigger,
            retryCount = retryCount,
        )

        if (!diagnostics.connected || !diagnostics.authenticated) {
            queuePending(PendingDeliveryAck(conversationId, messageId, senderId, source, trigger, retryCount))
            logSkip(
                reason = if (!diagnostics.connected) "socket_not_connected" else "socket_not_authenticated",
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                queued = true,
                retryCount = retryCount,
            )
            return
        }

        val result = chatRealtimeClient.markDelivered(conversationId, messageId)
        if (result.isSuccess) {
            acknowledgedMessageIds.add(messageId)
            removePending(messageId)
        } else {
            queuePending(PendingDeliveryAck(conversationId, messageId, senderId, source, trigger, retryCount))
            logSkip(
                reason = "emit_failed:${result.exceptionOrNull()?.message}",
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                queued = true,
                retryCount = retryCount,
            )
        }
    }

    private suspend fun flushPendingAcks(trigger: String) {
        val pending = mutex.withLock { pendingAcks.values.toList() }
        if (pending.isEmpty()) return

        log(
            event = "FLUSH_PENDING_ACKS",
            source = "internal",
            conversationId = -1,
            messageId = -1,
            senderId = -1,
            trigger = "$trigger count=${pending.size}",
        )

        pending.forEach { ack ->
            val recipientId = resolvedUserId()
            if (recipientId == null || ack.senderId == recipientId) return@forEach
            if (acknowledgedMessageIds.contains(ack.messageId)) {
                removePending(ack.messageId)
                return@forEach
            }
            emitDeliveredAck(
                conversationId = ack.conversationId,
                messageId = ack.messageId,
                senderId = ack.senderId,
                recipientId = recipientId,
                source = ack.source,
                trigger = "${trigger}:${ack.trigger}",
                retryCount = ack.retryCount + 1,
            )
        }
    }

    private fun resolvedUserId(): Int? =
        (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user?.id

    private fun log(
        event: String,
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
        retryCount: Int = 0,
    ) {
        val diagnostics = chatRealtimeClient.connectionDiagnostics()
        RuntimeDiagnostics.event(
            "DeliveryAck",
            "$event source=$source conversationId=$conversationId messageId=$messageId " +
                "senderId=$senderId userId=${resolvedUserId()} trigger=$trigger retryCount=$retryCount " +
                "socketConnected=${diagnostics.connected} socketAuthenticated=${diagnostics.authenticated} " +
                "socketId=${diagnostics.socketId} socketUserId=${diagnostics.userId}",
        )
    }

    private fun logSkip(
        reason: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
        queued: Boolean = false,
        retryCount: Int = 0,
    ) {
        val diagnostics = chatRealtimeClient.connectionDiagnostics()
        RuntimeDiagnostics.event(
            "DeliveryAck",
            "MESSAGE_DELIVERY_ACK_SKIPPED reason=$reason conversationId=$conversationId messageId=$messageId " +
                "senderId=$senderId userId=${resolvedUserId()} trigger=$trigger queued=$queued " +
                "retryCount=$retryCount socketConnected=${diagnostics.connected} " +
                "socketAuthenticated=${diagnostics.authenticated} socketId=${diagnostics.socketId} " +
                "socketUserId=${diagnostics.userId}",
        )
    }

    private suspend fun queuePending(ack: PendingDeliveryAck) {
        mutex.withLock {
            if (!pendingAcks.containsKey(ack.messageId)) {
                pendingAcks[ack.messageId] = ack
            }
        }
    }

    private suspend fun removePending(messageId: Int) {
        mutex.withLock {
            pendingAcks.remove(messageId)
        }
    }

    private data class PendingDeliveryAck(
        val conversationId: Int,
        val messageId: Int,
        val senderId: Int,
        val source: String,
        val trigger: String,
        val retryCount: Int = 0,
    )
}
