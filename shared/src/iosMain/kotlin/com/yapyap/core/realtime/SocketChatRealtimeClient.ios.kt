package com.yapyap.core.realtime

import co.touchlab.kermit.Logger
import com.yapyap.core.datastore.TokenStorage
import com.yapyap.core.diagnostics.OutboxPipelineTrace
import com.yapyap.core.diagnostics.RealtimeTransportClock
import com.yapyap.core.diagnostics.RuntimeDiagnostics
import com.yapyap.core.network.Environment
import com.yapyap.core.util.DispatcherProvider
import com.yapyap.feature.chat.data.dto.MessageDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min

private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L

class SocketChatRealtimeClient(
    private val socketEventParser: SocketEventParser,
    private val tokenStorage: TokenStorage,
    dispatcherProvider: DispatcherProvider,
) : ChatRealtimeClient {
    private val log = Logger.withTag("SocketIO")
    private val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    private val connectMutex = Mutex()

    private val _connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Disconnected,
    )
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    override val isReady: Boolean
        get() = _connectionState.value is RealtimeConnectionState.Ready

    override fun connectionDiagnostics(): SocketConnectionDiagnostics {
        val readyState = _connectionState.value as? RealtimeConnectionState.Ready
        return SocketConnectionDiagnostics(
            connected = nativeConnected,
            authenticated = readyState != null,
            socketId = null,
            userId = readyState?.userId,
        )
    }

    private val pendingAcks = mutableMapOf<Int, CompletableDeferred<String?>>()
    private var nextAckId = 1
    private var reconnectJob: Job? = null
    private var shouldStayConnected = false
    private var reconnectAttempt = 0
    private var backoffMs = INITIAL_BACKOFF_MS
    private var activeAccessToken: String? = null
    private var nativeConnected = false

    init {
        IosSocketBridge.setEventListener { event, payload ->
            scope.launch { handleNativeEvent(event, payload) }
        }
        IosSocketBridge.setAckListener { ackId, payload ->
            pendingAcks.remove(ackId)?.complete(payload)
        }
    }

    override fun connect(accessToken: String) {
        shouldStayConnected = true
        activeAccessToken = accessToken
        reconnectAttempt = 0
        reconnectJob?.cancel()
        log.d { "connect requested to ${Environment.SOCKET_URL}" }
        RuntimeDiagnostics.event("Socket", "connect requested url=${Environment.SOCKET_URL}")
        reconnectJob = scope.launch {
            connectInternal(accessToken)
        }
    }

    override fun disconnect() {
        RuntimeDiagnostics.event("Socket", "disconnect requested")
        shouldStayConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        nativeConnected = false
        IosSocketBridge.disconnect()
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    override fun refreshAccessToken(accessToken: String) {
        if (!shouldStayConnected) return
        activeAccessToken = accessToken
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            connectInternal(accessToken)
        }
    }

    override suspend fun sendMessage(payload: SocketSendMessagePayload): Result<MessageDto> {
        if (!isReady || !nativeConnected) {
            return Result.failure(IllegalStateException("Socket not connected"))
        }
        return emitWithAck(SocketConstants.MESSAGE_SEND, socketEventParser.encodePayload(payload))
            .let { raw -> socketEventParser.parseSendMessageAck(raw ?: return Result.failure(IllegalStateException("Empty socket ack"))) }
    }

    override fun emitTypingStart(conversationId: Int) {
        emitConversationEvent(SocketConstants.TYPING_START, conversationId)
    }

    override fun emitTypingStop(conversationId: Int) {
        emitConversationEvent(SocketConstants.TYPING_STOP, conversationId)
    }

    override suspend fun markRead(conversationId: Int, messageId: Int?): Result<Unit> {
        if (!isReady || !nativeConnected) {
            return Result.failure(IllegalStateException("Socket not connected"))
        }
        val payload = SocketMessageReadPayload(
            conversationId = conversationId,
            messageId = messageId,
        )
        return emitWithAck(SocketConstants.MESSAGE_READ, socketEventParser.encodePayload(payload))
            .let { raw -> socketEventParser.parseAckSuccess(raw ?: return Result.failure(IllegalStateException("Empty socket ack"))) }
    }

    override suspend fun markDelivered(conversationId: Int, messageId: Int): Result<Unit> {
        val diagnostics = connectionDiagnostics()
        if (!isReady || !nativeConnected) {
            RuntimeDiagnostics.event(
                "DeliveryAck",
                "MESSAGE_DELIVERY_ACK_SKIPPED reason=socket_not_ready conversationId=$conversationId " +
                    "messageId=$messageId socketConnected=${diagnostics.connected} " +
                    "socketAuthenticated=${diagnostics.authenticated} socketId=${diagnostics.socketId}",
            )
            return Result.failure(IllegalStateException("Socket not connected"))
        }
        val payload = SocketMessageDeliveredPayload(
            conversationId = conversationId,
            messageId = messageId,
        )
        val payloadJson = socketEventParser.encodePayload(payload)
        RuntimeDiagnostics.event(
            "DeliveryAck",
            "MESSAGE_DELIVERY_ACK_EMIT event=${SocketConstants.MESSAGE_DELIVERED} conversationId=$conversationId " +
                "messageId=$messageId socketConnected=${diagnostics.connected} " +
                "socketAuthenticated=${diagnostics.authenticated} socketId=${diagnostics.socketId} " +
                "payload=$payloadJson",
        )
        val raw = emitWithAck(SocketConstants.MESSAGE_DELIVERED, payloadJson)
        if (raw == null) {
            RuntimeDiagnostics.event(
                "DeliveryAck",
                "MESSAGE_DELIVERY_ACK_SKIPPED reason=empty_socket_ack conversationId=$conversationId " +
                    "messageId=$messageId socketId=${diagnostics.socketId}",
            )
            return Result.failure(IllegalStateException("Empty socket ack"))
        }
        val result = socketEventParser.parseAckSuccess(raw)
        if (result.isSuccess) {
            RuntimeDiagnostics.event(
                "DeliveryAck",
                "MESSAGE_DELIVERY_ACK_SUCCESS conversationId=$conversationId messageId=$messageId " +
                    "socketId=${diagnostics.socketId} ackResponse=$raw",
            )
        }
        return result
    }

    override suspend fun acceptCall(callSessionId: String): Result<Unit> {
        return emitCallAction(SocketConstants.CALL_ACCEPT, callSessionId)
    }

    override suspend fun rejectCall(callSessionId: String): Result<Unit> {
        return emitCallAction(SocketConstants.CALL_REJECT, callSessionId)
    }

    override suspend fun endCall(callSessionId: String): Result<Unit> {
        return emitCallAction(SocketConstants.CALL_END, callSessionId)
    }

    private suspend fun emitCallAction(eventName: String, callSessionId: String): Result<Unit> {
        if (!isReady || !nativeConnected) {
            return Result.failure(IllegalStateException("Socket not connected"))
        }
        val payload = socketEventParser.encodePayload(CallSessionIdPayload(callSessionId = callSessionId))
        return emitWithAck(eventName, payload)
            .let { raw -> socketEventParser.parseAckSuccess(raw ?: return Result.failure(IllegalStateException("Empty socket ack"))) }
    }

    private suspend fun connectInternal(accessToken: String) {
        connectMutex.withLock {
            nativeConnected = false
            _connectionState.value = RealtimeConnectionState.Connecting
            IosSocketBridge.connect(Environment.SOCKET_URL, accessToken)
        }
    }

    private suspend fun emitWithAck(event: String, payloadJson: String): String? {
        val ackId = nextAckId++
        val deferred = CompletableDeferred<String?>()
        pendingAcks[ackId] = deferred
        IosSocketBridge.emit(event, payloadJson, ackId)
        return deferred.await()
    }

    private fun emitConversationEvent(eventName: String, conversationId: Int) {
        if (!isReady || !nativeConnected) return
        val payload = socketEventParser.encodePayload(SocketConversationPayload(conversationId))
        IosSocketBridge.emit(eventName, payload, ackId = -1)
    }

    private suspend fun handleNativeEvent(event: String, payload: String?) {
        when (event) {
            SocketConstants.NATIVE_CONNECT -> {
                nativeConnected = true
                RuntimeDiagnostics.event("Socket", "connected")
                _connectionState.value = RealtimeConnectionState.Connected
                backoffMs = INITIAL_BACKOFF_MS
                reconnectAttempt = 0
            }
            SocketConstants.NATIVE_DISCONNECT -> {
                nativeConnected = false
                RealtimeTransportClock.recordSocketDisconnected()
                RuntimeDiagnostics.event("Socket", "disconnected")
                _connectionState.value = RealtimeConnectionState.Disconnected
                scheduleReconnect()
            }
            SocketConstants.NATIVE_CONNECT_ERROR -> {
                nativeConnected = false
                _connectionState.value = RealtimeConnectionState.Disconnected
                Logger.withTag("SocketIO").w { "connect_error: ${payload ?: "unknown"}" }
                scheduleReconnect()
            }
            SocketConstants.SOCKET_READY -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseReady(raw) }
                    .onSuccess { readyEvent ->
                        log.d { "socket ready for userId=${readyEvent.userId}" }
                        RealtimeTransportClock.recordSocketReady(
                            userId = readyEvent.userId,
                            socketId = null,
                        )
                        _connectionState.value = RealtimeConnectionState.Ready(readyEvent.userId)
                        _events.emit(readyEvent)
                    }
                    .onFailure { error -> log.w(error) { "failed to parse socket:ready: $raw" } }
            }
            SocketConstants.MESSAGE_NEW -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseMessageNew(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse message:new: $raw" } }
            }
            SocketConstants.MESSAGE_STATUS -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseMessageStatus(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse message:status: $raw" } }
            }
            SocketConstants.TYPING -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseTyping(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse typing: $raw" } }
            }
            SocketConstants.PRESENCE_UPDATE -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parsePresenceUpdate(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse presence:update: $raw" } }
            }
            SocketConstants.CALL_INCOMING -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseCallIncoming(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse call:incoming: $raw" } }
            }
            SocketConstants.CALL_ACCEPTED -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseCallAccepted(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse call:accepted: $raw" } }
            }
            SocketConstants.CALL_REJECTED -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseCallRejected(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse call:rejected: $raw" } }
            }
            SocketConstants.CALL_ENDED -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseCallEnded(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse call:ended: $raw" } }
            }
            SocketConstants.CALL_SYNC -> {
                val raw = payload ?: return
                runCatching { socketEventParser.parseCallSync(raw) }
                    .onSuccess { _events.emit(it) }
                    .onFailure { error -> log.w(error) { "failed to parse call:sync: $raw" } }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt += 1
            RuntimeDiagnostics.event("Socket", "reconnect attempt=$reconnectAttempt backoffMs=$backoffMs")
            _connectionState.value = RealtimeConnectionState.Reconnecting(reconnectAttempt)
            delay(backoffMs)
            backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            val token = tokenStorage.getAccessToken() ?: activeAccessToken
            if (token.isNullOrBlank()) {
                _connectionState.value = RealtimeConnectionState.Disconnected
                return@launch
            }
            activeAccessToken = token
            connectInternal(token)
        }
    }
}

actual fun createChatRealtimeClient(
    socketEventParser: SocketEventParser,
    tokenStorage: TokenStorage,
    dispatcherProvider: DispatcherProvider,
): ChatRealtimeClient = SocketChatRealtimeClient(
    socketEventParser = socketEventParser,
    tokenStorage = tokenStorage,
    dispatcherProvider = dispatcherProvider,
)
