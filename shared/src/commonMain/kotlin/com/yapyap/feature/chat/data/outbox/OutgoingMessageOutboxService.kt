package com.yapyap.feature.chat.data.outbox

import com.yapyap.core.diagnostics.OutboxPipelineTrace
import com.yapyap.core.diagnostics.RealtimeTransportClock
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.network.NetworkConnectivityMonitor
import com.yapyap.core.network.getOrThrow
import com.yapyap.core.realtime.ChatRealtimeClient
import com.yapyap.core.realtime.RealtimeConnectionState
import com.yapyap.core.realtime.SocketSendMessagePayload
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.auth.domain.model.AuthState
import com.yapyap.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.yapyap.feature.chat.data.dto.SendMessageRequestDto
import com.yapyap.feature.chat.data.mapper.toDomain
import com.yapyap.feature.chat.data.outbox.OutgoingMessageRestGateway
import com.yapyap.feature.chat.domain.entity.Message
import com.yapyap.feature.chat.domain.entity.MessageStatus
import com.yapyap.feature.chat.domain.entity.MessageType
import com.yapyap.feature.chat.domain.entity.OutboxMessage
import com.yapyap.feature.chat.domain.entity.OutboxStatus
import com.yapyap.feature.chat.domain.repository.OutgoingMessageCacheWriter
import com.yapyap.feature.chat.util.currentTimestampIso
import com.yapyap.feature.chat.util.newClientMessageId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OutgoingMessageOutboxService(
    private val store: OutgoingMessageOutboxPersistence,
    cacheWriterProvider: () -> OutgoingMessageCacheWriter,
    private val chatApi: OutgoingMessageRestGateway,
    private val chatRealtimeClient: ChatRealtimeClient,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val observeAuthStateUseCase: ObserveAuthStateUseCase,
    private val dispatcherProvider: DispatcherProvider,
    private val config: OutboxConfig = OutboxConfig(),
) : OutgoingMessageOutboxGateway {
    private val cacheWriter: OutgoingMessageCacheWriter by lazy(cacheWriterProvider)

    private val flushMutex = Mutex()
    private var parentScope: CoroutineScope? = null
    private var backoffJob: kotlinx.coroutines.Job? = null
    private var reconnectFlushJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var reconnectFlushActive: Boolean = false
    private var flushSequence = 0L

    internal val flushTriggers = mutableListOf<String>()

    override fun start(parentScope: CoroutineScope) {
        bindParentScope(parentScope)
        networkMonitor.start()
        parentScope.launch(dispatcherProvider.default) {
            store.withLock {
                store.resetSendingToQueued()
                restoreOutboxToCache()
            }
            flushPendingOutgoingMessages(trigger = "service_start")
        }
        parentScope.launch(dispatcherProvider.default) {
            networkMonitor.isOnline.collect { online ->
                    logEvent(
                        "OUTBOX_NETWORK_CHANGED online=$online socket=${chatRealtimeClient.connectionState.value}",
                    )
                    if (online) {
                        RealtimeTransportClock.recordNetworkOnline()
                        scheduleReconnectFlush()
                    }
                }
        }
        parentScope.launch(dispatcherProvider.default) {
            chatRealtimeClient.connectionState.collect { state ->
                if (state is RealtimeConnectionState.Ready && !reconnectFlushActive) {
                    flushPendingOutgoingMessages(trigger = "socket_ready")
                }
            }
        }
        parentScope.launch(dispatcherProvider.default) {
            observeAuthStateUseCase()
                .map { it is AuthState.Authenticated }
                .distinctUntilChanged()
                .collect { authenticated ->
                    if (authenticated) {
                        flushPendingOutgoingMessages(trigger = "user_authenticated")
                    }
                }
        }
    }

    internal fun bindParentScope(scope: CoroutineScope) {
        parentScope = scope
    }

    override suspend fun enqueue(
        conversationId: Int,
        senderId: Int,
        content: String,
    ): Message {
        val clientMessageId = newClientMessageId()
        val createdAt = currentTimestampIso()
        val outboxMessage = store.withLock {
            val entry = OutboxMessage(
                clientMessageId = clientMessageId,
                conversationId = conversationId,
                senderId = senderId,
                content = content,
                messageType = MessageType.TEXT,
                status = OutboxStatus.QUEUED,
                createdAt = createdAt,
                retryCount = 0,
                nextRetryAtMs = 0L,
                enqueueOrder = store.nextEnqueueOrder(),
            )
            store.insert(entry)
            entry
        }
        val message = outboxMessage.toDomainMessage()
        cacheWriter.upsertOutboxMessage(message)
        OutboxPipelineTrace.stage(
            stage = "OUTBOX_ENQUEUED",
            clientMessageId = clientMessageId,
            conversationId = conversationId,
            enqueueOrder = outboxMessage.enqueueOrder,
            retryCount = outboxMessage.retryCount,
            extra = mapOf("trigger" to "enqueue"),
        )
        logLifecycle(
            event = "OUTBOX_MESSAGE_CREATED",
            conversationId = conversationId,
            clientMessageId = clientMessageId,
            state = OutboxStatus.QUEUED.name,
            trigger = "enqueue",
        )
        logLifecycle(
            event = "OUTBOX_ENQUEUED",
            conversationId = conversationId,
            clientMessageId = clientMessageId,
            state = OutboxStatus.QUEUED.name,
            trigger = "enqueue",
            queueSize = store.pendingCount(),
        )
        flushPendingOutgoingMessages(trigger = "enqueue")
        return message
    }

    override suspend fun retry(clientMessageId: String, trigger: String) {
        store.withLock {
            val existing = store.selectByClientMessageId(clientMessageId) ?: return@withLock
            store.updateStatus(
                clientMessageId = clientMessageId,
                status = OutboxStatus.QUEUED,
                retryCount = 0,
                nextRetryAtMs = 0L,
            )
            cacheWriter.upsertOutboxMessage(
                existing.copy(status = OutboxStatus.QUEUED, retryCount = 0, nextRetryAtMs = 0L).toDomainMessage(),
            )
        }
        logLifecycle(
            event = "OUTBOX_STATE_CHANGED",
            conversationId = store.selectByClientMessageId(clientMessageId)?.conversationId,
            clientMessageId = clientMessageId,
            state = OutboxStatus.QUEUED.name,
            trigger = trigger,
        )
        flushPendingOutgoingMessages(trigger = trigger)
    }

    override suspend fun delete(clientMessageId: String, trigger: String) {
        val conversationId = store.withLock {
            val existing = store.selectByClientMessageId(clientMessageId) ?: return@withLock null
            store.delete(clientMessageId)
            existing.conversationId
        } ?: return
        cacheWriter.removeOutboxMessage(clientMessageId)
        logLifecycle(
            event = "OUTBOX_MESSAGE_REMOVED",
            conversationId = conversationId,
            clientMessageId = clientMessageId,
            state = "DELETED",
            trigger = trigger,
            queueSize = store.pendingCount(),
        )
    }

    override suspend fun flushPendingOutgoingMessages(trigger: String) {
        flushMutex.withLock {
            performFlush(trigger)
        }
    }

    internal fun triggerNetworkOnlineReconnect() {
        scheduleReconnectFlush()
    }

    internal suspend fun awaitReconnectFlush() {
        reconnectFlushJob?.join()
    }

    private fun scheduleReconnectFlush() {
        val scope = parentScope ?: return
        reconnectFlushJob?.cancel()
        reconnectFlushJob = scope.launch(dispatcherProvider.default) {
            reconnectFlushActive = true
            try {
                flushMutex.withLock {
                    val socketReady = waitForSocketReady(config.socketReadyWaitMs)
                    val trigger = if (socketReady) {
                        "socket_ready_after_network"
                    } else {
                        "socket_timeout_rest"
                    }
                    performFlush(trigger)
                }
            } finally {
                reconnectFlushActive = false
            }
        }
    }

    internal suspend fun waitForSocketReady(timeoutMs: Long): Boolean {
        if (chatRealtimeClient.connectionState.value is RealtimeConnectionState.Ready) {
            return true
        }
        val deadline = Clock.System.now().toEpochMilliseconds() + timeoutMs
        while (Clock.System.now().toEpochMilliseconds() < deadline) {
            if (chatRealtimeClient.connectionState.value is RealtimeConnectionState.Ready) {
                return true
            }
            delay(config.socketReadyPollMs)
        }
        return chatRealtimeClient.connectionState.value is RealtimeConnectionState.Ready
    }

    override fun pendingMessagesFor(conversationId: Int): List<Message> {
        return store.selectForConversation(conversationId).map { it.toDomainMessage() }
    }

    private suspend fun performFlush(trigger: String) {
        if (observeAuthStateUseCase().value !is AuthState.Authenticated) {
            logEvent("OUTBOX_FLUSH_SKIPPED trigger=$trigger reason=not_authenticated")
            return
        }
        flushTriggers.add(trigger)
        val flushId = nextFlushId()
        val flushStartMs = Clock.System.now().toEpochMilliseconds()
        val pending = store.withLock { store.selectPendingOrdered() }
        val socketStateAtFlushStart = chatRealtimeClient.connectionState.value
        val networkOnlineAtFlushStart = networkMonitor.isOnline.value
        OutboxPipelineTrace.stage(
            stage = "OUTBOX_FLUSH_STARTED",
            conversationId = pending.firstOrNull()?.conversationId,
            extra = mapOf(
                "flushId" to flushId,
                "trigger" to trigger,
                "queueSize" to pending.size.toString(),
                "pendingClientMessageIds" to pending.joinToString(",") { it.clientMessageId },
                "networkOnline" to networkOnlineAtFlushStart.toString(),
                "socketStateAtFlushStart" to socketStateAtFlushStart.toString(),
                "timeSinceSocketReadyMs" to RealtimeTransportClock.timeSinceSocketReadyMs(flushStartMs)?.toString(),
                "timeSinceNetworkOnlineMs" to RealtimeTransportClock.timeSinceNetworkOnlineMs(flushStartMs)?.toString(),
                "socketReadyGeneration" to RealtimeTransportClock.socketReadyGeneration().toString(),
                "transportDecisionMode" to "live_per_message",
            ),
        )
        logLifecycle(
            event = "OUTBOX_FLUSH_STARTED",
            trigger = trigger,
            networkOnline = networkOnlineAtFlushStart,
            socketState = socketStateAtFlushStart.toString(),
            queueSize = store.pendingCount(),
            details = "flushId=$flushId",
        )
        if (pending.isEmpty()) {
            logLifecycle(
                event = "OUTBOX_FLUSH_FINISHED",
                trigger = trigger,
                queueSize = 0,
            )
            return
        }
        var sentAny = false
        var messageIndex = 0
        for (entry in pending) {
            messageIndex += 1
            if (entry.status == OutboxStatus.SENDING) continue
            val online = networkMonitor.isOnline.value
            val nowMs = Clock.System.now().toEpochMilliseconds()
            if (!online && entry.nextRetryAtMs > nowMs) continue
            val blockedByEarlierFailure = hasEarlierUnsent(entry, pending)
            if (blockedByEarlierFailure) break
            val result = sendSingle(
                entry = entry,
                trigger = trigger,
                online = online,
                flushId = flushId,
                flushStartMs = flushStartMs,
                messageIndex = messageIndex,
                queueSize = pending.size,
            )
            if (result == SendAttemptResult.RetryableFailure) {
                scheduleBackoffRetry(trigger)
                break
            }
            if (result == SendAttemptResult.Success) {
                sentAny = true
            }
        }
        if (sentAny && store.selectPendingOrdered().isNotEmpty()) {
            performFlush("continue_flush")
        }
        OutboxPipelineTrace.stage(
            stage = "OUTBOX_FLUSH_FINISHED",
            extra = mapOf(
                "flushId" to flushId,
                "trigger" to trigger,
                "queueSize" to store.pendingCount().toString(),
                "socketStateAtFlushEnd" to chatRealtimeClient.connectionState.value.toString(),
                "transportDecisionMode" to "live_per_message",
            ),
        )
        logLifecycle(
            event = "OUTBOX_FLUSH_FINISHED",
            trigger = trigger,
            queueSize = store.pendingCount(),
            networkOnline = networkMonitor.isOnline.value,
            socketState = chatRealtimeClient.connectionState.value.toString(),
            details = "flushId=$flushId",
        )
    }

    private fun nextFlushId(): String {
        flushSequence += 1
        return "flush-${flushSequence}-${Clock.System.now().toEpochMilliseconds()}"
    }

    private suspend fun sendSingle(
        entry: OutboxMessage,
        trigger: String,
        online: Boolean,
        flushId: String,
        flushStartMs: Long,
        messageIndex: Int,
        queueSize: Int,
    ): SendAttemptResult {
        val decisionMs = Clock.System.now().toEpochMilliseconds()
        val socketState = chatRealtimeClient.connectionState.value
        OutboxPipelineTrace.stage(
            stage = "NETWORK_STATE_AT_SEND",
            clientMessageId = entry.clientMessageId,
            conversationId = entry.conversationId,
            enqueueOrder = entry.enqueueOrder,
            retryCount = entry.retryCount,
            extra = mapOf(
                "flushId" to flushId,
                "messageIndex" to messageIndex.toString(),
                "queueSize" to queueSize.toString(),
                "networkOnline" to online.toString(),
                "liveNetworkOnline" to networkMonitor.isOnline.value.toString(),
                "trigger" to trigger,
                "elapsedSinceFlushStartMs" to (decisionMs - flushStartMs).toString(),
            ),
        )
        OutboxPipelineTrace.stage(
            stage = "SOCKET_STATE_AT_SEND",
            clientMessageId = entry.clientMessageId,
            conversationId = entry.conversationId,
            enqueueOrder = entry.enqueueOrder,
            retryCount = entry.retryCount,
            extra = mapOf(
                "flushId" to flushId,
                "messageIndex" to messageIndex.toString(),
                "socketState" to socketState.toString(),
                "socketReady" to (socketState is RealtimeConnectionState.Ready).toString(),
                "trigger" to trigger,
                "elapsedSinceFlushStartMs" to (decisionMs - flushStartMs).toString(),
            ),
        )
        markSending(entry)
        logLifecycle(
            event = "OUTBOX_RETRY_STARTED",
            conversationId = entry.conversationId,
            clientMessageId = entry.clientMessageId,
            state = OutboxStatus.SENDING.name,
            trigger = trigger,
            retryCount = entry.retryCount,
            networkOnline = online,
            socketState = socketState.toString(),
            details = "flushId=$flushId messageIndex=$messageIndex/$queueSize",
        )
        val sendResult = transmit(
            entry = entry,
            flushId = flushId,
            flushStartMs = flushStartMs,
            messageIndex = messageIndex,
            queueSize = queueSize,
            trigger = trigger,
        )
        return sendResult.fold(
            onSuccess = { serverMessage ->
                OutboxPipelineTrace.stage(
                    stage = "OUTBOX_MARK_SENT",
                    clientMessageId = entry.clientMessageId,
                    serverMessageId = serverMessage.id,
                    conversationId = entry.conversationId,
                    enqueueOrder = entry.enqueueOrder,
                    retryCount = entry.retryCount,
                    extra = mapOf("trigger" to trigger),
                )
                store.withLock { store.delete(entry.clientMessageId) }
                OutboxPipelineTrace.stage(
                    stage = "OUTBOX_DELETE",
                    clientMessageId = entry.clientMessageId,
                    serverMessageId = serverMessage.id,
                    conversationId = entry.conversationId,
                    enqueueOrder = entry.enqueueOrder,
                    retryCount = entry.retryCount,
                )
                cacheWriter.markOutboxSent(entry.clientMessageId, serverMessage)
                OutboxPipelineTrace.stage(
                    stage = "RECONNECT_AFTER_SEND",
                    clientMessageId = entry.clientMessageId,
                    serverMessageId = serverMessage.id,
                    conversationId = entry.conversationId,
                    extra = mapOf(
                        "socketStateAfterSend" to chatRealtimeClient.connectionState.value.toString(),
                        "networkOnlineAfterSend" to networkMonitor.isOnline.value.toString(),
                    ),
                )
                logLifecycle(
                    event = "OUTBOX_SEND_SUCCESS",
                    conversationId = entry.conversationId,
                    clientMessageId = entry.clientMessageId,
                    serverMessageId = serverMessage.id,
                    state = serverMessage.status.name,
                    trigger = trigger,
                    retryCount = entry.retryCount,
                    queueSize = store.pendingCount(),
                )
                logLifecycle(
                    event = "OUTBOX_MESSAGE_REMOVED",
                    conversationId = entry.conversationId,
                    clientMessageId = entry.clientMessageId,
                    serverMessageId = serverMessage.id,
                    state = "SENT",
                    trigger = trigger,
                    queueSize = store.pendingCount(),
                )
                SendAttemptResult.Success
            },
            onFailure = { error ->
                handleSendFailure(
                    entry = entry,
                    error = error,
                    online = online,
                    trigger = trigger,
                )
            },
        )
    }

    private suspend fun transmit(
        entry: OutboxMessage,
        flushId: String,
        flushStartMs: Long,
        messageIndex: Int,
        queueSize: Int,
        trigger: String,
    ): Result<Message> {
        val socketResult = trySendViaSocket(
            entry = entry,
            flushId = flushId,
            flushStartMs = flushStartMs,
            messageIndex = messageIndex,
            queueSize = queueSize,
            trigger = trigger,
            reason = "socket_ready",
        )
        if (socketResult != null) {
            return socketResult
        }

        if (!networkMonitor.isOnline.value) {
            logTransportDecision(
                entry = entry,
                flushId = flushId,
                messageIndex = messageIndex,
                queueSize = queueSize,
                trigger = trigger,
                transport = "NONE",
                reason = "offline",
                socketState = chatRealtimeClient.connectionState.value,
                networkOnline = false,
                flushStartMs = flushStartMs,
            )
            return Result.failure(OfflineException)
        }

        val recheckSocketResult = trySendViaSocket(
            entry = entry,
            flushId = flushId,
            flushStartMs = flushStartMs,
            messageIndex = messageIndex,
            queueSize = queueSize,
            trigger = trigger,
            reason = "socket_ready_before_rest",
        )
        if (recheckSocketResult != null) {
            return recheckSocketResult
        }

        logTransportDecision(
            entry = entry,
            flushId = flushId,
            messageIndex = messageIndex,
            queueSize = queueSize,
            trigger = trigger,
            transport = "REST",
            reason = "socket_not_ready",
            socketState = chatRealtimeClient.connectionState.value,
            networkOnline = true,
            flushStartMs = flushStartMs,
        )
        OutboxPipelineTrace.stage(
            stage = "REST_SEND_BEGIN",
            clientMessageId = entry.clientMessageId,
            conversationId = entry.conversationId,
            enqueueOrder = entry.enqueueOrder,
            retryCount = entry.retryCount,
            extra = mapOf("flushId" to flushId),
        )
        return runCatching {
            chatApi.sendMessage(
                conversationId = entry.conversationId,
                request = SendMessageRequestDto(
                    content = entry.content,
                    messageType = MessageType.toApi(entry.messageType),
                    clientMessageId = entry.clientMessageId,
                ),
            ).getOrThrow().toDomain()
        }.also { result ->
            if (result.isSuccess) {
                OutboxPipelineTrace.stage(
                    stage = "REST_SEND_SUCCESS",
                    clientMessageId = entry.clientMessageId,
                    serverMessageId = result.getOrNull()?.id,
                    conversationId = entry.conversationId,
                    enqueueOrder = entry.enqueueOrder,
                    retryCount = entry.retryCount,
                    extra = mapOf("flushId" to flushId),
                )
            } else {
                OutboxPipelineTrace.stage(
                    stage = "REST_SEND_FAILURE",
                    clientMessageId = entry.clientMessageId,
                    conversationId = entry.conversationId,
                    enqueueOrder = entry.enqueueOrder,
                    retryCount = entry.retryCount,
                    extra = mapOf("error" to (result.exceptionOrNull()?.message ?: "unknown")),
                )
            }
        }
    }

    private suspend fun trySendViaSocket(
        entry: OutboxMessage,
        flushId: String,
        flushStartMs: Long,
        messageIndex: Int,
        queueSize: Int,
        trigger: String,
        reason: String,
    ): Result<Message>? {
        val socketState = chatRealtimeClient.connectionState.value
        if (socketState !is RealtimeConnectionState.Ready) {
            return null
        }
        logTransportDecision(
            entry = entry,
            flushId = flushId,
            messageIndex = messageIndex,
            queueSize = queueSize,
            trigger = trigger,
            transport = "SOCKET",
            reason = reason,
            socketState = socketState,
            networkOnline = networkMonitor.isOnline.value,
            flushStartMs = flushStartMs,
        )
        val socketResult = chatRealtimeClient.sendMessage(
            SocketSendMessagePayload(
                conversationId = entry.conversationId,
                content = entry.content,
                messageType = MessageType.toApi(entry.messageType),
                clientMessageId = entry.clientMessageId,
            ),
        )
        if (socketResult.isSuccess) {
            return Result.success(socketResult.getOrThrow().toDomain())
        }
        OutboxPipelineTrace.stage(
            stage = "SOCKET_SEND_FAILURE",
            clientMessageId = entry.clientMessageId,
            conversationId = entry.conversationId,
            enqueueOrder = entry.enqueueOrder,
            retryCount = entry.retryCount,
            extra = mapOf(
                "flushId" to flushId,
                "error" to (socketResult.exceptionOrNull()?.message ?: "unknown"),
            ),
        )
        return null
    }

    private fun logTransportDecision(
        entry: OutboxMessage,
        flushId: String,
        messageIndex: Int,
        queueSize: Int,
        trigger: String,
        transport: String,
        reason: String,
        socketState: RealtimeConnectionState,
        networkOnline: Boolean,
        flushStartMs: Long,
    ) {
        val decisionMs = Clock.System.now().toEpochMilliseconds()
        OutboxPipelineTrace.stage(
            stage = "OUTBOX_TRANSPORT_DECISION",
            clientMessageId = entry.clientMessageId,
            conversationId = entry.conversationId,
            enqueueOrder = entry.enqueueOrder,
            retryCount = entry.retryCount,
            extra = mapOf(
                "flushId" to flushId,
                "messageIndex" to messageIndex.toString(),
                "queueSize" to queueSize.toString(),
                "trigger" to trigger,
                "transport" to transport,
                "reasonTransportChosen" to reason,
                "socketState" to socketState.toString(),
                "networkState" to if (networkOnline) "Online" else "Offline",
                "timeSinceSocketReadyMs" to RealtimeTransportClock.timeSinceSocketReadyMs(decisionMs)?.toString(),
                "timeSinceNetworkOnlineMs" to RealtimeTransportClock.timeSinceNetworkOnlineMs(decisionMs)?.toString(),
                "elapsedSinceFlushStartMs" to (decisionMs - flushStartMs).toString(),
                "transportDecisionMode" to "live_per_message",
            ),
        )
    }

    private suspend fun handleSendFailure(
        entry: OutboxMessage,
        error: Throwable,
        online: Boolean,
        trigger: String,
    ): SendAttemptResult {
        val socketState = chatRealtimeClient.connectionState.value
        val retryable = OutgoingSendFailureClassifier.isRetryable(
            throwable = error,
            socketReady = socketState is RealtimeConnectionState.Ready,
            networkOnline = online,
        )
        logLifecycle(
            event = "OUTBOX_SEND_FAILED",
            conversationId = entry.conversationId,
            clientMessageId = entry.clientMessageId,
            state = if (retryable) OutboxStatus.QUEUED.name else OutboxStatus.FAILED.name,
            trigger = trigger,
            retryCount = entry.retryCount + 1,
            networkOnline = online,
            socketState = socketState.toString(),
            details = error.message,
        )
        if (retryable && entry.retryCount + 1 < config.maxRetryCount) {
            val nextRetry = nextBackoffDelayMs(entry.retryCount + 1)
            store.withLock {
                store.updateStatus(
                    clientMessageId = entry.clientMessageId,
                    status = OutboxStatus.QUEUED,
                    retryCount = entry.retryCount + 1,
                    nextRetryAtMs = Clock.System.now().toEpochMilliseconds() + nextRetry,
                )
            }
            cacheWriter.upsertOutboxMessage(
                entry.copy(
                    status = OutboxStatus.QUEUED,
                    retryCount = entry.retryCount + 1,
                    nextRetryAtMs = Clock.System.now().toEpochMilliseconds() + nextRetry,
                ).toDomainMessage(),
            )
            logLifecycle(
                event = "OUTBOX_STATE_CHANGED",
                conversationId = entry.conversationId,
                clientMessageId = entry.clientMessageId,
                state = OutboxStatus.QUEUED.name,
                trigger = trigger,
                retryCount = entry.retryCount + 1,
            )
            scheduleBackoffRetry(trigger)
            return SendAttemptResult.RetryableFailure
        }
        store.withLock {
            store.updateStatus(
                clientMessageId = entry.clientMessageId,
                status = OutboxStatus.FAILED,
                retryCount = entry.retryCount + 1,
                nextRetryAtMs = 0L,
            )
        }
        cacheWriter.markOutboxFailed(entry.clientMessageId)
        logLifecycle(
            event = "OUTBOX_STATE_CHANGED",
            conversationId = entry.conversationId,
            clientMessageId = entry.clientMessageId,
            state = OutboxStatus.FAILED.name,
            trigger = trigger,
            retryCount = entry.retryCount + 1,
        )
        return SendAttemptResult.PermanentFailure
    }

    private suspend fun markSending(entry: OutboxMessage) {
        store.withLock {
            store.updateStatus(
                clientMessageId = entry.clientMessageId,
                status = OutboxStatus.SENDING,
                retryCount = entry.retryCount,
                nextRetryAtMs = entry.nextRetryAtMs,
            )
        }
        cacheWriter.markOutboxSending(entry.clientMessageId)
        OutboxPipelineTrace.stage(
            stage = "OUTBOX_MARK_SENDING",
            clientMessageId = entry.clientMessageId,
            conversationId = entry.conversationId,
            enqueueOrder = entry.enqueueOrder,
            retryCount = entry.retryCount,
        )
        logLifecycle(
            event = "OUTBOX_STATE_CHANGED",
            conversationId = entry.conversationId,
            clientMessageId = entry.clientMessageId,
            state = OutboxStatus.SENDING.name,
            trigger = "send_attempt",
        )
    }

    private fun hasEarlierUnsent(entry: OutboxMessage, pending: List<OutboxMessage>): Boolean {
        return pending.any { candidate ->
            candidate.conversationId == entry.conversationId &&
                candidate.enqueueOrder < entry.enqueueOrder &&
                candidate.status != OutboxStatus.SENDING
        }
    }

    private fun nextBackoffDelayMs(retryCount: Int): Long {
        val index = (retryCount - 1).coerceIn(0, config.backoffDelaysMs.lastIndex)
        return config.backoffDelaysMs[index]
    }

    private fun scheduleBackoffRetry(trigger: String) {
        val scope = parentScope ?: return
        backoffJob?.cancel()
        backoffJob = scope.launch {
            val pending = store.selectPendingOrdered()
            val nextDue = pending.minOfOrNull { it.nextRetryAtMs } ?: return@launch
            val delayMs = (nextDue - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(1_000L)
            delay(delayMs.coerceAtMost(60_000L))
            flushPendingOutgoingMessages(trigger = "backoff_$trigger")
        }
    }

    private suspend fun restoreOutboxToCache() {
        store.selectAllOrdered().forEach { entry ->
            cacheWriter.upsertOutboxMessage(entry.toDomainMessage())
        }
    }

    private fun OutboxMessage.toDomainMessage(): Message {
        val messageStatus = when (status) {
            OutboxStatus.QUEUED -> MessageStatus.QUEUED
            OutboxStatus.SENDING -> MessageStatus.SENDING
            OutboxStatus.FAILED -> MessageStatus.FAILED
        }
        return Message(
            id = null,
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            messageType = messageType,
            mediaUrl = null,
            status = messageStatus,
            clientMessageId = clientMessageId,
            createdAt = createdAt,
            isPending = status != OutboxStatus.FAILED,
        )
    }

    private fun logLifecycle(
        event: String,
        conversationId: Int? = null,
        clientMessageId: String? = null,
        serverMessageId: Int? = null,
        state: String? = null,
        trigger: String? = null,
        retryCount: Int? = null,
        networkOnline: Boolean? = null,
        socketState: String? = null,
        queueSize: Long? = null,
        details: String? = null,
    ) {
        val parts = buildList {
            add("event=$event")
            conversationId?.let { add("conversationId=$it") }
            clientMessageId?.let { add("clientMessageId=$it") }
            serverMessageId?.let { add("serverMessageId=$it") }
            state?.let { add("state=$it") }
            trigger?.let { add("trigger=$trigger") }
            retryCount?.let { add("retryCount=$it") }
            networkOnline?.let { add("networkOnline=$it") }
            socketState?.let { add("socketState=$it") }
            queueSize?.let { add("queueSize=$it") }
            details?.let { add("error=$it") }
        }
        logEvent(parts.joinToString(" "))
    }

    private fun logEvent(message: String) {
        RuntimeDiagnostics.event("Outbox", message)
    }

    private enum class SendAttemptResult {
        Success,
        RetryableFailure,
        PermanentFailure,
    }

    private object OfflineException : Exception("Offline")

    data class OutboxConfig(
        val maxRetryCount: Int = 50,
        val backoffDelaysMs: List<Long> = listOf(1_000, 2_000, 5_000, 10_000, 30_000, 60_000),
        val socketReadyWaitMs: Long = 300L,
        val socketReadyPollMs: Long = 10L,
    )
}
