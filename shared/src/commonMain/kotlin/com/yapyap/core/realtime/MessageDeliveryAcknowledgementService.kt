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
 *
 * All ingress paths (socket, FCM, REST sync, notification tap) must call [onMessagePersisted].
 */
class MessageDeliveryAcknowledgementService(
    private val chatRealtimeClient: ChatRealtimeClient,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : MessageDeliveryAcknowledgementGateway {
    private val stateMutex = Mutex()
    private val flushMutex = Mutex()
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
        parentScope.launch(dispatcherProvider.default) {
            tryFlushIfReady(trigger = "service_start")
        }
    }

    override suspend fun onMessageReceived(
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
    ) {
        RuntimeDiagnostics.enter(
            "DeliveryAck",
            "MessageDeliveryAcknowledgementService.onMessageReceived",
            "source=$source conversationId=$conversationId messageId=$messageId trigger=$trigger",
        )
        logLifecycle(
            stage = "received",
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
        RuntimeDiagnostics.enter(
            "DeliveryAck",
            "MessageDeliveryAcknowledgementService.onMessagePersisted",
            "MESSAGE_ACK_SERVICE_ENTER source=$source conversationId=$conversationId messageId=$messageId trigger=$trigger",
        )
        logLifecycle(
            stage = "persisted",
            source = source,
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            trigger = trigger,
        )

        val recipientId = resolvedUserId()
        if (recipientId == null) {
            queueAck(
                PendingDeliveryAck(conversationId, messageId, senderId, source, trigger),
                reason = "not_authenticated",
            )
            logLifecycle(
                stage = "queued_for_ack",
                source = source,
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                queued = true,
                skipReason = "not_authenticated",
            )
            return
        }
        if (senderId == recipientId) {
            logLifecycle(
                stage = "skipped",
                source = source,
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                skipReason = "own_message",
            )
            return
        }
        if (acknowledgedMessageIds.contains(messageId)) {
            logLifecycle(
                stage = "skipped",
                source = source,
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                skipReason = "already_acknowledged",
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
        tryFlushIfReady(trigger = "after_persist:$source")
    }

    override suspend fun ackConversationSummaries(
        conversations: List<Conversation>,
        trigger: String,
    ) {
        val recipientId = resolvedUserId()
        logLifecycle(
            stage = "rest_conversation_sync",
            source = SOURCE_REST_SYNC,
            conversationId = -1,
            messageId = -1,
            senderId = -1,
            trigger = "$trigger count=${conversations.size} recipientId=$recipientId",
        )
        conversations.forEach { conversation ->
            val last = conversation.lastMessage ?: return@forEach
            val messageId = last.id
            onMessageReceived(
                source = SOURCE_REST_SYNC,
                conversationId = conversation.id,
                messageId = messageId,
                senderId = last.senderId,
                trigger = trigger,
            )
            onMessagePersisted(
                source = SOURCE_REST_SYNC,
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
        logLifecycle(
            stage = "emit_attempt",
            source = source,
            conversationId = conversationId,
            messageId = messageId,
            senderId = senderId,
            trigger = trigger,
            retryCount = retryCount,
            socketConnected = diagnostics.connected,
            socketAuthenticated = diagnostics.authenticated,
        )

        if (!diagnostics.connected || !diagnostics.authenticated) {
            queueAck(
                PendingDeliveryAck(conversationId, messageId, senderId, source, trigger, retryCount),
                reason = if (!diagnostics.connected) "socket_not_connected" else "socket_not_authenticated",
            )
            logLifecycle(
                stage = "queued_for_ack",
                source = source,
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                retryCount = retryCount,
                queued = true,
                skipReason = if (!diagnostics.connected) "socket_not_connected" else "socket_not_authenticated",
                socketConnected = diagnostics.connected,
                socketAuthenticated = diagnostics.authenticated,
            )
            return
        }

        val result = chatRealtimeClient.markDelivered(conversationId, messageId)
        if (result.isSuccess) {
            stateMutex.withLock {
                acknowledgedMessageIds.add(messageId)
                pendingAcks.remove(messageId)
            }
            logLifecycle(
                stage = "emit_success",
                source = source,
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                retryCount = retryCount,
                socketConnected = diagnostics.connected,
                socketAuthenticated = diagnostics.authenticated,
            )
            RuntimeDiagnostics.event(
                "DeliveryAck",
                "DELIVERY_ACK_SENT source=$source conversationId=$conversationId messageId=$messageId " +
                    "senderId=$senderId userId=$recipientId trigger=$trigger retryCount=$retryCount " +
                    "socketConnected=${diagnostics.connected} socketAuthenticated=${diagnostics.authenticated}",
            )
        } else {
            queueAck(
                PendingDeliveryAck(conversationId, messageId, senderId, source, trigger, retryCount),
                reason = "emit_failed:${result.exceptionOrNull()?.message}",
            )
            logLifecycle(
                stage = "emit_failure",
                source = source,
                conversationId = conversationId,
                messageId = messageId,
                senderId = senderId,
                trigger = trigger,
                retryCount = retryCount,
                queued = true,
                skipReason = result.exceptionOrNull()?.message,
                socketConnected = diagnostics.connected,
                socketAuthenticated = diagnostics.authenticated,
            )
        }
    }

    private suspend fun flushPendingAcks(trigger: String) {
        flushMutex.withLock {
            RuntimeDiagnostics.enter(
                "DeliveryAck",
                "MessageDeliveryAcknowledgementService.flushPendingAcks",
                "trigger=$trigger",
            )
            val pending = stateMutex.withLock { pendingAcks.values.toList() }
            val queueSize = pending.size
            logLifecycle(
                stage = "flush_started",
                source = "internal",
                conversationId = -1,
                messageId = -1,
                senderId = -1,
                trigger = trigger,
                pendingQueueSize = queueSize,
            )
            if (pending.isEmpty()) {
                RuntimeDiagnostics.event(
                    "DeliveryAck",
                    "FLUSH_EMPTY trigger=$trigger pending_queue_size=0 reason=no_pending_acks",
                )
                logLifecycle(
                    stage = "flush_finished",
                    source = "internal",
                    conversationId = -1,
                    messageId = -1,
                    senderId = -1,
                    trigger = trigger,
                    pendingQueueSize = 0,
                )
                return
            }
            RuntimeDiagnostics.event("DeliveryAck", "FLUSH_ACK count=$queueSize trigger=$trigger")

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

            val remaining = stateMutex.withLock { pendingAcks.size }
            logLifecycle(
                stage = "flush_finished",
                source = "internal",
                conversationId = -1,
                messageId = -1,
                senderId = -1,
                trigger = trigger,
                pendingQueueSize = remaining,
            )
        }
    }

    /**
     * Drains queued acks when the socket became ready before the message was persisted
     * (common killed-app path: socket:ready → REST sync → onMessagePersisted → queue).
     */
    private suspend fun tryFlushIfReady(trigger: String) {
        val diagnostics = chatRealtimeClient.connectionDiagnostics()
        if (!diagnostics.connected || !diagnostics.authenticated) {
            RuntimeDiagnostics.event(
                "DeliveryAck",
                "TRY_FLUSH_SKIP trigger=$trigger socketConnected=${diagnostics.connected} " +
                    "socketAuthenticated=${diagnostics.authenticated} pending_queue_size=${pendingQueueSize()}",
            )
            return
        }
        flushPendingAcks(trigger = "try_flush:$trigger")
    }

    private suspend fun queueAck(ack: PendingDeliveryAck, reason: String) {
        stateMutex.withLock {
            if (!pendingAcks.containsKey(ack.messageId)) {
                pendingAcks[ack.messageId] = ack
            }
        }
        RuntimeDiagnostics.event(
            "DeliveryAck",
            "QUEUE_ACK conversationId=${ack.conversationId} messageId=${ack.messageId} " +
                "source=${ack.source} reason=$reason pending_queue_size=${pendingQueueSize()}",
        )
        tryFlushIfReady(trigger = "after_queue")
    }

    private suspend fun removePending(messageId: Int) {
        stateMutex.withLock {
            pendingAcks.remove(messageId)
        }
    }

    private fun pendingQueueSize(): Int = pendingAcks.size

    private fun resolvedUserId(): Int? =
        (observeAuthStateUseCase().value as? AuthState.Authenticated)?.user?.id

    private fun logLifecycle(
        stage: String,
        source: String,
        conversationId: Int,
        messageId: Int,
        senderId: Int,
        trigger: String,
        retryCount: Int = 0,
        queued: Boolean = false,
        skipReason: String? = null,
        socketConnected: Boolean? = null,
        socketAuthenticated: Boolean? = null,
        pendingQueueSize: Int? = null,
    ) {
        val diagnostics = chatRealtimeClient.connectionDiagnostics()
        val connected = socketConnected ?: diagnostics.connected
        val authenticated = socketAuthenticated ?: diagnostics.authenticated
        val queueSize = pendingQueueSize ?: pendingQueueSize()
        RuntimeDiagnostics.event(
            "DeliveryAck",
            "LIFECYCLE stage=$stage source=$source conversationId=$conversationId messageId=$messageId " +
                "senderId=$senderId userId=${resolvedUserId()} trigger=$trigger retryCount=$retryCount " +
                "queued=$queued skipReason=${skipReason.orEmpty()} socketConnected=$connected " +
                "socketAuthenticated=$authenticated pending_queue_size=$queueSize " +
                "socketId=${diagnostics.socketId} socketUserId=${diagnostics.userId}",
        )
    }

    private data class PendingDeliveryAck(
        val conversationId: Int,
        val messageId: Int,
        val senderId: Int,
        val source: String,
        val trigger: String,
        val retryCount: Int = 0,
    )

    private companion object {
        const val SOURCE_REST_SYNC = "rest_sync"
    }
}
